package cm.afrilandfirstbank.rations.reporting.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.reporting.application.ExportExcelService;
import cm.afrilandfirstbank.rations.reporting.application.ExportPdfService;
import org.mockito.ArgumentCaptor;

import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.reporting.application.RapportService;
import cm.afrilandfirstbank.rations.reporting.application.TracabiliteRapportService;
import cm.afrilandfirstbank.rations.reporting.application.SuiviService;
import cm.afrilandfirstbank.rations.reporting.domaine.Rapport;
import cm.afrilandfirstbank.rations.reporting.domaine.Rapport.Synthese;
import cm.afrilandfirstbank.rations.reporting.infrastructure.config.RoleJwtConverter;
import cm.afrilandfirstbank.rations.reporting.infrastructure.config.SecurityConfig;

/**
 * Les deux endpoints de rapport, traversés de bout en bout — tests 4 et 10 du
 * guide du Sprint 6.2.
 *
 * <p>Ce que {@code RapportServiceTest} et les tests d'export ne peuvent pas voir :
 * l'application réelle de {@code @PreAuthorize("hasRole('ARH')")} par Spring
 * Security, et le code HTTP effectivement rendu par
 * {@link GestionnaireErreursApi}. Même montage que {@code ProcessusControllerIT}
 * du service Workflow (Sprint 4.1) : jeton → sécurité → contrôleur → gestionnaire
 * d'erreurs.
 */
@WebMvcTest(controllers = ReportingController.class)
@Import({ SecurityConfig.class, RoleJwtConverter.class, GestionnaireErreursApi.class,
        TracabiliteRapportService.class })
class ReportingControllerIT {

    private static final String JETON = "Bearer jeton-de-test";
    private static final String LOGIN = "claire_nkolo";

    @Autowired
    private MockMvc mockMvc;

    /** Requis : autoconfiguration de rations-audit-commun non chargée sous @WebMvcTest. */
    @MockitoBean
    private PublicateurAudit publicateurAudit;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private SuiviService suiviService;

    @MockitoBean
    private RapportService rapportService;

    @MockitoBean
    private ExportPdfService exportPdfService;

    @MockitoBean
    private ExportExcelService exportExcelService;

    private void keycloakEmet(String role) {
        when(jwtDecoder.decode(anyString())).thenReturn(Jwt.withTokenValue("jeton-de-test")
                .header("alg", "RS256")
                .subject("f31c8b04-77aa-4f52-9c18-6d2e0a9b4c73")
                .claim("preferred_username", LOGIN)
                .claim("realm_access", Map.of("roles", List.of(role)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build());
    }

    private Rapport unRapport() {
        return new Rapport(8, 2026, null, LocalDateTime.of(2026, 9, 4, 10, 0), LOGIN,
                List.of(), List.of(), Synthese.vide(), true);
    }

    @Test
    @DisplayName("4a. GET /reporting/rapports par un role hors ARH : 403 ACCES_REFUSE")
    void rapportsInterditHorsRoleArh() throws Exception {
        keycloakEmet("CHEF_UNITE_DA");

        mockMvc.perform(get("/reporting/rapports")
                        .header(HttpHeaders.AUTHORIZATION, JETON)
                        .param("periode", "2026-08"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCES_REFUSE"));

        org.mockito.Mockito.verifyNoInteractions(rapportService);
    }

    @Test
    @DisplayName("4b. GET /reporting/rapports/export par un role hors ARH : 403 ACCES_REFUSE")
    void exportInterditHorsRoleArh() throws Exception {
        keycloakEmet("AGENT_UNITE");

        mockMvc.perform(get("/reporting/rapports/export")
                        .header(HttpHeaders.AUTHORIZATION, JETON)
                        .param("periode", "2026-08")
                        .param("format", "pdf"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCES_REFUSE"));

        org.mockito.Mockito.verifyNoInteractions(exportPdfService);
    }

    @Test
    @DisplayName("10. format inconnu : 422 FORMAT_EXPORT_INVALIDE, ni le PDF ni l'Excel ne sont produits")
    void formatInconnu_refuseAvecMessageExplicite() throws Exception {
        keycloakEmet("ARH");
        when(rapportService.produire(anyInt(), anyInt(), any(), anyString(), anyString()))
                .thenReturn(unRapport());

        mockMvc.perform(get("/reporting/rapports/export")
                        .header(HttpHeaders.AUTHORIZATION, JETON)
                        .param("periode", "2026-08")
                        .param("format", "csv"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FORMAT_EXPORT_INVALIDE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("pdf")));

        org.mockito.Mockito.verify(exportPdfService, never()).exporter(any());
        org.mockito.Mockito.verify(exportExcelService, never()).exporter(any());
    }

    @Test
    @DisplayName("Cas nominal : ARH, periode valide, rapport rendu en 200")
    void rapportNominal_rendu200() throws Exception {
        keycloakEmet("ARH");
        when(rapportService.produire(eq(8), eq(2026), eq((String) null), eq(LOGIN), eq(JETON)))
                .thenReturn(unRapport());

        mockMvc.perform(get("/reporting/rapports")
                        .header(HttpHeaders.AUTHORIZATION, JETON)
                        .param("periode", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vide").value(true))
                .andExpect(jsonPath("$.loginUtilisateur").value(LOGIN));
    }

    // --- Sprint 6.3 : couverture d'audit du service Reporting -------------------
    //
    // Ce service ne publiait aucun evenement avant ce sprint, alors qu'il declarait
    // deja rations-audit-commun dans son pom. Les tests ci-dessous verifient la
    // chaine complete controleur -> TracabiliteRapportService -> PublicateurAudit :
    // le service de tracabilite est importe pour de vrai, seul le publicateur est
    // simule.

    @Test
    @DisplayName("6.3-a. GET /reporting/rapports publie GENERATION_RAPPORT")
    void generationDeRapport_publieUnEvenementDAudit() throws Exception {
        keycloakEmet("ARH");
        when(rapportService.produire(anyInt(), anyInt(), any(), anyString(), anyString()))
                .thenReturn(unRapport());

        mockMvc.perform(get("/reporting/rapports")
                        .with(requete -> { requete.setRemoteAddr("10.20.30.40"); return requete; })
                        .header(HttpHeaders.AUTHORIZATION, JETON)
                        .param("periode", "2026-08"))
                .andExpect(status().isOk());

        ArgumentCaptor<EvenementAudit> capture = ArgumentCaptor.forClass(EvenementAudit.class);
        org.mockito.Mockito.verify(publicateurAudit).publier(capture.capture());
        EvenementAudit evenement = capture.getValue();

        assertThat(evenement.action()).isEqualTo("GENERATION_RAPPORT");
        assertThat(evenement.entiteCible()).isEqualTo("rapport_activite");
        assertThat(evenement.adresseIp()).isEqualTo("10.20.30.40");
        assertThat(evenement.detailJson()).contains(LOGIN).contains("2026");
        // Aucune unite demandee : la portee la plus large que ce service produise,
        // rendue explicitement plutot que par un champ vide.
        assertThat(evenement.detailJson()).contains("TOUTES_UNITES_VISIBLES");
    }

    @Test
    @DisplayName("6.3-b. GET /reporting/rapports/export publie EXPORT_RAPPORT, avec le nom et la taille du fichier")
    void exportDeRapport_publieUnEvenementDAudit() throws Exception {
        keycloakEmet("ARH");
        when(rapportService.produire(anyInt(), anyInt(), any(), anyString(), anyString()))
                .thenReturn(unRapport());
        when(exportPdfService.exporter(any())).thenReturn("%PDF-faux-contenu".getBytes());

        mockMvc.perform(get("/reporting/rapports/export")
                        .with(requete -> { requete.setRemoteAddr("10.20.30.40"); return requete; })
                        .header(HttpHeaders.AUTHORIZATION, JETON)
                        .param("periode", "2026-08")
                        .param("format", "pdf"))
                .andExpect(status().isOk());

        ArgumentCaptor<EvenementAudit> capture = ArgumentCaptor.forClass(EvenementAudit.class);
        org.mockito.Mockito.verify(publicateurAudit).publier(capture.capture());
        EvenementAudit evenement = capture.getValue();

        assertThat(evenement.action()).isEqualTo("EXPORT_RAPPORT");
        // Le nom du fichier et sa taille sont ce qui permet, des mois plus tard,
        // de rapprocher un document retrouve hors du systeme de l'extraction qui
        // l'a produit.
        assertThat(evenement.detailJson())
                .contains("rapport-rations-toutes-unites-202608.pdf")
                .contains("PDF")
                .contains("17"); // "%PDF-faux-contenu".length()
    }

    @Test
    @DisplayName("6.3-c. un format refuse ne publie AUCUN export : rien n'est sorti du systeme")
    void formatRefuse_nePublieAucunExport() throws Exception {
        keycloakEmet("ARH");
        when(rapportService.produire(anyInt(), anyInt(), any(), anyString(), anyString()))
                .thenReturn(unRapport());

        mockMvc.perform(get("/reporting/rapports/export")
                        .header(HttpHeaders.AUTHORIZATION, JETON)
                        .param("periode", "2026-08")
                        .param("format", "csv"))
                .andExpect(status().isUnprocessableEntity());

        org.mockito.Mockito.verifyNoInteractions(publicateurAudit);
    }

    @Test
    @DisplayName("6.3-d. les deux endpoints de suivi ne publient rien : ce sont des lectures de travail, pas des extractions")
    void endpointsDeSuivi_nePublientAucunEvenement() throws Exception {
        keycloakEmet("CHEF_UNITE_DA");
        when(suiviService.rechercher(any(), anyInt(), anyInt(), anyString()))
                .thenReturn(cm.afrilandfirstbank.rations.reporting.api.dto.PageResponse
                        .depuis(List.of(), 0, 20));

        mockMvc.perform(get("/reporting/demandes")
                        .header(HttpHeaders.AUTHORIZATION, JETON)
                        .param("periode", "2026-08"))
                .andExpect(status().isOk());

        // Tracer une consultation d'ecran produirait un evenement par affichage,
        // et noierait les faits notables. Decision Sprint 6.3, meme raisonnement
        // que pour date_dernier_acces cote Identite.
        org.mockito.Mockito.verifyNoInteractions(publicateurAudit);
    }

}
