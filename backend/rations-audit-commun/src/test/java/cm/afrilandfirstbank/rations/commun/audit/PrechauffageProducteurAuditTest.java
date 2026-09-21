package cm.afrilandfirstbank.rations.commun.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Le prechauffage ne doit JAMAIS faire echouer ni retenir le demarrage : il
 * s'execute sur le pool d'audit et avale toute exception. Ces tests verifient ce
 * qui se passe quand le broker ne repond pas, pas que Kafka fonctionne.
 */
class PrechauffageProducteurAuditTest {

    private static final String TOPIC = "rations.audit.evenement";

    private KafkaTemplate<String, String> kafkaTemplate;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void preparer() {
        kafkaTemplate = mock(KafkaTemplate.class);
    }

    private PrechauffageProducteurAudit prechauffage(Executor executeur) {
        return new PrechauffageProducteurAudit(kafkaTemplate, TOPIC, executeur, Duration.ZERO);
    }

    @Test
    @DisplayName("broker joignable : les metadonnees du topic sont demandees une seule fois")
    void broker_joignable_une_seule_demande() {
        when(kafkaTemplate.partitionsFor(TOPIC)).thenReturn(List.of(mock(PartitionInfo.class)));

        prechauffage(Runnable::run).demarrer();

        verify(kafkaTemplate, times(1)).partitionsFor(TOPIC);
    }

    @Test
    @DisplayName("premiere tentative au-dela de la borne : une seconde tentative aboutit")
    void reessaie_apres_un_depassement() {
        when(kafkaTemplate.partitionsFor(TOPIC))
                .thenThrow(new TimeoutException("Topic not present in metadata after 2000 ms."))
                .thenReturn(List.of(mock(PartitionInfo.class)));

        prechauffage(Runnable::run).demarrer();

        verify(kafkaTemplate, times(2)).partitionsFor(TOPIC);
    }

    @Test
    @DisplayName("broker absent : trois tentatives au plus, puis on renonce sans lever")
    void broker_absent_renonce_sans_lever() {
        doThrow(new TimeoutException("Topic not present in metadata after 2000 ms."))
                .when(kafkaTemplate).partitionsFor(TOPIC);

        assertThatCode(() -> prechauffage(Runnable::run).demarrer()).doesNotThrowAnyException();

        verify(kafkaTemplate, times(PrechauffageProducteurAudit.TENTATIVES_MAX)).partitionsFor(TOPIC);
    }

    @Test
    @DisplayName("toute exception est avalee, y compris une exception imprevue")
    void exception_imprevue_avalee() {
        doThrow(new IllegalStateException("producteur ferme")).when(kafkaTemplate).partitionsFor(TOPIC);

        assertThatCode(() -> prechauffage(Runnable::run).demarrer()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("le prechauffage est confie au pool : demarrer() ne l'execute pas sur le fil appelant")
    void confie_au_pool() {
        AtomicBoolean confie = new AtomicBoolean(false);
        // Executeur qui capture la tache sans l'executer, comme un pool occupe.
        Executor capture = tache -> confie.set(true);

        prechauffage(capture).demarrer();

        assertThat(confie).isTrue();
        verify(kafkaTemplate, times(0)).partitionsFor(TOPIC);
    }

    @Test
    @DisplayName("pool sature ou ferme : le rejet n'echoue pas le demarrage")
    void rejet_du_pool_sans_effet() {
        Executor sature = tache -> {
            throw new RejectedExecutionException("file pleine");
        };

        assertThatCode(() -> prechauffage(sature).demarrer()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("arret pendant l'attente : on renonce et l'interruption est conservee")
    void interruption_conservee() {
        doThrow(new TimeoutException("delai")).when(kafkaTemplate).partitionsFor(TOPIC);
        PrechauffageProducteurAudit avecPause = new PrechauffageProducteurAudit(kafkaTemplate, TOPIC,
                Runnable::run, Duration.ofSeconds(30));

        Thread.currentThread().interrupt();
        try {
            avecPause.demarrer();
            // Un seul essai : la pause a ete interrompue, on ne poursuit pas.
            verify(kafkaTemplate, times(1)).partitionsFor(TOPIC);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

}
