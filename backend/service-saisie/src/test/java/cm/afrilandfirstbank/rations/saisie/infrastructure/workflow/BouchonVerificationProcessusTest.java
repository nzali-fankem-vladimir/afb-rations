package cm.afrilandfirstbank.rations.saisie.infrastructure.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import cm.afrilandfirstbank.rations.saisie.application.ResultatVerificationProcessus;
import cm.afrilandfirstbank.rations.saisie.application.ResultatVerificationProcessus.ProcessusVerifie;
import cm.afrilandfirstbank.rations.saisie.domaine.StatutProcessusEnum;

/**
 * Vérifie les deux propriétés qui rendent {@link BouchonVerificationProcessus}
 * tolérable : il fonctionne sous {@code dev}, il refuse de démarrer ailleurs
 * (garde-fou ajouté à la demande de l'utilisateur, Sprint 3.3 étape 3).
 *
 * <p>Un boot complet sous un profil non-{@code dev} échoue pour une raison
 * différente et non probante — {@code application-dev.yml} ne se charge pas du
 * tout, la datasource manque avant même que ce garde-fou s'exécute. Le
 * comportement du garde-fou lui-même s'isole donc au niveau unitaire, avec un
 * {@link MockEnvironment} qui simule les profils actifs sans démarrer de
 * contexte Spring.
 */
@DisplayName("BouchonVerificationProcessus — garde-fou de developpement")
class BouchonVerificationProcessusTest {

    private BouchonVerificationProcessus bouchon(MockEnvironment environnement) {
        return new BouchonVerificationProcessus(environnement, "00002", 8, 2026, "EN_COURS_SAISIE");
    }

    @Test
    @DisplayName("le profil dev seul, sans bouchon-workflow, ne suffit pas a activer ce bean "
            + "(verifie indirectement : le bean n'existe que sous @Profile(\"bouchon-workflow\"))")
    void demarreSousLeProfilDev() {
        MockEnvironment environnement = new MockEnvironment();
        environnement.setActiveProfiles("dev", BouchonVerificationProcessus.PROFIL);

        // Ne doit pas lever : dev est actif, le demarrage est tolere.
        bouchon(environnement).verifierQueLeProfilDeveloppementEstActif();
    }

    @Test
    @DisplayName("refuse de demarrer si le profil bouchon-workflow est actif sans dev "
            + "(recette, production) : demarrage du service bloque, pas un simple avertissement")
    void refuseDeDemarrerHorsDeveloppement() {
        MockEnvironment environnement = new MockEnvironment();
        environnement.setActiveProfiles(BouchonVerificationProcessus.PROFIL);

        assertThatThrownBy(() -> bouchon(environnement).verifierQueLeProfilDeveloppementEstActif())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(BouchonVerificationProcessus.PROFIL)
                .hasMessageContaining("demarrage refuse");
    }

    @Test
    @DisplayName("verifier(...) rend un processus tenu pour modifiable, tel que configure, "
            + "sans aucun appel reseau")
    void rendUnProcessusVerifieSansAppelReseau() {
        MockEnvironment environnement = new MockEnvironment();
        environnement.setActiveProfiles("dev", BouchonVerificationProcessus.PROFIL);

        ResultatVerificationProcessus resultat =
                bouchon(environnement).verifier(740L, "Bearer jeton-de-test");

        assertThat(resultat).isInstanceOf(ProcessusVerifie.class);
        ProcessusVerifie processus = (ProcessusVerifie) resultat;
        assertThat(processus.idProcessus()).isEqualTo(740L);
        assertThat(processus.statut()).isEqualTo(StatutProcessusEnum.EN_COURS_SAISIE);
        assertThat(processus.codeUnite()).isEqualTo("00002");
        assertThat(processus.estModifiable()).isTrue();
    }

}
