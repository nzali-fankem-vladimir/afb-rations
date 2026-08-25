package cm.afrilandfirstbank.rations.identite.infrastructure.config;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verifie la chaine complete de protection par role : en-tete Authorization,
 * revendication realm_access.roles, conversion en autorite ROLE_, puis controle
 * {@code @PreAuthorize}.
 *
 * <p>Seul le decodeur est simule : la requete traverse reellement le filtre de
 * securite et le convertisseur. Sans ce dernier, meme un jeton portant le bon
 * role serait refuse en 403.
 *
 * <p>Le controleur utilise ici n'existe que pour le test : le module n'expose
 * encore aucun endpoint reserve a un role au sprint 0.4.
 */
@WebMvcTest(controllers = ProtectionParRoleTest.ControleurReserve.class)
@Import({ SecurityConfig.class, RoleJwtConverter.class, ProtectionParRoleTest.ControleurReserve.class })
class ProtectionParRoleTest {

    @RestController
    static class ControleurReserve {
        @GetMapping("/zone-administrateur")
        @PreAuthorize("hasRole('ADMIN')")
        ResponseEntity<String> reserveAdmin() {
            return ResponseEntity.ok("acces autorise");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    /** Jeton equivalent a celui emis par le realm afb-rations-dev pour ce compte. */
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

    @Test
    @DisplayName("le role attendu ouvre l'acces en 200")
    void roleAttenduAutorise() throws Exception {
        keycloakEmet("martin_fouda", "ADMIN");

        mockMvc.perform(get("/zone-administrateur").header(HttpHeaders.AUTHORIZATION, "Bearer jeton-de-test"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("un autre role est refuse en 403, pas en 401 : l'authentification a bien eu lieu")
    void roleInsuffisantRefuse() throws Exception {
        keycloakEmet("jean_mbarga", "AGENT_UNITE");

        mockMvc.perform(get("/zone-administrateur").header(HttpHeaders.AUTHORIZATION, "Bearer jeton-de-test"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sans jeton, le refus est un 401 et non un 403")
    void sansJetonRefuseEn401() throws Exception {
        mockMvc.perform(get("/zone-administrateur"))
                .andExpect(status().isUnauthorized());
    }

}
