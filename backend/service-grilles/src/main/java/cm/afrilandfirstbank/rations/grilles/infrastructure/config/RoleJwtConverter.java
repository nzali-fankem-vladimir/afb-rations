package cm.afrilandfirstbank.rations.grilles.infrastructure.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import cm.afrilandfirstbank.rations.grilles.domaine.RoleEnum;

/**
 * Traduit les roles du realm Keycloak en autorites Spring Security.
 *
 * <p>Keycloak place les roles de realm dans la revendication {@code realm_access.roles}.
 * Spring Security ne lit pas cette revendication par defaut : sans ce convertisseur,
 * l'authentification reussit mais l'utilisateur n'a aucune autorite, et toute
 * verification de role echoue en 403.
 *
 * <p>Seuls les roles connus du module ({@link RoleEnum}) sont convertis. Un realm
 * partage peut porter des roles appartenant a d'autres applications : ils sont
 * ignores plutot que promus en autorites.
 *
 * <p>Le prefixe {@code ROLE_} est celui qu'attendent {@code hasRole()} et
 * {@code @PreAuthorize("hasRole(...)")}.
 */
@Component
public class RoleJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String REVENDICATION_ACCES_REALM = "realm_access";
    public static final String CLE_ROLES = "roles";
    public static final String PREFIXE_AUTORITE = "ROLE_";

    @Override
    public JwtAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, extraireAutorites(jwt), jwt.getSubject());
    }

    private Collection<GrantedAuthority> extraireAutorites(Jwt jwt) {
        return rolesDuRealm(jwt).stream()
                .map(RoleEnum::depuisLibelle)
                .flatMap(Optional::stream)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(PREFIXE_AUTORITE + role.name()))
                .collect(Collectors.toUnmodifiableSet());
    }

    private List<String> rolesDuRealm(Jwt jwt) {
        Map<String, Object> accesRealm = jwt.getClaimAsMap(REVENDICATION_ACCES_REALM);
        if (accesRealm == null) {
            return List.of();
        }
        if (!(accesRealm.get(CLE_ROLES) instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

}
