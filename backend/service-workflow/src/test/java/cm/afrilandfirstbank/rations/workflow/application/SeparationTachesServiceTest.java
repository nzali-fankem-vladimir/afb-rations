package cm.afrilandfirstbank.rations.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cm.afrilandfirstbank.rations.workflow.domaine.EtapeWorkflow;
import cm.afrilandfirstbank.rations.workflow.domaine.NomEtapeEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.RoleEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.SeparationTachesException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.EtapeWorkflowRepository;

/** Separation des taches, RG-12 (CT-17). */
class SeparationTachesServiceTest {

    private static final Long ID_PROCESSUS = 740L;

    private static final ActeurSignataire AGENT =
            new ActeurSignataire(41L, "clarisse_mbarga", RoleEnum.AGENT_UNITE);
    private static final ActeurSignataire CHEF_UNITE =
            new ActeurSignataire(57L, "raymond_ndzana", RoleEnum.CHEF_UNITE_DA);
    private static final ActeurSignataire DIRECTEUR_RESEAU =
            new ActeurSignataire(12L, "estelle_fotso", RoleEnum.DIRECTEUR_RESEAU_DR);

    private EtapeWorkflowRepository etapeWorkflowRepository;
    private SeparationTachesService service;

    @BeforeEach
    void preparer() {
        etapeWorkflowRepository = mock(EtapeWorkflowRepository.class);
        service = new SeparationTachesService(etapeWorkflowRepository);
    }

    // --- 1. Le soumissionnaire ne valide pas ----------------------------------------

    @Test
    @DisplayName("1. CT-17 : l'agent qui a soumis ne peut pas valider son propre etat")
    void leSoumissionnaireNeValidePas() {
        parcours(etape(1, AGENT, NomEtapeEnum.SOUMISSION_AGENT));

        assertThatThrownBy(() -> service.exigerSeparationDesTaches(ID_PROCESSUS, AGENT))
                .isInstanceOf(SeparationTachesException.class)
                .hasMessageContaining("Vous avez soumis cet etat")
                .hasMessageContaining("RG-12");
    }

    // --- 2. Un valideur neuf passe ---------------------------------------------------

    @Test
    @DisplayName("2. Un chef d'unite qui n'a pas soumis valide sans obstacle")
    void leChefUniteNonSoumissionnairePasse() {
        parcours(etape(1, AGENT, NomEtapeEnum.SOUMISSION_AGENT));

        assertThatCode(() -> service.exigerSeparationDesTaches(ID_PROCESSUS, CHEF_UNITE))
                .doesNotThrowAnyException();

        assertThat(service.verifier(ID_PROCESSUS, CHEF_UNITE))
                .isInstanceOf(ResultatSeparationTaches.Autorise.class);
    }

    // --- 3. Pas de double visa de validation -----------------------------------------

    @Test
    @DisplayName("3. Celui qui a valide au premier niveau ne valide pas au second")
    void pasDeDoubleVisaDeValidation() {
        parcours(
                etape(1, AGENT, NomEtapeEnum.SOUMISSION_AGENT),
                etape(2, CHEF_UNITE, NomEtapeEnum.VALIDATION_DA));

        assertThatThrownBy(() -> service.exigerSeparationDesTaches(ID_PROCESSUS, CHEF_UNITE))
                .isInstanceOf(SeparationTachesException.class)
                .hasMessageContaining("deja valide cet etat au niveau du chef d'unite");
    }

    // --- 4. Le cumul de roles, tranche a l'etape 1 ------------------------------------

    @Test
    @DisplayName("4a. Cumul de roles : refus strict, meme si la personne a change de role")
    void cumulDeRolesRefuseStrictement() {
        // Meme identifiant local, role different : la personne a ete promue entre la
        // soumission et la validation. RG-12 la refuse quand meme — le deblocage est
        // organisationnel (suppleant), pas technique.
        ActeurSignataire memePersonnePromue =
                new ActeurSignataire(AGENT.id(), AGENT.login(), RoleEnum.CHEF_UNITE_DA);

        parcours(etape(1, AGENT, NomEtapeEnum.SOUMISSION_AGENT));

        assertThatThrownBy(
                () -> service.exigerSeparationDesTaches(ID_PROCESSUS, memePersonnePromue))
                .isInstanceOf(SeparationTachesException.class);
    }

    @Test
    @DisplayName("4b. Le retour clot le cycle : le seul DA de l'unite revalide la version corrigee")
    void leRetourNeBloquePasLeValideurAVie() {
        // Cycle 1 : soumission, puis retour du chef d'unite (une ligne etape_workflow
        // a son nom). Cycle 2 : l'agent a corrige et resoumis.
        parcours(
                etape(1, AGENT, NomEtapeEnum.SOUMISSION_AGENT),
                etape(2, CHEF_UNITE, NomEtapeEnum.VALIDATION_DA),
                etape(3, AGENT, NomEtapeEnum.SOUMISSION_AGENT));

        assertThatCode(() -> service.exigerSeparationDesTaches(ID_PROCESSUS, CHEF_UNITE))
                .as("sans le decoupage en cycles, l'unique DA de l'unite serait bloque a vie")
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> service.exigerSeparationDesTaches(ID_PROCESSUS, AGENT))
                .as("l'agent vient de resoumettre : il reste bloque, lui")
                .isInstanceOf(SeparationTachesException.class);
    }

    // --- 5. Un refus distinct du refus pour role insuffisant --------------------------

    @Test
    @DisplayName("5. Le refus nomme la separation des taches, pas un manque de droit")
    void refusDistinctDuRoleInsuffisant() {
        parcours(
                etape(1, AGENT, NomEtapeEnum.SOUMISSION_AGENT),
                etape(2, DIRECTEUR_RESEAU, NomEtapeEnum.VALIDATION_DA));

        ResultatSeparationTaches resultat = service.verifier(ID_PROCESSUS, DIRECTEUR_RESEAU);

        assertThat(resultat).isInstanceOf(ResultatSeparationTaches.Refuse.class);

        ResultatSeparationTaches.Refuse refus = (ResultatSeparationTaches.Refuse) resultat;
        assertThat(refus.etapeDejaRealisee()).isEqualTo(NomEtapeEnum.VALIDATION_DA);
        assertThat(refus.motif())
                .doesNotContain("habilitation")
                .doesNotContain("role ne permet pas")
                .contains("separation des taches");
    }

    // --- Bordures ---------------------------------------------------------------------

    @Test
    @DisplayName("6. Un dossier sans aucune etape ne refuse personne")
    void dossierVierge() {
        parcours();

        assertThatCode(() -> service.exigerSeparationDesTaches(ID_PROCESSUS, CHEF_UNITE))
                .doesNotThrowAnyException();
    }

    // --- Outillage ---------------------------------------------------------------------

    private void parcours(EtapeWorkflow... etapes) {
        when(etapeWorkflowRepository.findByIdProcessusOrderByOrdreEtape(anyLong()))
                .thenReturn(List.of(etapes));
    }

    private static EtapeWorkflow etape(int ordre, ActeurSignataire acteur, NomEtapeEnum nom) {
        return new EtapeWorkflow(ID_PROCESSUS, acteur.id(), ordre, nom);
    }

}
