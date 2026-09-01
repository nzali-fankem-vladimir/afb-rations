package cm.afrilandfirstbank.rations.workflow.application;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.workflow.domaine.NomEtapeEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.PieceJointe;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.AgentNonHabiliteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.DocumentNonProduitException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ServiceIdentiteIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.SeuilIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.TransitionProcessusInterditeException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.PieceJointeRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ProcessusMensuelRepository;

/**
 * Validation de premier niveau par le Chef d'Unite (US-08, CT-14, CT-15, RG-07,
 * RG-08, RG-09).
 *
 * <h2>L'ordre des operations, comme a la soumission</h2>
 *
 * <pre>
 *   HORS TRANSACTION
 *     1. charger le processus                        -&gt; 404
 *     2. habilitation sur l'unite DU PROCESSUS       -&gt; 403 / 503
 *     3. statut : EN_ATTENTE_DA uniquement           -&gt; 422
 *     4. la piece jointe existe                      -&gt; 500
 *     5. profil du valideur (GET /identite/moi)      -&gt; 403 / 503
 *     6. [ point d'accroche RG-12, sous-sprint 4.4 ]
 *     7. AIGUILLAGE : lecture du seuil, decision     -&gt; 500
 *     8. ESTAMPER, ECRIRE, CONFIRMER                 -&gt; 500
 *
 *   TRANSACTION ({@link EnregistrementValidation})
 *     9. relire, revalider le statut et le montant (concurrence)
 *    10. etape VALIDATION_DA, VALIDEE, signee
 *    11. piece jointe : nombre_signatures passe a deux
 *    12. transition CLOTURE ou EN_ATTENTE_DR, selon la decision
 *    13. audit (publie apres le commit)
 * </pre>
 *
 * <h2>Pourquoi l'aiguillage precede l'ecriture du document</h2>
 *
 * <p>Parce qu'un seuil illisible doit arreter la validation <b>avant</b> qu'une
 * signature ne soit gravee dans le PDF. Dans l'ordre inverse, un parametre absent
 * laisserait un document estampe d'un visa que la base ne connaitrait jamais : la
 * piece archivee affirmerait une validation qui n'a pas eu lieu. C'est le meme
 * raisonnement qu'au Sprint 4.2 pour la completude — tous les refus possibles sont
 * epuises avant la premiere ecriture.
 *
 * <h2>Le montant n'est jamais recalcule ici</h2>
 *
 * <p>{@link AiguillageService} compare le montant <b>enregistre a la soumission</b>.
 * Redemander l'etat consolide au service Saisie ferait dependre l'aiguillage d'une
 * grille tarifaire modifiee entretemps, et le chef d'unite validerait un montant
 * different de celui qu'il a lu et signe.
 *
 * <h2>La cloture ne transmet rien</h2>
 *
 * <p>{@code transmis_comptabilite} reste faux. La publication sur
 * {@code rations.etat.valide} est le Sprint 5 ; l'anticiper ici contournerait le
 * verrou de transmission unique de RG-13.
 *
 * <h2>Ce qui n'est pas de ce sous-sprint</h2>
 *
 * <p>La separation des taches (RG-12) et le retour a l'agent (RG-10, RG-11) sont le
 * sous-sprint 4.4. Le point d'accroche de RG-12 est marque a sa place exacte
 * ci-dessous : le profil du valideur, donc son identifiant local, est deja connu a
 * cet instant, et {@code EtapeWorkflowRepository.findByIdProcessusAndIdActeur}
 * existe depuis le Sprint 4.1. Il n'y a rien a reorganiser pour l'y inserer.
 */
@Service
public class ValidationService {

    private final ProcessusMensuelRepository processusMensuelRepository;
    private final PieceJointeRepository pieceJointeRepository;
    private final HabilitationService habilitationService;
    private final ProfilClient profilClient;
    private final AiguillageService aiguillageService;
    private final SignatureService signatureService;
    private final EnregistrementValidation enregistrementValidation;

    public ValidationService(ProcessusMensuelRepository processusMensuelRepository,
            PieceJointeRepository pieceJointeRepository,
            HabilitationService habilitationService,
            ProfilClient profilClient,
            AiguillageService aiguillageService,
            SignatureService signatureService,
            EnregistrementValidation enregistrementValidation) {
        this.processusMensuelRepository = processusMensuelRepository;
        this.pieceJointeRepository = pieceJointeRepository;
        this.habilitationService = habilitationService;
        this.profilClient = profilClient;
        this.aiguillageService = aiguillageService;
        this.signatureService = signatureService;
        this.enregistrementValidation = enregistrementValidation;
    }

    /**
     * Valide l'etat au niveau du Chef d'Unite et applique l'aiguillage RG-08.
     *
     * <p><b>Non transactionnelle</b>, comme la soumission : deux appels reseau et une
     * ecriture disque s'y enchainent, et une transaction ouverte autour
     * immobiliserait une connexion a la base pendant toute leur duree (doctrines des
     * Sprints 2.3, 3.4, 4.1 et 4.2). Le seul bloc transactionnel est
     * {@link EnregistrementValidation}.
     *
     * @param idProcessus l'etat a valider
     * @param enteteAutorisation en-tete {@code Authorization} du chef d'unite, relaye
     *        tel quel aux appels sortants (doctrine Sprint 1.3)
     * @param adresseIp origine de la requete, pour la trace d'audit
     * @throws SeuilIndisponibleException si le seuil RG-08 n'est pas lisible : la
     *         validation est refusee avant toute ecriture
     */
    public ResultatValidation valider(Long idProcessus, String enteteAutorisation,
            String adresseIp) {

        // 1. Le processus existe ? Question posee avant toute autre : un identifiant
        //    inconnu se refuse en 404 sans interroger le service Identite.
        ProcessusMensuel processus = processusMensuelRepository.findById(idProcessus)
                .orElseThrow(() -> new ProcessusIntrouvableException(idProcessus));

        // 2. Portee d'acces, sur l'unite DU PROCESSUS et non sur un parametre. Le role
        //    CHEF_UNITE_DA est deja exige par le controleur ; il reste a savoir si CE
        //    chef d'unite a portee sur CETTE unite (RG-12).
        habilitationService.exigerHabilitationSurUnite(processus.getCodeUnite(), enteteAutorisation);

        // 3. Le statut permet-il la validation de premier niveau ?
        exigerStatutValidableParChefUnite(processus);

        // 4. Le document existe : on l'enrichit, on ne le recree jamais.
        PieceJointe pieceJointe = pieceJointeOuRefus(processus);

        // 5. Le valideur. Appel impose par etape_workflow.id_acteur, NOT NULL.
        ActeurSignataire acteur = profilOuRefus(enteteAutorisation);

        // 6. POINT D'ACCROCHE RG-12 (sous-sprint 4.4). Ici, et pas ailleurs : le
        //    profil du valideur vient d'etre obtenu, donc son identifiant local est
        //    connu, et rien n'a encore ete ecrit ni sur le disque ni en base. Le
        //    controle consistera a verifier que cet acteur n'a pas deja agi sur ce
        //    dossier (EtapeWorkflowRepository.findByIdProcessusAndIdActeur), et a
        //    refuser en 403 SEPARATION_TACHES le cas echeant.

        // 7. RG-08. Place AVANT toute ecriture : un seuil illisible doit arreter la
        //    validation avant qu'une signature ne soit gravee dans le document.
        ResultatAiguillage aiguillage = aiguillageService.aiguiller(processus);

        // 8. Un seul horodatage pour tout l'acte, comme a la soumission : la mention
        //    imprimee et la ligne etape_workflow portent le meme instant.
        LocalDateTime horodatage = LocalDateTime.now();

        ResultatSignature signature = signatureService.enrichirEtSigner(
                pieceJointe, acteur, NomEtapeEnum.VALIDATION_DA, horodatage);

        // 9 a 13. Le document porte la signature et son ecriture est confirmee : on
        //         peut ecrire en base.
        return enregistrementValidation.enregistrer(
                idProcessus, acteur, signature, aiguillage, adresseIp);
    }

    // --- Regles -----------------------------------------------------------------

    /**
     * Seul {@link StatutEnum#EN_ATTENTE_DA} ouvre la validation de premier niveau
     * (RG-07 : aucun saut de niveau).
     *
     * <p>Le message nomme le statut reel <b>et l'action attendue</b>, comme au Sprint
     * 3.3 cote Saisie : un « validation impossible » sec laisserait le chef d'unite
     * sans recours, devant un dossier qu'il croit lui appartenir.
     *
     * <p>Le controle est fait ici <b>et</b> revalide dans la transaction : entre les
     * deux, deux appels reseau et une ecriture disque laissent le temps a une seconde
     * requete de passer.
     */
    private void exigerStatutValidableParChefUnite(ProcessusMensuel processus) {
        if (processus.getStatut() == StatutEnum.EN_ATTENTE_DA) {
            return;
        }

        String suite = switch (processus.getStatut()) {
            case EN_COURS_SAISIE -> " L'agent n'a pas encore soumis cet etat : il n'y a rien a "
                    + "valider tant qu'il est en saisie.";
            case SOUMIS -> " La soumission de cet etat est en cours d'enregistrement ; "
                    + "reaffichez le dossier dans un instant.";
            case EN_ATTENTE_DR -> " Cet etat a deja recu la validation du chef d'unite et attend "
                    + "celle du directeur reseau : son montant depasse le seuil d'aiguillage.";
            case RETOURNE -> " Cet etat a ete retourne a l'agent pour correction ; il vous "
                    + "reviendra apres une nouvelle soumission.";
            case CLOTURE -> " Cet etat est cloture, donc definitif. Une regularisation passe par "
                    + "un etat complementaire qui le reference, jamais par sa reouverture.";
            default -> "";
        };

        throw new TransitionProcessusInterditeException(
                "L'etat " + libellePeriode(processus) + " de l'unite " + processus.getCodeUnite()
                        + " ne peut pas etre valide par le chef d'unite : son statut est "
                        + processus.getStatut() + ", alors que cette validation exige "
                        + StatutEnum.EN_ATTENTE_DA + "." + suite);
    }

    /**
     * Le document doit exister : la validation l'<b>enrichit</b>, elle ne le cree
     * jamais.
     *
     * <p>Son absence sur un etat {@code EN_ATTENTE_DA} est une incoherence, pas un
     * cas metier : la soumission ne peut pas avoir abouti sans produire de piece
     * jointe. D'ou un {@code 500} — le chef d'unite n'a rien fait de faux et n'a rien
     * a corriger — et non un refus metier qui l'enverrait chercher une faute
     * inexistante. Meme parti qu'au Sprint 2.4 pour {@code INCOHERENCE_GRILLE} : le
     * service signale, il n'improvise pas.
     */
    private PieceJointe pieceJointeOuRefus(ProcessusMensuel processus) {
        return pieceJointeRepository.findByIdProcessus(processus.getId())
                .orElseThrow(() -> new DocumentNonProduitException(
                        "Aucun document n'est enregistre pour l'etat " + libellePeriode(processus)
                                + " de l'unite " + processus.getCodeUnite() + ", alors que son "
                                + "statut est " + processus.getStatut() + ". La validation appose "
                                + "une signature sur la piece existante et ne la recree jamais : "
                                + "elle est refusee. Signalez-le a l'administrateur du module."));
    }

    private ActeurSignataire profilOuRefus(String enteteAutorisation) {
        ResultatProfil resultat = profilClient.obtenir(enteteAutorisation);

        return switch (resultat) {
            case ResultatProfil.ProfilObtenu obtenu -> obtenu.acteur();
            case ResultatProfil.ProfilAbsent refus -> throw new AgentNonHabiliteException(
                    "La validation exige un profil ouvert dans le module : " + refus.motif()
                            + ". Rapprochez-vous de l'administrateur du module.");
            case ResultatProfil.ServiceIdentiteIndisponible panne ->
                    throw new ServiceIdentiteIndisponibleException(
                            "Le service Identite est momentanement indisponible : la validation "
                                    + "est refusee par precaution (" + panne.motifTechnique()
                                    + "). Le document n'a pas ete modifie. Reessayez dans un "
                                    + "instant.");
        };
    }

    private String libellePeriode(ProcessusMensuel processus) {
        return String.format("%02d/%d", processus.getMoisPaiement(), processus.getAnneePaiement());
    }

}
