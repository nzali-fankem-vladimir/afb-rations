package cm.afrilandfirstbank.rations.transmission.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import cm.afrilandfirstbank.rations.commun.audit.AuditCommunAutoConfiguration;
import cm.afrilandfirstbank.rations.transmission.infrastructure.EtatValideProducer;

/**
 * Le cablage du producteur, verifie au demarrage plutot qu'a l'execution.
 *
 * <h2>Pourquoi ce fichier existe</h2>
 *
 * <p>Il a ete ecrit <b>apres coup</b>, a la verification manuelle du Sprint 5.1, qui a
 * revele un defaut qu'aucun test ne pouvait montrer : le service refusait de demarrer,
 * faute de bean {@code ObjectMapper}. Spring Boot 4 n'en auto-configure aucun de ce type —
 * la classe est bien au classpath, tiree par des dependances tierces, mais aucun bean
 * n'existe.
 *
 * <p>Les tests unitaires n'y voyaient rien : ils construisent leur propre convertisseur et
 * appellent le producteur directement, sans jamais demander a Spring de l'assembler. Un
 * defaut de cablage ne se voit qu'en assemblant.
 *
 * <h2>Pourquoi un ApplicationContextRunner, et non un @SpringBootTest</h2>
 *
 * <p>Un {@code @SpringBootTest} chargerait le contexte complet, donc la securite OAuth2,
 * donc un appel reel au realm Keycloak au demarrage. Le test dependrait alors d'un
 * conteneur externe, et echouerait pour une raison sans rapport avec ce qu'il verifie.
 * {@link ApplicationContextRunner} n'assemble que ce qu'on lui donne, sans reseau et en
 * quelques millisecondes.
 */
@DisplayName("Cablage du producteur de l'etat valide")
class CablageProducteurEtatValideTest {

    private final ApplicationContextRunner contexte = new ApplicationContextRunner()
            .withUserConfiguration(ConfigurationProducteurEtatValide.class)
            .withPropertyValues(
                    "spring.kafka.bootstrap-servers=localhost:9092",
                    "app.transmission.topic-etat-valide=rations.etat.valide");

    /**
     * L'assemblage aboutit : c'est exactement ce que la verification manuelle a pris en
     * defaut.
     */
    @Test
    @DisplayName("le producteur s'assemble : le service demarre")
    void producteurAssemblable() {
        new ApplicationContextRunner()
                .withUserConfiguration(ConfigurationProducteurEtatValide.class)
                .withPropertyValues("spring.kafka.bootstrap-servers=localhost:9092")
                .run(assemblage -> {
                    assertThat(assemblage).hasNotFailed();

                    EtatValideProducer producteur = new EtatValideProducer(
                            assemblage.getBean("kafkaTemplateEtatValide", KafkaTemplate.class),
                            assemblage.getBean("convertisseurChargeComptable", ObjectMapper.class),
                            "rations.etat.valide");

                    assertThat(producteur).isNotNull();
                });
    }

    /**
     * Les dates doivent partir en ISO 8601 (contrat d'API section 1.1), et non en tableau de
     * composants — le defaut de Jackson, releve et corrige au Sprint 2.2 dans
     * {@code DeltaAudit}.
     *
     * <p>La charge de la section 7.1 ne porte aujourd'hui aucune date. Le reglage doit
     * pourtant etre en place <b>avant</b> le Sprint 5.2, qui en ajoutera une a l'accuse :
     * un defaut de serialisation de date se decouvre autrement le jour ou une date apparait,
     * c'est-a-dire trop tard.
     */
    @Test
    @DisplayName("le convertisseur rend les dates en ISO 8601, jamais en tableau")
    void datesEnIso8601() {
        contexte.run(assemblage -> {
            ObjectMapper convertisseur =
                    assemblage.getBean("convertisseurChargeComptable", ObjectMapper.class);

            assertThat(convertisseur.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
                    .as("un [2026,9,1] au lieu d'un 2026-09-01 partirait vers un module "
                            + "auquel l'equipe n'a pas acces")
                    .isFalse();

            assertThat(convertisseur.writeValueAsString(java.time.LocalDate.of(2026, 9, 1)))
                    .isEqualTo("\"2026-09-01\"");
        });
    }

    /**
     * Le service porte <b>deux</b> {@code KafkaTemplate} : celui de l'echange comptable et
     * celui de l'audit, fourni par {@code rations-audit-commun}.
     *
     * <p>Ce test assemble les deux configurations ensemble et verifie que chacun est
     * joignable par son nom. Sans qualificatif, l'injection serait ambigue — et, pire, une
     * resolution par type pourrait publier les etats valides sur le topic du journal
     * d'audit, ou l'inverse. Ni le compilateur ni un test unitaire ne le verraient.
     */
    @Test
    @DisplayName("les deux KafkaTemplate coexistent sans ambiguite")
    void deuxTemplatesDistincts() {
        new ApplicationContextRunner()
                // AopAutoConfiguration est presente dans toute application Boot reelle, et
                // c'est elle qui impose les mandataires CGLIB. Sans elle, le banc de test
                // fabrique des mandataires JDK, et l'ecouteur @TransactionalEventListener du
                // module d'audit devient introuvable -- un artefact du montage, pas un
                // defaut du code : les cinq services demarrent avec ce meme module.
                .withConfiguration(AutoConfigurations.of(
                        org.springframework.boot.autoconfigure.aop.AopAutoConfiguration.class,
                        AuditCommunAutoConfiguration.class))
                .withUserConfiguration(ConfigurationProducteurEtatValide.class)
                .withPropertyValues(
                        "spring.kafka.bootstrap-servers=localhost:9092",
                        "spring.application.name=service-transmission")
                .run(assemblage -> {
                    assertThat(assemblage).hasNotFailed();
                    assertThat(assemblage).hasBean("kafkaTemplateEtatValide");
                    assertThat(assemblage).hasBean("kafkaTemplateAudit");

                    assertThat(assemblage.getBean("kafkaTemplateEtatValide"))
                            .isNotSameAs(assemblage.getBean("kafkaTemplateAudit"));
                });
    }

}
