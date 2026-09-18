package cm.afrilandfirstbank.rations.saisie.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.saisie.application.CommandeCreationLigne.IdentiteBeneficiaire;
import cm.afrilandfirstbank.rations.saisie.application.ResultatResolutionMontant.AucuneGrilleApplicable;
import cm.afrilandfirstbank.rations.saisie.application.ResultatResolutionMontant.MontantResolu;
import cm.afrilandfirstbank.rations.saisie.application.ResultatResolutionMontant.ServiceGrillesIndisponible;
import cm.afrilandfirstbank.rations.saisie.domaine.Beneficiaire;
import cm.afrilandfirstbank.rations.saisie.domaine.FicheJournaliere;
import cm.afrilandfirstbank.rations.saisie.domaine.LignePrestation;
import cm.afrilandfirstbank.rations.saisie.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.SessionEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.DoublonInterEtatsException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.DoublonLigneException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.GrilleIndisponibleException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.ServiceGrillesIndisponibleException;
import cm.afrilandfirstbank.rations.saisie.infrastructure.FicheJournaliereRepository;
import cm.afrilandfirstbank.rations.saisie.infrastructure.LignePrestationRepository;

/**
 * Valorisation des lignes — tests 1 a 5 du Sprint 3.2, plus l'ordre des
 * controles.
 *
 * <p>Le service Grilles est <b>bouchonne par Mockito</b> au niveau du port
 * {@link ResolutionMontantClient} : ce qui est teste ici n'est pas le dialogue
 * HTTP (c'est l'objet de {@code ResolutionMontantClientTest}) mais la decision
 * prise a partir de chacune des trois reponses possibles.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CreationLigneService — valorisation (RG-03) et refus")
class CreationLigneServiceTest {

    private static final Long ID_FICHE = 310L;
    private static final Long ID_BENEFICIAIRE = 88L;
    private static final String JETON_AGENT = "Bearer jeton-de-l-agent";

    /** Journee de la prestation : passee, et volontairement differente d'aujourd'hui. */
    private static final LocalDate JOURNEE_PASSEE = LocalDate.of(2026, 7, 10);

    @Mock
    private FicheJournaliereRepository ficheJournaliereRepository;
    @Mock
    private LignePrestationRepository lignePrestationRepository;
    @Mock
    private ResolutionBeneficiaireService resolutionBeneficiaireService;
    @Mock
    private ControleDoublonService controleDoublonService;
    @Mock
    private ResolutionMontantClient resolutionMontantClient;
    @Mock
    private PublicateurAudit publicateurAudit;

    private CreationLigneService creationLigneService;

    private CommandeCreationLigne commande;

    @BeforeEach
    void preparer() {
        creationLigneService = new CreationLigneService(
                ficheJournaliereRepository, lignePrestationRepository, resolutionBeneficiaireService,
                controleDoublonService, resolutionMontantClient, publicateurAudit);

        commande = new CommandeCreationLigne(
                ID_FICHE,
                new IdentiteBeneficiaire("MBALLA", "Paul", "02009998888", "00002"),
                NatureEnum.RATION,
                SessionEnum.JOUR);
    }

    // --- 1. Cas nominal ---------------------------------------------------

    @Test
    @DisplayName("1. ligne valide : le montant vient de la grille, la ligne est creee et tracee")
    void ligneValide_estCreeeAuMontantDeLaGrille() {
        ficheExistante();
        beneficiaireResolu();
        aucunDoublon();
        when(resolutionMontantClient.resoudre(NatureEnum.RATION, SessionEnum.JOUR, JOURNEE_PASSEE, JETON_AGENT))
                .thenReturn(new MontantResolu(2500, 12L,
                        LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));
        when(lignePrestationRepository.save(any(LignePrestation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LignePrestation ligne = creationLigneService.creer(commande, JETON_AGENT);

        assertThat(ligne.getMontantApplique()).isEqualTo(2500);
        // La grille est conservee sur la ligne : c'est ce qui permettra de
        // justifier a posteriori un montant conteste (colonne id_grille, V2).
        assertThat(ligne.getIdGrille()).isEqualTo(12L);
        assertThat(ligne.getIdFicheJournaliere()).isEqualTo(ID_FICHE);
        assertThat(ligne.getIdBeneficiaire()).isEqualTo(ID_BENEFICIAIRE);

        ArgumentCaptor<EvenementAudit> trace = ArgumentCaptor.forClass(EvenementAudit.class);
        verify(publicateurAudit).publier(trace.capture());
        assertThat(trace.getValue().action()).isEqualTo("CREATION_LIGNE_PRESTATION");
        assertThat(trace.getValue().entiteCible()).isEqualTo("ligne_prestation");
        assertThat(trace.getValue().detailJson()).contains("2500", "12");
    }

    // --- 2. Aucune grille -------------------------------------------------

    @Test
    @DisplayName("2. aucune grille ne couvre la date : ligne refusee, message nommant nature, session et date")
    void aucuneGrille_refuseLaLigneAvecUnMessageExplicite() {
        ficheExistante();
        beneficiaireResolu();
        aucunDoublon();
        when(resolutionMontantClient.resoudre(any(), any(), any(), anyString()))
                .thenReturn(new AucuneGrilleApplicable(NatureEnum.RATION, SessionEnum.JOUR, JOURNEE_PASSEE));

        assertThatThrownBy(() -> creationLigneService.creer(commande, JETON_AGENT))
                .isInstanceOf(GrilleIndisponibleException.class)
                .hasMessageContaining("RATION")
                .hasMessageContaining("JOUR")
                .hasMessageContaining("2026-07-10")
                // Le message dit a l'agent ce qui debloquera la situation :
                // une grille proposee puis validee, pas un nouvel essai.
                .hasMessageContaining("validee par la directrice RH");

        verify(lignePrestationRepository, never()).save(any());
    }

    // --- 3. Service injoignable -------------------------------------------

    @Test
    @DisplayName("3. service Grilles injoignable : refus conservateur, aucune ligne enregistree, message distinct")
    void serviceGrillesInjoignable_refuseSansRienEnregistrer() {
        ficheExistante();
        beneficiaireResolu();
        aucunDoublon();
        when(resolutionMontantClient.resoudre(any(), any(), any(), anyString()))
                .thenReturn(new ServiceGrillesIndisponible("connexion refusee"));

        assertThatThrownBy(() -> creationLigneService.creer(commande, JETON_AGENT))
                // Type d'exception distinct de GrilleIndisponibleException : les
                // deux refus donneront deux codes HTTP et deux messages
                // differents (503 contre 422, decision Sprint 2.4).
                .isInstanceOf(ServiceGrillesIndisponibleException.class)
                .hasMessageContaining("reessayez")
                // Le motif technique reste dans le journal, jamais dans le
                // message a l'agent.
                .hasMessageNotContaining("connexion refusee");

        // Refus conservateur : rien n'est enregistre en attente de valorisation.
        verify(lignePrestationRepository, never()).save(any());
        // Un seul appel : aucun reessai automatique (decision Sprint 3.2).
        verify(resolutionMontantClient, times(1)).resoudre(any(), any(), any(), anyString());
    }

    // --- 4. Le montant de l'utilisateur est ignore -------------------------

    @Test
    @DisplayName("4. le montant transmis par l'utilisateur est ignore : structurellement impossible a fournir")
    void montantUtilisateur_estIgnoreParConstruction() {
        // La facon la plus sure d'ignorer une valeur est de n'avoir aucun endroit
        // ou la mettre. Ce test verrouille cette decision : si quelqu'un ajoutait
        // un champ montant a la commande d'entree, il echouerait ici avant que la
        // valeur n'ait la moindre chance d'atteindre une ligne de prestation.
        assertThat(Arrays.stream(CommandeCreationLigne.class.getRecordComponents())
                .map(RecordComponent::getName))
                .as("RG-03 : aucun montant ne doit pouvoir entrer par la commande de creation")
                .noneMatch(nom -> nom.toLowerCase().contains("montant"));
        assertThat(Arrays.stream(IdentiteBeneficiaire.class.getRecordComponents())
                .map(RecordComponent::getName))
                .noneMatch(nom -> nom.toLowerCase().contains("montant"));

        // Et le montant effectivement enregistre est bien celui de la grille.
        ficheExistante();
        beneficiaireResolu();
        aucunDoublon();
        when(resolutionMontantClient.resoudre(any(), any(), any(), anyString()))
                .thenReturn(new MontantResolu(2500, 12L, LocalDate.of(2026, 7, 1), null));
        when(lignePrestationRepository.save(any(LignePrestation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LignePrestation ligne = creationLigneService.creer(commande, JETON_AGENT);

        assertThat(ligne.getMontantApplique()).isEqualTo(2500);
    }

    // --- 5. Saisie retroactive --------------------------------------------

    @Test
    @DisplayName("5. saisie sur une journee passee : c'est la grille de l'epoque qui est sollicitee")
    void saisieRetroactive_interrogeLaGrilleDuJourDeLaPrestation() {
        ficheExistante();
        beneficiaireResolu();
        aucunDoublon();
        when(resolutionMontantClient.resoudre(any(), any(), any(), anyString()))
                .thenReturn(new MontantResolu(2000, 7L,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 7, 31)));
        when(lignePrestationRepository.save(any(LignePrestation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        creationLigneService.creer(commande, JETON_AGENT);

        ArgumentCaptor<LocalDate> dateInterrogee = ArgumentCaptor.forClass(LocalDate.class);
        verify(resolutionMontantClient).resoudre(eq(NatureEnum.RATION), eq(SessionEnum.JOUR),
                dateInterrogee.capture(), eq(JETON_AGENT));

        assertThat(dateInterrogee.getValue())
                .as("la date interrogee est celle de la fiche, pas celle de la saisie")
                .isEqualTo(JOURNEE_PASSEE)
                .isNotEqualTo(LocalDate.now());
    }

    // --- Ordre des controles ----------------------------------------------

    @Test
    @DisplayName("doublon : refus RG-04 SANS appel au service Grilles, et message citant les quatre elements")
    void doublon_refuseAvantToutAppelReseau() {
        ficheExistante();
        beneficiaireResolu();
        when(controleDoublonService.estDoublonSurLaJournee(
                ID_FICHE, ID_BENEFICIAIRE, NatureEnum.RATION, SessionEnum.JOUR)).thenReturn(true);

        assertThatThrownBy(() -> creationLigneService.creer(commande, JETON_AGENT))
                .isInstanceOf(DoublonLigneException.class)
                .hasMessageContaining("MBALLA")          // le beneficiaire
                .hasMessageContaining("2026-07-10")      // la journee
                .hasMessageContaining("RATION")          // la nature
                .hasMessageContaining("JOUR");           // la session

        // L'ordre compte : une ligne qui sera refusee de toute facon ne doit pas
        // couter un aller-retour reseau au service Grilles.
        verifyNoInteractions(resolutionMontantClient);
        verify(lignePrestationRepository, never()).save(any());
    }

    @Test
    @DisplayName("RG-15 : prestation deja servie ailleurs sur la periode, refus SANS appel au service Grilles")
    void doublonInterEtats_refuseAvantToutAppelReseau() {
        ficheExistante();
        beneficiaireResolu();
        aucunDoublon();
        when(controleDoublonService.etatDeLaPeriodePortantDeja(
                any(), eq(ID_BENEFICIAIRE), eq(NatureEnum.RATION), eq(SessionEnum.JOUR)))
                .thenReturn(Optional.of(4_242L));

        assertThatThrownBy(() -> creationLigneService.creer(commande, JETON_AGENT))
                .isInstanceOf(DoublonInterEtatsException.class)
                .hasMessageContaining("MBALLA")      // le beneficiaire
                .hasMessageContaining("2026-07-10")  // la journee
                .hasMessageContaining("RATION")      // la nature
                .hasMessageContaining("JOUR")        // la session
                .hasMessageContaining("4242");       // l'etat en conflit, sans quoi
                                                     // l'agent ne peut rien verifier

        // Meme motif qu'a la ligne precedente : RG-15 est locale et indexee, elle
        // s'evalue AVANT de solliciter le service Grilles.
        verifyNoInteractions(resolutionMontantClient);
        verify(lignePrestationRepository, never()).save(any());
    }

    @Test
    @DisplayName("RG-15 : aucun conflit sur la periode, la ligne suit son chemin normal")
    void aucunConflitInterEtats_laisseCreerLaLigne() {
        // Non-regression du chemin le plus frequent : un etat NORMAL en cours de
        // saisie n'a, par construction, aucun autre etat couvrant ses journees.
        // RG-15 y est un no-op, et le comportement du Sprint 3.2 est inchange.
        ficheExistante();
        beneficiaireResolu();
        aucunDoublon();
        when(resolutionMontantClient.resoudre(any(), any(), any(), any()))
                .thenReturn(new MontantResolu(2500, 11L, JOURNEE_PASSEE, null));
        when(lignePrestationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        creationLigneService.creer(commande, JETON_AGENT);

        verify(lignePrestationRepository).save(any());
    }

    // --- Utilitaires ------------------------------------------------------

    private void ficheExistante() {
        FicheJournaliere fiche = new FicheJournaliere(7_800_003L, JOURNEE_PASSEE, "00002", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        ReflectionTestUtils.setField(fiche, "id", ID_FICHE);
        when(ficheJournaliereRepository.findById(ID_FICHE)).thenReturn(Optional.of(fiche));
    }

    private void beneficiaireResolu() {
        Beneficiaire beneficiaire = new Beneficiaire("MBALLA", "Paul", "02009998888", "00002");
        ReflectionTestUtils.setField(beneficiaire, "id", ID_BENEFICIAIRE);
        when(resolutionBeneficiaireService.resoudre(eq("MBALLA"), eq("Paul"), eq("02009998888"),
                eq("00002"), any()))
                .thenReturn(beneficiaire);
    }

    /** Ni RG-04 sur la journee, ni RG-15 sur la periode. */
    private void aucunDoublon() {
        when(controleDoublonService.estDoublonSurLaJournee(any(), any(), any(), any()))
                .thenReturn(false);
        lenient().when(controleDoublonService.etatDeLaPeriodePortantDeja(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
    }

}
