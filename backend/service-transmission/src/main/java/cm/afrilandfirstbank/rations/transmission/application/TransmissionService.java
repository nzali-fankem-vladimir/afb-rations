package cm.afrilandfirstbank.rations.transmission.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.commun.audit.DeltaAudit;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.transmission.application.ResultatConstruction.ChargeConstruite;
import cm.afrilandfirstbank.rations.transmission.application.ResultatConstruction.ChargeRefusee;
import cm.afrilandfirstbank.rations.transmission.application.ResultatPublication.EchecAvantEnvoi;
import cm.afrilandfirstbank.rations.transmission.application.ResultatPublication.EchecIssueIncertaine;
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
 *   3. deja transmis ? (lecture, pas encore le verrou) -&gt; 200 DEJA_TRANSMIS
 *   4. detail consolide, lu au service Saisie          -&gt; 503
 *   5. assembler et CONTROLER la charge                -&gt; 500
 *   6. RESERVER le verrou de RG-13                     -&gt; 200 DEJA_TRANSMIS, ou 503
 *   7. publier sur rations.etat.valide, et ATTENDRE    -&gt; 503
 *   8. CONFIRMER le verrou                             (ne leve jamais)
 *   9. audit (publie apres coup, sans bloquer)
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
 * <h2>Le controle d'unicite de RG-13 (Sprint 5.3)</h2>
 *
 * <p>Il apparait <b>deux fois</b>, et les deux ne se valent pas.
 *
 * <p>L'etape 3 est une simple lecture de l'en-tete deja obtenu : elle evite de deranger le
 * service Saisie et de construire une charge pour un etat manifestement deja parti. <b>Ce
 * n'est pas le controle</b> — deux instances la franchiraient toutes les deux.
 *
 * <p>L'etape 6 <b>est</b> le controle : une reservation atomique, posee sous verrou de
 * ligne dans la base du service Workflow, qui est la seule source de verite. Elle a lieu
 * <b>dans le chemin de cette requete</b> et non en amont, pour que l'appel manuel de cet
 * endpoint — l'un des chemins de double transmission — y soit soumis comme les autres.
 *
 * <p>Voir {@link UniciteTransmissionService} pour l'enumeration des cinq chemins et le
 * choix de l'ordre reserver / publier / confirmer.
 */
@Service
public class TransmissionService {

    private static final Logger journal = LoggerFactory.getLogger(TransmissionService.class);

    private static final String ENTITE_CIBLE = "processus_mensuel";
    private static final String ACTION_TRANSMISSION = "TRANSMISSION_ETAT_VALIDE";
    private static final String ACTION_REFUS = "TRANSMISSION_REFUSEE";

    /** Prefixe reperable en supervision, convention {@code INCOHERENCE GRILLE} (Sprint 2.4). */
    private static final String PREFIXE_CHARGE = "CHARGE INCOMPLETE";

    /**
     * Prefixe reperable : l'etat a peut-etre ete publie, et le verrou de RG-13 reste pose.
     * Distinct de {@code PUBLICATION ETAT VALIDE EN ECHEC}, qui dit qu'un envoi a echoue :
     * celui-ci dit qu'un etat est <b>bloque en reservation</b> et attend une decision
     * humaine.
     */
    public static final String PREFIXE_ISSUE_INCERTAINE = "TRANSMISSION ISSUE INCERTAINE";

    /** Seul statut dont un etat part en comptabilite. */
    static final String STATUT_CLOTURE = "CLOTURE";

    private final ProcessusClient processusClient;
    private final ConsolidationClient consolidationClient;
    private final ConstructionChargeService constructionChargeService;
    private final PublicateurEtatValide publicateurEtatValide;
    private final UniciteTransmissionService uniciteTransmissionService;
    private final PublicateurAudit publicateurAudit;

    public TransmissionService(ProcessusClient processusClient,
            ConsolidationClient consolidationClient,
            ConstructionChargeService constructionChargeService,
            PublicateurEtatValide publicateurEtatValide,
            UniciteTransmissionService uniciteTransmissionService,
            PublicateurAudit publicateurAudit) {
        this.processusClient = processusClient;
        this.consolidationClient = consolidationClient;
        this.constructionChargeService = constructionChargeService;
        this.publicateurEtatValide = publicateurEtatValide;
        this.uniciteTransmissionService = uniciteTransmissionService;
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

        // 3. RG-13, premiere lecture. Une economie, PAS le controle : deux instances
        //    liraient toutes les deux « non transmis » avant que l'une ait pu reserver.
        //    Elle evite simplement de deranger le service Saisie et de construire une
        //    charge de plusieurs centaines de lignes pour un etat manifestement deja
        //    parti - le cas ordinaire d'un rejeu.
        if (Boolean.TRUE.equals(enTete.transmisComptabilite())) {
            return refusDeSecondeTransmission(idProcessus, enTete);
        }

        // 4. Le detail : la seule source des lignes qui partent en paiement.
        EtatConsolide etat = detailOuRefus(idProcessus, enTete, enteteAutorisation);

        // 5. Le dernier filet. Rien ne rattrape un evenement parti.
        EtatValideEvent charge = chargeOuRefus(idProcessus, enTete, etat, adresseIp);

        // 6. RG-13, le controle. Reservation atomique dans la base du service Workflow,
        //    sous verrou de ligne : c'est ici que deux demandes concurrentes sont
        //    departagees, et c'est la derniere chose faite avant l'irreversible.
        if (!uniciteTransmissionService.reserverOuRefuser(idProcessus, enteteAutorisation)) {
            return refusDeSecondeTransmission(idProcessus, enTete);
        }

        // 7. La seule operation irreversible de la chaine.
        Publiee accuse = publierOuLiberer(idProcessus, charge, enteteAutorisation, adresseIp);

        // 8. Le verrou passe de « reserve » a « transmis » : sans cela, l'etat resterait
        //    signale comme une publication d'issue inconnue alors qu'elle a abouti.
        uniciteTransmissionService.confirmerPublication(idProcessus, accuse,
                charge.lignes().size(), charge.montantTotal(), enteteAutorisation, adresseIp);

        publierTraceDeTransmission(charge, accuse, adresseIp);

        return new ResultatTransmission.Transmise(charge, accuse);
    }

    /**
     * Rend le refus explicite qu'exige le guide : {@code 200}, pas une erreur.
     *
     * <p>Une seconde demande n'est pas forcement une anomalie. Vue de la comptabilite, la
     * situation est meme celle qu'on voulait : l'etat y est, une fois et une seule. Une
     * erreur technique ferait croire a une panne et pousserait a reessayer, ce qui est
     * exactement le geste a ne pas encourager.
     *
     * <p><b>Aucune trace d'audit publiee ici</b> : le refus est trace par le service
     * Workflow, ou le verrou l'a oppose et ou le dossier est connu. La tracer aux deux
     * endroits ferait deux lignes pour un seul fait - et sur le chemin de l'etape 3, ou le
     * verrou n'a meme pas ete sollicite, la trace ne dirait rien de plus que la lecture.
     */
    private ResultatTransmission refusDeSecondeTransmission(Long idProcessus,
            EnTeteProcessus enTete) {

        String message = "L'etat " + idProcessus + " de l'unite " + enTete.codeUnite()
                + " a deja ete transmis a la comptabilite. Aucune seconde publication n'a lieu :"
                + " elle produirait un second jeu d'ecritures pour les memes beneficiaires"
                + " (RG-13).";

        journal.info("{}", message);
        return new ResultatTransmission.DejaTransmise(idProcessus, message);
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

    /**
     * Publie, et decide du sort de la reservation selon ce que l'echec <b>prouve</b>.
     *
     * <table>
     *   <caption>Ce que chaque issue fait du verrou</caption>
     *   <tr><td>{@link Publiee}</td><td>le verrou sera confirme par l'appelant</td></tr>
     *   <tr><td>{@link EchecAvantEnvoi}</td><td>rien n'a quitte la machine : le verrou est <b>libere</b>, une reprise reste possible</td></tr>
     *   <tr><td>{@link EchecIssueIncertaine}</td><td>on ignore si l'evenement est parti : le verrou <b>reste pose</b></td></tr>
     * </table>
     *
     * <p>Le deuxieme cas est celui du broker arrete, et c'est le plus frequent :
     * {@code send()} leve des l'appel, le message n'a jamais ete remis au client Kafka. Y
     * laisser le verrou figerait un etat cloture, repute transmis et jamais paye, alors
     * qu'on <i>sait</i> qu'il n'est pas parti.
     *
     * <p>Le troisieme est celui du doute, et le doute ne se rejoue pas : un accuse peut se
     * perdre apres que le broker a ecrit le message. La reservation reste posee et l'etat
     * devient reperable par son anciennete - c'est la doctrine du Sprint 5.1, vue de
     * l'autre bout.
     *
     * <p>Les deux echecs rendent le meme {@code 503 PUBLICATION_ECHOUEE} : du point de vue
     * de l'appelant, la reponse est la meme - rien n'a ete publie a l'instant, et il ne
     * doit pas reessayer de lui-meme. La difference se joue en base, la ou elle compte.
     */
    private Publiee publierOuLiberer(Long idProcessus, EtatValideEvent charge,
            String enteteAutorisation, String adresseIp) {

        return switch (publicateurEtatValide.publier(charge)) {

            case Publiee accuse -> accuse;

            case EchecAvantEnvoi rienNEstParti -> {
                uniciteTransmissionService.libererApresEchecProuve(
                        idProcessus, rienNEstParti.motifTechnique(), enteteAutorisation);
                throw refusDePublication(idProcessus, rienNEstParti.motifTechnique(), adresseIp);
            }

            case EchecIssueIncertaine doute -> {
                journal.error("{} : l'etat {} a peut-etre ete publie - le verrou de RG-13 reste "
                                + "pose et l'etat ne sera pas rejoue automatiquement. Motif : {}. "
                                + "A lever a la main apres verification du topic.",
                        PREFIXE_ISSUE_INCERTAINE, idProcessus, doute.motifTechnique());
                throw refusDePublication(idProcessus, doute.motifTechnique(), adresseIp);
            }
        };
    }

    private PublicationEchoueeException refusDePublication(Long idProcessus, String motifTechnique,
            String adresseIp) {

        PublicationEchoueeException refus =
                new PublicationEchoueeException(idProcessus, motifTechnique);
        publierRefus(idProcessus, null, "PUBLICATION_ECHOUEE", refus.getMessage(), adresseIp);
        return refus;
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
                        .contexte("dateDebut", charge.periode().dateDebut())
                        .contexte("dateFin", charge.periode().dateFin())
                        .contexte("versionCharge", charge.versionCharge())
                        .contexte("compteCharge", charge.compteCharge())
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
                    .contexte("dateDebut", enTete.dateDebut())
                    .contexte("dateFin", enTete.dateFin());
        }

        publicateurAudit.publier(EvenementAudit.de(
                null, ACTION_REFUS, ENTITE_CIBLE, idProcessus, adresseIp, delta.enJson()));
    }

}
