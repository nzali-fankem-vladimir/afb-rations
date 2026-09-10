package cm.afrilandfirstbank.rations.workflow.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cm.afrilandfirstbank.rations.commun.audit.DeltaAudit;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.workflow.domaine.EtapeWorkflow;
import cm.afrilandfirstbank.rations.workflow.domaine.NomEtapeEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.PieceJointe;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.TransitionProcessus;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.TransitionProcessusInterditeException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.EtapeWorkflowRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.PieceJointeRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ProcessusMensuelRepository;

/**
 * Le seul bloc transactionnel de la soumission.
 *
 * <h2>Pourquoi une classe a part plutot qu'une methode de SoumissionService</h2>
 *
 * <p>Deux raisons, dans cet ordre.
 *
 * <ol>
 *   <li><b>Technique.</b> Spring pose ses transactions par mandataire :
 *       {@code @Transactional} sur une methode appelee depuis la meme classe n'a
 *       aucun effet. La transaction serait absente, et rien ne le signalerait.</li>
 *   <li><b>Lisible.</b> La frontiere devient visible dans la structure du code.
 *       On voit, sans lire une annotation, ou commence l'ecriture en base et ou
 *       s'arretent les appels reseau et l'ecriture disque.</li>
 * </ol>
 *
 * <p><b>Aucun appel reseau ici</b>, ni ecriture de fichier : uniquement des
 * ecritures en base. Une transaction ouverte pendant un appel reseau
 * immobiliserait une connexion pour toute sa duree (doctrines des Sprints 2.3,
 * 3.4 et 4.1).
 *
 * <h2>Ce qui est deja acquis quand cette methode commence</h2>
 *
 * <p>Le fichier existe sur le stockage, complet, {@code fsync} effectue et
 * renomme atomiquement. Incrementer {@code piece_jointe.nombre_signatures} ici est
 * donc legitime : le compteur constate une ecriture, il ne l'anticipe pas
 * (migration V3).
 */
@Component
public class EnregistrementSoumission {

    private static final String ENTITE_CIBLE = "processus_mensuel";
    private static final String ACTION_SOUMISSION = "SOUMISSION_PROCESSUS";

    /** Premier pas du circuit : {@code etape_workflow.ordre_etape} vaut 1. */


    private final ProcessusMensuelRepository processusMensuelRepository;
    private final EtapeWorkflowRepository etapeWorkflowRepository;
    private final PieceJointeRepository pieceJointeRepository;
    private final PublicateurAudit publicateurAudit;

    public EnregistrementSoumission(ProcessusMensuelRepository processusMensuelRepository,
            EtapeWorkflowRepository etapeWorkflowRepository,
            PieceJointeRepository pieceJointeRepository,
            PublicateurAudit publicateurAudit) {
        this.processusMensuelRepository = processusMensuelRepository;
        this.etapeWorkflowRepository = etapeWorkflowRepository;
        this.pieceJointeRepository = pieceJointeRepository;
        this.publicateurAudit = publicateurAudit;
    }

    /**
     * Enregistre la soumission : montant, transitions, etape signee, piece jointe,
     * audit.
     *
     * @param montantTotal total recopie de l'etat consolide, jamais readdiitionne
     * @param signature preuve d'ecriture rendue par {@link SignatureService}
     */
    @Transactional
    public ResultatSoumission enregistrer(Long idProcessus, long montantTotal,
            ActeurSignataire acteur, ResultatSignature signature, EtatConsolide etat,
            String adresseIp) {

        // Relecture dans la transaction. Entre le controle de SoumissionService et
        // cet instant, trois appels reseau et une ecriture disque se sont ecoules :
        // une seconde requete a eu tout le temps de soumettre le meme etat.
        ProcessusMensuel processus = processusMensuelRepository.findById(idProcessus)
                .orElseThrow(() -> new ProcessusIntrouvableException(idProcessus));

        exigerStatutEncoreSoumissible(processus);

        StatutEnum statutAvant = processus.getStatut();
        int montantAvant = processus.getMontantTotal();

        processus.reporterMontantTotal((int) montantTotal);

        // LA REPRISE, quand l'etat revient d'un retour. Elle est portee ici et nulle
        // part ailleurs : le contrat d'API ne prevoit pas d'endpoint de reprise, et en
        // creer un en ferait un septieme. Le dossier est donc reste visiblement
        // RETOURNE jusqu'a cet instant — ce qui permet a l'agent de le reconnaitre
        // dans sa liste — et la transition n'est appliquee qu'au moment ou il
        // resoumet.
        if (processus.getStatut() == StatutEnum.RETOURNE) {
            TransitionProcessus.reprendreParAgent(processus);
        }

        // Les deux transitions d'ET01, dans l'ordre. La soumission mene toujours au
        // Chef d'Unite : l'aiguillage au seuil (RG-08) intervient APRES sa validation
        // (sous-sprint 4.3). Une resoumission repart donc du DEBUT du circuit, jamais
        // du niveau ou le retour avait eu lieu (RG-07).
        TransitionProcessus.soumettre(processus);
        TransitionProcessus.transfererAuChefUnite(processus);

        EtapeWorkflow etape = new EtapeWorkflow(idProcessus, acteur.id(),
                ordreEtapeSuivant(idProcessus), NomEtapeEnum.SOUMISSION_AGENT);
        etape.validerAvecSignature(signature.empreinte());

        // Premiere soumission : la piece jointe nait a une signature, le fichier en
        // porte deja une et son ecriture est confirmee. Resoumission : le document a
        // ete REGENERE depuis l'etat corrige, et le compteur repart a un — les visas
        // d'avant le retour ont disparu avec l'ancien fichier.
        PieceJointe pieceJointe = pieceJointeRepository.findByIdProcessus(idProcessus)
                .map(existante -> {
                    existante.regenererApresRetour(signature.document().cheminRelatif());
                    return existante;
                })
                .orElseGet(() -> new PieceJointe(
                        idProcessus, signature.document().cheminRelatif()));

        ProcessusMensuel enregistre = processusMensuelRepository.save(processus);
        EtapeWorkflow etapeEnregistree = etapeWorkflowRepository.save(etape);
        PieceJointe pieceEnregistree = pieceJointeRepository.save(pieceJointe);

        publierSoumission(enregistre, acteur, signature, etat, statutAvant, montantAvant, adresseIp);

        return new ResultatSoumission(enregistre, pieceEnregistree, etapeEnregistree);
    }

    /**
     * Garde de concurrence.
     *
     * <p>Si une autre requete a soumis le meme etat entretemps, le statut n'est
     * plus {@link StatutEnum#EN_COURS_SAISIE} et l'on refuse ici. La contrainte
     * {@code id_processus UNIQUE} de {@code piece_jointe} et la machine a etats
     * refuseraient de toute facon, mais avec un message technique ; ce controle
     * rend un refus lisible.
     *
     * <p>Le fichier ecrit par la requete perdante reste sur le stockage, orphelin.
     * C'est l'asymetrie assumee du Sprint 4.2 — et le renommage <i>sans</i>
     * {@code REPLACE_EXISTING} garantit qu'elle n'a pas ecrase le document signe de
     * la requete gagnante.
     */
    private void exigerStatutEncoreSoumissible(ProcessusMensuel processus) {
        if (processus.getStatut() != StatutEnum.EN_COURS_SAISIE
                && processus.getStatut() != StatutEnum.RETOURNE) {
            throw new TransitionProcessusInterditeException(
                    "L'etat " + processus.getId() + " a change de statut pendant la preparation "
                            + "de la soumission : il est passe a " + processus.getStatut()
                            + ". Il a vraisemblablement ete soumis par ailleurs. Rechargez le "
                            + "dossier avant toute nouvelle action.");
        }
    }

    /**
     * Rang du pas dans le parcours : celui du dernier pas connu, plus un.
     *
     * <p>Calcule plutot que fixe a un. Une resoumission apres retour ouvre un nouveau
     * cycle sur un parcours qui compte deja des etapes ; un rang constant ferait
     * apparaitre deux soumissions de rang un, et le decoupage en cycles de validation
     * — sur lequel repose RG-12 — ne saurait plus laquelle est la derniere
     * ({@code CycleValidation}).
     */
    private int ordreEtapeSuivant(Long idProcessus) {
        return etapeWorkflowRepository.findFirstByIdProcessusOrderByOrdreEtapeDesc(idProcessus)
                .map(derniere -> derniere.getOrdreEtape() + 1)
                .orElse(1);
    }

    /**
     * Trace la soumission (CT-04, CLAUDE.md section 12).
     *
     * <p><b>Premier evenement de ce service a porter un {@code idUtilisateur}.</b>
     * Le declenchement du Sprint 4.1 le laissait nul, faute d'identifiant local :
     * {@code GET /identite/habilitation} ne rend qu'un {@code login}. La soumission
     * appelle {@code GET /identite/moi} pour {@code etape_workflow.id_acteur} et
     * dispose donc de l'identifiant.
     *
     * <p>La publication a lieu dans la transaction ; l'envoi sur le topic, lui, est
     * differe apres le commit par {@code rations-audit-commun}
     * ({@code @TransactionalEventListener(AFTER_COMMIT)}, doctrine Sprint 1.3). Un
     * rollback ne laisse donc jamais la trace d'une soumission annulee.
     */
    private void publierSoumission(ProcessusMensuel processus, ActeurSignataire acteur,
            ResultatSignature signature, EtatConsolide etat, StatutEnum statutAvant,
            int montantAvant, String adresseIp) {

        publicateurAudit.publier(EvenementAudit.de(
                acteur.id(),
                ACTION_SOUMISSION,
                ENTITE_CIBLE,
                processus.getId(),
                adresseIp,
                DeltaAudit.nouveau()
                        .champ("statut", statutAvant, processus.getStatut())
                        .champ("montantTotal", montantAvant, processus.getMontantTotal())
                        .contexte("auteur", acteur.login())
                        .contexte("role", acteur.role())
                        .contexte("codeUnite", processus.getCodeUnite())
                        .contexte("dateDebut", processus.getDateDebut())
                        .contexte("dateFin", processus.getDateFin())
                        .contexte("nombreJournees", etat.nombreJournees())
                        .contexte("nombreLignes", etat.nombreLignes())
                        .contexte("pieceJointe", signature.document().cheminRelatif())
                        // L'empreinte dans l'audit permet de recouper le document
                        // archive avec une trace que le module ne peut pas reecrire.
                        .contexte("empreinte", signature.empreinte())
                        .enJson()));
    }

}
