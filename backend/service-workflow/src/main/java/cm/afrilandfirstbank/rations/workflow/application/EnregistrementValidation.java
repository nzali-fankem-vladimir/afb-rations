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
import cm.afrilandfirstbank.rations.workflow.domaine.exception.DocumentNonProduitException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.TransitionProcessusInterditeException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.EtapeWorkflowRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.PieceJointeRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ProcessusMensuelRepository;

/**
 * Le seul bloc transactionnel de la validation du Chef d'Unite.
 *
 * <p>Classe a part pour les deux raisons du Sprint 4.2, dans le meme ordre.
 * <b>Technique</b> : Spring pose ses transactions par mandataire, et
 * {@code @Transactional} sur une methode appelee depuis la meme classe n'a aucun
 * effet — la transaction serait absente sans que rien ne le signale.
 * <b>Lisible</b> : la frontiere entre les appels reseau, l'ecriture disque et les
 * ecritures en base devient visible dans la structure du code.
 *
 * <p><b>Aucun appel reseau ici</b>, ni ecriture de fichier. Quand cette methode
 * commence, le document estampe est deja sur le stockage, {@code fsync} effectue et
 * renomme atomiquement : incrementer {@code piece_jointe.nombre_signatures}
 * <i>constate</i> donc une ecriture, il ne l'anticipe pas (migration V3).
 *
 * <h2>La cloture ne transmet rien a la comptabilite</h2>
 *
 * <p>{@code transmis_comptabilite} n'est jamais touche ici : il reste a
 * {@code false}. La publication sur {@code rations.etat.valide} est le Sprint 5, et
 * c'est elle qui posera le drapeau. Le poser des la cloture contournerait le verrou
 * de transmission unique de RG-13 : l'etat paraitrait transmis avant de l'etre, et
 * une transmission reelle serait ensuite refusee comme un doublon.
 */
@Component
public class EnregistrementValidation {

    private static final String ENTITE_CIBLE = "processus_mensuel";
    private static final String ACTION_VALIDATION = "VALIDATION_PROCESSUS";

    private final ProcessusMensuelRepository processusMensuelRepository;
    private final EtapeWorkflowRepository etapeWorkflowRepository;
    private final PieceJointeRepository pieceJointeRepository;
    private final PublicateurAudit publicateurAudit;

    public EnregistrementValidation(ProcessusMensuelRepository processusMensuelRepository,
            EtapeWorkflowRepository etapeWorkflowRepository,
            PieceJointeRepository pieceJointeRepository,
            PublicateurAudit publicateurAudit) {
        this.processusMensuelRepository = processusMensuelRepository;
        this.etapeWorkflowRepository = etapeWorkflowRepository;
        this.pieceJointeRepository = pieceJointeRepository;
        this.publicateurAudit = publicateurAudit;
    }

    /**
     * Enregistre la validation : etape signee, compteur de signatures, transition
     * d'aiguillage, audit.
     *
     * @param signature preuve d'ecriture rendue par {@link SignatureService}
     * @param aiguillage la decision RG-08, deja prise hors transaction
     */
    @Transactional
    public ResultatValidation enregistrer(Long idProcessus, ActeurSignataire acteur,
            ResultatSignature signature, ResultatAiguillage aiguillage, String adresseIp) {

        // Relecture dans la transaction. Entre le controle de ValidationService et cet
        // instant, deux appels reseau et une ecriture disque se sont ecoules : une
        // seconde requete a eu tout le temps de valider le meme etat.
        ProcessusMensuel processus = processusMensuelRepository.findById(idProcessus)
                .orElseThrow(() -> new ProcessusIntrouvableException(idProcessus));

        exigerStatutEncoreValidable(processus);
        exigerMontantInchange(processus, aiguillage);

        PieceJointe pieceJointe = pieceJointeRepository.findByIdProcessus(idProcessus)
                .orElseThrow(() -> new DocumentNonProduitException(
                        "La piece jointe du processus " + idProcessus + " a disparu pendant la "
                                + "validation : la signature vient pourtant d'etre ecrite sur le "
                                + "stockage. Rien n'est enregistre."));

        StatutEnum statutAvant = processus.getStatut();

        EtapeWorkflow etape = new EtapeWorkflow(idProcessus, acteur.id(),
                ordreEtapeSuivant(idProcessus), NomEtapeEnum.VALIDATION_DA);
        etape.validerAvecSignature(signature.empreinte());

        // Le fichier porte desormais une signature de plus, et son ecriture est
        // confirmee : le compteur passe a deux. Il ne repart jamais a un — le document
        // est enrichi, jamais regenere.
        pieceJointe.enregistrerSignatureSupplementaire();

        appliquerAiguillage(processus, aiguillage);

        ProcessusMensuel enregistre = processusMensuelRepository.save(processus);
        EtapeWorkflow etapeEnregistree = etapeWorkflowRepository.save(etape);
        PieceJointe pieceEnregistree = pieceJointeRepository.save(pieceJointe);

        publierValidation(enregistre, acteur, signature, aiguillage, statutAvant, adresseIp);

        return new ResultatValidation(
                enregistre, pieceEnregistree, etapeEnregistree, aiguillage);
    }

    // --- Application de la decision ---------------------------------------------

    /**
     * Applique la transition correspondant a la decision d'aiguillage.
     *
     * <p><b>Cette methode ne compare rien.</b> Elle traduit une decision deja prise
     * par {@link AiguillageService} en l'une des deux transitions d'ET01 qui partent
     * de {@link StatutEnum#EN_ATTENTE_DA}. C'est ce qui garantit qu'il n'existe,
     * dans tout le module, qu'un seul endroit ou un montant est compare a un seuil :
     * refaire la comparaison ici — ne serait-ce que « pour verifier » — creerait un
     * second chemin, donc une divergence possible.
     *
     * <p>Le {@code switch} sur l'enumeration est exhaustif : ajouter une troisieme
     * issue a {@link DecisionAiguillage} un jour ferait echouer la compilation ici,
     * plutot que de laisser une decision sans effet.
     */
    private void appliquerAiguillage(ProcessusMensuel processus, ResultatAiguillage aiguillage) {
        switch (aiguillage.decision()) {
            case SOUS_SEUIL_CLOTURE_DIRECTE ->
                    TransitionProcessus.cloturerApresValidationChefUnite(processus);
            case ENVOI_DIRECTEUR_RESEAU ->
                    TransitionProcessus.aiguillerVersDirecteurReseau(processus);
        }
    }

    /**
     * Rang du pas dans le parcours de l'etat : celui du dernier pas connu, plus un.
     *
     * <p>Calcule plutot que fixe a deux. Un etat retourne puis resoumis (sous-sprint
     * 4.4) repassera par le chef d'unite, et son parcours comptera alors plus de
     * trois pas : un rang constant ferait apparaitre deux etapes de meme ordre, et
     * l'historique du dossier deviendrait ambigu a la lecture.
     */
    private int ordreEtapeSuivant(Long idProcessus) {
        return etapeWorkflowRepository.findFirstByIdProcessusOrderByOrdreEtapeDesc(idProcessus)
                .map(derniere -> derniere.getOrdreEtape() + 1)
                .orElse(1);
    }

    // --- Gardes de concurrence ---------------------------------------------------

    /**
     * Si une autre requete a valide le meme etat entretemps, le statut n'est plus
     * {@link StatutEnum#EN_ATTENTE_DA} et l'on refuse ici — la machine a etats
     * refuserait de toute facon, mais avec un message technique.
     *
     * <p>Le document a en revanche deja recu la seconde mention : c'est l'asymetrie
     * assumee du Sprint 4.2, transposee. Un visa en trop sur un PDF se constate et
     * s'explique ; un compteur affirmant une signature absente du fichier, non.
     */
    private void exigerStatutEncoreValidable(ProcessusMensuel processus) {
        if (processus.getStatut() != StatutEnum.EN_ATTENTE_DA) {
            throw new TransitionProcessusInterditeException(
                    "L'etat " + processus.getId() + " a change de statut pendant la preparation "
                            + "de la validation : il est passe a " + processus.getStatut()
                            + ". Il a vraisemblablement ete valide par ailleurs. Rechargez le "
                            + "dossier avant toute nouvelle action.");
        }
    }

    /**
     * Le montant qui a produit la decision doit etre celui qui est encore enregistre.
     *
     * <p>Deux lignes qui ne devraient jamais se declencher : le montant n'est ecrit
     * qu'a la soumission, et le statut {@link StatutEnum#EN_ATTENTE_DA} garantit
     * qu'elle est passee. C'est precisement pourquoi le controle est peu couteux — et
     * il rend explicite l'invariant de RG-08 : <b>la decision appliquee est celle qui
     * correspond au montant enregistre</b>, pas a un montant lu quelque part avant.
     * Sans lui, cet invariant reposerait sur un raisonnement juste aujourd'hui et
     * fragile a la prochaine evolution du circuit.
     */
    private void exigerMontantInchange(ProcessusMensuel processus, ResultatAiguillage aiguillage) {
        if (processus.getMontantTotal() != aiguillage.montantTotalFcfa()) {
            throw new TransitionProcessusInterditeException(
                    "Le montant de l'etat " + processus.getId() + " a change pendant la validation ("
                            + aiguillage.montantTotalFcfa() + " puis " + processus.getMontantTotal()
                            + " FCFA) : l'aiguillage a ete decide sur une valeur qui n'est plus "
                            + "celle du dossier. La validation est refusee. Rechargez le dossier.");
        }
    }

    // --- Audit -------------------------------------------------------------------

    /**
     * Trace la validation et la decision d'aiguillage (CLAUDE.md section 12).
     *
     * <p><b>Le seuil applique est enregistre avec le montant.</b> Un controle interne
     * qui relit cette trace dans six mois doit pouvoir refaire la comparaison
     * lui-meme, meme si le parametre a change depuis : sans le seuil du jour, la
     * trace dirait qu'un etat de 84 000 FCFA a ete cloture, sans permettre de juger
     * si c'etait la bonne decision.
     *
     * <p>Publication dans la transaction ; envoi sur le topic differe apres le commit
     * par {@code rations-audit-commun} (doctrine Sprint 1.3). Un rollback ne laisse
     * donc jamais la trace d'une validation annulee.
     */
    private void publierValidation(ProcessusMensuel processus, ActeurSignataire acteur,
            ResultatSignature signature, ResultatAiguillage aiguillage, StatutEnum statutAvant,
            String adresseIp) {

        publicateurAudit.publier(EvenementAudit.de(
                acteur.id(),
                ACTION_VALIDATION,
                ENTITE_CIBLE,
                processus.getId(),
                adresseIp,
                DeltaAudit.nouveau()
                        .champ("statut", statutAvant, processus.getStatut())
                        .contexte("etape", NomEtapeEnum.VALIDATION_DA)
                        .contexte("auteur", acteur.login())
                        .contexte("role", acteur.role())
                        .contexte("codeUnite", processus.getCodeUnite())
                        .contexte("moisPaiement", processus.getMoisPaiement())
                        .contexte("anneePaiement", processus.getAnneePaiement())
                        .contexte("montantTotal", aiguillage.montantTotalFcfa())
                        .contexte("seuilApplique", aiguillage.seuilApplique())
                        .contexte("aiguillage", aiguillage.decision())
                        .contexte("pieceJointe", signature.document().cheminRelatif())
                        .contexte("empreinte", signature.empreinte())
                        .enJson()));
    }

}
