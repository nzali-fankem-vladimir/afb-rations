package cm.afrilandfirstbank.rations.audit.infrastructure.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Consommateur Kafka du journal d'audit : la moitie aval de la chaine, absente
 * depuis le Sprint 1.3 (guide de rattrapage, etape 2).
 *
 * <h2>{@code @EnableKafka}, et pourquoi il est ecrit explicitement ici</h2>
 *
 * <p>Meme piege qu'au Sprint 5.2 (voir {@code ConfigurationConsommateurAccuse}
 * du service Transmission) : le module declare {@code spring-kafka} directement,
 * sans {@code spring-boot-starter-kafka}, donc l'auto-configuration qui poserait
 * cette annotation n'est pas entrainee. Sans elle, {@code @KafkaListener} n'est
 * jamais traite et la methode d'ecoute reste une methode ordinaire que personne
 * n'appelle -- le service demarre sans erreur et n'ecoute rien.
 *
 * <h2>Groupe de consommateurs</h2>
 *
 * <p>{@code rations-audit-lecture}, distinct de {@code rations-transmission-accuse}
 * (Sprint 5.2), pour qu'un {@code kafka-consumer-groups.sh --list} distingue les
 * deux chaines de consommation du module.
 *
 * <h2>Deserialisation en chaine</h2>
 *
 * <p>Cle et valeur en {@link StringDeserializer}, jamais {@code JsonDeserializer} :
 * ce dernier convertirait avant que le code d'ecoute ne soit appele, et un message
 * malforme echouerait dans le conteneur, hors de portee de toute capture (meme
 * raisonnement qu'au Sprint 5.2). La conversion JSON a lieu dans
 * {@code AuditEvenementConsumer}, en tolerant reader, sans dependance a
 * {@code EvenementAudit} (CLAUDE.md section 15).
 *
 * <h2>Lecture depuis l'offset le plus ancien</h2>
 *
 * <p>{@code auto-offset-reset=earliest} : un groupe qui s'abonne pour la premiere
 * fois doit relire l'integralite du topic depuis l'offset 0, condition posee par
 * ce sprint (fenetre de recuperation des 176 evenements deja publies depuis le
 * 27 aout). Une fois le groupe etabli, cet offset ne joue plus : Kafka reprend
 * la ou le groupe s'est arrete.
 */
@Configuration
@EnableKafka
public class ConfigurationConsommateurAudit {

    private static final Logger journal = LoggerFactory.getLogger(ConfigurationConsommateurAudit.class);

    @Bean("consumerFactoryAudit")
    public ConsumerFactory<String, String> consumerFactoryAudit(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String brokers,
            @Value("${app.audit.groupe}") String groupe) {

        Map<String, Object> configuration = new HashMap<>();
        configuration.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        configuration.put(ConsumerConfig.GROUP_ID_CONFIG, groupe);
        configuration.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configuration.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configuration.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // Offset valide par le conteneur apres traitement (AckMode.RECORD), jamais
        // sur une horloge de fond : un acquittement automatique pourrait survenir
        // avant que la trace ne soit persistee.
        configuration.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        journal.info("Consommateur audit configure : groupe={} brokers={}", groupe, brokers);

        return new DefaultKafkaConsumerFactory<>(configuration);
    }

    /**
     * Convertisseur JSON propre a ce consommateur, jamais un bean
     * {@code ObjectMapper} auto-configure : Spring Boot 4 n'en fournit aucun de
     * type {@code com.fasterxml.jackson.databind.ObjectMapper} (defaut releve au
     * Sprint 5.1). {@code JavaTimeModule} pour lire {@code dateAction} (ISO 8601
     * sans fuseau) ; {@code FAIL_ON_UNKNOWN_PROPERTIES} desactive pour rester
     * tolerant reader face a une evolution additive du contrat de fil.
     */
    @Bean("convertisseurAudit")
    public ObjectMapper convertisseurAudit() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Bean("kafkaListenerContainerFactoryAudit")
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactoryAudit(
            ConsumerFactory<String, String> consumerFactoryAudit) {

        ConcurrentKafkaListenerContainerFactory<String, String> fabrique =
                new ConcurrentKafkaListenerContainerFactory<>();
        fabrique.setConsumerFactory(consumerFactoryAudit);
        fabrique.getContainerProperties().setAckMode(AckMode.RECORD);
        // Confirmation explicite, au demarrage, de la souscription effective au
        // topic (guide de ce sprint, etape 2) : le symptome du piege du Sprint 5.2
        // est un demarrage muet, sans la moindre ligne de journal.
        fabrique.getContainerProperties().setConsumerRebalanceListener(
                new JournalisationSouscription());
        return fabrique;
    }

    private static final class JournalisationSouscription
            implements org.apache.kafka.clients.consumer.ConsumerRebalanceListener {

        @Override
        public void onPartitionsRevoked(java.util.Collection<org.apache.kafka.common.TopicPartition> partitions) {
            // Rien a faire : pas d'etat local a liberer.
        }

        @Override
        public void onPartitionsAssigned(java.util.Collection<org.apache.kafka.common.TopicPartition> partitions) {
            journal.info("Souscription audit effective : partitions assignees={}", partitions);
        }

    }

}
