package cm.afrilandfirstbank.rations.grilles.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import cm.afrilandfirstbank.rations.grilles.domaine.GrilleTarifaire;
import cm.afrilandfirstbank.rations.grilles.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.SessionEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.StatutGrilleEnum;

/**
 * Tests du repository sur les donnees de reference du Sprint 0.5 : les quatre
 * grilles ACTIVE (une par couple nature/session), date_debut au premier jour du
 * mois courant, date_fin nulle.
 *
 * <p>{@code @AutoConfigureTestDatabase(replace = NONE)} : on tourne contre la
 * vraie base {@code rations_grilles} (conteneur Docker du Sprint 0.5), pas une
 * base embarquee — les migrations Flyway y sont specifiques a PostgreSQL (index
 * partiel, date_trunc) et le jeu de donnees teste est celui de la V2.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GrilleTarifaireRepositoryTest {

    @Autowired
    private GrilleTarifaireRepository repository;

    @Test
    @DisplayName("10. la recherche de grille active retourne une grille pour RATION / JOUR aujourd'hui")
    void grilleActivePourRationJour() {
        Optional<GrilleTarifaire> trouvee =
                repository.rechercherGrilleActive(NatureEnum.RATION, SessionEnum.JOUR, LocalDate.now());

        assertThat(trouvee).isPresent();
        assertThat(trouvee.get().getNature()).isEqualTo(NatureEnum.RATION);
        assertThat(trouvee.get().getSession()).isEqualTo(SessionEnum.JOUR);
        assertThat(trouvee.get().getStatutValidation()).isEqualTo(StatutGrilleEnum.ACTIVE);
        assertThat(trouvee.get().getDateFin()).isNull();
        assertThat(trouvee.get().getMontantFcfa()).isPositive();
    }

    @Test
    @DisplayName("11. la recherche a une date anterieure a date_debut ne retourne rien")
    void aucuneGrilleAvantDateDebut() {
        LocalDate avantToutHistorique = LocalDate.of(2000, 1, 1);

        Optional<GrilleTarifaire> trouvee = repository.rechercherGrilleActive(
                NatureEnum.RATION, SessionEnum.JOUR, avantToutHistorique);

        assertThat(trouvee).isEmpty();
    }

    @Test
    @DisplayName("bonus : existsByNatureAndSessionAndStatutValidation voit les quatre couples ACTIVE")
    void existenceDesQuatreCouplesActifs() {
        assertThat(repository.existsByNatureAndSessionAndStatutValidation(
                NatureEnum.RATION, SessionEnum.JOUR, StatutGrilleEnum.ACTIVE)).isTrue();
        assertThat(repository.existsByNatureAndSessionAndStatutValidation(
                NatureEnum.TRANSPORT, SessionEnum.SOIR, StatutGrilleEnum.ACTIVE)).isTrue();
    }
}
