package cm.afrilandfirstbank.rations.identite.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.identite.application.UtilisateurAdminService;
import cm.afrilandfirstbank.rations.identite.application.UtilisateurCourantService;
import cm.afrilandfirstbank.rations.identite.domaine.exception.UtilisateurNonHabiliteException;
import cm.afrilandfirstbank.rations.identite.infrastructure.config.RoleJwtConverter;
import cm.afrilandfirstbank.rations.identite.infrastructure.config.SecurityConfig;

/**
 * Publication d'audit sur les refus d'acces (CT-04, US-02) : « une tentative
 * d'action hors perimetre doit produire un evenement d'audit ».
 *
 * <p>Deux refus distincts sont couverts : role insuffisant sur un endpoint
 * reserve, et jeton valide sans profil local ouvert. Le second est le cas
 * limite qui compte : l'auteur n'a pas d'identifiant dans le module, et la trace
 * doit exister quand meme.
 */
@WebMvcTest(controllers = UtilisateurAdminController.class)
@Import({ SecurityConfig.class, RoleJwtConverter.class, GestionnaireErreursApi.class })
class PublicationDesRefusIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private PublicateurAudit publicateurAudit;

    @MockitoBean
    private UtilisateurAdminService utilisateurAdminService;

    @MockitoBean
    private UtilisateurCourantService utilisateurCourantService;

    private void keycloakEmet(String login, String role) {
        when(jwtDecoder.decode(anyString())).thenReturn(Jwt.withTokenValue("jeton-de-test")
                .header("alg", "RS256")
                .subject("4bf9cd35-4f6b-4b62-a005-12b1481d33fa")
                .claim("preferred_username", login)
                .claim("realm_access", Map.of("roles", List.of(role)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build());
    }

    private EvenementAudit refusPublie() {
        ArgumentCaptor<EvenementAudit> capture = ArgumentCaptor.forClass(EvenementAudit.class);
        verify(publicateurAudit).publier(capture.capture());
        return capture.getValue();
    }

    @Test
    @DisplayName("role insuffisant : 403 et evenement d'audit publie")
    void roleInsuffisantPublieUnRefus() throws Exception {
        keycloakEmet("jean_mbarga", "AGENT_UNITE");

        mockMvc.perform(get("/identite/utilisateurs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer jeton-de-test"))
                .andExpect(status().isForbidden());

        EvenementAudit refus = refusPublie();
        assertThat(refus.action()).isEqualTo("ACCES_REFUSE");
        assertThat(refus.entiteCible()).isEqualTo("acces");

        JsonNode contexte = MAPPER.readTree(refus.detailJson());
        assertThat(contexte.get("motif").asText()).isEqualTo("ROLE_INSUFFISANT");
        assertThat(contexte.get("chemin").asText()).isEqualTo("/identite/utilisateurs");
        assertThat(contexte.get("methode").asText()).isEqualTo("GET");
        assertThat(contexte.get("login").asText()).isEqualTo("jean_mbarga");
    }

    @Test
    @DisplayName("jeton valide sans profil local : 403 et refus trace malgre l'absence d'identifiant")
    void absenceDeProfilLocalPublieUnRefus() throws Exception {
        keycloakEmet("pierre_belinga", "ADMIN");
        when(utilisateurCourantService.resoudre(any()))
                .thenThrow(new UtilisateurNonHabiliteException(
                        "Aucun profil n'a ete ouvert dans le module pour ce compte."));

        // PUT : c'est l'attribution de role qui resout le profil local. La liste
        // (GET) ne le fait pas, elle ne passerait donc jamais par le refus vise.
        mockMvc.perform(put("/identite/utilisateurs/1/role")
                        .contentType("application/json")
                        .content("{\"role\":\"DRH\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer jeton-de-test"))
                .andExpect(status().isForbidden());

        EvenementAudit refus = refusPublie();
        assertThat(refus.action()).isEqualTo("ACCES_REFUSE");
        // Aucun profil local : pas d'identifiant utilisateur. Le login du jeton
        // est la seule identite disponible, et il doit etre dans la trace.
        assertThat(refus.idUtilisateur()).isNull();

        JsonNode contexte = MAPPER.readTree(refus.detailJson());
        assertThat(contexte.get("motif").asText()).isEqualTo("HABILITATION_ABSENTE");
        assertThat(contexte.get("login").asText()).isEqualTo("pierre_belinga");
    }

    @Test
    @DisplayName("sans jeton : 401 refuse par le filtre, aucun evenement publie")
    void sansJetonNePublieRien() throws Exception {
        mockMvc.perform(get("/identite/utilisateurs"))
                .andExpect(status().isUnauthorized());

        // Le 401 est prononce par le filtre de securite, en amont des
        // controleurs : il ne passe pas par le gestionnaire d'erreurs. Une
        // requete anonyme sans jeton n'est pas une tentative d'action hors
        // perimetre au sens de CT-04, mais une absence d'authentification.
        verify(publicateurAudit, never()).publier(any());
    }

}
