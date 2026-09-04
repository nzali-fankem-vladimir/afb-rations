package cm.afrilandfirstbank.rations.reporting.api;

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
import cm.afrilandfirstbank.rations.reporting.application.RapportService;
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
@Import({ SecurityConfig.class, RoleJwtConverter.class, GestionnaireErreursApi.class })
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

}
