package cm.afrilandfirstbank.rations.workflow.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import cm.afrilandfirstbank.rations.workflow.application.FonctionnaliteService;
import cm.afrilandfirstbank.rations.workflow.application.ParametreAdminService;
import cm.afrilandfirstbank.rations.workflow.infrastructure.config.RoleJwtConverter;
import cm.afrilandfirstbank.rations.workflow.infrastructure.config.SecurityConfig;

/**
 * L'endpoint des fonctionnalites actives (Sprint 6bis.1,
 * {@code docs/dispositifs_provisoires.md} section 1.4).
 *
 * <pre>
 *   GET /parametres/fonctionnalites    tout utilisateur authentifie
 * </pre>
 *
 * <p>Ce qu'un test de service ne verrait pas et qui se joue ici : que l'endpoint est
 * bien <b>ouvert a tous les roles</b> — le frontend l'appelle au chargement de
 * l'application, avant de savoir quoi afficher —, et qu'il reste malgre tout
 * <b>ferme aux requetes sans jeton</b>. Les deux proprietes vivent dans la chaine de
 * securite, pas dans le controleur.
 */
@WebMvcTest(controllers = ParametreController.class)
@Import({ SecurityConfig.class, RoleJwtConverter.class, GestionnaireErreursApi.class })
@DisplayName("ParametreController — fonctionnalites actives")
class ParametreControllerIT {

    private static final String JETON = "Bearer jeton-de-test";
    private static final String SUB = "f31c8b04-77aa-4f52-9c18-6d2e0a9b4c73";
    private static final String LOGIN = "jean_mbarga";

    @Autowired
    private MockMvc mockMvc;

    /** Requis : autoconfiguration de rations-audit-commun non chargee sous @WebMvcTest. */
    @MockitoBean
    private PublicateurAudit publicateurAudit;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private FonctionnaliteService fonctionnaliteService;

    /** Requis depuis le guide 7F.6, etape 6 : ParametreController porte desormais
     * un second service applicatif (PUT /parametres/{code}). */
    @MockitoBean
    private ParametreAdminService parametreAdminService;

    /**
     * Test 18 du guide, premiere moitie : la reponse reflete l'etat reel du parametre.
     * Ici il est ferme — la valeur de la migration V2.
     */
    @Test
    @DisplayName("1. Drapeau ferme en base : rattrapageActif vaut false")
    void drapeauFerme() throws Exception {
        keycloakEmet("AGENT_UNITE");
        when(fonctionnaliteService.rattrapageActif()).thenReturn(false);

        mockMvc.perform(get("/parametres/fonctionnalites")
                        .header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rattrapageActif").value(false));
    }

    /**
     * Test 18, seconde moitie. La reponse <b>suit</b> le parametre : c'est ce qui permet
     * d'ouvrir la fonctionnalite par une simple mise a jour, sans redeploiement, et de
     * la refermer aussi vite en cas de probleme (dispositifs_provisoires.md section 1.5).
     */
    @Test
    @DisplayName("2. Drapeau ouvert en base : rattrapageActif vaut true")
    void drapeauOuvert() throws Exception {
        keycloakEmet("AGENT_UNITE");
        when(fonctionnaliteService.rattrapageActif()).thenReturn(true);

        mockMvc.perform(get("/parametres/fonctionnalites")
                        .header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rattrapageActif").value(true));
    }

    /**
     * <b>Aucun role n'est exige</b>, et c'est delibere : le frontend appelle cet endpoint
     * des la connexion, pour tous les roles a la fois. Le reserver a l'agent d'unite
     * obligerait a traiter le {@code 403} des autres roles comme un « non » deguise.
     *
     * <p>Le contenu ne le justifie pas davantage : savoir qu'une fonctionnalite du module
     * est fermee n'apprend rien sur un dossier, un montant ou une personne.
     */
    @Test
    @DisplayName("3. Ouvert a tous les roles du module, sans restriction")
    void ouvertATousLesRoles() throws Exception {
        when(fonctionnaliteService.rattrapageActif()).thenReturn(false);

        for (String role : List.of("AGENT_UNITE", "CHEF_UNITE_DA", "DIRECTEUR_RESEAU_DR",
                "ARH", "DRH", "ADMIN")) {

            keycloakEmet(role);

            mockMvc.perform(get("/parametres/fonctionnalites")
                            .header(HttpHeaders.AUTHORIZATION, JETON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rattrapageActif").value(false));
        }
    }

    /**
     * Ouvert a tous les roles ne veut pas dire ouvert a tout le monde. Sans jeton, la
     * chaine de securite refuse en amont du controleur : seules la sonde de sante et la
     * documentation Springdoc sont publiques (CLAUDE.md section 10).
     */
    @Test
    @DisplayName("4. Sans jeton : 401, l'endpoint n'est pas public")
    void refuseSansJeton() throws Exception {
        mockMvc.perform(get("/parametres/fonctionnalites"))
                .andExpect(status().isUnauthorized());
    }

    private void keycloakEmet(String role) {
        when(jwtDecoder.decode(anyString())).thenReturn(Jwt.withTokenValue("jeton-de-test")
                .header("alg", "RS256")
                .subject(SUB)
                .claim("preferred_username", LOGIN)
                .claim("realm_access", Map.of("roles", List.of(role)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build());
    }

}
