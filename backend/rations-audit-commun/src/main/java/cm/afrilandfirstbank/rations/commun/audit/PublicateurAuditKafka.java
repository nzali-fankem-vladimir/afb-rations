package cm.afrilandfirstbank.rations.commun.audit;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Producteur d'evenements d'audit vers {@code rations.audit.evenement}.
 * Ecrit une fois, repris par les six services metier (decision Sprint 1.3).
 *
 * <h2>Les cinq couches qui garantissent que l'audit ne fait jamais echouer le metier</h2>
 *
 * <p>Aucune n'est facultative. Retirer l'une d'elles rouvre un chemin par lequel
 * une panne d'audit remonte jusqu'a l'utilisateur.
 *
 * <ol>
 *   <li><b>{@code max.block.ms} borne</b> ({@link AuditProprietes#blocageMaximal}).
 *       Piege principal : {@code KafkaProducer.send()} bloque le thread appelant
 *       en attente des metadonnees du cluster, jusqu'a 60 secondes par defaut.
 *       Broker eteint, un envoi naif gele l'operation metier une minute.</li>
 *   <li><b>{@code try/catch} synchrone autour de l'envoi.</b> Serialisation
 *       impossible, {@code max.block.ms} depasse, producteur ferme : ces
 *       exceptions sont levees sur le thread appelant.</li>
 *   <li><b>Callback d'echec qui journalise seulement.</b> L'echec de livraison
 *       asynchrone survient sur le thread reseau de Kafka ; le futur n'est
 *       jamais attendu ({@code get()} et {@code join()} sont proscrits ici).</li>
 *   <li><b>Listener {@code @Async} avec {@code catch (Throwable)}.</b> Sans
 *       {@code @Async}, un listener {@code AFTER_COMMIT} s'execute sur le thread
 *       appelant, dans {@code TransactionSynchronization.afterCommit()} : une
 *       exception y remonte a travers le commit et devient une erreur HTTP,
 *       alors meme que la modification est deja enregistree en base. Decision
 *       reussie, rapportee comme echec.</li>
 *   <li><b>Handler de rejet non levant</b> (voir {@code AuditCommunAutoConfiguration}).
 *       {@code @Async} rouvre la meme porte ailleurs : file pleine, la
 *       soumission leve {@code TaskRejectedException} — encore sur le thread
 *       appelant, encore dans {@code afterCommit()}. Le handler journalise et
 *       abandonne l'evenement au lieu de lever.</li>
 * </ol>
 *
 * <h2>Publication apres commit</h2>
 *
 * <p>{@link #publier} ne parle pas a Kafka : il emet un evenement applicatif
 * Spring. L'envoi reel a lieu dans {@link #envoyerSurLeTopic}, apres le commit
 * de la transaction en cours. Un rollback ne laisse donc jamais derriere lui la
 * trace d'une operation qui n'a pas eu lieu.
 *
 * <p>{@code fallbackExecution = true} est indispensable : sans lui, un evenement
 * emis hors transaction ne serait jamais publie. Or c'est exactement le cas d'un
 * refus d'acces (CT-04), qui survient avant toute ouverture de transaction — le
 * cas le plus interessant a tracer serait le seul perdu.
 *
 * <p><b>Risque residuel, assume et documente.</b> Si le commit reussit puis la
 * JVM s'arrete avant l'envoi, l'evenement est perdu. Or une trace manquante ne
 * se detecte pas, quand une trace fausse se detecte par recoupement avec l'etat
 * reel : le risque conserve ici est le plus difficile a reperer des deux. C'est
 * un compromis de cout pour ce sprint, pas un etat correct. La reponse de fond
 * est un outbox transactionnel, consignee dans {@code docs/publication-audit.md}
 * et {@code docs/points-en-attente.md}.
 *
 * <p><b>Aucun appel REST vers le service Audit</b>, ni en nominal ni en repli
 * (CLAUDE.md section 15). La seule sortie de cette classe est
 * {@code KafkaTemplate.send()}.
 */
public class PublicateurAuditKafka implements PublicateurAudit {

    private static final Logger log = LoggerFactory.getLogger(PublicateurAuditKafka.class);

    /**
     * Serialiseur dedie, volontairement independant de l'ObjectMapper du service
     * hote : le format de fil ne doit pas changer parce qu'un service a
     * reconfigure son Jackson. Dates en ISO 8601 (CLAUDE.md section 11).
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ApplicationEventPublisher publicateurSpring;
    private final AuditProprietes proprietes;
    private final String nomDuService;

    public PublicateurAuditKafka(KafkaTemplate<String, String> kafkaTemplate,
            ApplicationEventPublisher publicateurSpring, AuditProprietes proprietes, String nomDuService) {
        this.kafkaTemplate = kafkaTemplate;
        this.publicateurSpring = publicateurSpring;
        this.proprietes = proprietes;
        this.nomDuService = nomDuService;
    }

    /**
     * Couche 2, premiere moitie : rien de ce qui se passe ici ne remonte a
     * l'appelant. L'estampille du service emetteur a lieu maintenant, pour
     * qu'aucun service ne puisse publier une trace anonyme.
     */
    @Override
    public void publier(EvenementAudit evenement) {
        try {
            publicateurSpring.publishEvent(evenement.avecServiceEmetteur(nomDuService));
        } catch (Throwable echec) {
            log.warn("AUDIT PERDU a l'emission : action={} entite={} idEntite={}. "
                            + "L'operation metier se poursuit normalement.",
                    evenement.action(), evenement.entiteCible(), evenement.idEntite(), echec);
        }
    }

    /**
     * Couches 2 (seconde moitie), 3 et 4. Execute apres le commit, sur le pool
     * dedie a l'audit.
     */
    @Async("executeurAudit")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void envoyerSurLeTopic(EvenementAudit evenement) {
        try {
            String charge = MAPPER.writeValueAsString(evenement);

            CompletableFuture<SendResult<String, String>> futur =
                    kafkaTemplate.send(proprietes.topic(), evenement.clePartition(), charge);

            // Couche 3 : on observe l'issue, on ne l'attend pas. Ni get(), ni join().
            futur.whenComplete((resultat, echec) -> {
                if (echec != null) {
                    log.warn("AUDIT PERDU a la livraison : action={} entite={} idEntite={} topic={}. "
                                    + "L'operation metier a abouti.",
                            evenement.action(), evenement.entiteCible(), evenement.idEntite(),
                            proprietes.topic(), echec);
                } else if (log.isDebugEnabled()) {
                    log.debug("Audit publie : action={} entite={} partition={} offset={}",
                            evenement.action(), evenement.entiteCible(),
                            resultat.getRecordMetadata().partition(),
                            resultat.getRecordMetadata().offset());
                }
            });

        } catch (Throwable echec) {
            // Couches 2 et 4 : serialisation impossible, max.block.ms depasse,
            // producteur ferme. Rien ne remonte, l'operation metier est deja
            // committee et ne doit pas etre rapportee comme un echec.
            log.warn("AUDIT PERDU a l'envoi : action={} entite={} idEntite={}. "
                            + "L'operation metier a abouti.",
                    evenement.action(), evenement.entiteCible(), evenement.idEntite(), echec);
        }
    }

}
