package cm.afrilandfirstbank.rations.grilles.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import cm.afrilandfirstbank.rations.grilles.domaine.GrilleTarifaire;
import cm.afrilandfirstbank.rations.grilles.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.ResolutionMontant;
import cm.afrilandfirstbank.rations.grilles.domaine.SessionEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.exception.IncoherenceGrilleException;
import cm.afrilandfirstbank.rations.grilles.infrastructure.GrilleTarifaireRepository;

/**
 * Resolution du montant applicable (Sprint 2.4, RG-03, US-05, CT-10, CT-29).
 *
 * <p>Le test le plus important du sous-sprint est
 * {@link ADateDeLaPrestation#laDateInterrogeeEstCelleDeLaPrestationPasCelleDuJour()} :
 * il est le seul qui distingue une implementation correcte d'une implementation
 * qui resout a {@code LocalDate.now()}. Toutes les autres verifications passent
 * avec les deux, tant que la date demandee est la journee courante.
 *
 * <p>Les bornes de dates elles-memes (inclusives des deux cotes) sont verifiees
 * la ou elles s'executent reellement, dans {@code GrilleTarifaireRepositoryTest},
 * contre PostgreSQL. Ici le repository est simule : ce qui est teste est
 * l'arbitrage entre zero, une et plusieurs grilles, et la fidelite des
 * parametres transmis.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Resolution du montant applicable (RG-03)")
class ResolutionMontantServiceTest {

    /** Date de prestation volontairement passee : jamais la date d'execution du test. */
    private static final LocalDate LE_10_JUILLET = LocalDate.of(2026, 7, 10);

    @Mock
    private GrilleTarifaireRepository grilleTarifaireRepository;

    @InjectMocks
    private ResolutionMontantService resolutionMontantService;

    // --- Fabriques ----------------------------------------------------------

    private static GrilleTarifaire grille(Long id, int montant, LocalDate dateDebut, LocalDate dateFin) {
        GrilleTarifaire g = new GrilleTarifaire(NatureEnum.RATION, SessionEnum.JOUR,
                montant, dateDebut, 4L, "NKOLO Claire");
        ReflectionTestUtils.setField(g, "id", id);
        ReflectionTestUtils.setField(g, "dateFin", dateFin);
        return g;
    }

    @Nested
    @DisplayName("Une grille couvre la date")
    class GrilleTrouvee {

        @Test
        @DisplayName("le montant retourne est celui de la grille, avec l'identite de la grille qui l'a fourni")
        void montantEtOrigine() {
            GrilleTarifaire deJuillet = grille(12L, 1500, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
            when(grilleTarifaireRepository.rechercherGrillesCouvrant(
                    NatureEnum.RATION, SessionEnum.JOUR, LE_10_JUILLET))
                    .thenReturn(List.of(deJuillet));

            ResolutionMontant resolution = resolutionMontantService.resoudre(
                    NatureEnum.RATION, SessionEnum.JOUR, LE_10_JUILLET);

            assertThat(resolution.disponible()).isTrue();
            assertThat(resolution.montantFcfa()).isEqualTo(1500);

            // L'origine du montant, sans laquelle l'appelant ne pourrait pas
            // justifier ce qu'il fige dans la ligne de prestation.
            assertThat(resolution.idGrille()).isEqualTo(12L);
            assertThat(resolution.dateDebut()).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(resolution.dateFin()).isEqualTo(LocalDate.of(2026, 7, 31));

            // La question posee est rappelee dans la reponse.
            assertThat(resolution.nature()).isEqualTo(NatureEnum.RATION);
            assertThat(resolution.session()).isEqualTo(SessionEnum.JOUR);
            assertThat(resolution.date()).isEqualTo(LE_10_JUILLET);
        }

        @Test
        @DisplayName("une grille courante, sans terme pose, a une dateFin nulle et reste applicable")
        void grilleSansTerme() {
            when(grilleTarifaireRepository.rechercherGrillesCouvrant(any(), any(), any()))
                    .thenReturn(List.of(grille(30L, 2000, LocalDate.of(2026, 1, 1), null)));

            ResolutionMontant resolution = resolutionMontantService.resoudre(
                    NatureEnum.RATION, SessionEnum.JOUR, LE_10_JUILLET);

            assertThat(resolution.disponible()).isTrue();
            assertThat(resolution.montantFcfa()).isEqualTo(2000);
            assertThat(resolution.dateFin()).isNull();
        }
    }

    @Nested
    @DisplayName("Aucune grille ne couvre la date")
    class Indisponibilite {

        /**
         * Le point de vigilance central du sous-sprint. Retourner zero
         * enregistrerait des prestations a montant nul qui partiraient en
         * comptabilite sans que personne ne s'en apercoive : l'indisponibilite
         * doit rester distincte d'un montant.
         */
        @Test
        @DisplayName("le montant est null, jamais zero, et le drapeau disponible est faux")
        void indisponibiliteExpliciteJamaisZero() {
            when(grilleTarifaireRepository.rechercherGrillesCouvrant(any(), any(), any()))
                    .thenReturn(List.of());

            ResolutionMontant resolution = resolutionMontantService.resoudre(
                    NatureEnum.RATION, SessionEnum.JOUR, LE_10_JUILLET);

            assertThat(resolution.disponible()).isFalse();
            assertThat(resolution.montantFcfa())
                    .as("un montant nul serait enregistre comme un tarif ; l'absence doit rester une absence")
                    .isNull();
            assertThat(resolution.idGrille()).isNull();
            assertThat(resolution.dateDebut()).isNull();
            assertThat(resolution.dateFin()).isNull();
        }

        @Test
        @DisplayName("l'indisponibilite rappelle la question posee, pour que l'appelant puisse formuler son refus")
        void indisponibiliteRappelleLaQuestion() {
            when(grilleTarifaireRepository.rechercherGrillesCouvrant(any(), any(), any()))
                    .thenReturn(List.of());

            ResolutionMontant resolution = resolutionMontantService.resoudre(
                    NatureEnum.TRANSPORT, SessionEnum.SOIR, LocalDate.of(2020, 1, 1));

            assertThat(resolution.nature()).isEqualTo(NatureEnum.TRANSPORT);
            assertThat(resolution.session()).isEqualTo(SessionEnum.SOIR);
            assertThat(resolution.date()).isEqualTo(LocalDate.of(2020, 1, 1));
        }

        /**
         * Une date anterieure a tout l'historique et une periode fermee sans
         * remplacante donnent la meme reponse : le service ne les distingue pas,
         * et l'agent n'agirait pas differemment — dans les deux cas, aucune
         * prestation n'est tarifable ce jour-la.
         */
        @Test
        @DisplayName("date anterieure a tout l'historique : meme reponse qu'une periode fermee sans remplacante")
        void memeReponsePourLesDeuxCasDAbsence() {
            when(grilleTarifaireRepository.rechercherGrillesCouvrant(any(), any(), any()))
                    .thenReturn(List.of());

            ResolutionMontant avantHistorique = resolutionMontantService.resoudre(
                    NatureEnum.RATION, SessionEnum.JOUR, LocalDate.of(2000, 1, 1));
            ResolutionMontant apresFermeture = resolutionMontantService.resoudre(
                    NatureEnum.RATION, SessionEnum.JOUR, LocalDate.of(2030, 1, 1));

            assertThat(avantHistorique.disponible()).isFalse();
            assertThat(apresFermeture.disponible()).isFalse();
            assertThat(avantHistorique.montantFcfa()).isNull();
            assertThat(apresFermeture.montantFcfa()).isNull();
        }
    }

    @Nested
    @DisplayName("A la date de la prestation, pas a la date du jour")
    class ADateDeLaPrestation {

        /**
         * <b>Le test qui compte.</b> Il echoue si la resolution interroge
         * {@code LocalDate.now()} au lieu de la date recue — defaut invisible
         * partout ailleurs, puisque les deux dates coincident des qu'on saisit la
         * journee courante.
         */
        @Test
        @DisplayName("la date interrogee est celle de la prestation, pas celle du jour")
        void laDateInterrogeeEstCelleDeLaPrestationPasCelleDuJour() {
            when(grilleTarifaireRepository.rechercherGrillesCouvrant(any(), any(), any()))
                    .thenReturn(List.of(grille(12L, 1500, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))));

            resolutionMontantService.resoudre(NatureEnum.RATION, SessionEnum.JOUR, LE_10_JUILLET);

            ArgumentCaptor<LocalDate> dateInterrogee = ArgumentCaptor.forClass(LocalDate.class);
            org.mockito.Mockito.verify(grilleTarifaireRepository).rechercherGrillesCouvrant(
                    eq(NatureEnum.RATION), eq(SessionEnum.JOUR), dateInterrogee.capture());

            assertThat(dateInterrogee.getValue())
                    .as("resoudre a la date du jour tarifierait une saisie retroactive au mauvais montant")
                    .isEqualTo(LE_10_JUILLET)
                    .isNotEqualTo(LocalDate.now());
        }

        /**
         * Complement du precedent, cote resultat : une date passee doit rendre la
         * grille de l'epoque, avec son montant, et non celle en vigueur
         * aujourd'hui (CT-29 lu a l'envers — apres une bascule, le passe ne bouge
         * pas).
         */
        @Test
        @DisplayName("une date passee retourne la grille de l'epoque, pas la grille courante")
        void datePasseeRetourneLaGrilleDeLEpoque() {
            GrilleTarifaire deJuillet = grille(12L, 1500, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
            when(grilleTarifaireRepository.rechercherGrillesCouvrant(
                    NatureEnum.RATION, SessionEnum.JOUR, LE_10_JUILLET))
                    .thenReturn(List.of(deJuillet));

            ResolutionMontant resolution = resolutionMontantService.resoudre(
                    NatureEnum.RATION, SessionEnum.JOUR, LE_10_JUILLET);

            assertThat(resolution.idGrille()).isEqualTo(12L);
            assertThat(resolution.montantFcfa()).isEqualTo(1500);
            assertThat(resolution.dateFin())
                    .as("la grille retenue est close : c'est bien une grille du passe, pas la courante")
                    .isEqualTo(LocalDate.of(2026, 7, 31));
        }
    }

    @Nested
    @DisplayName("Deux grilles couvrent la meme date")
    class Incoherence {

        /**
         * Cas qui ne doit pas se produire — index partiel et bascule atomique du
         * Sprint 2.3 l'ecartent a l'ecriture. S'il survenait malgre tout (reprise
         * de donnees, intervention en base), arbitrer servirait un montant
         * potentiellement faux que personne ne verrait passer.
         */
        @Test
        @DisplayName("le service refuse plutot que d'arbitrer, et nomme les grilles en conflit")
        void refusExpliciteSansArbitrage() {
            when(grilleTarifaireRepository.rechercherGrillesCouvrant(any(), any(), any()))
                    .thenReturn(List.of(
                            grille(30L, 2000, LocalDate.of(2026, 7, 1), null),
                            grille(12L, 1500, LocalDate.of(2026, 1, 1), null)));

            assertThatThrownBy(() -> resolutionMontantService.resoudre(
                    NatureEnum.RATION, SessionEnum.JOUR, LE_10_JUILLET))
                    .isInstanceOf(IncoherenceGrilleException.class)
                    .hasMessageContaining("#30")
                    .hasMessageContaining("#12")
                    .hasMessageContaining("RATION")
                    .hasMessageContaining("2026-07-10");
        }

        @Test
        @DisplayName("aucun montant n'est retourne : le refus prime sur une valeur plausible")
        void aucunMontantRetourne() {
            when(grilleTarifaireRepository.rechercherGrillesCouvrant(any(), any(), any()))
                    .thenReturn(List.of(
                            grille(30L, 2000, LocalDate.of(2026, 7, 1), null),
                            grille(12L, 1500, LocalDate.of(2026, 1, 1), null)));

            assertThatThrownBy(() -> resolutionMontantService.resoudre(
                    NatureEnum.RATION, SessionEnum.JOUR, LE_10_JUILLET))
                    .isInstanceOf(IncoherenceGrilleException.class);
        }
    }

}
