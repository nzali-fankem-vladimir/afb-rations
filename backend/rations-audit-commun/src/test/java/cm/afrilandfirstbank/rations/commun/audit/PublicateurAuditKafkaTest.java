package cm.afrilandfirstbank.rations.commun.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Verifie les garanties du producteur d'audit, exigence centrale du Sprint 1.3 :
 * l'echec de publication ne doit JAMAIS faire echouer ni ralentir l'operation
 * metier (CLAUDE.md section 9.2).
 *
 * <p>Ces tests ne verifient pas que Kafka fonctionne — ils verifient ce qui se
 * passe quand il ne fonctionne pas.
 */
class PublicateurAuditKafkaTest {

    private static final String TOPIC = "rations.audit.evenement";
    private static final String NOM_DU_SERVICE = "service-identite";

    private KafkaTemplate<String, String> kafkaTemplate;
    private ApplicationEventPublisher publicateurSpring;
    private PublicateurAuditKafka producteur;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void preparer() {
        kafkaTemplate = mock(KafkaTemplate.class);
        publicateurSpring = mock(ApplicationEventPublisher.class);
        AuditProprietes proprietes = new AuditProprietes(TOPIC, Duration.ofSeconds(2),
                Duration.ofSeconds(30), Duration.ofSeconds(10), 2, 500);
        producteur = new PublicateurAuditKafka(kafkaTemplate, publicateurSpring, proprietes, NOM_DU_SERVICE);
    }

    private EvenementAudit attributionDeRole() {
        return EvenementAudit.de(1L, "ATTRIBUTION_ROLE", "utilisateurs", 11L, "10.20.30.40",
                DeltaAudit.nouveau()
                        .champ("role", "AGENT_UNITE", "CHEF_UNITE_DA")
                        .champ("codeUnite", "00002", "00003")
                        .enJson());
    }

    @Test
    @DisplayName("le service emetteur est estampille par le producteur, pas par l'appelant")
    void serviceEmetteurEstampille() {
        producteur.publier(attributionDeRole());

        ArgumentCaptor<Object> capture = ArgumentCaptor.forClass(Object.class);
        verify(publicateurSpring).publishEvent(capture.capture());

        assertThat(capture.getValue()).isInstanceOf(EvenementAudit.class);
        assertThat(((EvenementAudit) capture.getValue()).serviceEmetteur()).isEqualTo(NOM_DU_SERVICE);
    }

    @Test
    @DisplayName("broker indisponible : l'envoi leve, l'operation metier n'en sait rien")
    void brokerIndisponibleNeRemontePas() {
        // max.block.ms depasse : Kafka leve TimeoutException sur le thread appelant.
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new TimeoutException("Topic rations.audit.evenement not present in metadata"));

        assertThatCode(() -> producteur.envoyerSurLeTopic(attributionDeRole()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("livraison echouee de maniere asynchrone : rien ne remonte")
    void echecDeLivraisonAsynchroneNeRemontePas() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(
                        new TimeoutException("Expiring 1 record(s) for rations.audit.evenement")));

        assertThatCode(() -> producteur.envoyerSurLeTopic(attributionDeRole()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("le producteur n'attend jamais le futur : aucun get(), aucun join()")
    void nAttendJamaisLeFutur() {
        // Un futur qui ne se termine jamais : si le code appelait get() ou join(),
        // ce test ne rendrait pas la main. C'est la preuve la plus directe que la
        // publication est bien asynchrone.
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(new CompletableFuture<>());

        assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> producteur.envoyerSurLeTopic(attributionDeRole()),
                "le producteur a attendu le futur : la publication n'est plus asynchrone");
    }

    @Test
    @DisplayName("emission impossible : publier() ne remonte rien non plus")
    void echecDEmissionNeRemontePas() {
        doThrow(new IllegalStateException("contexte applicatif ferme"))
                .when(publicateurSpring).publishEvent(any(Object.class));

        assertThatCode(() -> producteur.publier(attributionDeRole()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("topic et cle de partition : les evenements d'une meme entite restent ordonnes")
    void topicEtClePartition() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        producteur.envoyerSurLeTopic(attributionDeRole());

        verify(kafkaTemplate).send(eq(TOPIC), eq("utilisateurs:11"), anyString());
    }

    @Test
    @DisplayName("la charge publiee porte les huit champs, dates en ISO 8601")
    void chargePubliee() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        EvenementAudit evenement = new EvenementAudit(1L, NOM_DU_SERVICE, "ATTRIBUTION_ROLE",
                "utilisateurs", 11L, LocalDateTime.of(2026, 8, 27, 9, 30, 0), "10.20.30.40",
                "{\"role\":{\"avant\":\"AGENT_UNITE\",\"apres\":\"CHEF_UNITE_DA\"}}");

        producteur.envoyerSurLeTopic(evenement);

        ArgumentCaptor<String> charge = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), anyString(), charge.capture());

        assertThat(charge.getValue())
                .contains("\"idUtilisateur\":1")
                .contains("\"serviceEmetteur\":\"service-identite\"")
                .contains("\"action\":\"ATTRIBUTION_ROLE\"")
                .contains("\"entiteCible\":\"utilisateurs\"")
                .contains("\"idEntite\":11")
                .contains("\"adresseIp\":\"10.20.30.40\"")
                // ISO 8601, pas un timestamp numerique (CLAUDE.md section 11)
                .contains("\"dateAction\":\"2026-08-27T09:30:00\"");
    }

    @Test
    @DisplayName("un evenement sans profil local (refus CT-04) reste publiable")
    void evenementSansIdentifiantUtilisateur() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        // Refus oppose a un jeton valide sans profil ouvert : aucun identifiant
        // local n'existe. Le cas le plus interessant a tracer ne doit pas etre
        // celui qu'on ne peut pas tracer.
        EvenementAudit refus = EvenementAudit.de(null, "ACCES_REFUSE", "utilisateurs", null,
                "10.20.30.41", null);

        assertThatCode(() -> producteur.envoyerSurLeTopic(refus)).doesNotThrowAnyException();
        verify(kafkaTemplate).send(eq(TOPIC), eq("utilisateurs:-"), anyString());
    }

}
