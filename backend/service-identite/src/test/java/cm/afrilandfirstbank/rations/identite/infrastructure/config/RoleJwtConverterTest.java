package cm.afrilandfirstbank.rations.identite.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Le convertisseur est le point de passage oblige entre le realm Keycloak et les
 * verifications de role du module : un role non converti se traduit par un 403
 * silencieux, sans message d'erreur exploitable.
 */
class RoleJwtConverterTest {

    private static final String SUB_JEAN_MBARGA = "4bf9cd35-4f6b-4b62-a005-12b1481d33fa";

    private final RoleJwtConverter convertisseur = new RoleJwtConverter();

    /** Jeton minimal equivalent a celui emis par le realm afb-rations-dev. */
    private Jwt jetonAvecRealmAccess(Object realmAccess) {
        Jwt.Builder builder = Jwt.withTokenValue("jeton-de-test")
                .header("alg", "RS256")
                .subject(SUB_JEAN_MBARGA)
                .claim("preferred_username", "jean_mbarga")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        if (realmAccess != null) {
            builder.claim(RoleJwtConverter.REVENDICATION_ACCES_REALM, realmAccess);
        }
        return builder.build();
    }

    private List<String> autorites(Jwt jwt) {
        JwtAuthenticationToken authentification = convertisseur.convert(jwt);
        return authentification.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList();
    }

    @Test
    @DisplayName("un jeton portant un role connu du module donne l'autorite prefixee ROLE_")
    void roleConnuConvertiEnAutorite() {
        Jwt jwt = jetonAvecRealmAccess(Map.of("roles", List.of("AGENT_UNITE")));

        assertThat(autorites(jwt)).containsExactly("ROLE_AGENT_UNITE");
    }

    @Test
    @DisplayName("un jeton sans role ne donne aucune autorite, sans lever d'exception")
    void jetonSansRole() {
        Jwt jwt = jetonAvecRealmAccess(null);

        assertThat(autorites(jwt)).isEmpty();
    }

    @Test
    @DisplayName("un role inconnu du module est ignore, les roles connus restent convertis")
    void roleInconnuIgnore() {
        // Le realm de developpement est partage avec d'autres modules : ses roles
        // ne doivent pas devenir des autorites ici.
        Jwt jwt = jetonAvecRealmAccess(Map.of("roles", List.of("CRH", "EMPLOYE", "DRH")));

        assertThat(autorites(jwt)).containsExactly("ROLE_DRH");
    }

    @Test
    @DisplayName("les six roles du module sont tous convertis")
    void tousLesRolesDuModule() {
        Jwt jwt = jetonAvecRealmAccess(Map.of("roles", List.of(
                "AGENT_UNITE", "CHEF_UNITE_DA", "DIRECTEUR_RESEAU_DR", "ARH", "DRH", "ADMIN")));

        assertThat(autorites(jwt)).containsExactly(
                "ROLE_ADMIN", "ROLE_AGENT_UNITE", "ROLE_ARH",
                "ROLE_CHEF_UNITE_DA", "ROLE_DIRECTEUR_RESEAU_DR", "ROLE_DRH");
    }

    @Test
    @DisplayName("une revendication realm_access malformee ne fait pas echouer la conversion")
    void realmAccessMalforme() {
        Jwt jwt = jetonAvecRealmAccess(Map.of("roles", "AGENT_UNITE"));

        assertThat(autorites(jwt)).isEmpty();
    }

    @Test
    @DisplayName("l'identifiant de l'authentification est le sub Keycloak, cle de la projection locale")
    void identifiantIssuDuSub() {
        Jwt jwt = jetonAvecRealmAccess(Map.of("roles", List.of("AGENT_UNITE")));

        assertThat(convertisseur.convert(jwt).getName()).isEqualTo(SUB_JEAN_MBARGA);
    }

}
