package cm.afrilandfirstbank.rations.workflow.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cm.afrilandfirstbank.rations.commun.audit.DeltaAudit;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.workflow.domaine.EtapeWorkflow;
import cm.afrilandfirstbank.rations.workflow.domaine.NiveauValidation;
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
 * Le seul bloc transactionnel de la validation, aux deux niveaux du circuit.
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
 * <h2>Le niveau vient d'en haut, il n'est pas rededuit ici</h2>
 *
 * <p>{@link NiveauValidation} est passe en parametre par {@link ValidationService},
 * qui l'a lu sur le statut du dossier. Le rededuire ici a partir du role de l'acteur
 * ou du statut relu creerait un second endroit ou le niveau se decide, donc une
 * divergence possible sur la regle qui commande le niveau d'approbation de la
 * banque — la meme faute que d'ecrire une seconde comparaison au seuil.
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
     * @param niveau le niveau valide, designe par le statut du dossier
     * @param signature preuve d'ecriture rendue par {@link SignatureService}
     * @param aiguillage la decision RG-08 au premier niveau, <b>nulle au second</b> :
     *        apres le visa du directeur reseau il n'y a plus d'echelon, donc rien a
     *        arbitrer
     */
    @Transactional
    public ResultatValidation enregistrer(Long idProcessus, NiveauValidation niveau,
            ActeurSignataire acteur, ResultatSignature signature, ResultatAiguillage aiguillage,
            String adresseIp) {

        // Relecture dans la transaction. Entre le controle de ValidationService et cet
        // instant, deux appels reseau et une ecriture disque se sont ecoules : une
        // seconde requete a eu tout le temps de valider le meme etat.
        ProcessusMensuel processus = processusMensuelRepository.findById(idProcessus)
                .orElseThrow(() -> new ProcessusIntrouvableException(idProcessus));

        exigerStatutEncoreValidable(processus, niveau);
        exigerMontantInchange(processus, aiguillage);

        PieceJointe pieceJointe = pieceJointeRepository.findByIdProcessus(idProcessus)
                .orElseThrow(() -> new DocumentNonProduitException(
                        "La piece jointe du processus " + idProcessus + " a disparu pendant la "
                                + "validation : la signature vient pourtant d'etre ecrite sur le "
                                + "stockage. Rien n'est enregistre."));

        StatutEnum statutAvant = processus.getStatut();

        EtapeWorkflow etape = new EtapeWorkflow(idProcessus, acteur.id(),
                ordreEtapeSuivant(idProcessus), niveau.nomEtape());
        etape.validerAvecSignature(signature.empreinte());

        // Le fichier porte desormais une signature de plus, et son ecriture est
        // confirmee : le compteur avance d'un cran.
        pieceJointe.enregistrerSignatureSupplementaire();

        appliquerTransition(processus, niveau, aiguillage);

        ProcessusMensuel enregistre = processusMensuelRepository.save(processus);
        EtapeWorkflow etapeEnregistree = etapeWorkflowRepository.save(etape);
        PieceJointe pieceEnregistree = pieceJointeRepository.save(pieceJointe);

        publierValidation(enregistre, niveau, acteur, signature, aiguillage, statutAvant,
                adresseIp);

        return new ResultatValidation(
                enregistre, pieceEnregistree, etapeEnregistree, niveau, aiguillage);
    }

    // --- Application de la decision ---------------------------------------------

    /**
     * Applique la transition d'ET01 qui correspond au niveau valide.
     *
     * <p><b>Cette methode ne compare rien.</b> Au premier niveau, elle traduit une
     * decision deja prise par {@link AiguillageService} en l'une des deux transitions
     * qui partent de {@code EN_ATTENTE_DA}. C'est ce qui garantit qu'il n'existe, dans
     * tout le module, qu'un seul endroit ou un montant est compare a un seuil :
     * refaire la comparaison ici — ne serait-ce que « pour verifier » — creerait un
     * second chemin, donc une divergence possible.
     *
     * <p>Au second niveau, <b>il n'y a rien a arbitrer</b> : apres le visa du
     * directeur reseau, il n'y a plus d'echelon, et la seule issue est la cloture. Le
     * service d'aiguillage n'est meme pas appele en amont, donc {@code aiguillage} est
     * nul — un nul qui signifie « aucune decision a prendre », et non « decision
     * inconnue » : c'est le niveau qui le determine, pas l'inverse.
     *
     * <p>Les deux {@code switch} sont exhaustifs sur leurs enumerations : ajouter un
     * troisieme niveau ou une troisieme issue d'aiguillage ferait echouer la
     * compilation ici, plutot que de laisser une decision sans effet.
     */
    private void appliquerTransition(ProcessusMensuel processus, NiveauValidation niveau,
            ResultatAiguillage aiguillage) {

        switch (niveau) {
            case CHEF_UNITE -> appliquerAiguillage(processus, aiguillage);
            case DIRECTEUR_RESEAU ->
                    TransitionProcessus.cloturerApresValidationDirecteurReseau(processus);
        }
    }

    private void appliquerAiguillage(ProcessusMensuel processus, ResultatAiguillage aiguillage) {
        if (aiguillage == null) {
            throw new IllegalStateException(
                    "Aucune decision d'aiguillage pour la validation de premier niveau du "
                            + "processus " + processus.getId() + " : RG-08 exige que le montant "
                            + "ait ete confronte au seuil avant la transition.");
        }

        switch (aiguillage.decision()) {
            case SOUS_SEUIL_CLOTURE_DIRECTE ->
                    TransitionProcessus.cloturerApresValidationChefUnite(processus);
            case ENVOI_DIRECTEUR_RESEAU ->
                    TransitionProcessus.aiguillerVersDirecteurReseau(processus);
            // Meme transition que la precedente : la consequence est identique, seul le
            // motif differe (etat complementaire, seuil non lu). Les deux cas restent
            // distincts a dessein — les fondre ferait disparaitre des traces d'audit la
            // raison pour laquelle ce dossier est monte, alors que la regle qui la
            // produit est provisoire et devra etre retrouvee (Sprint 6bis.1).
            case COMPLEMENTAIRE_ENVOI_DIRECTEUR_RESEAU ->
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
    private void exigerStatutEncoreValidable(ProcessusMensuel processus,
            NiveauValidation niveau) {
        if (processus.getStatut() != niveau.statutRequis()) {
            throw new TransitionProcessusInterditeException(
                    "L'etat " + processus.getId() + " a change de statut pendant la preparation "
                            + "de la validation : il est passe a " + processus.getStatut()
                            + ", alors que la validation du " + niveau.libelle() + " exige "
                            + niveau.statutRequis() + ". Il a vraisemblablement ete traite par "
                            + "ailleurs. Rechargez le dossier avant toute nouvelle action.");
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
        if (aiguillage == null) {
            // Second niveau : aucun montant n'a servi a decider quoi que ce soit, il
            // n'y a donc rien a confronter.
            return;
        }
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
    private void publierValidation(ProcessusMensuel processus, NiveauValidation niveau,
            ActeurSignataire acteur, ResultatSignature signature, ResultatAiguillage aiguillage,
            StatutEnum statutAvant, String adresseIp) {

        DeltaAudit delta = DeltaAudit.nouveau()
                .champ("statut", statutAvant, processus.getStatut())
                .contexte("niveau", niveau)
                .contexte("etape", niveau.nomEtape())
                .contexte("auteur", acteur.login())
                .contexte("role", acteur.role())
                .contexte("codeUnite", processus.getCodeUnite())
                .contexte("dateDebut", processus.getDateDebut())
                .contexte("dateFin", processus.getDateFin())
                .contexte("montantTotal", processus.getMontantTotal())
                .contexte("pieceJointe", signature.document().cheminRelatif())
                .contexte("empreinte", signature.empreinte());

        // Le seuil et la decision n'existent qu'au premier niveau. Les inscrire a
        // vide au second laisserait croire qu'une comparaison a eu lieu.
        if (aiguillage != null) {
            delta.contexte("seuilApplique", aiguillage.seuilApplique())
                    .contexte("aiguillage", aiguillage.decision());
        }

        publicateurAudit.publier(EvenementAudit.de(
                acteur.id(),
                ACTION_VALIDATION,
                ENTITE_CIBLE,
                processus.getId(),
                adresseIp,
                delta.enJson()));
    }

}
