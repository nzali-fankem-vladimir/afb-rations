package cm.afrilandfirstbank.rations.transmission.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.transmission.application.ResultatMiseAJourIntegration.AccuseContradictoire;
import cm.afrilandfirstbank.rations.transmission.application.ResultatMiseAJourIntegration.Applique;
import cm.afrilandfirstbank.rations.transmission.application.ResultatMiseAJourIntegration.DejaApplique;
import cm.afrilandfirstbank.rations.transmission.application.ResultatMiseAJourIntegration.ProcessusInconnu;
import cm.afrilandfirstbank.rations.transmission.application.ResultatMiseAJourIntegration.ProcessusNonTransmis;
import cm.afrilandfirstbank.rations.transmission.application.ResultatMiseAJourIntegration.ServiceWorkflowIndisponible;
import cm.afrilandfirstbank.rations.transmission.application.ResultatValidationAccuse.AccuseRecevable;
import cm.afrilandfirstbank.rations.transmission.domaine.AccuseComptableEvent;
import cm.afrilandfirstbank.rations.transmission.domaine.CodeAnomalieAccuseEnum;
import cm.afrilandfirstbank.rations.transmission.domaine.StatutIntegrationEnum;

/**
 * Le traitement d'un accuse recu : ce qui est applique, ce qui est refuse definitivement,
 * ce qui est rejoue (guide 5.2 etapes 4 et 5, tests 1, 2, 4, 5, 8 et 9).
 *
 * <p><b>Le test le plus important est celui de l'idempotence.</b> Sans elle, un rejeu de
 * topic — operation courante en exploitation — corromprait les statuts de tous les
 * processus concernes et doublerait leurs lignes d'audit.
 */
class TraitementAccuseServiceTest {

    private static final Long ID = 740L;
    private static final String TOPIC = "rations.etat.accuse";
    private static final String DATE_ISO = "2026-08-18T02:15:00Z";
    private static final String REFERENCE = "CPT-2026-07-000512";

    private final StatutIntegrationClient statutIntegrationClient =
            mock(StatutIntegrationClient.class);
    private final PublicateurAudit publicateurAudit = mock(PublicateurAudit.class);

    private final TraitementAccuseService service = new TraitementAccuseService(
            new ValidationAccuseService(), statutIntegrationClient, publicateurAudit);

    // --- Test 1 du guide : accuse d'integration -----------------------------------

    @Test
    @DisplayName("Test 1 — un accuse d'integration est applique : statut, reference et date "
            + "remontent tels quels au service Workflow")
    void accuseIntegreApplique() {
        when(statutIntegrationClient.mettreAJour(any()))
                .thenReturn(new Applique(ID, StatutIntegrationEnum.INTEGRE));

        ResultatTraitementAccuse resultat = service.traiter(
                new AccuseComptableEvent(ID, "INTEGRE", REFERENCE, DATE_ISO, null), TOPIC);

        assertThat(resultat).isInstanceOf(ResultatTraitementAccuse.Applique.class);

        ArgumentCaptor<AccuseRecevable> transmis = ArgumentCaptor.forClass(AccuseRecevable.class);
        verify(statutIntegrationClient).mettreAJour(transmis.capture());
        assertThat(transmis.getValue().idProcessus()).isEqualTo(ID);
        assertThat(transmis.getValue().statutIntegration())
                .isEqualTo(StatutIntegrationEnum.INTEGRE);
        assertThat(transmis.getValue().referenceComptable()).isEqualTo(REFERENCE);
        assertThat(transmis.getValue().dateTraitement())
                .isEqualTo(OffsetDateTime.of(2026, 8, 18, 2, 15, 0, 0, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("L'application est tracee en audit, avec la reference et la date : un controle "
            + "interne doit pouvoir rapprocher un paiement de son accuse")
    void applicationTracee() {
        when(statutIntegrationClient.mettreAJour(any()))
                .thenReturn(new Applique(ID, StatutIntegrationEnum.INTEGRE));

        service.traiter(new AccuseComptableEvent(ID, "INTEGRE", REFERENCE, DATE_ISO, null), TOPIC);

        EvenementAudit trace = uneSeuleTrace();
        assertThat(trace.action()).isEqualTo("ACCUSE_COMPTABLE_APPLIQUE");
        assertThat(trace.entiteCible()).isEqualTo("processus_mensuel");
        assertThat(trace.idEntite()).isEqualTo(ID);
        assertThat(trace.detailJson()).contains("INTEGRE").contains(REFERENCE);
    }

    // --- Test 2 du guide : accuse de rejet ----------------------------------------

    @Test
    @DisplayName("Test 2 — un accuse de rejet motive est applique, et le motif remonte au "
            + "service Workflow puis dans la trace d'audit")
    void accuseRejeteMotifConserve() {
        String motif = "Compte 00002000123456 clos depuis le 12/07.";
        when(statutIntegrationClient.mettreAJour(any()))
                .thenReturn(new Applique(ID, StatutIntegrationEnum.REJETE));

        service.traiter(new AccuseComptableEvent(ID, "REJETE", null, DATE_ISO, motif), TOPIC);

        ArgumentCaptor<AccuseRecevable> transmis = ArgumentCaptor.forClass(AccuseRecevable.class);
        verify(statutIntegrationClient).mettreAJour(transmis.capture());
        assertThat(transmis.getValue().motif()).isEqualTo(motif);
        assertThat(uneSeuleTrace().detailJson()).contains(motif);
    }

    // --- Tests 6 et 7 du guide, vus depuis le traitement --------------------------

    @Test
    @DisplayName("Un accuse invalide est refuse sans jamais atteindre le service Workflow : on ne "
            + "demande pas d'ecrire ce qu'on a deja juge faux")
    void accuseInvalideNAtteintPasLeWorkflow() {
        ResultatTraitementAccuse resultat = service.traiter(
                new AccuseComptableEvent(ID, "REJETE", null, DATE_ISO, null), TOPIC);

        assertThat(codesDe(resultat))
                .containsExactly(CodeAnomalieAccuseEnum.MOTIF_REJET_ABSENT);
        verifyNoInteractions(statutIntegrationClient);
    }

    // --- Test 4 du guide : processus inconnu --------------------------------------

    @Test
    @DisplayName("Test 4 — un accuse pour un processus inconnu est refuse definitivement et "
            + "trace : le rejouer ne ferait pas naitre le processus")
    void processusInconnuRefuse() {
        when(statutIntegrationClient.mettreAJour(any())).thenReturn(new ProcessusInconnu(ID));

        ResultatTraitementAccuse resultat = service.traiter(
                new AccuseComptableEvent(ID, "INTEGRE", REFERENCE, DATE_ISO, null), TOPIC);

        assertThat(codesDe(resultat)).containsExactly(CodeAnomalieAccuseEnum.PROCESSUS_INCONNU);
        assertThat(uneSeuleTrace().action()).isEqualTo("ACCUSE_COMPTABLE_REFUSE");
    }

    // --- Test 5 du guide : processus jamais transmis ------------------------------

    @Test
    @DisplayName("Test 5 — un accuse pour un etat jamais transmis est refuse et trace : "
            + "l'ignorer silencieusement priverait d'un signal utile")
    void processusJamaisTransmisRefuse() {
        when(statutIntegrationClient.mettreAJour(any()))
                .thenReturn(new ProcessusNonTransmis(ID, "L'etat 740 n'a jamais ete transmis."));

        ResultatTraitementAccuse resultat = service.traiter(
                new AccuseComptableEvent(ID, "INTEGRE", REFERENCE, DATE_ISO, null), TOPIC);

        assertThat(codesDe(resultat))
                .containsExactly(CodeAnomalieAccuseEnum.PROCESSUS_NON_TRANSMIS);
        assertThat(uneSeuleTrace().detailJson()).contains("jamais ete transmis");
    }

    // --- Test 8 du guide : idempotence --------------------------------------------

    @Test
    @DisplayName("Test 8 — le meme accuse recu deux fois produit le meme resultat, et UNE SEULE "
            + "trace d'audit : sans cela, un rejeu de topic corromprait le journal")
    void memeAccuseDeuxFoisUneSeuleTrace() {
        AccuseComptableEvent accuse =
                new AccuseComptableEvent(ID, "INTEGRE", REFERENCE, DATE_ISO, null);

        // Premiere reception : le Workflow ecrit.
        when(statutIntegrationClient.mettreAJour(any()))
                .thenReturn(new Applique(ID, StatutIntegrationEnum.INTEGRE));
        ResultatTraitementAccuse premiere = service.traiter(accuse, TOPIC);

        // Seconde reception : le Workflow reconnait l'accuse et n'ecrit rien.
        when(statutIntegrationClient.mettreAJour(any()))
                .thenReturn(new DejaApplique(ID, StatutIntegrationEnum.INTEGRE));
        ResultatTraitementAccuse seconde = service.traiter(accuse, TOPIC);

        assertThat(premiere).isInstanceOf(ResultatTraitementAccuse.Applique.class);
        assertThat(seconde).isInstanceOf(ResultatTraitementAccuse.DejaApplique.class);

        // L'etat final est le meme : INTEGRE dans les deux cas.
        assertThat(((ResultatTraitementAccuse.Applique) premiere).statutApplique())
                .isEqualTo(((ResultatTraitementAccuse.DejaApplique) seconde).statutCourant());

        // Et surtout : une seule ligne d'audit pour deux receptions. Une seconde ferait
        // croire a un second traitement comptable du meme etat.
        verify(publicateurAudit, times(1)).publier(any());
    }

    @Test
    @DisplayName("Un rejeu ne redemande jamais d'ecriture inutile au-dela du verdict : le "
            + "Workflow est interroge une fois par message, et lui seul decide")
    void rejeuInterrogeLeWorkflowUneFoisParMessage() {
        AccuseComptableEvent accuse =
                new AccuseComptableEvent(ID, "INTEGRE", REFERENCE, DATE_ISO, null);
        when(statutIntegrationClient.mettreAJour(any()))
                .thenReturn(new DejaApplique(ID, StatutIntegrationEnum.INTEGRE));

        service.traiter(accuse, TOPIC);
        service.traiter(accuse, TOPIC);
        service.traiter(accuse, TOPIC);

        verify(statutIntegrationClient, times(3)).mettreAJour(any());
        verifyNoInteractions(publicateurAudit);
    }

    // --- Test 9 du guide : accuse contradictoire ----------------------------------

    @Test
    @DisplayName("Test 9 — un accuse contredisant un statut deja recu est refuse, trace, et "
            + "n'ecrit rien : le module refuse et signale, il n'arbitre jamais")
    void accuseContradictoireRefuse() {
        when(statutIntegrationClient.mettreAJour(any())).thenReturn(new AccuseContradictoire(ID,
                "L'etat 740 porte deja le statut d'integration INTEGRE, et l'accuse recu "
                        + "porte REJETE."));

        ResultatTraitementAccuse resultat = service.traiter(
                new AccuseComptableEvent(ID, "REJETE", null, DATE_ISO, "Compte clos."), TOPIC);

        assertThat(codesDe(resultat))
                .containsExactly(CodeAnomalieAccuseEnum.ACCUSE_CONTRADICTOIRE);
        // Le message nomme les DEUX statuts : sans cela, personne ne pourrait lever la
        // contradiction avec la comptabilite.
        assertThat(uneSeuleTrace().detailJson()).contains("INTEGRE").contains("REJETE");
    }

    // --- Echec temporaire ---------------------------------------------------------

    @Test
    @DisplayName("Un service Workflow injoignable est la SEULE issue temporaire : elle n'est pas "
            + "un refus, et le consommateur la rejouera")
    void serviceWorkflowInjoignableEstTemporaire() {
        when(statutIntegrationClient.mettreAJour(any()))
                .thenReturn(new ServiceWorkflowIndisponible(ID, "connexion refusee"));

        ResultatTraitementAccuse resultat = service.traiter(
                new AccuseComptableEvent(ID, "INTEGRE", REFERENCE, DATE_ISO, null), TOPIC);

        assertThat(resultat).isInstanceOf(ResultatTraitementAccuse.EchecTemporaire.class);
        // Aucune trace de refus : rien n'est refuse, on ne sait simplement pas encore.
        // La tracer ferait croire a un accuse fautif, ce qu'il n'est pas.
        verifyNoInteractions(publicateurAudit);
    }

    @Test
    @DisplayName("Aucune exception ne sort du traitement, quelle que soit l'issue")
    void aucuneExceptionNeSort() {
        List<ResultatMiseAJourIntegration> issues = new ArrayList<>(List.of(
                new Applique(ID, StatutIntegrationEnum.INTEGRE),
                new DejaApplique(ID, StatutIntegrationEnum.INTEGRE),
                new ProcessusInconnu(ID),
                new ProcessusNonTransmis(ID, "jamais transmis"),
                new AccuseContradictoire(ID, "contradiction"),
                new ServiceWorkflowIndisponible(ID, "panne")));

        for (ResultatMiseAJourIntegration issue : issues) {
            when(statutIntegrationClient.mettreAJour(any())).thenReturn(issue);
            assertThat(service.traiter(
                    new AccuseComptableEvent(ID, "INTEGRE", REFERENCE, DATE_ISO, null), TOPIC))
                    .describedAs("issue %s", issue.getClass().getSimpleName())
                    .isNotNull();
        }
    }

    // --- Outils -------------------------------------------------------------------

    private EvenementAudit uneSeuleTrace() {
        ArgumentCaptor<EvenementAudit> capture = ArgumentCaptor.forClass(EvenementAudit.class);
        verify(publicateurAudit, times(1)).publier(capture.capture());
        return capture.getValue();
    }

    private static List<CodeAnomalieAccuseEnum> codesDe(ResultatTraitementAccuse resultat) {
        assertThat(resultat).isInstanceOf(ResultatTraitementAccuse.RefusDefinitif.class);
        return ((ResultatTraitementAccuse.RefusDefinitif) resultat).anomalies().stream()
                .map(AnomalieAccuse::code)
                .toList();
    }

}
