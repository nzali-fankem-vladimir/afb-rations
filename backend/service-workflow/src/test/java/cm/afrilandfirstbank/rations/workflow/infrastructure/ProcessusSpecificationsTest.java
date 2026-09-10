package cm.afrilandfirstbank.rations.workflow.infrastructure;

import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.TransitionProcessus;

/**
 * La restriction de portée de la recherche, contre la vraie base
 * {@code rations_workflow} (Sprint 6.1, tests 6 et 7 du guide).
 *
 * <p>{@code @AutoConfigureTestDatabase(replace = NONE)} : les migrations Flyway
 * sont spécifiques à PostgreSQL, une base embarquée les ferait échouer (même
 * doctrine que {@code VerrouTransmissionServiceIT}).
 *
 * <h2>Ce que ce test établit, que des mocks ne peuvent pas établir</h2>
 *
 * <p>{@link RechercheProcessusService} délègue la construction de la requête à
 * {@link ProcessusSpecifications#avecFiltres}. Un test avec un repository
 * bouchonné ne prouverait que l'appel a été fait, pas que le filtre SQL produit
 * réellement les bonnes lignes. Seule une base réelle le prouve.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("ProcessusSpecifications — restriction de portée (Sprint 6.1)")
class ProcessusSpecificationsTest {

    // Periode et codes unite exclusifs a ce test : la base de developpement porte
    // des donnees accumulees d'autres executions (tests d'integration sans
    // rollback), et "00002"/"00003" a mois=8/annee=2026 y sont deja tres presents.
    private static final Integer MOIS = 3;
    private static final Integer ANNEE = 2099;

    /** Les bornes de la periode d'essai — mars 2099, un mois entier. */
    private static final LocalDate DEBUT = LocalDate.of(ANNEE, MOIS, 1);
    private static final LocalDate FIN = LocalDate.of(ANNEE, MOIS, 31);

    @Autowired
    private ProcessusMensuelRepository repository;

    private ProcessusMensuel enregistrer(String codeUnite) {
        return repository.saveAndFlush(declencherSur(MOIS, ANNEE, codeUnite));
    }

    @Test
    @DisplayName("6. portée locale : ne voit que les unités listées")
    void porteeLocale_neVoitQueSesUnites() {
        enregistrer("09001");
        enregistrer("09002");
        enregistrer("09099");

        List<ProcessusMensuel> resultat = repository.findAll(
                ProcessusSpecifications.avecFiltres(DEBUT, FIN, null, null, Set.of("09001", "09002")));

        assertThat(resultat)
                .extracting(ProcessusMensuel::getCodeUnite)
                .containsExactlyInAnyOrder("09001", "09002");
    }

    @Test
    @DisplayName("7. portée nationale : voit toutes les unités")
    void porteeNationale_voitToutesLesUnites() {
        enregistrer("09003");
        enregistrer("09004");

        // null = portee nationale, aucun filtre d'unite (voir ProcessusSpecifications).
        List<ProcessusMensuel> resultat = repository.findAll(
                ProcessusSpecifications.avecFiltres(DEBUT, FIN, null, null, null));

        assertThat(resultat)
                .extracting(ProcessusMensuel::getCodeUnite)
                .contains("09003", "09004");
    }

    @Test
    @DisplayName("Une portée vide (aucune unité) ne rend jamais rien, sans erreur SQL")
    void porteeVide_neRendRien() {
        enregistrer("09005");

        List<ProcessusMensuel> resultat = repository.findAll(
                ProcessusSpecifications.avecFiltres(DEBUT, FIN, null, null, Set.of()));

        assertThat(resultat).isEmpty();
    }

    @Test
    @DisplayName("Une unité demandée hors de la portée locale n'apparaît jamais, même filtrée explicitement")
    void uniteHorsPortee_filtreeExplicitement_neRendRien() {
        enregistrer("09006");

        List<ProcessusMensuel> resultat = repository.findAll(
                ProcessusSpecifications.avecFiltres(DEBUT, FIN, "09006", null, Set.of("09001")));

        assertThat(resultat).isEmpty();
    }


    /**
     * Un etat declenche sur le mois indique, borne du premier au dernier jour.
     *
     * <p><b>Le mois n'est evalue qu'une fois</b>, ce qui compte : les jeux d'essai
     * l'obtiennent souvent d'un compteur {@code prochainMois()} a effet de bord, et
     * l'inliner deux fois pour composer les deux bornes produirait une periode a
     * cheval sur deux mois differents.
     *
     * <p>Les periodes mensuelles restent DISJOINTES entre elles, ce qui est
     * desormais indispensable : la contrainte d'exclusion
     * {@code ex_processus_normal_sans_chevauchement} refuse deux etats NORMAL dont
     * les periodes se recouvrent, meme partiellement (Maille 1).
     */
    private static ProcessusMensuel declencherSur(int mois, int annee, String codeUnite) {
        LocalDate debut = LocalDate.of(annee, mois, 1);
        return TransitionProcessus.declencher(debut, debut.plusMonths(1).minusDays(1), codeUnite);
    }

}
