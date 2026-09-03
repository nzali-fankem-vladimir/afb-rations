package cm.afrilandfirstbank.rations.transmission.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.test.util.ReflectionTestUtils;

import org.springframework.kafka.config.KafkaListenerEndpointRegistry;

import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.transmission.application.TraitementAccuseService;
import cm.afrilandfirstbank.rations.transmission.infrastructure.AccuseComptableConsumer;

/**
 * Le cablage du consommateur de l'accuse, verifie <b>a l'assemblage</b> plutot qu'a
 * l'execution (Sprint 5.2).
 *
 * <h2>Pourquoi ce test remplace le test bout en bout du guide (test 10)</h2>
 *
 * <p>Le guide demande un bout en bout « avec un Kafka de test ». Il n'est pas
 * automatisable en l'etat : le pom parent epingle {@code spring-kafka} en <b>3.3.0</b>
 * (Sprint 0.2) tandis que les {@code kafka-clients} sont en <b>4.2.1</b>, et Kafka 4 a
 * deplace {@code kafka.testkit.KafkaClusterTestKit} — {@code @EmbeddedKafka} echoue donc
 * au demarrage du broker. La montee vers {@code spring-kafka} 4.1.0, deja gere par Spring
 * Boot 4.1, touche les producteurs des <b>six</b> services et releve d'un sprint technique
 * dedie ({@code docs/points-en-attente.md}). Le bout en bout reel se fait donc a la
 * verification manuelle, sur le conteneur {@code rations-kafka}, comme le guide section 8
 * le prescrit deja.
 *
 * <h2>Ce que ce test attrape, et que les tests unitaires ne peuvent pas voir</h2>
 *
 * <p>Le defaut de cablage. C'est exactement ce qui avait fait echouer le demarrage au
 * Sprint 5.1 — un bean {@code ObjectMapper} absent, que Spring Boot 4 n'auto-configure pas
 * — sans qu'aucun des vingt-neuf tests d'alors ne puisse le montrer : ils construisaient
 * tout a la main. Ici, c'est Spring qui assemble.
 *
 * <p>Il verifie aussi les trois reglages qui portent les decisions du sous-sprint : un seul
 * fil, l'acquittement <b>apres</b> traitement, et un reessai borne. Les laisser sans garde
 * les rendrait modifiables sans que personne ne relise le raisonnement.
 */
@DisplayName("Cablage du consommateur de l'accuse comptable")
class CablageConsommateurAccuseTest {

    private final ApplicationContextRunner contexte = new ApplicationContextRunner()
            .withUserConfiguration(ConfigurationConsommateurAccuse.class, DoublonsDeTest.class)
            .withPropertyValues(
                    "spring.kafka.bootstrap-servers=localhost:9092",
                    "app.transmission.topic-etat-accuse=rations.etat.accuse",
                    "app.transmission.groupe-accuse=rations-transmission-accuse");

    @Test
    @DisplayName("l'assemblage aboutit : le consommateur et sa fabrique existent")
    void assemblageAboutit() {
        contexte.run(assemblage -> {
            assertThat(assemblage).hasNotFailed();
            assertThat(assemblage).hasBean("consumerFactoryAccuse");
            assertThat(assemblage).hasBean("kafkaListenerContainerFactoryAccuse");
        });
    }

    @Test
    @DisplayName("le consommateur s'assemble avec le convertisseur de la charge comptable : "
            + "c'est l'absence de ce bean qui bloquait le demarrage au Sprint 5.1")
    void consommateurAssemblableAvecSonConvertisseur() {
        contexte.withUserConfiguration(ConfigurationProducteurEtatValide.class)
                .withBean(AccuseComptableConsumer.class)
                .run(assemblage -> {
                    assertThat(assemblage).hasNotFailed();
                    assertThat(assemblage).hasSingleBean(AccuseComptableConsumer.class);
                });
    }

    @Test
    @DisplayName("l'ECOUTE est effectivement enregistree : sans @EnableKafka, le service "
            + "demarre normalement et n'ecoute rien -- pas une ligne, pas une erreur")
    void ecouteEffectivementEnregistree() {
        // NON-REGRESSION D'UN DEFAUT REEL, trouve a la verification manuelle du Sprint 5.2.
        // Le service demarrait, tous les beans existaient, et AUCUN consommateur ne
        // s'abonnait au topic : sans @EnableKafka, l'annotation @KafkaListener n'est jamais
        // traitee et la methode d'ecoute reste une methode ordinaire que personne n'appelle.
        //
        // Les six tests voisins ne pouvaient rien voir : ils verifient que les beans
        // existent, pas que l'ecoute est ENREGISTREE. C'est la meme famille de defaut que
        // le bean ObjectMapper absent au Sprint 5.1 -- un cablage qui ne se voit qu'en
        // assemblant.
        contexte.withUserConfiguration(ConfigurationProducteurEtatValide.class)
                .withBean(AccuseComptableConsumer.class)
                .run(assemblage -> {
                    assertThat(assemblage).hasNotFailed();
                    assertThat(assemblage).hasSingleBean(KafkaListenerEndpointRegistry.class);

                    KafkaListenerEndpointRegistry registre =
                            assemblage.getBean(KafkaListenerEndpointRegistry.class);
                    assertThat(registre.getListenerContainers())
                            .describedAs("conteneurs d'ecoute enregistres")
                            .isNotEmpty();
                });
    }

    @Test
    @DisplayName("deserialisation en CHAINE, pas en JSON : un JsonDeserializer echouerait dans "
            + "le conteneur, hors de portee du code, et le message empoisonne serait rejoue "
            + "sans fin")
    void deserialisationEnChaine() {
        contexte.run(assemblage -> {
            DefaultKafkaConsumerFactory<?, ?> fabrique = (DefaultKafkaConsumerFactory<?, ?>)
                    assemblage.getBean("consumerFactoryAccuse");

            assertThat(fabrique.getConfigurationProperties()
                    .get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG).toString())
                    .contains("StringDeserializer");
            assertThat(fabrique.getConfigurationProperties()
                    .get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG).toString())
                    .contains("StringDeserializer");
        });
    }

    @Test
    @DisplayName("acquittement APRES traitement, et jamais automatique sur une horloge : un "
            + "acquittement anticipe perdrait un accuse si le Workflow tombait entre les deux")
    void acquittementApresTraitement() {
        contexte.run(assemblage -> {
            ConcurrentKafkaListenerContainerFactory<?, ?> fabrique =
                    (ConcurrentKafkaListenerContainerFactory<?, ?>)
                            assemblage.getBean("kafkaListenerContainerFactoryAccuse");

            assertThat(fabrique.getContainerProperties().getAckMode()).isEqualTo(AckMode.RECORD);

            DefaultKafkaConsumerFactory<?, ?> consommateurs = (DefaultKafkaConsumerFactory<?, ?>)
                    assemblage.getBean("consumerFactoryAccuse");
            assertThat(consommateurs.getConfigurationProperties()
                    .get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG)).isEqualTo(false);
        });
    }

    @Test
    @DisplayName("un seul fil : le debit n'est pas l'enjeu, et deux fils pourraient traiter deux "
            + "accuses du meme etat en meme temps")
    void unSeulFil() {
        contexte.run(assemblage -> {
            ConcurrentKafkaListenerContainerFactory<?, ?> fabrique =
                    (ConcurrentKafkaListenerContainerFactory<?, ?>)
                            assemblage.getBean("kafkaListenerContainerFactoryAccuse");

            assertThat(fabrique.getContainerProperties()).isNotNull();
            assertThat(ConfigurationConsommateurAccuse.NOMBRE_DE_FILS).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("un gestionnaire d'erreurs est pose, et son reessai est BORNE : un rejeu "
            + "illimite tiendrait la partition bloquee tant que le Workflow ne repond pas")
    void reessaiBorne() {
        contexte.run(assemblage -> {
            ConcurrentKafkaListenerContainerFactory<?, ?> fabrique =
                    (ConcurrentKafkaListenerContainerFactory<?, ?>)
                            assemblage.getBean("kafkaListenerContainerFactoryAccuse");

            CommonErrorHandler gestionnaire = (CommonErrorHandler)
                    ReflectionTestUtils.getField(fabrique, "commonErrorHandler");
            assertThat(gestionnaire).isInstanceOf(DefaultErrorHandler.class);
            assertThat(ConfigurationConsommateurAccuse.TENTATIVES_SUPPLEMENTAIRES)
                    .isGreaterThan(0L)
                    .isLessThanOrEqualTo(3L);
        });
    }

    @Test
    @DisplayName("un groupe qui s'abonne pour la premiere fois lit depuis le debut : sinon les "
            + "accuses publies avant le premier demarrage seraient perdus sans un mot")
    void lectureDepuisLeDebut() {
        contexte.run(assemblage -> {
            DefaultKafkaConsumerFactory<?, ?> fabrique = (DefaultKafkaConsumerFactory<?, ?>)
                    assemblage.getBean("consumerFactoryAccuse");

            assertThat(fabrique.getConfigurationProperties()
                    .get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG)).isEqualTo("earliest");
        });
    }

    /** Les collaborateurs du consommateur, hors du perimetre de ce cablage. */
    @Configuration
    static class DoublonsDeTest {

        @Bean
        TraitementAccuseService traitementAccuseService() {
            return mock(TraitementAccuseService.class);
        }

        @Bean
        PublicateurAudit publicateurAudit() {
            return mock(PublicateurAudit.class);
        }

    }

}
