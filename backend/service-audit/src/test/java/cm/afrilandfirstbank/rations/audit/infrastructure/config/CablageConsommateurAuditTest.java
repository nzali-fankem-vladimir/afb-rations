package cm.afrilandfirstbank.rations.audit.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;

import cm.afrilandfirstbank.rations.audit.infrastructure.AuditEvenementConsumer;
import cm.afrilandfirstbank.rations.audit.infrastructure.AuditLogRepository;

/**
 * Le cablage du consommateur d'audit, verifie <b>a l'assemblage</b> plutot
 * qu'a l'execution — meme demarche que {@code CablageConsommateurAccuseTest}
 * (service Transmission, Sprint 5.2), pour la meme raison.
 *
 * <h2>Pourquoi ce test remplace le test de reprise « bout en bout » du guide
 * (etape 7, preuve 2)</h2>
 *
 * <p>Le pom parent epingle {@code spring-kafka} en 3.3.0 (Sprint 0.2) tandis
 * que les {@code kafka-clients} sont en 4.2.1 : {@code @EmbeddedKafka} echoue
 * au demarrage du broker de test (Kafka 4 a deplace
 * {@code kafka.testkit.KafkaClusterTestKit}). Meme contrainte, meme
 * consequence qu'au Sprint 5.2 : la reprise reelle depuis l'offset 0 se
 * verifie manuellement contre le conteneur {@code rations-kafka}
 * (docs/robustesse-audit.md), pas par un test automatise qui simulerait un
 * redemarrage.
 *
 * <h2>Ce que ce test verifie, et que les tests unitaires ne peuvent pas voir</h2>
 *
 * <p>Le meme piege qu'au Sprint 5.2, ici pour la deuxieme fois dans le
 * module : sans {@code @EnableKafka}, le service demarre normalement et
 * n'ecoute rien — pas une ligne de journal, pas une erreur. Il verifie aussi
 * les trois reglages qui portent la garantie de reprise sans perte ni doublon
 * (guide de ce sprint, section 5) : groupe de consommateurs <b>fixe</b>
 * (jamais genere), acquittement <b>apres</b> traitement, et lecture depuis
 * l'offset le plus ancien pour un groupe qui s'abonne pour la premiere fois.
 */
@DisplayName("Cablage du consommateur d'audit")
class CablageConsommateurAuditTest {

    private final ApplicationContextRunner contexte = new ApplicationContextRunner()
            .withUserConfiguration(ConfigurationConsommateurAudit.class, DoublonsDeTest.class)
            .withPropertyValues(
                    "spring.kafka.bootstrap-servers=localhost:9092",
                    "app.audit.topic=rations.audit.evenement",
                    "app.audit.groupe=rations-audit-lecture");

    @Test
    @DisplayName("l'assemblage aboutit : le consommateur et sa fabrique existent")
    void assemblageAboutit() {
        contexte.run(assemblage -> {
            assertThat(assemblage).hasNotFailed();
            assertThat(assemblage).hasBean("consumerFactoryAudit");
            assertThat(assemblage).hasBean("kafkaListenerContainerFactoryAudit");
            assertThat(assemblage).hasBean("convertisseurAudit");
        });
    }

    @Test
    @DisplayName("l'ECOUTE est effectivement enregistree : sans @EnableKafka, le service "
            + "demarre normalement et n'ecoute rien -- pas une ligne, pas une erreur "
            + "(meme piege qu'au Sprint 5.2, ici pour la 2e fois du module)")
    void ecouteEffectivementEnregistree() {
        contexte.withBean(AuditEvenementConsumer.class).run(assemblage -> {
            assertThat(assemblage).hasNotFailed();
            assertThat(assemblage).hasSingleBean(KafkaListenerEndpointRegistry.class);

            KafkaListenerEndpointRegistry registre = assemblage.getBean(KafkaListenerEndpointRegistry.class);
            assertThat(registre.getListenerContainers())
                    .describedAs("conteneurs d'ecoute enregistres")
                    .isNotEmpty();
        });
    }

    @Test
    @DisplayName("deserialisation en CHAINE, pas en JSON : un JsonDeserializer echouerait "
            + "dans le conteneur, hors de portee du code")
    void deserialisationEnChaine() {
        contexte.run(assemblage -> {
            DefaultKafkaConsumerFactory<?, ?> fabrique =
                    (DefaultKafkaConsumerFactory<?, ?>) assemblage.getBean("consumerFactoryAudit");

            assertThat(fabrique.getConfigurationProperties()
                    .get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG).toString())
                    .contains("StringDeserializer");
            assertThat(fabrique.getConfigurationProperties()
                    .get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG).toString())
                    .contains("StringDeserializer");
        });
    }

    @Test
    @DisplayName("groupe de consommateurs FIXE (rations-audit-lecture), jamais genere : "
            + "un redemarrage doit reprendre au dernier offset acquitte, pas relire depuis "
            + "le debut a chaque lancement")
    void groupeDeConsommateursFixe() {
        contexte.run(assemblage -> {
            DefaultKafkaConsumerFactory<?, ?> fabrique =
                    (DefaultKafkaConsumerFactory<?, ?>) assemblage.getBean("consumerFactoryAudit");

            assertThat(fabrique.getConfigurationProperties()
                    .get(ConsumerConfig.GROUP_ID_CONFIG)).isEqualTo("rations-audit-lecture");
        });
    }

    @Test
    @DisplayName("acquittement APRES traitement, jamais automatique sur une horloge : "
            + "un acquittement anticipe perdrait un evenement si le service Audit "
            + "tombait entre les deux")
    void acquittementApresTraitement() {
        contexte.run(assemblage -> {
            ConcurrentKafkaListenerContainerFactory<?, ?> fabrique =
                    (ConcurrentKafkaListenerContainerFactory<?, ?>)
                            assemblage.getBean("kafkaListenerContainerFactoryAudit");
            assertThat(fabrique.getContainerProperties().getAckMode()).isEqualTo(AckMode.RECORD);

            DefaultKafkaConsumerFactory<?, ?> consommateurs =
                    (DefaultKafkaConsumerFactory<?, ?>) assemblage.getBean("consumerFactoryAudit");
            assertThat(consommateurs.getConfigurationProperties()
                    .get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG)).isEqualTo(false);
        });
    }

    @Test
    @DisplayName("un groupe qui s'abonne pour la premiere fois lit depuis le DEBUT : "
            + "condition de la reprise des 176+ evenements deja publies depuis le "
            + "27 aout (fenetre de recuperation, guide de ce sprint section 3)")
    void lectureDepuisLeDebut() {
        contexte.run(assemblage -> {
            DefaultKafkaConsumerFactory<?, ?> fabrique =
                    (DefaultKafkaConsumerFactory<?, ?>) assemblage.getBean("consumerFactoryAudit");

            assertThat(fabrique.getConfigurationProperties()
                    .get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG)).isEqualTo("earliest");
        });
    }

    /** Le seul collaborateur du consommateur, hors du perimetre de ce cablage. */
    @Configuration
    static class DoublonsDeTest {

        @Bean
        AuditLogRepository auditLogRepository() {
            return mock(AuditLogRepository.class);
        }

    }

}
