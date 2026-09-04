package cm.afrilandfirstbank.rations.audit.infrastructure;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import cm.afrilandfirstbank.rations.audit.domaine.AuditLog;

/**
 * Ecoute {@code rations.audit.evenement} et persiste chaque evenement dans
 * {@code audit_log} (guide de rattrapage du service Audit, etape 5).
 *
 * <h2>Tolerant reader</h2>
 *
 * <p>Ce consommateur ne depend pas de {@code rations-audit-commun}
 * (CLAUDE.md section 15) : il deserialise avec {@link MessageAuditEntrant},
 * un type propre a ce service, independant d'{@code EvenementAudit}. Un champ
 * absent sur un message ancien se lit nul et n'empeche pas la persistance du
 * reste de l'evenement — sauf s'il s'agit d'un champ que {@code audit_log}
 * exige non nul (voir ci-dessous).
 *
 * <h2>Ce consommateur ne tombe jamais sur un message illisible</h2>
 *
 * <p>Meme doctrine que {@code AccuseComptableConsumer} (service Transmission,
 * Sprint 5.2), pour la meme raison : un message malforme ne doit jamais
 * bloquer la consommation de <b>tous les suivants</b>, alors meme que 176+
 * evenements reels de contrôle interne attendent sur le topic.
 *
 * <ol>
 *   <li>Deserialisation Kafka en chaine ({@code StringDeserializer}, pose
 *       dans {@code ConfigurationConsommateurAudit}) : rien n'echoue dans le
 *       conteneur, hors de portee de toute capture.</li>
 *   <li>La conversion JSON a lieu <b>ici</b>, dans un {@code try} : un echec
 *       devient un rejet trace, jamais une exception qui remonte.</li>
 *   <li>Un message lisible mais incomplet (un des quatre champs que
 *       {@code audit_log} exige non nul — {@code action}, {@code entiteCible},
 *       {@code dateAction}, {@code serviceEmetteur} — absent ou vide) est
 *       trace de la meme facon, sans etre persiste : la contrainte
 *       {@code NOT NULL} de la base ne doit jamais etre la premiere a le
 *       decouvrir.</li>
 * </ol>
 *
 * <p>Dans les deux cas, le prefixe {@code AUDIT ENTREE REJETEE} rend le
 * defaut reperable en supervision (convention {@code ACCUSE ILLISIBLE},
 * {@code SEUIL INDISPONIBLE}, {@code TRANSMISSION MANQUEE}). Le message
 * avance : le rejouer ne le rendrait pas persistable, et bloquerait tous les
 * evenements d'audit suivants — y compris ceux d'autres services, sans rapport
 * avec le defaut.
 *
 * <h2>Ce qui, en revanche, fait rejouer</h2>
 *
 * <p>Aucune capture n'entoure {@link cm.afrilandfirstbank.rations.audit.infrastructure.AuditLogRepository#save}.
 * Un echec d'ecriture (base indisponible) est une panne locale du service
 * Audit lui-meme, pas un defaut du message : il doit etre rejoue une fois la
 * base revenue, jamais tenu pour un rejet definitif. Le gestionnaire d'erreurs
 * par defaut de spring-kafka rejoue et journalise ; aucune borne particuliere
 * n'est posee ici, une indisponibilite de la propre base du consommateur
 * n'ayant pas de raison de se resoudre en quelques secondes comme un service
 * distant.
 */
@Component
public class AuditEvenementConsumer {

    private static final Logger journal = LoggerFactory.getLogger(AuditEvenementConsumer.class);

    /** Prefixe de supervision, convention {@code ACCUSE ILLISIBLE} (Sprint 5.2). */
    static final String PREFIXE_REJET = "AUDIT ENTREE REJETEE";

    /** Meme convention que {@code AccuseComptableConsumer} : un fragment, pas le message entier. */
    static final int LONGUEUR_FRAGMENT_JOURNALISE = 500;

    private final ObjectMapper convertisseur;
    private final AuditLogRepository repository;

    public AuditEvenementConsumer(
            @Qualifier("convertisseurAudit") ObjectMapper convertisseur,
            AuditLogRepository repository) {
        this.convertisseur = convertisseur;
        this.repository = repository;
    }

    @KafkaListener(topics = "${app.audit.topic}", containerFactory = "kafkaListenerContainerFactoryAudit")
    public void consommer(ConsumerRecord<String, String> message) {
        MessageAuditEntrant entrant = deserialiserOuTracer(message);
        if (entrant == null) {
            return; // deja trace : le message avance, aucun rejeu ne le reparerait
        }

        String champManquant = entrant.premierChampObligatoireManquant();
        if (champManquant != null) {
            journal.error("{} : champ obligatoire absent ou vide ({}). partition={} offset={}. "
                            + "Debut du message : <<{}>>. Le message avance : audit_log exige "
                            + "cette colonne non nulle, un rejeu ne la fournirait pas davantage.",
                    PREFIXE_REJET, champManquant, message.partition(), message.offset(),
                    fragment(message.value()));
            return;
        }

        repository.save(new AuditLog(
                entrant.idUtilisateur(),
                entrant.serviceEmetteur(),
                entrant.action(),
                entrant.entiteCible(),
                entrant.idEntite(),
                entrant.dateAction(),
                entrant.adresseIp(),
                entrant.detailJson()));
    }

    private MessageAuditEntrant deserialiserOuTracer(ConsumerRecord<String, String> message) {
        try {
            MessageAuditEntrant entrant = convertisseur.readValue(message.value(), MessageAuditEntrant.class);
            if (entrant == null) {
                throw new IllegalArgumentException("charge JSON vide (message \"null\")");
            }
            return entrant;
        } catch (Exception illisible) {
            journal.error("{} : message non exploitable sur {} (partition {}, offset {}) : {}. "
                            + "Debut du message : <<{}>>. Le message avance : le rejouer ne le "
                            + "rendrait pas lisible, et bloquerait tous les evenements d'audit "
                            + "suivants.",
                    PREFIXE_REJET, message.topic(), message.partition(), message.offset(),
                    illisible.getMessage(), fragment(message.value()));
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

}
