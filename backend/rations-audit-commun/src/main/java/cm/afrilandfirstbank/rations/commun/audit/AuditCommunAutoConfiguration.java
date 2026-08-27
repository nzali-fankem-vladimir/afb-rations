package cm.afrilandfirstbank.rations.commun.audit;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Cablage de la publication d'audit. Un service metier n'a rien a configurer :
 * ajouter la dependance {@code rations-audit-commun} suffit, et
 * {@link PublicateurAudit} devient injectable.
 *
 * <p><b>Producteur Kafka dedie, volontairement isole du service hote.</b> On
 * n'utilise ni le {@code KafkaTemplate} auto-configure par Spring Boot, ni les
 * reglages {@code spring.kafka.producer.*}. Motif : les bornes qui font tenir la
 * garantie « l'audit ne fait jamais echouer le metier » — au premier rang
 * {@code max.block.ms} — doivent voyager avec le module. Si le service
 * Transmission regle son propre producteur pour l'echange comptable, ses choix
 * ne doivent pas modifier en silence le comportement de l'audit. Seule
 * l'adresse des brokers est reprise du service hote.
 *
 * <p>Le bean {@code kafkaTemplateAudit} est nomme et qualifie : le service
 * Transmission possede deja son propre {@code KafkaTemplate} pour
 * {@code rations.etat.valide}, et deux beans du meme type sans qualificatif
 * rendraient l'injection ambigue.
 */
@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(name = "rations.audit.actif", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AuditProprietes.class)
@EnableAsync
public class AuditCommunAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AuditCommunAutoConfiguration.class);

    @Bean
    public ProducerFactory<String, String> producerFactoryAudit(AuditProprietes proprietes,
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String brokers) {

        Map<String, Object> configuration = new HashMap<>();
        configuration.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        configuration.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configuration.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // Couche 1 : borne l'attente des metadonnees. Sans elle, broker eteint,
        // send() gele le thread metier jusqu'a 60 secondes (defaut Kafka).
        configuration.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, (int) proprietes.blocageMaximal().toMillis());
        configuration.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,
                (int) proprietes.delaiDeLivraison().toMillis());
        configuration.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
                (int) proprietes.delaiDeRequete().toMillis());

        // Le journal d'audit est une piece de controle interne : on veut l'accuse
        // de toutes les repliques, pas seulement du leader. Sans cout reel en
        // developpement, ou le facteur de replication vaut 1.
        configuration.put(ProducerConfig.ACKS_CONFIG, "all");

        return new DefaultKafkaProducerFactory<>(configuration);
    }

    @Bean("kafkaTemplateAudit")
    public KafkaTemplate<String, String> kafkaTemplateAudit(
            @Qualifier("producerFactoryAudit") ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * Pool dedie a la publication d'audit.
     *
     * <p>Separe du pool HTTP a dessein : une saturation de l'audit ne doit pas
     * consommer les threads qui servent les utilisateurs.
     *
     * <p><b>Couche 5 — le handler de rejet ne leve jamais.</b> Avec le handler
     * par defaut ({@code AbortPolicy}), une file pleine ferait lever
     * {@code TaskRejectedException} au moment de la soumission, c'est-a-dire sur
     * le thread appelant, a l'interieur de {@code afterCommit()} : exactement le
     * defaut que {@code @Async} etait cense fermer, par une autre porte.
     * {@code CallerRunsPolicy} serait pire encore, puisqu'elle ferait executer la
     * publication par le thread metier.
     *
     * <p>Sous saturation, on perd donc des evenements d'audit. C'est delibere et
     * coherent avec la regle : l'audit ne bloque jamais le metier. La perte est
     * journalisee en {@code WARN}, donc visible en supervision, plutot que
     * silencieuse.
     */
    @Bean("executeurAudit")
    public Executor executeurAudit(AuditProprietes proprietes) {
        RejectedExecutionHandler abandonJournalise = (tache, executeur) ->
                log.warn("AUDIT PERDU par saturation : file de {} taches pleine, evenement abandonne. "
                                + "L'operation metier a abouti.",
                        proprietes.capaciteFile());

        ThreadPoolTaskExecutor executeur = new ThreadPoolTaskExecutor();
        executeur.setCorePoolSize(proprietes.threads());
        executeur.setMaxPoolSize(proprietes.threads());
        executeur.setQueueCapacity(proprietes.capaciteFile());
        executeur.setThreadNamePrefix("audit-");
        executeur.setRejectedExecutionHandler(abandonJournalise);
        // A l'arret, on laisse les evenements deja en file partir : quelques
        // secondes suffisent et evitent de perdre les dernieres traces.
        executeur.setWaitForTasksToCompleteOnShutdown(true);
        executeur.setAwaitTerminationSeconds(5);
        executeur.initialize();
        return executeur;
    }

    /**
     * {@code spring.application.name} sert de {@code service_emetteur} : chaque
     * service estampille ses traces sans avoir a le declarer, et aucun ne peut
     * l'oublier.
     */
    @Bean
    public PublicateurAudit publicateurAudit(
            @Qualifier("kafkaTemplateAudit") KafkaTemplate<String, String> kafkaTemplate,
            ApplicationEventPublisher publicateurSpring,
            AuditProprietes proprietes,
            @Value("${spring.application.name:service-inconnu}") String nomDuService) {

        if ("service-inconnu".equals(nomDuService)) {
            log.warn("spring.application.name n'est pas renseigne : les traces d'audit de ce service "
                    + "seront publiees sans origine identifiable.");
        }
        return new PublicateurAuditKafka(kafkaTemplate, publicateurSpring, proprietes, nomDuService);
    }

}
