package cm.afrilandfirstbank.rations.grilles.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.grilles.domaine.GrilleTarifaire;
import cm.afrilandfirstbank.rations.grilles.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.SessionEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.StatutGrilleEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.TransitionGrille;
import cm.afrilandfirstbank.rations.grilles.domaine.exception.GrilleIntrouvableException;
import cm.afrilandfirstbank.rations.grilles.domaine.exception.GrilleNonProprietaireException;
import cm.afrilandfirstbank.rations.grilles.domaine.exception.MotifRejetRequisException;
import cm.afrilandfirstbank.rations.grilles.domaine.exception.TransitionGrilleInterditeException;
import cm.afrilandfirstbank.rations.grilles.infrastructure.GrilleTarifaireRepository;
import cm.afrilandfirstbank.rations.grilles.infrastructure.identite.AuteurIdentifie;
import cm.afrilandfirstbank.rations.grilles.infrastructure.identite.ClientIdentite;

/**
 * Retrait d'une grille en attente par son propre auteur (rattrapage post-7F.6, demande
 * n°7 de la vérification visuelle du Sprint 7F.6, RG-14, RG-10).
 *
 * <p>Ce que {@code GrilleControllerIT} ne peut pas voir : que la propriété est
 * vérifiée par identifiant et jamais par libellé, que le refus de propriété
 * précède la vérification de transition, et que le statut initial figure bien
 * dans le delta d'audit.
 */
class RetraitGrilleServiceTest {

    private static final Long ID = 29L;
    private static final String JETON = "Bearer jeton-de-test";
    private static final String IP = "10.12.4.31";
    private static final String MOTIF = "Erreur de montant, je repropose une grille corrigee";

    private static final AuteurIdentifie CLAIRE_NKOLO = new AuteurIdentifie(4L, "claire_nkolo", "NKOLO", "Claire");
    private static final AuteurIdentifie AUTRE_ARH = new AuteurIdentifie(9L, "paul_essama", "ESSAMA", "Paul");

    private final GrilleTarifaireRepository grilleTarifaireRepository = mock(GrilleTarifaireRepository.class);
    private final ClientIdentite clientIdentite = mock(ClientIdentite.class);
    private final PublicateurAudit publicateurAudit = mock(PublicateurAudit.class);

    private final RetraitGrilleService service =
            new RetraitGrilleService(grilleTarifaireRepository, clientIdentite, publicateurAudit);

    private static GrilleTarifaire enAttente(Long id, Long idCreateur, String libelleCreateur) {
        GrilleTarifaire g = new GrilleTarifaire(NatureEnum.RATION, SessionEnum.JOUR,
                1200, LocalDate.of(2026, 10, 1), idCreateur, libelleCreateur);
        ReflectionTestUtils.setField(g, "id", id);
        TransitionGrille.soumettre(g);
        return g;
    }

    // --- Nominal ------------------------------------------------------------------

    @Test
    @DisplayName("L'auteur retire sa propre proposition : statut REJETEE, un seul evenement "
            + "RETRAIT_GRILLE publie, portant le statut initial")
    void retraitParLAuteur() {
        GrilleTarifaire cible = enAttente(ID, CLAIRE_NKOLO.id(), CLAIRE_NKOLO.libelle());
        when(clientIdentite.resoudreAuteur(JETON)).thenReturn(CLAIRE_NKOLO);
        when(grilleTarifaireRepository.findById(ID)).thenReturn(Optional.of(cible));
        when(grilleTarifaireRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        GrilleTarifaire retiree = service.retirer(ID, MOTIF, JETON, IP);

        assertThat(retiree.getStatutValidation()).isEqualTo(StatutGrilleEnum.REJETEE);
        assertThat(retiree.getMotifRejet()).isEqualTo(MOTIF);

        ArgumentCaptor<EvenementAudit> capture = ArgumentCaptor.forClass(EvenementAudit.class);
        verify(publicateurAudit).publier(capture.capture());
        EvenementAudit evenement = capture.getValue();
        assertThat(evenement.action()).isEqualTo("RETRAIT_GRILLE");
        assertThat(evenement.entiteCible()).isEqualTo("grille_tarifaire");
        assertThat(evenement.idEntite()).isEqualTo(ID);
        assertThat(evenement.idUtilisateur()).isEqualTo(CLAIRE_NKOLO.id());
        assertThat(evenement.detailJson())
                .contains("EN_ATTENTE_DRH")
                .contains("REJETEE");
    }

    // --- Refus ----------------------------------------------------------------

    @Test
    @DisplayName("Un autre Analyste RH ne peut pas retirer la proposition : 403 "
            + "GRILLE_NON_PROPRIETAIRE, rien n'est modifie ni trace")
    void refuseSiAutreAuteur() {
        GrilleTarifaire cible = enAttente(ID, CLAIRE_NKOLO.id(), CLAIRE_NKOLO.libelle());
        when(clientIdentite.resoudreAuteur(JETON)).thenReturn(AUTRE_ARH);
        when(grilleTarifaireRepository.findById(ID)).thenReturn(Optional.of(cible));

        assertThatThrownBy(() -> service.retirer(ID, MOTIF, JETON, IP))
                .isInstanceOf(GrilleNonProprietaireException.class);

        assertThat(cible.getStatutValidation()).isEqualTo(StatutGrilleEnum.EN_ATTENTE_DRH);
        verify(grilleTarifaireRepository, never()).save(any());
        verify(publicateurAudit, never()).publier(any());
    }

    @Test
    @DisplayName("Grille introuvable : 404, la propriete n'est meme pas verifiee")
    void grilleIntrouvable() {
        when(clientIdentite.resoudreAuteur(JETON)).thenReturn(CLAIRE_NKOLO);
        when(grilleTarifaireRepository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.retirer(ID, MOTIF, JETON, IP))
                .isInstanceOf(GrilleIntrouvableException.class);

        verify(publicateurAudit, never()).publier(any());
    }

    @Test
    @DisplayName("Grille deja tranchee par la DRH (ACTIVE) : la propriete est verifiee, "
            + "mais la transition est refusee -- meme le veritable auteur ne peut plus la retirer")
    void refuseSiDejaTranchee() {
        GrilleTarifaire active = enAttente(ID, CLAIRE_NKOLO.id(), CLAIRE_NKOLO.libelle());
        TransitionGrille.valider(active, 7L, java.time.LocalDateTime.now());
        when(clientIdentite.resoudreAuteur(JETON)).thenReturn(CLAIRE_NKOLO);
        when(grilleTarifaireRepository.findById(ID)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.retirer(ID, MOTIF, JETON, IP))
                .isInstanceOf(TransitionGrilleInterditeException.class);

        verify(grilleTarifaireRepository, never()).save(any());
        verify(publicateurAudit, never()).publier(any());
    }

    @Test
    @DisplayName("Motif vide : refuse avant toute ecriture (RG-10)")
    void motifVideRefuse() {
        GrilleTarifaire cible = enAttente(ID, CLAIRE_NKOLO.id(), CLAIRE_NKOLO.libelle());
        when(clientIdentite.resoudreAuteur(JETON)).thenReturn(CLAIRE_NKOLO);
        when(grilleTarifaireRepository.findById(ID)).thenReturn(Optional.of(cible));

        assertThatThrownBy(() -> service.retirer(ID, "   ", JETON, IP))
                .isInstanceOf(MotifRejetRequisException.class);

        verify(grilleTarifaireRepository, never()).save(any());
        verify(publicateurAudit, never()).publier(any());
    }

}
