package cm.afrilandfirstbank.rations.workflow.domaine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Le decoupage du parcours en cycles de validation, support de RG-12.
 *
 * <p>Le test central est {@link #leRetourClotUnCycle()} : c'est lui qui verrouille
 * la raison d'etre de cette classe — sans le decoupage en cycles, l'unique chef
 * d'unite d'une agence serait definitivement incapable de valider un etat qu'il a
 * lui-meme retourne pour correction.
 */
class CycleValidationTest {

    private static final Long AGENT = 41L;
    private static final Long CHEF_UNITE = 57L;
    private static final Long DIRECTEUR_RESEAU = 12L;

    private static final Long ID_PROCESSUS = 740L;

    // --- Cycle courant -------------------------------------------------------------

    @Test
    @DisplayName("1. Un parcours vide n'a aucun cycle courant")
    void parcoursVide() {
        assertThat(CycleValidation.etapesDuCycleCourant(List.of())).isEmpty();
        assertThat(CycleValidation.etapesDuCycleCourant(null)).isEmpty();
    }

    @Test
    @DisplayName("2. Un premier circuit sans retour : tout le parcours est le cycle courant")
    void premierCircuit() {
        List<EtapeWorkflow> parcours = List.of(
                etape(1, AGENT, NomEtapeEnum.SOUMISSION_AGENT),
                etape(2, CHEF_UNITE, NomEtapeEnum.VALIDATION_DA));

        assertThat(CycleValidation.etapesDuCycleCourant(parcours))
                .extracting(EtapeWorkflow::getOrdreEtape)
                .containsExactly(1, 2);
    }

    @Test
    @DisplayName("3. Le cycle courant part de la DERNIERE soumission, pas de la premiere")
    void cycleCourantPartDeLaDerniereSoumission() {
        List<EtapeWorkflow> parcours = List.of(
                etape(1, AGENT, NomEtapeEnum.SOUMISSION_AGENT),
                etape(2, CHEF_UNITE, NomEtapeEnum.VALIDATION_DA),
                etape(3, AGENT, NomEtapeEnum.SOUMISSION_AGENT),
                etape(4, CHEF_UNITE, NomEtapeEnum.VALIDATION_DA));

        assertThat(CycleValidation.etapesDuCycleCourant(parcours))
                .extracting(EtapeWorkflow::getOrdreEtape)
                .containsExactly(3, 4);
    }

    @Test
    @DisplayName("4. Sans aucune soumission, le cycle courant est tout le parcours (conservateur)")
    void parcoursSansSoumission() {
        List<EtapeWorkflow> parcours = List.of(
                etape(1, CHEF_UNITE, NomEtapeEnum.VALIDATION_DA));

        assertThat(CycleValidation.etapesDuCycleCourant(parcours)).hasSize(1);
    }

    // --- Etape deja realisee par un acteur ------------------------------------------

    @Test
    @DisplayName("5. L'agent qui a soumis la version courante a bien une etape dans le cycle")
    void soumissionnaireDuCycleCourant() {
        List<EtapeWorkflow> parcours = List.of(
                etape(1, AGENT, NomEtapeEnum.SOUMISSION_AGENT));

        Optional<EtapeWorkflow> trouvee =
                CycleValidation.etapeDeLActeurDansLeCycleCourant(parcours, AGENT);

        assertThat(trouvee).isPresent();
        assertThat(trouvee.get().getNomEtape()).isEqualTo(NomEtapeEnum.SOUMISSION_AGENT);
    }

    @Test
    @DisplayName("6. Un acteur qui n'a rien fait sur le dossier n'a aucune etape")
    void acteurNeuf() {
        List<EtapeWorkflow> parcours = List.of(
                etape(1, AGENT, NomEtapeEnum.SOUMISSION_AGENT));

        assertThat(CycleValidation.etapeDeLActeurDansLeCycleCourant(parcours, CHEF_UNITE))
                .isEmpty();
    }

    @Test
    @DisplayName("7. RG-12 : le chef d'unite qui a valide le cycle courant y a bien une etape")
    void valideurDuCycleCourant() {
        List<EtapeWorkflow> parcours = List.of(
                etape(1, AGENT, NomEtapeEnum.SOUMISSION_AGENT),
                etape(2, CHEF_UNITE, NomEtapeEnum.VALIDATION_DA));

        assertThat(CycleValidation.etapeDeLActeurDansLeCycleCourant(parcours, CHEF_UNITE))
                .isPresent();
    }

    @Test
    @DisplayName("8. LE TEST CENTRAL : un retour clot le cycle, le valideur retrouve sa liberte")
    void leRetourClotUnCycle() {
        // Cycle 1 : l'agent soumet, le chef d'unite retourne pour correction.
        // Cycle 2 : l'agent corrige et resoumet. Le chef d'unite, seul DA habilite
        //           sur son unite, doit pouvoir examiner la version corrigee.
        List<EtapeWorkflow> parcours = List.of(
                etape(1, AGENT, NomEtapeEnum.SOUMISSION_AGENT),
                etape(2, CHEF_UNITE, NomEtapeEnum.VALIDATION_DA),
                etape(3, AGENT, NomEtapeEnum.SOUMISSION_AGENT));

        assertThat(CycleValidation.etapeDeLActeurDansLeCycleCourant(parcours, CHEF_UNITE))
                .as("le retour du cycle 1 ne doit plus peser contre le chef d'unite au cycle 2")
                .isEmpty();

        assertThat(CycleValidation.etapeDeLActeurDansLeCycleCourant(parcours, AGENT))
                .as("l'agent, lui, vient de resoumettre : il ne peut pas valider")
                .isPresent();
    }

    @Test
    @DisplayName("9. Le directeur reseau ayant retourne au cycle 1 revalide au cycle 2")
    void retourDirecteurReseauPuisResoumission() {
        List<EtapeWorkflow> parcours = List.of(
                etape(1, AGENT, NomEtapeEnum.SOUMISSION_AGENT),
                etape(2, CHEF_UNITE, NomEtapeEnum.VALIDATION_DA),
                etape(3, DIRECTEUR_RESEAU, NomEtapeEnum.VALIDATION_DR),
                etape(4, AGENT, NomEtapeEnum.SOUMISSION_AGENT),
                etape(5, CHEF_UNITE, NomEtapeEnum.VALIDATION_DA));

        assertThat(CycleValidation.etapeDeLActeurDansLeCycleCourant(parcours, DIRECTEUR_RESEAU))
                .isEmpty();
        assertThat(CycleValidation.etapeDeLActeurDansLeCycleCourant(parcours, CHEF_UNITE))
                .as("le chef d'unite vient en revanche de valider le cycle courant")
                .isPresent();
    }

    @Test
    @DisplayName("10. Un acteur nul ne trouve aucune etape, il n'en cree pas une par defaut")
    void acteurNul() {
        List<EtapeWorkflow> parcours = List.of(
                etape(1, AGENT, NomEtapeEnum.SOUMISSION_AGENT));

        assertThat(CycleValidation.etapeDeLActeurDansLeCycleCourant(parcours, null)).isEmpty();
    }

    // --- Fabrique ------------------------------------------------------------------

    private static EtapeWorkflow etape(int ordre, Long idActeur, NomEtapeEnum nom) {
        return new EtapeWorkflow(ID_PROCESSUS, idActeur, ordre, nom);
    }

}
