package cm.afrilandfirstbank.rations.audit.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuration de securite du service.
 *
 * <p>Le service est un simple Resource Server OAuth2 : il valide le jeton emis par
 * Keycloak, en extrait l'identite et le role, et n'authentifie personne lui-meme.
 * Conformement a CLAUDE.md section 10, on ne trouvera donc ici ni formulaire de
 * login, ni {@code UserDetailsService}, ni {@code PasswordEncoder} : le module ne
 * stocke aucun mot de passe et n'expose aucune route de login.
 *
 * <p>Sessions stateless : chaque requete porte son jeton, rien n'est conserve entre
 * deux appels.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, RoleJwtConverter roleJwtConverter)
            throws Exception {
        http
                // API stateless consommee par un client porteur de jeton : pas de session
                // a proteger, donc pas de CSRF.
                .csrf(csrf -> csrf.disable())
                // CORS CENTRALISE SUR LA PASSERELLE (Sprint 8.1). Desactive ici
                // explicitement, et non supprime en silence : une seconde
                // configuration produirait des en-tetes en double, que le
                // navigateur rejette avec un message peu explicite. Le
                // navigateur n'atteint ce service que par la passerelle ; les
                // appels de service a service ne sont pas soumis au CORS.
                .cors(cors -> cors.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Seule la sonde de sante est ouverte : elle est interrogee par
                        // l'orchestrateur, qui ne porte pas de jeton.
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        // Documentation d'API a acces interne (CLAUDE.md section 2) : ouverte
                        // sans jeton pour rester consultable directement au navigateur.
                        .requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs",
                                "/v3/api-docs/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(roleJwtConverter)));
        return http.build();
    }

}
