package cm.afrilandfirstbank.rations.identite.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import cm.afrilandfirstbank.rations.identite.application.UtilisateurAdminService;
import cm.afrilandfirstbank.rations.identite.application.UtilisateurCourantService;
import cm.afrilandfirstbank.rations.identite.domaine.RoleEnum;
import cm.afrilandfirstbank.rations.identite.domaine.Utilisateur;
import cm.afrilandfirstbank.rations.identite.domaine.exception.UtilisateurIntrouvableException;
import cm.afrilandfirstbank.rations.identite.infrastructure.config.RoleJwtConverter;
import cm.afrilandfirstbank.rations.identite.infrastructure.config.SecurityConfig;

/**
 * Verifie la protection par role et le format de reponse des deux endpoints
 * d'administration (sous-sprint 1.2). Le service applicatif est simule : ce
 * test couvre la chaine jeton -> securite -> controleur, pas la logique
 * metier d'attribution, deja couverte par {@code UtilisateurAdminServiceTest}.
 *
 * <p>Comme {@code ProtectionParRoleTest}, le decodeur de jetons est simule et
 * la requete porte un veritable en-tete {@code Authorization}, pour que la
 * requete traverse reellement {@link RoleJwtConverter} : le raccourci MockMvc
 * {@code jwt()} attribue des autorites par defaut et contournerait ce
 * convertisseur, donnant un faux negatif sur {@code @PreAuthorize}.
 */
@WebMvcTest(controllers = UtilisateurAdminController.class)
@Import({ SecurityConfig.class, RoleJwtConverter.class, GestionnaireErreursApi.class })
class UtilisateurAdminControllerIT {

    private static final String SUB_ADMIN = "a1b2c3d4-0000-0000-0000-000000000099";

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
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UtilisateurAdminService utilisateurAdminService;

    @MockitoBean
    private UtilisateurCourantService utilisateurCourantService;

    private void keycloakEmet(String sub, String login, String role) {
        when(jwtDecoder.decode(anyString())).thenReturn(Jwt.withTokenValue("jeton-de-test")
                .header("alg", "RS256")
                .subject(sub)
                .claim("preferred_username", login)
                .claim("realm_access", Map.of("roles", List.of(role)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build());
    }

    private Utilisateur utilisateur(Long id, String login, String nom, String prenom, RoleEnum role,
            String codeUnite) {
        Utilisateur utilisateur = new Utilisateur(login, nom, prenom, role, codeUnite);
        ReflectionTestUtils.setField(utilisateur, "id", id);
        return utilisateur;
    }

    @Test
    @DisplayName("7. GET /identite/utilisateurs sans jeton : 401")
    void listeSansJetonRefusee() throws Exception {
        mockMvc.perform(get("/identite/utilisateurs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("8. GET /identite/utilisateurs avec un jeton AGENT_UNITE : 403")
    void listeAvecRoleInsuffisantRefusee() throws Exception {
        keycloakEmet("4bf9cd35-4f6b-4b62-a005-12b1481d33fa", "jean_mbarga", "AGENT_UNITE");

        mockMvc.perform(get("/identite/utilisateurs").header(HttpHeaders.AUTHORIZATION, "Bearer jeton-de-test"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("9. GET /identite/utilisateurs avec un jeton ADMIN : 200, page correcte")
    void listeAvecJetonAdminRetourneLaPage() throws Exception {
        keycloakEmet(SUB_ADMIN, "sara_biya", "ADMIN");
        Utilisateur eloundaEric = utilisateur(10L, "eric_elounda", "ELOUNDA", "Eric",
                RoleEnum.AGENT_UNITE, "00002");
        when(utilisateurAdminService.lister(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(eloundaEric), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/identite/utilisateurs").header(HttpHeaders.AUTHORIZATION, "Bearer jeton-de-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].login").value("eric_elounda"))
                .andExpect(jsonPath("$.content[0].subKeycloak").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("10. filtres combines role et codeUnite : resultats coherents")
    void listeAvecFiltresCombines() throws Exception {
        keycloakEmet(SUB_ADMIN, "sara_biya", "ADMIN");
        Utilisateur ngonoMarie = utilisateur(11L, "marie_ngono", "NGONO", "Marie",
                RoleEnum.CHEF_UNITE_DA, "00002");
        when(utilisateurAdminService.lister(eq(RoleEnum.CHEF_UNITE_DA), eq("00002"), any(), any()))
                .thenReturn(new PageImpl<>(List.of(ngonoMarie), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/identite/utilisateurs")
                        .param("role", "CHEF_UNITE_DA")
                        .param("codeUnite", "00002")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer jeton-de-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].codeUnite").value("00002"));
    }

    @Test
    @DisplayName("11. PUT sur un identifiant inexistant : 404")
    void attributionSurIdentifiantInexistantRefusee() throws Exception {
        keycloakEmet(SUB_ADMIN, "sara_biya", "ADMIN");
        Utilisateur appelantAdmin = utilisateur(1L, "sara_biya", "BIYA", "Sara", RoleEnum.ADMIN, null);
        when(utilisateurCourantService.resoudre(any())).thenReturn(appelantAdmin);
        when(utilisateurAdminService.attribuerRole(eq(99L), any(), any(), any()))
                .thenThrow(new UtilisateurIntrouvableException(99L));

        mockMvc.perform(put("/identite/utilisateurs/99/role")
                        .contentType("application/json")
                        .content("{\"role\":\"DRH\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer jeton-de-test"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UTILISATEUR_INTROUVABLE"));
    }

    @Test
    @DisplayName("12. PUT nominal avec un jeton ADMIN : 200, profil mis a jour")
    void attributionNominaleRetourneLeProfilMisAJour() throws Exception {
        keycloakEmet(SUB_ADMIN, "sara_biya", "ADMIN");
        Utilisateur appelantAdmin = utilisateur(1L, "sara_biya", "BIYA", "Sara", RoleEnum.ADMIN, null);
        Utilisateur ngonoMarie = utilisateur(11L, "marie_ngono", "NGONO", "Marie",
                RoleEnum.CHEF_UNITE_DA, "00002");
        when(utilisateurCourantService.resoudre(any())).thenReturn(appelantAdmin);
        when(utilisateurAdminService.attribuerRole(eq(11L), any(), any(), any())).thenReturn(ngonoMarie);

        mockMvc.perform(put("/identite/utilisateurs/11/role")
                        .contentType("application/json")
                        .content("{\"role\":\"CHEF_UNITE_DA\",\"codeUnite\":\"00002\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer jeton-de-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CHEF_UNITE_DA"))
                .andExpect(jsonPath("$.codeUnite").value("00002"));
    }

    @Test
    @DisplayName("PUT avec un code unite a quatre chiffres : 400, requete rejetee avant le service")
    void attributionAvecCodeUniteInvalideRefusee() throws Exception {
        keycloakEmet(SUB_ADMIN, "sara_biya", "ADMIN");

        mockMvc.perform(put("/identite/utilisateurs/11/role")
                        .contentType("application/json")
                        .content("{\"role\":\"AGENT_UNITE\",\"codeUnite\":\"0002\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer jeton-de-test"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"));
    }

}
