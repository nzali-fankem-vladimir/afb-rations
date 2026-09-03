package cm.afrilandfirstbank.rations.transmission.infrastructure;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import cm.afrilandfirstbank.rations.commun.audit.DeltaAudit;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.transmission.application.ResultatTraitementAccuse;
import cm.afrilandfirstbank.rations.transmission.application.TraitementAccuseService;
import cm.afrilandfirstbank.rations.transmission.domaine.AccuseComptableEvent;
import cm.afrilandfirstbank.rations.transmission.domaine.CodeAnomalieAccuseEnum;

/**
 * Ecoute {@code rations.etat.accuse} et remonte le statut d'integration au processus
 * (contrat d'API section 7.2, US-12, US-15, guide 5.2 etape 3).
 *
 * <h2>Ce consommateur ne tombe jamais sur un message illisible</h2>
 *
 * <p>C'est l'exigence premiere du sous-sprint (guide section 10) : un message mal forme
 * bloquerait la consommation de <b>tous les suivants</b>, y compris des accuses valides
 * portant sur d'autres etats. Trois dispositions s'y emploient, et il en faut trois :
 *
 * <ol>
 *   <li>La <b>deserialisation Kafka est en chaine</b> ({@code StringDeserializer}) : rien
 *       n'est converti hors de portee du code. Avec un {@code JsonDeserializer}, l'echec
 *       surviendrait dans le conteneur, avant meme que cette methode ne soit appelee.</li>
 *   <li>La conversion JSON a lieu <b>ici</b>, dans un {@code try}, et un echec devient un
 *       refus trace.</li>
 *   <li>Une capture de securite entoure le traitement, pour qu'aucune exception imprevue
 *       n'echappe et ne fasse rejouer sans fin un message qui n'a aucune chance
 *       d'aboutir.</li>
 * </ol>
 *
 * <h2>La seule chose qui fait rejouer un message</h2>
 *
 * <p>Cette methode ne <b>leve</b> que sur un {@link ResultatTraitementAccuse.EchecTemporaire},
 * c'est-a-dire un service Workflow injoignable. Toutes les autres issues laissent le
 * message avancer : un JSON tronque le restera, un processus inconnu ne naitra pas, un
 * statut definitif ne se defera pas. Le gestionnaire d'erreurs rejoue deux fois avec deux
 * secondes de pause, puis abandonne — voir {@code ConfigurationConsommateurAccuse}.
 *
 * <h2>Un rejeu est sans danger</h2>
 *
 * <p>Le traitement est idempotent : un accuse deja applique est reconnu comme tel, n'ecrit
 * rien et ne publie aucune trace. Un rejeu de topic — operation courante en exploitation —
 * ne corrompt donc ni les statuts ni le journal d'audit.
 */
@Component
public class AccuseComptableConsumer {

    private static final Logger journal = LoggerFactory.getLogger(AccuseComptableConsumer.class);

    private static final String ENTITE_CIBLE = "processus_mensuel";
    private static final String ACTION_ACCUSE_REFUSE = "ACCUSE_COMPTABLE_REFUSE";

    /** Prefixes de supervision, convention {@code INCOHERENCE GRILLE} (Sprint 2.4). */
    static final String PREFIXE_ILLISIBLE = "ACCUSE ILLISIBLE";
    static final String PREFIXE_ABANDONNE = "ACCUSE ABANDONNE";

    /**
     * Longueur maximale du fragment de message brut recopie au journal.
     *
     * <p>Un fragment, et non le message entier : il sert a reconnaitre le message et a le
     * rejouer a la main, pas a l'archiver. Un accuse malforme peut etre arbitrairement
     * long — un fichier entier publie par erreur sur le topic, par exemple — et le recopier
     * saturerait le journal au moment precis ou il faut pouvoir le lire.
     */
    static final int LONGUEUR_FRAGMENT_JOURNALISE = 500;

    private final ObjectMapper convertisseur;
    private final TraitementAccuseService traitementAccuseService;
    private final PublicateurAudit publicateurAudit;

    public AccuseComptableConsumer(
            @Qualifier("convertisseurChargeComptable") ObjectMapper convertisseur,
            TraitementAccuseService traitementAccuseService,
            PublicateurAudit publicateurAudit) {
        this.convertisseur = convertisseur;
        this.traitementAccuseService = traitementAccuseService;
        this.publicateurAudit = publicateurAudit;
    }

    /**
     * Traite un accuse recu.
     *
     * <p>Le nom du topic tient lieu d'origine dans la trace d'audit : un message Kafka n'a
     * pas d'adresse IP d'appelant, et en inventer une serait pire que de nommer la seule
     * origine reelle.
     *
     * @throws EchecTemporaireAccuse pour faire rejouer le message, et seulement pour cela
     */
    @KafkaListener(
            topics = "${app.transmission.topic-etat-accuse}",
            containerFactory = "kafkaListenerContainerFactoryAccuse")
    public void consommer(ConsumerRecord<String, String> message) {

        AccuseComptableEvent accuse = deserialiserOuTracer(message);
        if (accuse == null) {
            return; // deja trace : le message avance, aucun rejeu ne le reparerait
        }

        ResultatTraitementAccuse resultat;
        try {
            resultat = traitementAccuseService.traiter(accuse, message.topic());
        } catch (RuntimeException imprevue) {
            // Filet de securite. Une exception non prevue ferait rejouer indefiniment un
            // message qui n'a aucune chance d'aboutir, et bloquerait la partition.
            journal.error("{} : echec imprevu au traitement de l'accuse recu sur {} "
                            + "(partition {}, offset {}). Le message avance.",
                    PREFIXE_ABANDONNE, message.topic(), message.partition(), message.offset(),
                    imprevue);
            return;
        }

        if (resultat instanceof ResultatTraitementAccuse.EchecTemporaire panne) {
            // La seule issue qui leve : le Workflow n'a pas repondu, l'accuse est
            // peut-etre valide, on ne le saura qu'en reessayant.
            throw new EchecTemporaireAccuse(panne.idProcessus(), panne.motifTechnique());
        }
    }

    /**
     * Convertit le message, ou trace son illisibilite et rend {@code null}.
     *
     * <p>Le fragment brut est recopie au journal : sans lui, personne ne pourrait dire
     * <i>ce qui</i> etait illisible, ni rejouer le message a la main apres correction cote
     * comptabilite. Il est tronque, et il ne va qu'au journal — pas dans les anomalies,
     * qui seraient multipliees d'autant.
     */
    private AccuseComptableEvent deserialiserOuTracer(ConsumerRecord<String, String> message) {
        try {
            AccuseComptableEvent accuse =
                    convertisseur.readValue(message.value(), AccuseComptableEvent.class);
            if (accuse == null) {
                throw new IllegalArgumentException("charge JSON vide (message \"null\")");
            }
            return accuse;
        } catch (Exception illisible) {
            String detail = "Message recu sur " + message.topic() + " (partition "
                    + message.partition() + ", offset " + message.offset()
                    + ") non exploitable : " + illisible.getMessage();

            journal.error("{} : {}. Debut du message : <<{}>>. Le message avance : le rejouer "
                            + "ne le rendrait pas lisible, et bloquerait tous les accuses suivants.",
                    PREFIXE_ILLISIBLE, detail, fragment(message.value()));

            publicateurAudit.publier(EvenementAudit.de(
                    null,
                    ACTION_ACCUSE_REFUSE,
                    ENTITE_CIBLE,
                    null,
                    message.topic(),
                    DeltaAudit.nouveau()
                            .contexte("motif", CodeAnomalieAccuseEnum.ACCUSE_ILLISIBLE.name())
                            .contexte("detail", detail)
                            .contexte("partition", message.partition())
                            .contexte("offset", message.offset())
                            .enJson()));
            return null;
        }
    }

    private static String fragment(String brut) {
        if (brut == null) {
            return "(aucune charge)";
        }
        return brut.length() <= LONGUEUR_FRAGMENT_JOURNALISE
                ? brut
                : brut.substring(0, LONGUEUR_FRAGMENT_JOURNALISE) + "... (tronque)";
    }

    /**
     * Leve pour faire rejouer le message, et pour cela seulement.
     *
     * <p>Type dedie plutot qu'une exception generique : le gestionnaire d'erreurs et le
     * journal disent alors, sans ambiguite, qu'il s'agit d'une indisponibilite et non d'un
     * accuse fautif. Les deux appellent des reactions opposees — patienter, ou aller voir
     * la comptabilite.
     */
    public static class EchecTemporaireAccuse extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public EchecTemporaireAccuse(Long idProcessus, String motifTechnique) {
            super("Le service Workflow n'a pas repondu pour l'accuse de l'etat " + idProcessus
                    + " : " + motifTechnique + ". Le message sera rejoue.");
        }

    }

}
