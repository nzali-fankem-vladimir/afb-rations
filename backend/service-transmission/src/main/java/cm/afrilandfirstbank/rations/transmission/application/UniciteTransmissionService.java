package cm.afrilandfirstbank.rations.transmission.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.commun.audit.DeltaAudit;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.transmission.application.ResultatVerrou.DejaTransmis;
import cm.afrilandfirstbank.rations.transmission.application.ResultatVerrou.VerrouIndisponible;
import cm.afrilandfirstbank.rations.transmission.application.ResultatVerrou.VerrouTenu;
import cm.afrilandfirstbank.rations.transmission.domaine.exception.ServiceWorkflowIndisponibleException;

/**
 * Porte RG-13 du cote qui publie : <b>un etat valide n'est transmis qu'une seule fois</b>
 * (Sprint 5.3, US-12, CT-22).
 *
 * <h2>La consequence d'une violation, pour memoire</h2>
 *
 * <p>Deux publications du meme etat produisent deux jeux d'ecritures comptables, donc un
 * <b>double paiement</b> des memes beneficiaires. Ce n'est pas une contrainte technique de
 * confort : c'est de l'argent qui sort deux fois de la banque, et l'une des erreurs
 * interdites de CLAUDE.md section 15.
 *
 * <h2>Les cinq chemins d'une seconde transmission, et ce qui les ferme</h2>
 *
 * <table>
 *   <caption>Enumeres avant tout code (etape 1 du guide 5.3)</caption>
 *   <tr><th>Chemin</th><th>Ferme par</th></tr>
 *   <tr><td>Rejeu de la cloture par le service Workflow</td><td>la reservation : le second appel rend {@code DEJA_TRANSMISE}</td></tr>
 *   <tr><td><b>Appel manuel</b> de {@code POST /transmission/processus/{id}}</td><td>idem — c'est pourquoi le verrou est demande <b>ici</b>, dans le chemin de requete, et non en amont par le service Workflow</td></tr>
 *   <tr><td>Deux instances traitant la meme cloture</td><td>le verrou de ligne PostgreSQL cote Workflow : la seconde attend, puis lit le drapeau deja pose</td></tr>
 *   <tr><td>Reprise apres l'incident ambigu du Sprint 5.1</td><td>la reservation reste posee sur un echec incertain : elle refuse la reprise</td></tr>
 *   <tr><td>Reessai automatique mal classe du Sprint 5.1</td><td>le verrou est en aval de la classification : il protege meme si celle-ci se trompe</td></tr>
 * </table>
 *
 * <p>Un sixieme chemin existe et <b>n'est pas du ressort du module</b> : le rejeu du topic
 * par le module de comptabilisation. Rien ne dit qu'il dedoublonne par {@code idProcessus}
 * ; la question est ouverte cote DFT ({@code docs/points-en-attente.md}).
 *
 * <h2>L'ordre retenu : reserver, publier, confirmer</h2>
 *
 * <p>Les deux ordres possibles laissent chacun un risque, et il fallait choisir lequel :
 *
 * <ul>
 *   <li><b>Publier puis marquer</b> (ordre du Sprint 5.1) : si le marquage echoue apres une
 *       publication reussie, l'etat reste repute non transmis et la demande suivante
 *       <b>republie</b>. Risque : double paiement, irreversible.</li>
 *   <li><b>Reserver puis publier</b> (ordre retenu) : si la publication echoue apres la
 *       reservation, l'etat est repute transmis sans l'etre. Risque : un etat impaye,
 *       <b>visible et reparable</b>.</li>
 * </ul>
 *
 * <p>On retient le second parce que son risque se voit et se repare. Il se voit grace a
 * {@code date_reservation_transmission} : un etat reserve depuis quelques secondes est en
 * transit normal, un etat reserve depuis trois jours sans statut d'integration est un
 * incident. Et il se reduit encore : quand l'echec <b>prouve</b> qu'aucun evenement n'est
 * parti, la reservation est levee sur-le-champ et une reprise redevient possible.
 *
 * <h2>Ce que ce service ne fait pas</h2>
 *
 * <p><b>Il ne tient pas le verrou lui-meme.</b> Ce service n'a pas de base ; une memoire
 * locale disparaitrait au premier redemarrage, c'est-a-dire au moment ou un rejeu est le
 * plus probable, et ne serait de toute facon pas partagee entre deux instances. Il demande
 * le verrou au service Workflow, qui detient la seule source de verite.
 */
@Service
public class UniciteTransmissionService {

    private static final Logger journal =
            LoggerFactory.getLogger(UniciteTransmissionService.class);

    private static final String ENTITE_CIBLE = "processus_mensuel";
    private static final String ACTION_CONFIRMATION_MANQUEE = "TRANSMISSION_CONFIRMATION_MANQUEE";

    /**
     * Prefixe reperable en supervision : l'evenement est parti, mais le verrou n'a pas pu
     * etre confirme. L'etat reste « reserve, issue inconnue » alors qu'il est en realite
     * transmis — une fausse alerte qu'il faut pouvoir expliquer.
     */
    public static final String PREFIXE_CONFIRMATION = "CONFIRMATION TRANSMISSION MANQUEE";

    /** Prefixe reperable : la reservation n'a pas pu etre levee apres un echec prouve. */
    public static final String PREFIXE_LIBERATION = "LIBERATION VERROU MANQUEE";

    private final VerrouTransmissionClient verrouTransmissionClient;
    private final PublicateurAudit publicateurAudit;

    public UniciteTransmissionService(VerrouTransmissionClient verrouTransmissionClient,
            PublicateurAudit publicateurAudit) {
        this.verrouTransmissionClient = verrouTransmissionClient;
        this.publicateurAudit = publicateurAudit;
    }

    /**
     * Revendique le droit de publier cet etat, et lui seul.
     *
     * <p><b>Appelee juste avant la publication</b>, apres que tous les refus possibles ont
     * ete epuises : en-tete lu, statut verifie, detail obtenu, charge controlee. Reserver
     * plus tot allongerait la fenetre pendant laquelle un etat est repute transmis sans
     * l'etre, pour rien.
     *
     * @return {@code true} si le verrou est pose et que la publication peut avoir lieu,
     *         {@code false} si l'etat etait deja transmis — <b>aucune publication alors</b>
     * @throws ServiceWorkflowIndisponibleException si le verrou n'a pas pu etre demande.
     *         Refus conservateur : sans verrou, rien ne part
     */
    public boolean reserverOuRefuser(Long idProcessus, String enteteAutorisation) {
        return switch (verrouTransmissionClient.reserver(idProcessus, enteteAutorisation)) {

            case VerrouTenu tenu -> true;

            case DejaTransmis deja -> {
                journal.info("Transmission de l'etat {} refusee par le verrou de RG-13 : {}",
                        idProcessus, deja.message());
                yield false;
            }

            case VerrouIndisponible panne -> throw new ServiceWorkflowIndisponibleException(
                    idProcessus, "le verrou d'unicite n'a pas pu etre pose ("
                            + panne.motifTechnique() + "). Aucune publication n'a lieu : sans "
                            + "verrou, rien ne garantit qu'un autre appel ne publie pas le meme "
                            + "etat au meme instant (RG-13).");
        };
    }

    /**
     * Constate l'accuse du broker : l'etat cesse d'etre « reserve, issue inconnue ».
     *
     * <p><b>Ne leve jamais.</b> A ce stade, l'evenement <i>est</i> parti : transformer un
     * echec de confirmation en erreur ferait croire a l'appelant que rien n'a ete publie,
     * et pourrait le pousser a republier — exactement ce que RG-13 interdit. L'incident
     * est donc signale, jamais rejoue en exception.
     *
     * <p>Consequence assumee : l'etat reste marque « reserve non confirme » et la
     * supervision le signalera a tort. Une fausse alerte tracee vaut mieux qu'un second
     * paiement.
     */
    public void confirmerPublication(Long idProcessus, ResultatPublication.Publiee accuse,
            int nombreLignes, long montantTotal, String enteteAutorisation, String adresseIp) {

        ResultatVerrou resultat = verrouTransmissionClient.confirmer(
                idProcessus, accuse, nombreLignes, montantTotal, enteteAutorisation);

        if (resultat instanceof VerrouTenu) {
            return;
        }

        String motif = resultat instanceof VerrouIndisponible panne
                ? panne.motifTechnique()
                : "reponse inattendue du verrou";

        journal.error("{} : l'etat {} a bien ete publie sur {} (partition {}, offset {}), mais la "
                        + "confirmation du verrou a echoue ({}). L'etat restera signale comme "
                        + "« reserve, issue inconnue » : il est en realite transmis, ne le "
                        + "republiez pas.",
                PREFIXE_CONFIRMATION, idProcessus, accuse.topic(), accuse.partition(),
                accuse.offset(), motif);

        publicateurAudit.publier(EvenementAudit.de(
                null,
                ACTION_CONFIRMATION_MANQUEE,
                ENTITE_CIBLE,
                idProcessus,
                adresseIp,
                DeltaAudit.nouveau()
                        .contexte("motif", motif)
                        .contexte("topic", accuse.topic())
                        .contexte("partition", accuse.partition())
                        .contexte("offset", accuse.offset())
                        .contexte("nombreLignes", nombreLignes)
                        .contexte("montantTotal", montantTotal)
                        .enJson()));
    }

    /**
     * Leve la reservation apres un echec dont il est <b>prouve</b> qu'aucun evenement n'est
     * parti, pour qu'une reprise reste possible.
     *
     * <p>N'est jamais appelee sur un echec ambigu : le type {@link ResultatPublication} porte
     * la distinction, et c'est lui qui repond de la preuve. Un echec de liberation laisse
     * simplement la reservation en place — prudent par construction —, et se signale.
     *
     * <p><b>Ne leve jamais</b> : l'appelant a deja un refus a rendre, et une exception ici
     * le remplacerait par un diagnostic sans rapport avec la cause reelle.
     */
    public void libererApresEchecProuve(Long idProcessus, String motif, String enteteAutorisation) {
        ResultatVerrou resultat = verrouTransmissionClient.liberer(
                idProcessus, motif, enteteAutorisation);

        if (resultat instanceof VerrouTenu) {
            journal.warn("Reservation de transmission de l'etat {} levee : aucun evenement n'est "
                    + "parti ({}). Une reprise reste possible.", idProcessus, motif);
            return;
        }

        journal.error("{} : la reservation de l'etat {} n'a pas pu etre levee apres un echec "
                        + "prouve sans envoi. L'etat restera marque transmis alors qu'il ne l'est "
                        + "pas : a lever a la main.",
                PREFIXE_LIBERATION, idProcessus);
    }

}
