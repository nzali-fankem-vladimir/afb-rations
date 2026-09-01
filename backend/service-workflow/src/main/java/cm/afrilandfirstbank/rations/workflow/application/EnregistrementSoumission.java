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
    private static final int ORDRE_SOUMISSION = 1;

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

        // Les deux transitions d'ET01, dans l'ordre. La soumission mene toujours au
        // Chef d'Unite : l'aiguillage au seuil (RG-08) intervient APRES sa
        // validation, au sous-sprint 4.3.
        TransitionProcessus.soumettre(processus);
        TransitionProcessus.transfererAuChefUnite(processus);

        EtapeWorkflow etape = new EtapeWorkflow(
                idProcessus, acteur.id(), ORDRE_SOUMISSION, NomEtapeEnum.SOUMISSION_AGENT);
        etape.validerAvecSignature(signature.empreinte());

        // La piece jointe nait a une signature : le fichier en porte deja une, et
        // son ecriture est confirmee.
        PieceJointe pieceJointe = new PieceJointe(
                idProcessus, signature.document().cheminRelatif());

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
        if (processus.getStatut() != StatutEnum.EN_COURS_SAISIE) {
            throw new TransitionProcessusInterditeException(
                    "L'etat " + processus.getId() + " a change de statut pendant la preparation "
                            + "de la soumission : il est passe a " + processus.getStatut()
                            + ". Il a vraisemblablement ete soumis par ailleurs. Rechargez le "
                            + "dossier avant toute nouvelle action.");
        }
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
                        .contexte("moisPaiement", processus.getMoisPaiement())
                        .contexte("anneePaiement", processus.getAnneePaiement())
                        .contexte("nombreJournees", etat.nombreJournees())
                        .contexte("nombreLignes", etat.nombreLignes())
                        .contexte("pieceJointe", signature.document().cheminRelatif())
                        // L'empreinte dans l'audit permet de recouper le document
                        // archive avec une trace que le module ne peut pas reecrire.
                        .contexte("empreinte", signature.empreinte())
                        .enJson()));
    }

}
