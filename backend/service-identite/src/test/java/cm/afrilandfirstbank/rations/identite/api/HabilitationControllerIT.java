package cm.afrilandfirstbank.rations.identite.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import org.springframework.test.web.servlet.MockMvc;

import cm.afrilandfirstbank.rations.identite.application.HabilitationService;
import cm.afrilandfirstbank.rations.identite.application.PorteeAccesService;
import cm.afrilandfirstbank.rations.identite.application.UtilisateurCourantService;
import cm.afrilandfirstbank.rations.identite.domaine.RoleEnum;
import cm.afrilandfirstbank.rations.identite.domaine.Utilisateur;
import cm.afrilandfirstbank.rations.identite.domaine.exception.UtilisateurNonHabiliteException;
import cm.afrilandfirstbank.rations.identite.infrastructure.config.RoleJwtConverter;
import cm.afrilandfirstbank.rations.identite.infrastructure.config.SecurityConfig;

/**
 * Chaine jeton -> securite -> controleur sur GET /identite/habilitation
 * (Sprint 1.3). {@link HabilitationService} et {@link PorteeAccesService} sont
 * les vrais ; seule la resolution du profil depuis le jeton est simulee, pour
 * ne dependre ni d'un Keycloak demarre ni d'une base.
 */
@WebMvcTest(controllers = HabilitationController.class)
@Import({ SecurityConfig.class, RoleJwtConverter.class, GestionnaireErreursApi.class,
        HabilitationService.class, PorteeAccesService.class,
        HabilitationControllerIT.ConfigurationDeTest.class })
class HabilitationControllerIT {

    private static final String SUB_JEAN_MBARGA = "4bf9cd35-4f6b-4b62-a005-12b1481d33fa";
    private static final String UNITE_DOUALA_BONANJO = "00002";
    private static final String UNITE_BAFOUSSAM = "00003";

    @TestConfiguration
    static class ConfigurationDeTest {
        @Bean
        JwtDecoder jwtDecoder() {
            return Mockito.mock(JwtDecoder.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    /**
     * Le port d'audit vient de l'autoconfiguration de rations-audit-commun, que
     * la tranche @WebMvcTest ne charge pas. Le gestionnaire d'erreurs en depend
     * pour tracer les refus (CT-04) : sans ce mock, le contexte ne demarre pas.
     */
    @MockitoBean
    private PublicateurAudit publicateurAudit;

    @MockitoBean
    private UtilisateurCourantService utilisateurCourantService;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor jetonDe(String login,
            String role) {
        return jwt().jwt(jeton -> jeton
                .subject(SUB_JEAN_MBARGA)
                .claim("preferred_username", login)
                .claim("realm_access", Map.of("roles", List.of(role))));
    }

    @Test
    @DisplayName("sans jeton : 401")
    void sansJetonRefuse() throws Exception {
        mockMvc.perform(get("/identite/habilitation").param("codeUnite", UNITE_DOUALA_BONANJO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("agent sur sa propre unite : 200, autorise, portee non nationale")
    void agentSurSaPropreUniteAutorise() throws Exception {
        when(utilisateurCourantService.resoudre(any())).thenReturn(new Utilisateur(
                "jean_mbarga", "MBARGA", "Jean", RoleEnum.AGENT_UNITE, UNITE_DOUALA_BONANJO));

        mockMvc.perform(get("/identite/habilitation")
                        .param("codeUnite", UNITE_DOUALA_BONANJO)
                        .with(jetonDe("jean_mbarga", "AGENT_UNITE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("jean_mbarga"))
                .andExpect(jsonPath("$.role").value("AGENT_UNITE"))
                .andExpect(jsonPath("$.codeUniteDemande").value(UNITE_DOUALA_BONANJO))
                .andExpect(jsonPath("$.autorise").value(true))
                .andExpect(jsonPath("$.porteeNationale").value(false));
    }

    @Test
    @DisplayName("agent sur une autre unite : 200, refuse")
    void agentSurUneAutreUniteRefuse() throws Exception {
        when(utilisateurCourantService.resoudre(any())).thenReturn(new Utilisateur(
                "jean_mbarga", "MBARGA", "Jean", RoleEnum.AGENT_UNITE, UNITE_DOUALA_BONANJO));

        mockMvc.perform(get("/identite/habilitation")
                        .param("codeUnite", UNITE_BAFOUSSAM)
                        .with(jetonDe("jean_mbarga", "AGENT_UNITE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autorise").value(false))
                .andExpect(jsonPath("$.porteeNationale").value(false));
    }

    @Test
    @DisplayName("role a portee nationale : 200, autorise sur une unite quelconque")
    void rolePorteeNationaleAutorisePartout() throws Exception {
        when(utilisateurCourantService.resoudre(any())).thenReturn(new Utilisateur(
                "agnes_tchinda", "TCHINDA", "Agnes", RoleEnum.DRH, null));

        mockMvc.perform(get("/identite/habilitation")
                        .param("codeUnite", UNITE_BAFOUSSAM)
                        .with(jetonDe("agnes_tchinda", "DRH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autorise").value(true))
                .andExpect(jsonPath("$.porteeNationale").value(true));
    }

    @Test
    @DisplayName("parametre codeUnite absent : 400 au format d'erreur uniforme")
    void codeUniteAbsentRejete() throws Exception {
        mockMvc.perform(get("/identite/habilitation")
                        .with(jetonDe("jean_mbarga", "AGENT_UNITE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("CODE_UNITE_INCOHERENT"))
                .andExpect(jsonPath("$.path").value("/identite/habilitation"));
    }

    @Test
    @DisplayName("parametre codeUnite a quatre chiffres : 400")
    void codeUniteMalFormeRejete() throws Exception {
        mockMvc.perform(get("/identite/habilitation")
                        .param("codeUnite", "0002")
                        .with(jetonDe("jean_mbarga", "AGENT_UNITE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CODE_UNITE_INCOHERENT"));
    }

    @Test
    @DisplayName("jeton valide mais aucun profil local ouvert : 403")
    void jetonValideSansProfilLocal() throws Exception {
        when(utilisateurCourantService.resoudre(any()))
                .thenThrow(new UtilisateurNonHabiliteException(
                        "Aucun profil n'a ete ouvert dans le module pour ce compte."));

        mockMvc.perform(get("/identite/habilitation")
                        .param("codeUnite", UNITE_DOUALA_BONANJO)
                        .with(jetonDe("pierre_belinga", "AGENT_UNITE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UTILISATEUR_NON_HABILITE"));
    }

}
