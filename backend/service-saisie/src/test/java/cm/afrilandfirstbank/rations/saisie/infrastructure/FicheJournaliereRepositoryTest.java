package cm.afrilandfirstbank.rations.saisie.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import cm.afrilandfirstbank.rations.saisie.domaine.FicheJournaliere;

/**
 * Tests de recherche sur {@code fiche_journaliere}, contre la vraie base
 * {@code rations_saisie} (conteneur Docker du Sprint 0.5).
 *
 * <p>{@code @AutoConfigureTestDatabase(replace = NONE)} : les migrations Flyway
 * sont spécifiques à PostgreSQL, une base embarquée les ferait échouer
 * (décision {@code docs/decisions/2026-08-27-tests-repository-et-validation-schema.md}).
 * {@code @DataJpaTest} annule chaque test en fin d'exécution : rien n'est laissé
 * en base.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("FicheJournaliereRepository (RG-05)")
class FicheJournaliereRepositoryTest {

    // Identifiant de processus fictif, propre à ce test : la fiche n'a pas de clé
    // étrangère vers processus_mensuel (autre base), un Long quelconque suffit.
    private static final Long ID_PROCESSUS = 7_700_001L;

    @Autowired
    private FicheJournaliereRepository repository;

    @Test
    @DisplayName("4. recherche par processus et date : retrouve la bonne fiche")
    void rechercheParProcessusEtDate_retrouveLaBonneFiche() {
        repository.saveAndFlush(new FicheJournaliere(ID_PROCESSUS, LocalDate.of(2026, 8, 15)));
        repository.saveAndFlush(new FicheJournaliere(ID_PROCESSUS, LocalDate.of(2026, 8, 16)));

        Optional<FicheJournaliere> trouvee =
                repository.findByIdProcessusAndDateJour(ID_PROCESSUS, LocalDate.of(2026, 8, 15));

        assertThat(trouvee).isPresent();
        assertThat(trouvee.get().getIdProcessus()).isEqualTo(ID_PROCESSUS);
        assertThat(trouvee.get().getDateJour()).isEqualTo(LocalDate.of(2026, 8, 15));
    }

    @Test
    @DisplayName("5. recherche pour une date sans fiche : ne retourne rien")
    void rechercheParProcessusEtDate_sansFiche_neRetourneRien() {
        repository.saveAndFlush(new FicheJournaliere(ID_PROCESSUS, LocalDate.of(2026, 8, 15)));

        Optional<FicheJournaliere> trouvee =
                repository.findByIdProcessusAndDateJour(ID_PROCESSUS, LocalDate.of(2026, 8, 20));

        assertThat(trouvee).isEmpty();
    }

}
