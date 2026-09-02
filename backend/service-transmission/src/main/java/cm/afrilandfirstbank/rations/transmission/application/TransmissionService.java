package cm.afrilandfirstbank.rations.transmission.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.commun.audit.DeltaAudit;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.transmission.application.ResultatConstruction.ChargeConstruite;
import cm.afrilandfirstbank.rations.transmission.application.ResultatConstruction.ChargeRefusee;
import cm.afrilandfirstbank.rations.transmission.application.ResultatPublication.PublicationEchouee;
import cm.afrilandfirstbank.rations.transmission.application.ResultatPublication.Publiee;
import cm.afrilandfirstbank.rations.transmission.domaine.EtatValideEvent;
import cm.afrilandfirstbank.rations.transmission.domaine.exception.ChargeIncompleteException;
import cm.afrilandfirstbank.rations.transmission.domaine.exception.EtatNonClotureException;
import cm.afrilandfirstbank.rations.transmission.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.transmission.domaine.exception.PublicationEchoueeException;
import cm.afrilandfirstbank.rations.transmission.domaine.exception.ServiceSaisieIndisponibleException;
import cm.afrilandfirstbank.rations.transmission.domaine.exception.ServiceWorkflowIndisponibleException;

/**
 * Met un etat cloture a la disposition de la comptabilite (US-12, CT-21, contrat d'API
 * section 7.1).
 *
 * <h2>L'ordre des operations, et pourquoi il est celui-la</h2>
 *
 * <pre>
 *   1. en-tete du processus, lu au service Workflow    -&gt; 404 / 503
 *   2. l'etat est-il CLOTURE ?                         -&gt; 422
 *   3. detail consolide, lu au service Saisie          -&gt; 503
 *   4. assembler et CONTROLER la charge                -&gt; 500
 *   5. publier sur rations.etat.valide, et ATTENDRE    -&gt; 503
 *   6. audit (publie apres coup, sans bloquer)
 * </pre>
 *
 * <p>Tous les refus possibles sont epuises <b>avant</b> la publication, qui est la seule
 * operation irreversible de la chaine : une fois l'evenement parti, le module n'a aucun
 * moyen de le rattraper. C'est le meme raisonnement qu'au Sprint 4.2, ou la completude
 * precede l'ecriture du document, et qu'au Sprint 4.3, ou l'aiguillage precede
 * l'estampage.
 *
 * <h2>Ce que ce service ne fait pas</h2>
 *
 * <p><b>Aucune ecriture comptable.</b> Ni compte general, ni sens, ni journal, ni piece,
 * ni appel au CBS. Le schema debit-credit des specifications est informatif (CLAUDE.md
 * section 8). Ce service publie les donnees d'un etat valide ; le module de
 * comptabilisation, auquel l'equipe n'a pas acces, fabrique les ecritures.
 *
 * <p><b>Aucun acces a une base.</b> Ce service n'en a pas. L'en-tete vient de l'API du
 * service Workflow, le detail de celle du service Saisie (diagramme AR04).
 *
 * <p><b>Il ne pose pas le drapeau de RG-13.</b> {@code transmis_comptabilite} vit sur
 * {@code processus_mensuel}, dans la base du service Workflow, et c'est le service
 * Workflow qui l'ecrit — au vu de la reponse de cet endpoint. Le poser d'ici demanderait
 * un appel retour Transmission -&gt; Workflow, la ou une valeur de retour suffit.
 *
 * <h2>Le controle d'unicite de RG-13 n'est pas encore ici</h2>
 *
 * <p>Il releve du sous-sprint 5.3, qui doit d'abord couvrir tous les chemins d'une
 * seconde transmission — rejeu de la cloture, appel manuel de cet endpoint, deux
 * instances, reprise apres incident — et resister a la concurrence. Son point d'accroche
 * est marque a sa place exacte ci-dessous, apres la lecture de l'en-tete et avant tout
 * autre appel, comme l'a ete celui de RG-12 au Sprint 4.3.
 */
@Service
public class TransmissionService {

    private static final Logger journal = LoggerFactory.getLogger(TransmissionService.class);

    private static final String ENTITE_CIBLE = "processus_mensuel";
    private static final String ACTION_TRANSMISSION = "TRANSMISSION_ETAT_VALIDE";
    private static final String ACTION_REFUS = "TRANSMISSION_REFUSEE";

    /** Prefixe reperable en supervision, convention {@code INCOHERENCE GRILLE} (Sprint 2.4). */
    private static final String PREFIXE_CHARGE = "CHARGE INCOMPLETE";

    /** Seul statut dont un etat part en comptabilite. */
    static final String STATUT_CLOTURE = "CLOTURE";

    private final ProcessusClient processusClient;
    private final ConsolidationClient consolidationClient;
    private final ConstructionChargeService constructionChargeService;
    private final PublicateurEtatValide publicateurEtatValide;
    private final PublicateurAudit publicateurAudit;

    public TransmissionService(ProcessusClient processusClient,
            ConsolidationClient consolidationClient,
            ConstructionChargeService constructionChargeService,
            PublicateurEtatValide publicateurEtatValide,
            PublicateurAudit publicateurAudit) {
        this.processusClient = processusClient;
        this.consolidationClient = consolidationClient;
        this.constructionChargeService = constructionChargeService;
        this.publicateurEtatValide = publicateurEtatValide;
        this.publicateurAudit = publicateurAudit;
    }

    /**
     * Publie l'etat sur le topic de l'etat valide, ou refuse en disant pourquoi.
     *
     * <p><b>Non transactionnelle</b> — ce service n'a pas de base — et sans reessai :
     * chaque appel sortant est borne a 2 s / 3 s (doctrine Sprint 3.2), la publication a
     * 25 s d'attente d'accuse.
     *
     * @param idProcessus l'etat cloture a transmettre
     * @param enteteAutorisation en-tete {@code Authorization} de l'utilisateur final,
     *        relaye tel quel aux deux appels sortants (doctrine Sprint 1.3) : aucune
     *        identite machine n'existe au realm
     * @param adresseIp origine de la demande, pour la trace d'audit
     * @return la charge publiee et l'accuse du broker
     */
    public ResultatTransmission transmettre(Long idProcessus, String enteteAutorisation,
            String adresseIp) {

        // 1. L'en-tete, lu a la source. C'est ce qui permet a ce service de verifier
        //    lui-meme ce qu'il publie, au lieu de le tenir de l'appelant.
        EnTeteProcessus enTete = enTeteOuRefus(idProcessus, enteteAutorisation);

        // 2. Seul un etat cloture est payable. Controle avant tout autre appel : inutile
        //    de deranger le service Saisie pour un etat encore en saisie.
        exigerEtatCloture(idProcessus, enTete);

        // RG-13 — POINT D'ACCROCHE DU SOUS-SPRINT 5.3. Le controle d'unicite se pose ici,
        // sur enTete.transmisComptabilite(), avec le mecanisme resistant a la concurrence
        // que ce sous-sprint arbitrera. Il n'est pas ecrit maintenant : un controle en
        // deux temps, lecture puis ecriture, ne resisterait pas a deux instances traitant
        // la meme cloture, et un demi-verrou donnerait l'illusion de la protection.

        // 3. Le detail : la seule source des lignes qui partent en paiement.
        EtatConsolide etat = detailOuRefus(idProcessus, enTete, enteteAutorisation);

        // 4. Le dernier filet. Rien ne rattrape un evenement parti.
        EtatValideEvent charge = chargeOuRefus(idProcessus, enTete, etat, adresseIp);

        // 5. La seule operation irreversible de la chaine.
        Publiee accuse = publierOuRefus(idProcessus, charge, adresseIp);

        publierTraceDeTransmission(charge, accuse, adresseIp);

        return new ResultatTransmission(charge, accuse);
    }

    // --- Etapes -------------------------------------------------------------------

    private EnTeteProcessus enTeteOuRefus(Long idProcessus, String enteteAutorisation) {
        return switch (processusClient.obtenir(idProcessus, enteteAutorisation)) {
            case ResultatProcessus.ProcessusObtenu obtenu -> obtenu.enTete();
            case ResultatProcessus.ProcessusIntrouvable inconnu ->
                    throw new ProcessusIntrouvableException(inconnu.idProcessus());
            case ResultatProcessus.ServiceWorkflowIndisponible panne ->
                    throw new ServiceWorkflowIndisponibleException(
                            idProcessus, panne.motifTechnique());
        };
    }

    /**
     * Seul un etat {@code CLOTURE} part en comptabilite.
     *
     * <p>Sans ce controle, l'endpoint interne de declenchement publierait tout ce qu'on
     * lui presenterait : un etat encore en saisie, dont les montants ne sont ni
     * consolides, ni valides, ni signes, produirait des paiements que personne n'a
     * approuves.
     */
    private void exigerEtatCloture(Long idProcessus, EnTeteProcessus enTete) {
        if (!STATUT_CLOTURE.equals(enTete.statut())) {
            throw new EtatNonClotureException(idProcessus, enTete.statut());
        }
    }

    private EtatConsolide detailOuRefus(Long idProcessus, EnTeteProcessus enTete,
            String enteteAutorisation) {

        ResultatConsolidation resultat = consolidationClient.consolider(
                idProcessus, enTete.codeUnite(), enteteAutorisation);

        return switch (resultat) {
            case ResultatConsolidation.EtatObtenu obtenu -> obtenu.etat();
            case ResultatConsolidation.ServiceSaisieIndisponible panne ->
                    throw new ServiceSaisieIndisponibleException(
                            idProcessus, panne.motifTechnique());
        };
    }

    /**
     * Assemble la charge et refuse de la laisser partir si elle ne tient pas debout.
     *
     * <p>Le refus est journalise au prefixe reperable <b>et</b> trace en audit : une
     * charge refusee est un etat cloture qui ne sera pas paye tant que le defaut ne sera
     * pas corrige. Ce fait doit survivre dans une base qu'aucun service metier ne peut
     * reecrire.
     */
    private EtatValideEvent chargeOuRefus(Long idProcessus, EnTeteProcessus enTete,
            EtatConsolide etat, String adresseIp) {

        return switch (constructionChargeService.construire(enTete, etat)) {
            case ChargeConstruite construite -> construite.charge();
            case ChargeRefusee refus -> {
                ChargeIncompleteException echec =
                        new ChargeIncompleteException(idProcessus, refus.anomalies());
                journal.error("{} : l'etat {} de l'unite {} est cloture mais sa charge ne peut "
                                + "pas partir. {}",
                        PREFIXE_CHARGE, idProcessus, enTete.codeUnite(), echec.getMessage());
                publierRefus(idProcessus, enTete, "CHARGE_INCOMPLETE", echec.getMessage(),
                        adresseIp);
                throw echec;
            }
        };
    }

    private Publiee publierOuRefus(Long idProcessus, EtatValideEvent charge, String adresseIp) {
        return switch (publicateurEtatValide.publier(charge)) {
            case Publiee accuse -> accuse;
            case PublicationEchouee echec -> {
                PublicationEchoueeException refus =
                        new PublicationEchoueeException(idProcessus, echec.motifTechnique());
                publierRefus(idProcessus, null, "PUBLICATION_ECHOUEE", refus.getMessage(),
                        adresseIp);
                throw refus;
            }
        };
    }

    // --- Audit --------------------------------------------------------------------

    /**
     * Trace la mise a disposition (CLAUDE.md section 12).
     *
     * <p>La partition et l'offset y figurent : ils permettent de retrouver l'evenement
     * exact sur le broker, et de repondre a « qu'a-t-on envoye, precisement » longtemps
     * apres que la retention du topic a expire. Le montant et le nombre de lignes y
     * figurent aussi, pour la meme raison qu'au Sprint 4.3 pour le seuil : un controle
     * interne doit pouvoir refaire le rapprochement sans redemander les donnees.
     *
     * <p><b>{@code idUtilisateur} est nul.</b> Ce service n'appelle pas
     * {@code GET /identite/moi} : il n'a aucune ecriture a rattacher a un identifiant
     * local, contrairement au Sprint 4.2 ou {@code etape_workflow.id_acteur} l'imposait.
     * L'auteur reel de la cloture est deja porte par l'etape {@code VALIDATION_DA} ou
     * {@code VALIDATION_DR}, tracee par le service Workflow. Ajouter un appel reseau pour
     * recopier une information deja tracee allongerait la chaine sans rien apprendre.
     */
    private void publierTraceDeTransmission(EtatValideEvent charge, Publiee accuse,
            String adresseIp) {

        publicateurAudit.publier(EvenementAudit.de(
                null,
                ACTION_TRANSMISSION,
                ENTITE_CIBLE,
                charge.idProcessus(),
                adresseIp,
                DeltaAudit.nouveau()
                        .contexte("topic", accuse.topic())
                        .contexte("partition", accuse.partition())
                        .contexte("offset", accuse.offset())
                        .contexte("codeUnite", charge.codeUnite())
                        .contexte("moisPaiement", charge.periode().mois())
                        .contexte("anneePaiement", charge.periode().annee())
                        .contexte("typeProcessus", charge.typeProcessus())
                        .contexte("montantTotal", charge.montantTotal())
                        .contexte("nombreLignes", charge.lignes().size())
                        .enJson()));
    }

    /**
     * Trace un refus de transmission.
     *
     * <p>Un etat cloture non transmis est le scenario le plus dangereux du sous-sprint :
     * fige, donc plus corrigeable, et jamais paye. Le journal d'audit en garde la trace
     * dans une base qu'aucun service metier ne peut reecrire, avec le motif exact.
     */
    private void publierRefus(Long idProcessus, EnTeteProcessus enTete, String motif,
            String detail, String adresseIp) {

        DeltaAudit delta = DeltaAudit.nouveau()
                .contexte("motif", motif)
                .contexte("detail", detail);

        if (enTete != null) {
            delta.contexte("codeUnite", enTete.codeUnite())
                    .contexte("moisPaiement", enTete.moisPaiement())
                    .contexte("anneePaiement", enTete.anneePaiement());
        }

        publicateurAudit.publier(EvenementAudit.de(
                null, ACTION_REFUS, ENTITE_CIBLE, idProcessus, adresseIp, delta.enJson()));
    }

}
