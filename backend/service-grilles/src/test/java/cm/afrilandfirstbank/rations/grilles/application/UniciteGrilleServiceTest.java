package cm.afrilandfirstbank.rations.grilles.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cm.afrilandfirstbank.rations.grilles.domaine.GrilleTarifaire;
import cm.afrilandfirstbank.rations.grilles.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.SessionEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.TransitionGrille;
import cm.afrilandfirstbank.rations.grilles.domaine.exception.ConflitGrilleException;
import cm.afrilandfirstbank.rations.grilles.infrastructure.GrilleTarifaireRepository;

/**
 * Controle d'unicite de RG-14 (Sprint 2.2).
 *
 * <p>Le repository est simule : ce qui est teste ici est la REGLE, pas la
 * requete. Les requetes elles-memes sont couvertes par
 * {@code GrilleTarifaireRepositoryTest}, contre une vraie base PostgreSQL.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Controle d'unicite des grilles (RG-14)")
class UniciteGrilleServiceTest {

    private static final Long ID_ARH = 4L;            // claire_nkolo
    private static final Long ID_DRH = 5L;            // agnes_tchinda
    private static final String LIBELLE_ARH = "NKOLO Claire";
    private static final LocalDate DEBUT_EN_VIGUEUR = LocalDate.of(2026, 8, 1);

    @Mock
    private GrilleTarifaireRepository grilleTarifaireRepository;

    @InjectMocks
    private UniciteGrilleService uniciteGrilleService;

    /** Grille en vigueur : ACTIVE, sans date de fin. */
    private static GrilleTarifaire grilleCourante(NatureEnum nature, SessionEnum session,
            int montant, LocalDate dateDebut) {
        GrilleTarifaire grille = new GrilleTarifaire(nature, session, montant, dateDebut, ID_ARH, LIBELLE_ARH);
        TransitionGrille.soumettre(grille);
        TransitionGrille.valider(grille, ID_DRH, LocalDateTime.of(2026, 7, 30, 10, 0));
        return grille;
    }

    /** Proposition en attente de la DRH. */
    private static GrilleTarifaire propositionEnAttente(NatureEnum nature, SessionEnum session,
            int montant, LocalDate dateDebut) {
        GrilleTarifaire grille = new GrilleTarifaire(nature, session, montant, dateDebut, ID_ARH, LIBELLE_ARH);
        TransitionGrille.soumettre(grille);
        return grille;
    }

    private void aucuneProposition(NatureEnum nature, SessionEnum session) {
        when(grilleTarifaireRepository.rechercherPropositionsEnAttente(nature, session))
                .thenReturn(List.of());
    }

    @Nested
    @DisplayName("Propositions acceptees")
    class Acceptees {

        @Test
        @DisplayName("couple sans aucune grille : accepte, quelle que soit la date")
        void coupleLibreAccepte() {
            aucuneProposition(NatureEnum.TRANSPORT, SessionEnum.SOIR);
            when(grilleTarifaireRepository.rechercherGrilleCourante(NatureEnum.TRANSPORT, SessionEnum.SOIR))
                    .thenReturn(Optional.empty());

            assertThatCode(() -> uniciteGrilleService.verifierAvantCreation(
                    NatureEnum.TRANSPORT, SessionEnum.SOIR, LocalDate.of(2026, 9, 1)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("grille en vigueur, proposition posterieure : acceptee (remplacement normal)")
        void propositionPosterieureAcceptee() {
            aucuneProposition(NatureEnum.RATION, SessionEnum.JOUR);
            when(grilleTarifaireRepository.rechercherGrilleCourante(NatureEnum.RATION, SessionEnum.JOUR))
                    .thenReturn(Optional.of(grilleCourante(NatureEnum.RATION, SessionEnum.JOUR,
                            1500, DEBUT_EN_VIGUEUR)));

            // C'est le cas d'usage central du versionnement decide au Sprint 2.2 :
            // sans lui, aucun tarif ne pourrait plus jamais changer.
            assertThatCode(() -> uniciteGrilleService.verifierAvantCreation(
                    NatureEnum.RATION, SessionEnum.JOUR, LocalDate.of(2026, 10, 1)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("le lendemain du debut en vigueur suffit : la borne est stricte, pas large")
        void lendemainAccepte() {
            aucuneProposition(NatureEnum.RATION, SessionEnum.JOUR);
            when(grilleTarifaireRepository.rechercherGrilleCourante(NatureEnum.RATION, SessionEnum.JOUR))
                    .thenReturn(Optional.of(grilleCourante(NatureEnum.RATION, SessionEnum.JOUR,
                            1500, DEBUT_EN_VIGUEUR)));

            assertThatCode(() -> uniciteGrilleService.verifierAvantCreation(
                    NatureEnum.RATION, SessionEnum.JOUR, DEBUT_EN_VIGUEUR.plusDays(1)))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Propositions refusees")
    class Refusees {

        @Test
        @DisplayName("une proposition attend deja la DRH sur ce couple : refus")
        void propositionConcurrenteRefusee() {
            when(grilleTarifaireRepository.rechercherPropositionsEnAttente(
                    NatureEnum.TRANSPORT, SessionEnum.SOIR))
                    .thenReturn(List.of(propositionEnAttente(NatureEnum.TRANSPORT, SessionEnum.SOIR,
                            3000, LocalDate.of(2026, 9, 1))));

            assertThatThrownBy(() -> uniciteGrilleService.verifierAvantCreation(
                    NatureEnum.TRANSPORT, SessionEnum.SOIR, LocalDate.of(2026, 10, 1)))
                    .isInstanceOf(ConflitGrilleException.class)
                    .extracting(erreur -> ((ConflitGrilleException) erreur).getCode())
                    .isEqualTo(ConflitGrilleException.CODE_PROPOSITION_EN_ATTENTE);
        }

        @Test
        @DisplayName("proposition a la meme date que la grille en vigueur : refus")
        void memeDateRefusee() {
            aucuneProposition(NatureEnum.RATION, SessionEnum.JOUR);
            when(grilleTarifaireRepository.rechercherGrilleCourante(NatureEnum.RATION, SessionEnum.JOUR))
                    .thenReturn(Optional.of(grilleCourante(NatureEnum.RATION, SessionEnum.JOUR,
                            1500, DEBUT_EN_VIGUEUR)));

            // Fermer l'ancienne a la veille donnerait une fin anterieure a son
            // propre debut : un intervalle vide.
            assertThatThrownBy(() -> uniciteGrilleService.verifierAvantCreation(
                    NatureEnum.RATION, SessionEnum.JOUR, DEBUT_EN_VIGUEUR))
                    .isInstanceOf(ConflitGrilleException.class)
                    .extracting(erreur -> ((ConflitGrilleException) erreur).getCode())
                    .isEqualTo(ConflitGrilleException.CODE_GRILLE_ACTIVE);
        }

        @Test
        @DisplayName("proposition anti-datee avant la grille en vigueur : refus")
        void antiDatageRefuse() {
            aucuneProposition(NatureEnum.RATION, SessionEnum.SOIR);
            when(grilleTarifaireRepository.rechercherGrilleCourante(NatureEnum.RATION, SessionEnum.SOIR))
                    .thenReturn(Optional.of(grilleCourante(NatureEnum.RATION, SessionEnum.SOIR,
                            2000, DEBUT_EN_VIGUEUR)));

            assertThatThrownBy(() -> uniciteGrilleService.verifierAvantCreation(
                    NatureEnum.RATION, SessionEnum.SOIR, LocalDate.of(2026, 7, 1)))
                    .isInstanceOf(ConflitGrilleException.class)
                    .extracting(erreur -> ((ConflitGrilleException) erreur).getCode())
                    .isEqualTo(ConflitGrilleException.CODE_GRILLE_ACTIVE);
        }

        @Test
        @DisplayName("le message de refus nomme la nature et la session concernees")
        void messageNommeLeCouple() {
            aucuneProposition(NatureEnum.TRANSPORT, SessionEnum.JOUR);
            when(grilleTarifaireRepository.rechercherGrilleCourante(NatureEnum.TRANSPORT, SessionEnum.JOUR))
                    .thenReturn(Optional.of(grilleCourante(NatureEnum.TRANSPORT, SessionEnum.JOUR,
                            1000, DEBUT_EN_VIGUEUR)));

            assertThatThrownBy(() -> uniciteGrilleService.verifierAvantCreation(
                    NatureEnum.TRANSPORT, SessionEnum.JOUR, DEBUT_EN_VIGUEUR))
                    .hasMessageContaining("TRANSPORT")
                    .hasMessageContaining("JOUR")
                    .hasMessageContaining("01/08/2026");
        }
    }

    @Test
    @DisplayName("la proposition concurrente est signalee AVANT le probleme de date")
    void propositionConcurrentePrioritaire() {
        // Les deux conflits sont reunis. L'ARH doit lire « attendez la DRH », pas
        // « corrigez votre date » : sur une proposition concurrente, il n'a rien a
        // corriger.
        when(grilleTarifaireRepository.rechercherPropositionsEnAttente(NatureEnum.RATION, SessionEnum.JOUR))
                .thenReturn(List.of(propositionEnAttente(NatureEnum.RATION, SessionEnum.JOUR,
                        1800, LocalDate.of(2026, 10, 1))));

        assertThatThrownBy(() -> uniciteGrilleService.verifierAvantCreation(
                NatureEnum.RATION, SessionEnum.JOUR, DEBUT_EN_VIGUEUR))
                .isInstanceOf(ConflitGrilleException.class)
                .extracting(erreur -> ((ConflitGrilleException) erreur).getCode())
                .isEqualTo(ConflitGrilleException.CODE_PROPOSITION_EN_ATTENTE);

        // La seconde requete n'est meme pas emise : on refuse tot (document
        // maitre, section 7.3, point 3).
        verify(grilleTarifaireRepository, never()).rechercherGrilleCourante(any(), any());
    }

    @Test
    @DisplayName("l'exception porte le code du contrat d'API pour le conflit de grille active")
    void codeConformeAuContrat() {
        assertThat(ConflitGrilleException.CODE_GRILLE_ACTIVE).isEqualTo("GRILLE_ACTIVE_EXISTANTE");
    }

}
