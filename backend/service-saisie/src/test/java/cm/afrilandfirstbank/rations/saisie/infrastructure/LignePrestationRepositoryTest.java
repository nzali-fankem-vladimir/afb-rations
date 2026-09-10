package cm.afrilandfirstbank.rations.saisie.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import cm.afrilandfirstbank.rations.saisie.domaine.Beneficiaire;
import cm.afrilandfirstbank.rations.saisie.domaine.FicheJournaliere;
import cm.afrilandfirstbank.rations.saisie.domaine.LignePrestation;
import cm.afrilandfirstbank.rations.saisie.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.SessionEnum;

/**
 * Tests du contrôle d'existence de ligne, qui portera <b>RG-04</b> (unicité
 * journalière) au sous-sprint 3.2.
 *
 * <p>Les tests 7 et 8 sont les plus importants du sous-sprint : ils prouvent que
 * le contrôle porte sur la <b>combinaison complète</b>
 * (bénéficiaire × fiche × nature × session), et non sur le seul bénéficiaire. Un
 * même agent peut être servi le même jour en RATION et en TRANSPORT, en JOUR et
 * en SOIR.
 *
 * <p>Contre la vraie base {@code rations_saisie} : les trois entités ont des clés
 * étrangères réelles entre elles (migration V1), une base embarquée ne les
 * porterait pas. {@code @DataJpaTest} annule chaque test en fin d'exécution.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("LignePrestationRepository — contrôle d'existence (RG-04)")
class LignePrestationRepositoryTest {

    private static final Long ID_PROCESSUS = 7_800_001L;

    @Autowired
    private LignePrestationRepository ligneRepository;
    @Autowired
    private FicheJournaliereRepository ficheRepository;
    @Autowired
    private BeneficiaireRepository beneficiaireRepository;

    private Long idFiche;
    private Long idBeneficiaire;

    @BeforeEach
    void preparerUneLigneRationJour() {
        Beneficiaire beneficiaire = beneficiaireRepository.saveAndFlush(
                new Beneficiaire("MBALLA", "Paul", "03702009998888", "00002")); // Douala Bonanjo
        idBeneficiaire = beneficiaire.getId();

        FicheJournaliere fiche = ficheRepository.saveAndFlush(
                new FicheJournaliere(ID_PROCESSUS, LocalDate.of(2026, 8, 15), "00002", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));
        idFiche = fiche.getId();

        ligneRepository.saveAndFlush(new LignePrestation(
                idFiche, idBeneficiaire, NatureEnum.RATION, SessionEnum.JOUR, 2500, null));
    }

    @Test
    @DisplayName("6. existence vraie sur la combinaison présente (RATION / JOUR)")
    void existence_vraiSurLaCombinaisonPresente() {
        boolean existe = ligneRepository.existsByIdFicheJournaliereAndIdBeneficiaireAndNatureAndSession(
                idFiche, idBeneficiaire, NatureEnum.RATION, SessionEnum.JOUR);

        assertThat(existe).isTrue();
    }

    @Test
    @DisplayName("7. existence fausse quand la session diffère (RATION / SOIR)")
    void existence_fauxQuandLaSessionDiffere() {
        boolean existe = ligneRepository.existsByIdFicheJournaliereAndIdBeneficiaireAndNatureAndSession(
                idFiche, idBeneficiaire, NatureEnum.RATION, SessionEnum.SOIR);

        assertThat(existe)
                .as("même bénéficiaire, même fiche, même nature, mais SOIR : RG-04 ne doit pas bloquer")
                .isFalse();
    }

    @Test
    @DisplayName("8. existence fausse quand la nature diffère (TRANSPORT / JOUR)")
    void existence_fauxQuandLaNatureDiffere() {
        boolean existe = ligneRepository.existsByIdFicheJournaliereAndIdBeneficiaireAndNatureAndSession(
                idFiche, idBeneficiaire, NatureEnum.TRANSPORT, SessionEnum.JOUR);

        assertThat(existe)
                .as("même bénéficiaire, même fiche, même session, mais TRANSPORT : RG-04 ne doit pas bloquer")
                .isFalse();
    }

}
