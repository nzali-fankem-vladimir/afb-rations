package cm.afrilandfirstbank.rations.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cm.afrilandfirstbank.rations.reporting.domaine.EnTeteDemande;
import cm.afrilandfirstbank.rations.reporting.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.reporting.domaine.exception.RechercheTropLargeException;
import cm.afrilandfirstbank.rations.reporting.domaine.exception.ServiceSaisieIndisponibleException;
import cm.afrilandfirstbank.rations.reporting.domaine.exception.ServiceWorkflowIndisponibleException;
import cm.afrilandfirstbank.rations.reporting.domaine.exception.UtilisateurNonHabiliteException;

/**
 * Le croisement des deux sources — tests 1 à 5 et 8 du guide du Sprint 6.1.
 *
 * <p>Les deux clients de lecture sont bouchonnés par Mockito : ce qui est testé
 * ici n'est pas le dialogue HTTP (c'est l'objet de
 * {@code WorkflowLectureHttpClientTest} et {@code SaisieLectureHttpClientTest})
 * mais la <b>stratégie d'agrégation</b> — quand la Saisie est appelée, comment
 * l'intersection se calcule, et comment chaque panne se traduit.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AgregationService — croisement multi-services (Sprint 6.1, CT-30)")
class AgregationServiceTest {

    private static final String JETON = "Bearer jeton-utilisateur";
    private static final int LIMITE = 5000;

    @Mock
    private WorkflowLectureClient workflowClient;

    @Mock
    private SaisieLectureClient saisieClient;

    private AgregationService service;

    @BeforeEach
    void preparer() {
        service = new AgregationService(workflowClient, saisieClient, LIMITE);
    }

    private EnTeteDemande enTete(long id) {
        return new EnTeteDemande(id, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1).plusMonths(1).minusDays(1), "00002", "NORMAL", 50_000, "CLOTURE",
                true, "INTEGRE", LocalDateTime.now());
    }

    @Test
    @DisplayName("1. sans filtre : toutes les demandes accessibles a l'utilisateur, la Saisie n'est pas appelee")
    void sansFiltre_rendToutesLesDemandes_sansAppelerLaSaisie() {
        when(workflowClient.rechercher(isNull(), isNull(), isNull(), isNull(), eq(LIMITE), eq(JETON)))
                .thenReturn(new ResultatRechercheDemandes.Obtenue(List.of(enTete(1), enTete(2))));

        List<EnTeteDemande> resultat = service.rechercher(CriteresRecherche.aucun(), JETON);

        assertThat(resultat).extracting(EnTeteDemande::id).containsExactly(1L, 2L);
        verifyNoInteractions(saisieClient);
    }

    @Test
    @DisplayName("2. filtre sur la periode seule : transmis au Workflow, la Saisie n'est pas appelee")
    void filtrePeriodeSeule_transmisAuWorkflow_sansLaSaisie() {
        CriteresRecherche criteres = new CriteresRecherche(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1).plusMonths(1).minusDays(1), null, null, null, null);

        when(workflowClient.rechercher(eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 8, 31)), isNull(), isNull(), eq(LIMITE), eq(JETON)))
                .thenReturn(new ResultatRechercheDemandes.Obtenue(List.of(enTete(5))));

        List<EnTeteDemande> resultat = service.rechercher(criteres, JETON);

        assertThat(resultat).extracting(EnTeteDemande::id).containsExactly(5L);
        verifyNoInteractions(saisieClient);
    }

    @Test
    @DisplayName("3. filtre sur la nature seule : la Saisie est appelee et restreint le resultat")
    void filtreNatureSeule_croiseAvecLaSaisie() {
        CriteresRecherche criteres = new CriteresRecherche(null, null, null, NatureEnum.RATION, null, null);

        when(workflowClient.rechercher(isNull(), isNull(), isNull(), isNull(), eq(LIMITE), eq(JETON)))
                .thenReturn(new ResultatRechercheDemandes.Obtenue(List.of(enTete(1), enTete(2), enTete(3))));
        when(saisieClient.identifiantsAvecLigne(isNull(), isNull(), eq(NatureEnum.RATION), isNull(),
                isNull(), eq(JETON)))
                .thenReturn(ResultatIdentifiantsAvecLigne.Obtenus.de(List.of(2L)));

        List<EnTeteDemande> resultat = service.rechercher(criteres, JETON);

        assertThat(resultat).extracting(EnTeteDemande::id).containsExactly(2L);
    }

    @Test
    @DisplayName("4. filtres combines periode et beneficiaire : croisement strict des deux sources")
    void filtresCombines_periodeEtBeneficiaire_croisentLesDeuxSources() {
        CriteresRecherche criteres = new CriteresRecherche(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1).plusMonths(1).minusDays(1), null, null, null, "10001234567");

        when(workflowClient.rechercher(eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 8, 31)), isNull(), isNull(), eq(LIMITE), eq(JETON)))
                .thenReturn(new ResultatRechercheDemandes.Obtenue(List.of(enTete(10), enTete(11), enTete(12))));
        when(saisieClient.identifiantsAvecLigne(eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 8, 31)), isNull(), isNull(),
                eq("10001234567"), eq(JETON)))
                .thenReturn(ResultatIdentifiantsAvecLigne.Obtenus.de(List.of(11L, 99L)));

        List<EnTeteDemande> resultat = service.rechercher(criteres, JETON);

        // 99 n'appartient pas aux en-tetes rendus par le Workflow : l'intersection
        // l'exclut, elle ne l'ajoute jamais.
        assertThat(resultat).extracting(EnTeteDemande::id).containsExactly(11L);
    }

    @Test
    @DisplayName("5. aucun resultat : reponse vide explicite, jamais une erreur")
    void aucunResultat_reponseVideExplicite() {
        CriteresRecherche criteres = new CriteresRecherche(null, null, null, NatureEnum.TRANSPORT, null, null);

        when(workflowClient.rechercher(isNull(), isNull(), isNull(), isNull(), eq(LIMITE), eq(JETON)))
                .thenReturn(new ResultatRechercheDemandes.Obtenue(List.of()));

        List<EnTeteDemande> resultat = service.rechercher(criteres, JETON);

        assertThat(resultat).isEmpty();
        // Rien a croiser : la Saisie n'est pas derangee pour un ensemble deja vide.
        verifyNoInteractions(saisieClient);
    }

    @Test
    @DisplayName("Volume trop grand : refuse en RechercheTropLargeException, jamais un contenu partiel")
    void volumeTropGrand_refuseAvecLeNombreTrouve() {
        when(workflowClient.rechercher(any(), any(), any(), any(), eq(LIMITE), eq(JETON)))
                .thenReturn(new ResultatRechercheDemandes.TropDeResultats(6214));

        assertThatThrownBy(() -> service.rechercher(CriteresRecherche.aucun(), JETON))
                .isInstanceOf(RechercheTropLargeException.class)
                .hasMessageContaining("6214")
                .hasMessageContaining("5000");
    }

    @Test
    @DisplayName("8a. service Workflow injoignable : refus net, quel que soit le critere")
    void serviceWorkflowInjoignable_refusNet() {
        when(workflowClient.rechercher(any(), any(), any(), any(), eq(LIMITE), eq(JETON)))
                .thenReturn(new ResultatRechercheDemandes.ServiceIndisponible("connexion refusee"));

        assertThatThrownBy(() -> service.rechercher(CriteresRecherche.aucun(), JETON))
                .isInstanceOf(ServiceWorkflowIndisponibleException.class);

        verifyNoInteractions(saisieClient);
    }

    @Test
    @DisplayName("8b. service Saisie injoignable ET critere de ligne demande : echec net, jamais un resultat partiel")
    void serviceSaisieInjoignable_avecCritereDeLigne_echecNet() {
        CriteresRecherche criteres = new CriteresRecherche(null, null, null, NatureEnum.RATION, null, null);

        when(workflowClient.rechercher(isNull(), isNull(), isNull(), isNull(), eq(LIMITE), eq(JETON)))
                .thenReturn(new ResultatRechercheDemandes.Obtenue(List.of(enTete(1))));
        when(saisieClient.identifiantsAvecLigne(isNull(), isNull(), eq(NatureEnum.RATION), isNull(),
                isNull(), eq(JETON)))
                .thenReturn(new ResultatIdentifiantsAvecLigne.ServiceIndisponible("delai depasse"));

        assertThatThrownBy(() -> service.rechercher(criteres, JETON))
                .isInstanceOf(ServiceSaisieIndisponibleException.class);
    }

    @Test
    @DisplayName("Refus de portee du Workflow : relaye tel quel, en UtilisateurNonHabiliteException")
    void refusDePortee_relayeTelQuel() {
        CriteresRecherche criteres = new CriteresRecherche(null, null, "00099", null, null, null);

        when(workflowClient.rechercher(isNull(), isNull(), eq("00099"), isNull(), eq(LIMITE), eq(JETON)))
                .thenReturn(new ResultatRechercheDemandes.AccesRefuse(
                        "vous n'avez pas de droit sur cette unite"));

        assertThatThrownBy(() -> service.rechercher(criteres, JETON))
                .isInstanceOf(UtilisateurNonHabiliteException.class)
                .hasMessageContaining("droit");
    }

}
