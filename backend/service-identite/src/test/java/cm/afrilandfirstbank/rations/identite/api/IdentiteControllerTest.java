package cm.afrilandfirstbank.rations.identite.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import org.springframework.test.web.servlet.MockMvc;

import cm.afrilandfirstbank.rations.identite.application.PorteeAccesService;
import cm.afrilandfirstbank.rations.identite.application.UtilisateurCourantService;
import cm.afrilandfirstbank.rations.identite.domaine.RoleEnum;
import cm.afrilandfirstbank.rations.identite.domaine.Utilisateur;
import cm.afrilandfirstbank.rations.identite.domaine.exception.UtilisateurNonHabiliteException;
import cm.afrilandfirstbank.rations.identite.infrastructure.config.RoleJwtConverter;
import cm.afrilandfirstbank.rations.identite.infrastructure.config.SecurityConfig;

import org.mockito.Mockito;

/**
 * Verifie la chaine complete jeton -> securite -> controleur sur GET /identite/moi.
 *
 * <p>Le decodeur de jetons est simule : le test ne depend ni d'un Keycloak
 * demarre, ni d'une base de donnees.
 */
@WebMvcTest(controllers = IdentiteController.class)
@Import({ SecurityConfig.class, RoleJwtConverter.class, GestionnaireErreursApi.class,
        PorteeAccesService.class, IdentiteControllerTest.ConfigurationDeTest.class })
class IdentiteControllerTest {

    private static final String SUB_JEAN_MBARGA = "4bf9cd35-4f6b-4b62-a005-12b1481d33fa";

    @TestConfiguration
    static class ConfigurationDeTest {
        /** Evite d'aller chercher les cles du realm : aucun Keycloak n'est requis ici. */
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

    @Test
    @DisplayName("sans jeton, l'appel est refuse en 401")
    void sansJetonRefuse() throws Exception {
        mockMvc.perform(get("/identite/moi"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("avec un jeton valide, le profil local est retourne en 200")
    void avecJetonValideRetourneLeProfil() throws Exception {
        when(utilisateurCourantService.resoudre(any())).thenReturn(profilJeanMbarga());

        mockMvc.perform(get("/identite/moi")
                        .with(jwt().jwt(jeton -> jeton
                                .subject(SUB_JEAN_MBARGA)
                                .claim("preferred_username", "jean_mbarga")
                                .claim("realm_access", java.util.Map.of("roles", java.util.List.of("AGENT_UNITE"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("jean_mbarga"))
                .andExpect(jsonPath("$.nom").value("MBARGA"))
                .andExpect(jsonPath("$.prenom").value("Jean"))
                .andExpect(jsonPath("$.role").value("AGENT_UNITE"))
                .andExpect(jsonPath("$.codeUnite").value("00002"))
                .andExpect(jsonPath("$.porteeAcces.nationale").value(false))
                .andExpect(jsonPath("$.porteeAcces.codesUnite[0]").value("00002"))
                // Le profil expose ne doit jamais laisser filtrer l'identifiant technique.
                .andExpect(jsonPath("$.subKeycloak").doesNotExist());
    }

    @Test
    @DisplayName("jeton valide mais aucun profil ouvert dans le module : 403 au format d'erreur uniforme")
    void jetonValideSansProfilOuvert() throws Exception {
        when(utilisateurCourantService.resoudre(any()))
                .thenThrow(new UtilisateurNonHabiliteException(
                        "Aucun profil n'a ete ouvert dans le module pour ce compte."));

        mockMvc.perform(get("/identite/moi")
                        .with(jwt().jwt(jeton -> jeton
                                .subject("a1b2c3d4-0000-0000-0000-000000000001")
                                .claim("preferred_username", "pierre_belinga"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("UTILISATEUR_NON_HABILITE"))
                .andExpect(jsonPath("$.path").value("/identite/moi"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    private Utilisateur profilJeanMbarga() {
        return new Utilisateur("jean_mbarga", "MBARGA", "Jean", RoleEnum.AGENT_UNITE, "00002");
    }

}
