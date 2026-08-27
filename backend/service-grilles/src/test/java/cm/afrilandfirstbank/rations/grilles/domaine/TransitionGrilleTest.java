package cm.afrilandfirstbank.rations.grilles.domaine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import cm.afrilandfirstbank.rations.grilles.domaine.exception.MotifRejetRequisException;
import cm.afrilandfirstbank.rations.grilles.domaine.exception.TransitionGrilleInterditeException;

/**
 * Tests de la machine a etats du cycle de vie d'une grille (diagramme ET02).
 *
 * <p>Cinq transitions valides, quatre invalides. Chaque transition interdite doit
 * lever une erreur explicite, jamais retourner silencieusement.
 */
class TransitionGrilleTest {

    private static final LocalDateTime INSTANT = LocalDateTime.of(2026, 8, 27, 10, 0);
    private static final Long ID_DRH = 5L;   // agnes_tchinda, DRH (donnees de test service Identite)
    private static final Long ID_ARH = 4L;   // claire_nkolo, ARH
    private static final String LIBELLE_ARH = "NKOLO Claire"; // libelle fige a la creation (Sprint 2.2)

    /** Grille neuve : RATION / JOUR, 1500 FCFA, debut ce mois, creee par l'ARH. */
    private static GrilleTarifaire grilleNeuve() {
        return new GrilleTarifaire(NatureEnum.RATION, SessionEnum.JOUR, 1500,
                LocalDate.of(2026, 8, 1), ID_ARH, LIBELLE_ARH);
    }

    private static GrilleTarifaire grilleSoumise() {
        GrilleTarifaire g = grilleNeuve();
        TransitionGrille.soumettre(g);
        return g;
    }

    @Nested
    @DisplayName("Transitions valides")
    class Valides {

        @Test
        @DisplayName("1. creation -> BROUILLON")
        void creationVersBrouillon() {
            GrilleTarifaire g = grilleNeuve();

            assertThat(g.getStatutValidation()).isEqualTo(StatutGrilleEnum.BROUILLON);
            assertThat(TransitionGrille.estAutorisee(null, StatutGrilleEnum.BROUILLON)).isFalse();
        }

        @Test
        @DisplayName("2. BROUILLON -> EN_ATTENTE_DRH")
        void brouillonVersEnAttente() {
            GrilleTarifaire g = grilleNeuve();

            TransitionGrille.soumettre(g);

            assertThat(g.getStatutValidation()).isEqualTo(StatutGrilleEnum.EN_ATTENTE_DRH);
        }

        @Test
        @DisplayName("3. EN_ATTENTE_DRH -> ACTIVE")
        void enAttenteVersActive() {
            GrilleTarifaire g = grilleSoumise();

            TransitionGrille.valider(g, ID_DRH, INSTANT);

            assertThat(g.getStatutValidation()).isEqualTo(StatutGrilleEnum.ACTIVE);
            assertThat(g.getIdValidateur()).isEqualTo(ID_DRH);
            assertThat(g.getDateValidation()).isEqualTo(INSTANT);
            assertThat(g.getMotifRejet()).isNull();
        }

        @Test
        @DisplayName("4. EN_ATTENTE_DRH -> REJETEE avec motif")
        void enAttenteVersRejetee() {
            GrilleTarifaire g = grilleSoumise();

            TransitionGrille.rejeter(g, "Montant transport de nuit sous-evalue", INSTANT);

            assertThat(g.getStatutValidation()).isEqualTo(StatutGrilleEnum.REJETEE);
            assertThat(g.getMotifRejet()).isEqualTo("Montant transport de nuit sous-evalue");
            assertThat(g.getDateValidation()).isEqualTo(INSTANT);
        }

        @Test
        @DisplayName("5. ACTIVE -> fermee (date de fin posee, statut inchange)")
        void activeVersFermee() {
            GrilleTarifaire g = grilleSoumise();
            TransitionGrille.valider(g, ID_DRH, INSTANT);

            TransitionGrille.fermer(g, LocalDate.of(2026, 9, 30));

            assertThat(g.getDateFin()).isEqualTo(LocalDate.of(2026, 9, 30));
            assertThat(g.getStatutValidation())
                    .as("une grille fermee reste ACTIVE, seule sa date_fin change")
                    .isEqualTo(StatutGrilleEnum.ACTIVE);
        }
    }

    @Nested
    @DisplayName("Transitions invalides - erreur explicite")
    class Invalides {

        @Test
        @DisplayName("6. BROUILLON -> ACTIVE (saut de la validation)")
        void brouillonVersActiveInterdit() {
            GrilleTarifaire g = grilleNeuve();

            assertThat(TransitionGrille.estAutorisee(
                    StatutGrilleEnum.BROUILLON, StatutGrilleEnum.ACTIVE)).isFalse();
            assertThatThrownBy(() -> TransitionGrille.valider(g, ID_DRH, INSTANT))
                    .isInstanceOf(TransitionGrilleInterditeException.class)
                    .hasMessageContaining("BROUILLON")
                    .hasMessageContaining("ACTIVE");
        }

        @Test
        @DisplayName("7. REJETEE -> BROUILLON (une grille rejetee est conservee)")
        void rejeteeVersBrouillonInterdit() {
            GrilleTarifaire g = grilleSoumise();
            TransitionGrille.rejeter(g, "Grille incoherente", INSTANT);

            assertThat(TransitionGrille.estAutorisee(
                    StatutGrilleEnum.REJETEE, StatutGrilleEnum.BROUILLON)).isFalse();
            assertThatThrownBy(() -> TransitionGrille.soumettre(g))
                    .isInstanceOf(TransitionGrilleInterditeException.class)
                    .hasMessageContaining("REJETEE");
        }

        @Test
        @DisplayName("8. ACTIVE -> EN_ATTENTE_DRH")
        void activeVersEnAttenteInterdit() {
            GrilleTarifaire g = grilleSoumise();
            TransitionGrille.valider(g, ID_DRH, INSTANT);

            assertThat(TransitionGrille.estAutorisee(
                    StatutGrilleEnum.ACTIVE, StatutGrilleEnum.EN_ATTENTE_DRH)).isFalse();
            assertThatThrownBy(() -> TransitionGrille.soumettre(g))
                    .isInstanceOf(TransitionGrilleInterditeException.class)
                    .hasMessageContaining("ACTIVE");
        }

        @Test
        @DisplayName("9. rejet sans motif")
        void rejetSansMotifInterdit() {
            GrilleTarifaire g = grilleSoumise();

            assertThatThrownBy(() -> TransitionGrille.rejeter(g, "   ", INSTANT))
                    .isInstanceOf(MotifRejetRequisException.class)
                    .hasMessageContaining("motif");
            assertThatThrownBy(() -> TransitionGrille.rejeter(g, null, INSTANT))
                    .isInstanceOf(MotifRejetRequisException.class);
            assertThat(g.getStatutValidation())
                    .as("un rejet refuse ne change pas le statut")
                    .isEqualTo(StatutGrilleEnum.EN_ATTENTE_DRH);
        }
    }

}
