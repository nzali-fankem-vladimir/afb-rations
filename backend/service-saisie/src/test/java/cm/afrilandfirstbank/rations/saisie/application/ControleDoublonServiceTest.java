package cm.afrilandfirstbank.rations.saisie.application;

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
import cm.afrilandfirstbank.rations.saisie.infrastructure.BeneficiaireRepository;
import cm.afrilandfirstbank.rations.saisie.infrastructure.FicheJournaliereRepository;
import cm.afrilandfirstbank.rations.saisie.infrastructure.LignePrestationRepository;

/**
 * <b>RG-04</b> — unicite journaliere, tests 6 a 10 du Sprint 3.2.
 *
 * <p><b>Contre la vraie base {@code rations_saisie}, pas contre un bouchon.</b>
 * Un test a base de {@code Mockito} prouverait seulement que le service delegue
 * au repository — c'est-a-dire rien du tout sur la regle. Ce qu'il faut prouver
 * ici, c'est que la requete distingue reellement quatre elements, et cela ne se
 * verifie que sur une base qui execute le SQL. {@code @DataJpaTest} annule chaque
 * test en fin d'execution.
 *
 * <p><b>Les tests 7 a 10 sont les plus importants du sous-sprint.</b> Le test 6
 * verifie que le rempart existe ; les quatre suivants verifient qu'il ne bloque
 * pas des saisies parfaitement legitimes. Une regle d'unicite trop large est
 * plus difficile a detecter qu'une regle absente : elle ne produit pas de double
 * paiement, elle empeche un agent de la garde d'etre paye pour une prestation
 * qu'il a reellement effectuee, et personne ne s'en plaint dans les journaux.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("ControleDoublonService — unicite journaliere (RG-04)")
class ControleDoublonServiceTest {

    private static final Long ID_PROCESSUS = 7_800_002L;
    private static final LocalDate LE_15_AOUT = LocalDate.of(2026, 8, 15);
    private static final LocalDate LE_16_AOUT = LocalDate.of(2026, 8, 16);

    @Autowired
    private LignePrestationRepository ligneRepository;
    @Autowired
    private FicheJournaliereRepository ficheRepository;
    @Autowired
    private BeneficiaireRepository beneficiaireRepository;

    private ControleDoublonService controleDoublonService;

    private Long idFicheDu15;
    private Long idFicheDu16;
    private Long idMballa;
    private Long idNkoulou;

    /**
     * Situation de depart : MBALLA a recu une RATION de JOUR le 15 aout. Tout le
     * reste doit rester possible.
     */
    @BeforeEach
    void preparer() {
        // Le service est instancie a la main : @DataJpaTest ne charge que la
        // couche de persistance, pas les beans @Service. Ce qui est teste ici est
        // bien le service, avec son vrai repository derriere.
        controleDoublonService = new ControleDoublonService(ligneRepository);

        idMballa = beneficiaireRepository.saveAndFlush(
                new Beneficiaire("MBALLA", "Paul", "03702009998888", "00002")).getId(); // Douala Bonanjo
        idNkoulou = beneficiaireRepository.saveAndFlush(
                new Beneficiaire("NKOULOU", "Estelle", "03702007776666", "00001")).getId(); // Siege Yaounde

        idFicheDu15 = ficheRepository.saveAndFlush(
                new FicheJournaliere(ID_PROCESSUS, LE_15_AOUT, "00002", 8, 2026)).getId();
        idFicheDu16 = ficheRepository.saveAndFlush(
                new FicheJournaliere(ID_PROCESSUS, LE_16_AOUT, "00002", 8, 2026)).getId();

        ligneRepository.saveAndFlush(new LignePrestation(
                idFicheDu15, idMballa, NatureEnum.RATION, SessionEnum.JOUR, 2500, 12L));
    }

    @Test
    @DisplayName("6. meme beneficiaire, meme jour, meme nature, meme session : DOUBLON")
    void combinaisonIdentique_estUnDoublon() {
        boolean doublon = controleDoublonService.estDoublonSurLaJournee(
                idFicheDu15, idMballa, NatureEnum.RATION, SessionEnum.JOUR);

        assertThat(doublon)
                .as("deux fois la meme prestation le meme jour, c'est deux fois le meme paiement")
                .isTrue();
    }

    @Test
    @DisplayName("7. meme beneficiaire, meme jour, nature differente : ACCEPTE")
    void natureDifferente_nEstPasUnDoublon() {
        boolean doublon = controleDoublonService.estDoublonSurLaJournee(
                idFicheDu15, idMballa, NatureEnum.TRANSPORT, SessionEnum.JOUR);

        assertThat(doublon)
                .as("un agent peut recevoir une ration ET un transport le meme jour")
                .isFalse();
    }

    @Test
    @DisplayName("8. meme beneficiaire, meme jour, session differente : ACCEPTE")
    void sessionDifferente_nEstPasUnDoublon() {
        boolean doublon = controleDoublonService.estDoublonSurLaJournee(
                idFicheDu15, idMballa, NatureEnum.RATION, SessionEnum.SOIR);

        assertThat(doublon)
                .as("un agent de garde peut etre servi en session de jour ET de soir")
                .isFalse();
    }

    @Test
    @DisplayName("9. meme beneficiaire, meme combinaison, jour different : ACCEPTE")
    void journeeDifferente_nEstPasUnDoublon() {
        boolean doublon = controleDoublonService.estDoublonSurLaJournee(
                idFicheDu16, idMballa, NatureEnum.RATION, SessionEnum.JOUR);

        assertThat(doublon)
                .as("RG-04 s'arrete a la journee : une ration par jour, tous les jours, est la normale")
                .isFalse();
    }

    @Test
    @DisplayName("10. beneficiaire different, meme combinaison, meme jour : ACCEPTE")
    void beneficiaireDifferent_nEstPasUnDoublon() {
        boolean doublon = controleDoublonService.estDoublonSurLaJournee(
                idFicheDu15, idNkoulou, NatureEnum.RATION, SessionEnum.JOUR);

        assertThat(doublon)
                .as("toute l'unite recoit sa ration le meme jour : c'est le cas nominal, pas un doublon")
                .isFalse();
    }

}
