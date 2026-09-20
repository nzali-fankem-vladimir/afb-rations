package cm.afrilandfirstbank.rations.saisie.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
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
import cm.afrilandfirstbank.rations.saisie.application.ResultatResolutionMontant.MontantResolu;
import cm.afrilandfirstbank.rations.saisie.domaine.Beneficiaire;
import cm.afrilandfirstbank.rations.saisie.domaine.FicheJournaliere;
import cm.afrilandfirstbank.rations.saisie.domaine.LignePrestation;
import cm.afrilandfirstbank.rations.saisie.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.SessionEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.DoublonInterEtatsException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.DoublonLigneException;
import cm.afrilandfirstbank.rations.saisie.infrastructure.BeneficiaireRepository;
import cm.afrilandfirstbank.rations.saisie.infrastructure.FicheJournaliereRepository;
import cm.afrilandfirstbank.rations.saisie.infrastructure.LignePrestationRepository;

/**
 * Modification d'une ligne sur place : nature, session et identité du bénéficiaire,
 * dans une seule transaction (retour utilisateur après le Sprint 7F.7).
 *
 * <p>Ce que ces tests gardent : la voie en deux temps (suppression puis création)
 * pouvait laisser deux lignes pour la même prestation et refusait la correction
 * d'une agence ; la modification sur place rejoue RG-04, RG-15 et RG-03 <i>avant</i>
 * d'écrire quoi que ce soit.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LigneService, modification sur place")
class LigneServiceModificationTest {

    private static final Long ID_LIGNE = 1205L;
    private static final Long ID_FICHE = 310L;
    private static final Long ID_BENEFICIAIRE = 88L;
    private static final Long ID_AUTRE_BENEFICIAIRE = 90L;
    private static final String JETON = "Bearer jeton-de-l-agent";
    private static final LocalDate JOURNEE = LocalDate.of(2027, 9, 1);

    @Mock
    private FicheJournaliereRepository ficheJournaliereRepository;
    @Mock
    private LignePrestationRepository lignePrestationRepository;
    @Mock
    private BeneficiaireRepository beneficiaireRepository;
    @Mock
    private EtatModifiableService etatModifiableService;
    @Mock
    private CreationLigneService creationLigneService;
    @Mock
    private ControleDoublonService controleDoublonService;
    @Mock
    private ResolutionMontantClient resolutionMontantClient;
    @Mock
    private ResolutionBeneficiaireService resolutionBeneficiaireService;
    @Mock
    private PublicateurAudit publicateurAudit;

    private LigneService service;
    private LignePrestation ligne;
    private Beneficiaire beneficiaire;

    @BeforeEach
    void preparer() {
        service = new LigneService(ficheJournaliereRepository, lignePrestationRepository,
                beneficiaireRepository, etatModifiableService, creationLigneService,
                controleDoublonService, resolutionMontantClient, resolutionBeneficiaireService,
                publicateurAudit);

        FicheJournaliere fiche = new FicheJournaliere(7_800_003L, JOURNEE, "00002",
                LocalDate.of(2027, 9, 1), LocalDate.of(2027, 9, 7));
        ReflectionTestUtils.setField(fiche, "id", ID_FICHE);
        when(ficheJournaliereRepository.findById(ID_FICHE)).thenReturn(Optional.of(fiche));

        ligne = new LignePrestation(ID_FICHE, ID_BENEFICIAIRE, NatureEnum.TRANSPORT,
                SessionEnum.SOIR, 2000, 14L);
        ReflectionTestUtils.setField(ligne, "id", ID_LIGNE);
        when(lignePrestationRepository.findById(ID_LIGNE)).thenReturn(Optional.of(ligne));

        beneficiaire = new Beneficiaire("NZALI", "Vladimir", "00000012345", "00001");
        ReflectionTestUtils.setField(beneficiaire, "id", ID_BENEFICIAIRE);
        when(beneficiaireRepository.findById(ID_BENEFICIAIRE)).thenReturn(Optional.of(beneficiaire));

        lenient().when(controleDoublonService.estDoublonSurLaJourneeHorsLigne(any(), any(), any(), any(), any()))
                .thenReturn(false);
        lenient().when(controleDoublonService.etatDeLaPeriodePortantDeja(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(resolutionMontantClient.resoudre(any(), any(), any(), anyString()))
                .thenReturn(new MontantResolu(2000, 14L, LocalDate.of(2027, 1, 1), null));
    }

    @Test
    @DisplayName("1. corriger l'agence d'un beneficiaire connu, compte inchange : ligne modifiee sur place, aucun doublon")
    void correctionDeLAgenceSansCreationNiSuppression() {
        CommandeModificationLigne commande = new CommandeModificationLigne(
                NatureEnum.TRANSPORT, SessionEnum.SOIR, "NZALI", "Vladimir", "00000012345", "00002");

        LigneAvecBeneficiaire resultat = service.modifier(ID_LIGNE, commande, JETON, "10.0.0.1");

        assertThat(resultat.beneficiaire().getCodeAgence()).isEqualTo("00002");
        assertThat(beneficiaire.getCodeAgence()).isEqualTo("00002");
        // La ligne reste la meme, rattachee au meme beneficiaire.
        assertThat(resultat.ligne().getId()).isEqualTo(ID_LIGNE);
        assertThat(resultat.ligne().getIdBeneficiaire()).isEqualTo(ID_BENEFICIAIRE);
        // Ni creation ni suppression de ligne, ni resolution d'un autre beneficiaire.
        verify(lignePrestationRepository, never()).save(any());
        verify(lignePrestationRepository, never()).delete(any());
        verify(resolutionBeneficiaireService, never()).resoudre(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("2. la correction de l'identite est tracee avec l'avant et l'apres")
    void correctionTraceeAvecAvantEtApres() {
        service.modifier(ID_LIGNE, new CommandeModificationLigne(
                NatureEnum.TRANSPORT, SessionEnum.SOIR, "NZALI", "Vladimir", "00000012345", "00002"),
                JETON, "10.0.0.1");

        ArgumentCaptor<EvenementAudit> trace = ArgumentCaptor.forClass(EvenementAudit.class);
        verify(publicateurAudit).publier(trace.capture());
        assertThat(trace.getValue().action()).isEqualTo("MODIFICATION_LIGNE_PRESTATION");
        assertThat(trace.getValue().detailJson())
                .contains("codeAgenceBeneficiaire")
                .contains("00001")
                .contains("00002");
    }

    @Test
    @DisplayName("3. changer le numero de compte rattache la ligne a l'autre beneficiaire, sans toucher a l'ancien")
    void changementDeCompteRattacheLaLigne() {
        Beneficiaire autre = new Beneficiaire("ESSAMA", "Paul", "00000054321", "00003");
        ReflectionTestUtils.setField(autre, "id", ID_AUTRE_BENEFICIAIRE);
        when(resolutionBeneficiaireService.resoudre(eq("ESSAMA"), eq("Paul"), eq("00000054321"),
                eq("00003"), any())).thenReturn(autre);

        LigneAvecBeneficiaire resultat = service.modifier(ID_LIGNE, new CommandeModificationLigne(
                NatureEnum.TRANSPORT, SessionEnum.SOIR, "ESSAMA", "Paul", "00000054321", "00003"),
                JETON, null);

        assertThat(resultat.ligne().getIdBeneficiaire()).isEqualTo(ID_AUTRE_BENEFICIAIRE);
        // L'ancien beneficiaire n'est pas modifie par ce chemin.
        assertThat(beneficiaire.getNom()).isEqualTo("NZALI");
        assertThat(beneficiaire.getCodeAgence()).isEqualTo("00001");
        // RG-04 est evaluee pour le NOUVEAU beneficiaire, en excluant la ligne elle-meme.
        verify(controleDoublonService).estDoublonSurLaJourneeHorsLigne(
                ID_FICHE, ID_AUTRE_BENEFICIAIRE, NatureEnum.TRANSPORT, SessionEnum.SOIR, ID_LIGNE);
    }

    @Test
    @DisplayName("4. RG-04 refuse le doublon : rien n'est modifie, ni la ligne ni la fiche du beneficiaire")
    void doublonRefuseNeModifieRien() {
        when(controleDoublonService.estDoublonSurLaJourneeHorsLigne(any(), any(), any(), any(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.modifier(ID_LIGNE, new CommandeModificationLigne(
                NatureEnum.RATION, SessionEnum.JOUR, "NZALI", "Vladimir", "00000012345", "00002"),
                JETON, null))
                .isInstanceOf(DoublonLigneException.class);

        assertThat(ligne.getNature()).isEqualTo(NatureEnum.TRANSPORT);
        assertThat(ligne.getSession()).isEqualTo(SessionEnum.SOIR);
        assertThat(beneficiaire.getCodeAgence()).isEqualTo("00001");
        verify(publicateurAudit, never()).publier(any());
    }

    @Test
    @DisplayName("5. RG-15 refuse une prestation deja servie dans un autre etat : rien n'est modifie")
    void doublonInterEtatsRefuseNeModifieRien() {
        when(controleDoublonService.etatDeLaPeriodePortantDeja(any(), any(), any(), any()))
                .thenReturn(Optional.of(4242L));

        assertThatThrownBy(() -> service.modifier(ID_LIGNE, new CommandeModificationLigne(
                NatureEnum.TRANSPORT, SessionEnum.SOIR, "NZALI", "Vladimir", "00000012345", "00002"),
                JETON, null))
                .isInstanceOf(DoublonInterEtatsException.class);

        assertThat(beneficiaire.getCodeAgence()).isEqualTo("00001");
        verify(publicateurAudit, never()).publier(any());
    }

    @Test
    @DisplayName("6. les champs d'identite absents laissent le beneficiaire intact")
    void champsAbsentsInchanges() {
        service.modifier(ID_LIGNE, new CommandeModificationLigne(
                NatureEnum.TRANSPORT, SessionEnum.JOUR, null, null, null, null), JETON, null);

        assertThat(beneficiaire.getNom()).isEqualTo("NZALI");
        assertThat(beneficiaire.getCodeAgence()).isEqualTo("00001");
        assertThat(ligne.getSession()).isEqualTo(SessionEnum.JOUR);
    }
}
