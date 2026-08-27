package cm.afrilandfirstbank.rations.grilles.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.grilles.application.DecisionGrilleService.ResultatValidation;
import cm.afrilandfirstbank.rations.grilles.domaine.GrilleTarifaire;
import cm.afrilandfirstbank.rations.grilles.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.SessionEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.StatutGrilleEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.TransitionGrille;
import cm.afrilandfirstbank.rations.grilles.domaine.exception.GrilleIntrouvableException;
import cm.afrilandfirstbank.rations.grilles.domaine.exception.MotifRejetRequisException;
import cm.afrilandfirstbank.rations.grilles.domaine.exception.TransitionGrilleInterditeException;
import cm.afrilandfirstbank.rations.grilles.infrastructure.GrilleTarifaireRepository;
import cm.afrilandfirstbank.rations.grilles.infrastructure.identite.AuteurIdentifie;
import cm.afrilandfirstbank.rations.grilles.infrastructure.identite.ClientIdentite;

/**
 * Decision de la Directrice RH : validation avec bascule, et rejet motive
 * (Sprint 2.3, US-14, RG-14, CT-27 et CT-28).
 *
 * <p>Le test central du sous-sprint est
 * {@link Validation#echecPendantLaBasculeNeLaisseRienDeModifie()} : il prouve que
 * la fermeture de l'ancienne grille et l'activation de la nouvelle constituent
 * une seule operation. Une bascule a moitie faite ne se verrait pas ici, elle se
 * verrait au Sprint 3, sous la forme d'une saisie qui ne trouve plus de tarif.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Decision de la DRH sur une grille (US-14)")
class DecisionGrilleServiceTest {

    private static final String JETON = "Bearer jeton-de-test";
    private static final String IP = "10.12.4.31";
    private static final AuteurIdentifie AGNES_TCHINDA =
            new AuteurIdentifie(7L, "agnes_tchinda", "TCHINDA", "Agnes");

    private static final LocalDate DEBUT_ANCIENNE = LocalDate.of(2026, 1, 1);
    private static final LocalDate DEBUT_NOUVELLE = LocalDate.of(2026, 9, 1);
    /** Veille du 1er septembre : la borne attendue sur l'ancienne grille. */
    private static final LocalDate VEILLE = LocalDate.of(2026, 8, 31);

    @Mock
    private GrilleTarifaireRepository grilleTarifaireRepository;

    @Mock
    private ClientIdentite clientIdentite;

    @Mock
    private PublicateurAudit publicateurAudit;

    @InjectMocks
    private DecisionGrilleService decisionGrilleService;

    // --- Fabriques ----------------------------------------------------------

    private static GrilleTarifaire grille(Long id, int montant, LocalDate dateDebut) {
        GrilleTarifaire g = new GrilleTarifaire(NatureEnum.TRANSPORT, SessionEnum.SOIR,
                montant, dateDebut, 4L, "NKOLO Claire");
        ReflectionTestUtils.setField(g, "id", id);
        return g;
    }

    /** Grille soumise, en attente de la DRH : le seul etat validable ou rejetable. */
    private static GrilleTarifaire enAttente(Long id, int montant, LocalDate dateDebut) {
        GrilleTarifaire g = grille(id, montant, dateDebut);
        TransitionGrille.soumettre(g);
        return g;
    }

    /** Grille en vigueur : ACTIVE, sans date de fin. */
    private static GrilleTarifaire enVigueur(Long id, int montant, LocalDate dateDebut) {
        GrilleTarifaire g = enAttente(id, montant, dateDebut);
        TransitionGrille.valider(g, 7L, java.time.LocalDateTime.now(), "TCHINDA Agnes");
        return g;
    }

    private static GrilleTarifaire dejaRejetee(Long id) {
        GrilleTarifaire g = enAttente(id, 3000, DEBUT_NOUVELLE);
        TransitionGrille.rejeter(g, "Bareme non conforme", java.time.LocalDateTime.now());
        return g;
    }

    private void drhIdentifiee() {
        when(clientIdentite.resoudreAuteur(anyString())).thenReturn(AGNES_TCHINDA);
    }

    private void enregistrementRendLaGrille() {
        when(grilleTarifaireRepository.save(any(GrilleTarifaire.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void grilleChargee(GrilleTarifaire g) {
        when(grilleTarifaireRepository.findById(g.getId())).thenReturn(Optional.of(g));
    }

    private List<EvenementAudit> evenementsPublies() {
        ArgumentCaptor<EvenementAudit> capteur = ArgumentCaptor.forClass(EvenementAudit.class);
        verify(publicateurAudit, times(evenementsAttendus())).publier(capteur.capture());
        return capteur.getAllValues();
    }

    private int evenementsAttendus() {
        // Nombre reel d'appels : le capteur sert a inspecter, pas a compter.
        return org.mockito.Mockito.mockingDetails(publicateurAudit).getInvocations().size();
    }

    // ========================================================================
    // VALIDATION
    // ========================================================================

    @Nested
    @DisplayName("Validation par la DRH")
    class Validation {

        @Test
        @DisplayName("1. validation avec une ancienne grille active : la nouvelle est ACTIVE, l'ancienne est fermee a la veille")
        void basculeNominale() {
            drhIdentifiee();
            GrilleTarifaire cible = enAttente(29L, 3000, DEBUT_NOUVELLE);
            GrilleTarifaire ancienne = enVigueur(12L, 2500, DEBUT_ANCIENNE);
            grilleChargee(cible);
            when(grilleTarifaireRepository.rechercherGrilleCourante(NatureEnum.TRANSPORT, SessionEnum.SOIR))
                    .thenReturn(Optional.of(ancienne));
            enregistrementRendLaGrille();

            ResultatValidation resultat = decisionGrilleService.valider(29L, JETON, IP);

            // CT-27 : la nouvelle est applicable, l'ancienne porte une borne.
            assertThat(resultat.validee().getStatutValidation()).isEqualTo(StatutGrilleEnum.ACTIVE);
            assertThat(resultat.validee().getDateFin()).isNull();
            assertThat(resultat.validee().getIdValidateur()).isEqualTo(7L);
            assertThat(resultat.validee().getLibelleValidateur()).isEqualTo("TCHINDA Agnes");
            assertThat(resultat.validee().getDateValidation()).isNotNull();

            assertThat(resultat.ancienneFermee()).isSameAs(ancienne);
            // La veille du 1er septembre, pas la date de la decision : c'est
            // l'arbitrage du sous-sprint, et ce qui garantit l'absence de trou.
            assertThat(ancienne.getDateFin()).isEqualTo(VEILLE);
            // Fermer n'est pas rejeter : le statut de l'ancienne ne change pas.
            assertThat(ancienne.getStatutValidation()).isEqualTo(StatutGrilleEnum.ACTIVE);
        }

        @Test
        @DisplayName("1bis. l'ancienne est ecrite AVANT la nouvelle, pour ne jamais exposer deux grilles courantes")
        void fermetureEcriteAvantActivation() {
            drhIdentifiee();
            GrilleTarifaire cible = enAttente(29L, 3000, DEBUT_NOUVELLE);
            GrilleTarifaire ancienne = enVigueur(12L, 2500, DEBUT_ANCIENNE);
            grilleChargee(cible);
            when(grilleTarifaireRepository.rechercherGrilleCourante(any(), any()))
                    .thenReturn(Optional.of(ancienne));
            enregistrementRendLaGrille();

            decisionGrilleService.valider(29L, JETON, IP);

            // L'index partiel est evalue instruction par instruction par
            // PostgreSQL : si l'activation partait en premier, il existerait
            // l'espace d'un ordre SQL deux lignes ACTIVE sans date de fin, et la
            // base refuserait une bascule pourtant legitime.
            org.mockito.InOrder ordre = org.mockito.Mockito.inOrder(grilleTarifaireRepository);
            ordre.verify(grilleTarifaireRepository).saveAndFlush(ancienne);
            ordre.verify(grilleTarifaireRepository).save(cible);
        }

        @Test
        @DisplayName("2. premiere grille du couple, sans ancienne a fermer : validation sans erreur")
        void premiereGrilleDuCouple() {
            drhIdentifiee();
            GrilleTarifaire cible = enAttente(29L, 3000, DEBUT_NOUVELLE);
            grilleChargee(cible);
            when(grilleTarifaireRepository.rechercherGrilleCourante(any(), any()))
                    .thenReturn(Optional.empty());
            enregistrementRendLaGrille();

            ResultatValidation resultat = decisionGrilleService.valider(29L, JETON, IP);

            assertThat(resultat.validee().getStatutValidation()).isEqualTo(StatutGrilleEnum.ACTIVE);
            // Rien n'a ete ferme, et ce n'est pas une anomalie : c'est le cas du
            // demarrage, ou le couple n'a encore aucun tarif.
            assertThat(resultat.ancienneFermee()).isNull();
            verify(grilleTarifaireRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("3. validation d'une grille BROUILLON : refusee")
        void brouillonNonValidable() {
            drhIdentifiee();
            GrilleTarifaire brouillon = grille(29L, 3000, DEBUT_NOUVELLE);
            grilleChargee(brouillon);

            assertThatThrownBy(() -> decisionGrilleService.valider(29L, JETON, IP))
                    .isInstanceOf(TransitionGrilleInterditeException.class)
                    .hasMessageContaining("BROUILLON");

            // Le refus tombe avant toute ecriture : on ne cherche meme pas
            // l'ancienne grille.
            verify(grilleTarifaireRepository, never()).rechercherGrilleCourante(any(), any());
            verify(grilleTarifaireRepository, never()).save(any());
        }

        @Test
        @DisplayName("4. validation d'une grille deja ACTIVE : refusee")
        void grilleDejaActiveNonValidable() {
            drhIdentifiee();
            GrilleTarifaire active = enVigueur(29L, 3000, DEBUT_NOUVELLE);
            grilleChargee(active);

            assertThatThrownBy(() -> decisionGrilleService.valider(29L, JETON, IP))
                    .isInstanceOf(TransitionGrilleInterditeException.class)
                    .hasMessageContaining("ACTIVE");

            verify(grilleTarifaireRepository, never()).save(any());
        }

        @Test
        @DisplayName("5. validation d'une grille REJETEE : refusee")
        void grilleRejeteeNonValidable() {
            drhIdentifiee();
            grilleChargee(dejaRejetee(29L));

            // Une grille rejetee est conservee pour l'historique ; la ressusciter
            // contournerait la correction que l'ARH doit apporter.
            assertThatThrownBy(() -> decisionGrilleService.valider(29L, JETON, IP))
                    .isInstanceOf(TransitionGrilleInterditeException.class)
                    .hasMessageContaining("REJETEE");

            verify(grilleTarifaireRepository, never()).save(any());
        }

        @Test
        @DisplayName("6. echec pendant la bascule : aucune des deux lignes n'est modifiee")
        void echecPendantLaBasculeNeLaisseRienDeModifie() {
            drhIdentifiee();
            GrilleTarifaire cible = enAttente(29L, 3000, DEBUT_NOUVELLE);
            GrilleTarifaire ancienne = enVigueur(12L, 2500, DEBUT_ANCIENNE);
            grilleChargee(cible);
            when(grilleTarifaireRepository.rechercherGrilleCourante(any(), any()))
                    .thenReturn(Optional.of(ancienne));

            // La fermeture de l'ancienne echoue au vidage : violation d'index,
            // panne reseau, verrou. Peu importe la cause — ce qui compte est ce
            // que le systeme laisse derriere lui.
            when(grilleTarifaireRepository.saveAndFlush(ancienne))
                    .thenThrow(new DataIntegrityViolationException("ux_grille_active_par_couple"));

            assertThatThrownBy(() -> decisionGrilleService.valider(29L, JETON, IP))
                    .isInstanceOf(DataIntegrityViolationException.class);

            // L'exception traverse la methode @Transactional : Spring marque la
            // transaction pour rollback, rien n'est commite. Ce que le test peut
            // observer sans base, c'est que la seconde ecriture n'a meme pas ete
            // tentee et que l'etat en memoire de la cible est intact.
            verify(grilleTarifaireRepository, never()).save(any());
            assertThat(cible.getStatutValidation()).isEqualTo(StatutGrilleEnum.EN_ATTENTE_DRH);
            assertThat(cible.getIdValidateur()).isNull();
            assertThat(cible.getDateValidation()).isNull();

            // Aucun evenement d'audit : une bascule qui n'a pas eu lieu ne doit
            // pas laisser dans le journal la trace d'une decision.
            verify(publicateurAudit, never()).publier(any());
        }

        @Test
        @DisplayName("6bis. echec pendant l'activation : la fermeture de l'ancienne remonte, la transaction est annulee")
        void echecPendantActivation() {
            drhIdentifiee();
            GrilleTarifaire cible = enAttente(29L, 3000, DEBUT_NOUVELLE);
            GrilleTarifaire ancienne = enVigueur(12L, 2500, DEBUT_ANCIENNE);
            grilleChargee(cible);
            when(grilleTarifaireRepository.rechercherGrilleCourante(any(), any()))
                    .thenReturn(Optional.of(ancienne));
            when(grilleTarifaireRepository.save(cible))
                    .thenThrow(new DataIntegrityViolationException("panne a l'activation"));

            assertThatThrownBy(() -> decisionGrilleService.valider(29L, JETON, IP))
                    .isInstanceOf(DataIntegrityViolationException.class);

            // C'est le cas dangereux : l'ancienne a bien ete fermee EN MEMOIRE, et
            // seul le rollback de la transaction empeche cette fermeture
            // d'atteindre la base. Sans transaction, le couple TRANSPORT / SOIR se
            // retrouverait sans aucun tarif applicable.
            assertThat(ancienne.getDateFin()).isEqualTo(VEILLE);
            verify(publicateurAudit, never()).publier(any());
        }

        @Test
        @DisplayName("identifiant inexistant : GrilleIntrouvableException, traduite en 404")
        void grilleInexistante() {
            drhIdentifiee();
            when(grilleTarifaireRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> decisionGrilleService.valider(999L, JETON, IP))
                    .isInstanceOf(GrilleIntrouvableException.class)
                    .hasMessageContaining("999");
        }

        @Test
        @DisplayName("la decision est tracee : VALIDATION_GRILLE et FERMETURE_GRILLE")
        void decisionTracee() {
            drhIdentifiee();
            GrilleTarifaire cible = enAttente(29L, 3000, DEBUT_NOUVELLE);
            GrilleTarifaire ancienne = enVigueur(12L, 2500, DEBUT_ANCIENNE);
            grilleChargee(cible);
            when(grilleTarifaireRepository.rechercherGrilleCourante(any(), any()))
                    .thenReturn(Optional.of(ancienne));
            enregistrementRendLaGrille();

            decisionGrilleService.valider(29L, JETON, IP);

            List<EvenementAudit> evenements = evenementsPublies();
            assertThat(evenements).extracting(EvenementAudit::action)
                    .containsExactly("VALIDATION_GRILLE", "FERMETURE_GRILLE");
            assertThat(evenements).allSatisfy(evenement -> {
                assertThat(evenement.idUtilisateur()).isEqualTo(7L);
                assertThat(evenement.entiteCible()).isEqualTo("grille_tarifaire");
                assertThat(evenement.adresseIp()).isEqualTo(IP);
            });
            // Le journal doit permettre de comprendre le changement de montant
            // sans rapprocher deux entrees.
            assertThat(evenements.get(0).detailJson()).contains("\"grilleRemplacee\"", "12");
            assertThat(evenements.get(1).detailJson()).contains("2500", "3000");
        }
    }

    // ========================================================================
    // REJET
    // ========================================================================

    @Nested
    @DisplayName("Rejet par la DRH")
    class Rejet {

        @Test
        @DisplayName("7. rejet avec motif : statut REJETEE, motif enregistre, decideur trace")
        void rejetNominal() {
            drhIdentifiee();
            GrilleTarifaire cible = enAttente(29L, 3000, DEBUT_NOUVELLE);
            grilleChargee(cible);
            enregistrementRendLaGrille();

            GrilleTarifaire rejetee = decisionGrilleService.rejeter(
                    29L, "Montant superieur au bareme en vigueur", JETON, IP);

            assertThat(rejetee.getStatutValidation()).isEqualTo(StatutGrilleEnum.REJETEE);
            assertThat(rejetee.getMotifRejet()).isEqualTo("Montant superieur au bareme en vigueur");
            assertThat(rejetee.getIdValidateur()).isEqualTo(7L);
            assertThat(rejetee.getLibelleValidateur()).isEqualTo("TCHINDA Agnes");
            assertThat(rejetee.getDateValidation()).isNotNull();
        }

        @Test
        @DisplayName("8. rejet sans motif : refuse (RG-10)")
        void rejetSansMotifRefuse() {
            drhIdentifiee();
            GrilleTarifaire cible = enAttente(29L, 3000, DEBUT_NOUVELLE);
            grilleChargee(cible);

            assertThatThrownBy(() -> decisionGrilleService.rejeter(29L, null, JETON, IP))
                    .isInstanceOf(MotifRejetRequisException.class);

            assertThat(cible.getStatutValidation()).isEqualTo(StatutGrilleEnum.EN_ATTENTE_DRH);
            verify(grilleTarifaireRepository, never()).save(any());
        }

        @Test
        @DisplayName("8bis. motif fait d'espaces : refuse — une chaine vide n'est pas une explication")
        void motifEnEspacesRefuse() {
            drhIdentifiee();
            GrilleTarifaire cible = enAttente(29L, 3000, DEBUT_NOUVELLE);
            grilleChargee(cible);

            assertThatThrownBy(() -> decisionGrilleService.rejeter(29L, "     ", JETON, IP))
                    .isInstanceOf(MotifRejetRequisException.class);

            verify(grilleTarifaireRepository, never()).save(any());
        }

        @Test
        @DisplayName("9. rejet d'une grille ACTIVE : refuse")
        void rejetGrilleActiveRefuse() {
            drhIdentifiee();
            GrilleTarifaire active = enVigueur(29L, 3000, DEBUT_NOUVELLE);
            grilleChargee(active);

            assertThatThrownBy(() -> decisionGrilleService.rejeter(29L, "Trop cher", JETON, IP))
                    .isInstanceOf(TransitionGrilleInterditeException.class);

            assertThat(active.getStatutValidation()).isEqualTo(StatutGrilleEnum.ACTIVE);
            assertThat(active.getMotifRejet()).isNull();
            verify(grilleTarifaireRepository, never()).save(any());
        }

        @Test
        @DisplayName("10. apres rejet, la grille en vigueur est inchangee et n'est meme pas consultee")
        void ancienneGrilleIntacteApresRejet() {
            drhIdentifiee();
            GrilleTarifaire cible = enAttente(29L, 3000, DEBUT_NOUVELLE);
            grilleChargee(cible);
            enregistrementRendLaGrille();

            decisionGrilleService.rejeter(29L, "Bareme depasse", JETON, IP);

            // Un rejet dit « ce tarif ne s'appliquera pas », pas « il n'y a plus
            // de tarif ». Le service ne va meme pas chercher la grille en
            // vigueur : il n'a rien a lui faire.
            verify(grilleTarifaireRepository, never()).rechercherGrilleCourante(any(), any());
            verify(grilleTarifaireRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("le rejet est trace, motif compris")
        void rejetTrace() {
            drhIdentifiee();
            GrilleTarifaire cible = enAttente(29L, 3000, DEBUT_NOUVELLE);
            grilleChargee(cible);
            enregistrementRendLaGrille();

            decisionGrilleService.rejeter(29L, "Bareme depasse", JETON, IP);

            List<EvenementAudit> evenements = evenementsPublies();
            assertThat(evenements).hasSize(1);
            assertThat(evenements.get(0).action()).isEqualTo("REJET_GRILLE");
            assertThat(evenements.get(0).idEntite()).isEqualTo(29L);
            assertThat(evenements.get(0).detailJson()).contains("Bareme depasse");
        }
    }

}
