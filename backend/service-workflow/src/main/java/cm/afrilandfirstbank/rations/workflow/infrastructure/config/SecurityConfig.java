package cm.afrilandfirstbank.rations.workflow.infrastructure.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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

    private final List<String> originsAutorisees;

    public SecurityConfig(@Value("${app.cors.allowed-origins}") List<String> originsAutorisees) {
        this.originsAutorisees = originsAutorisees;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, RoleJwtConverter roleJwtConverter)
            throws Exception {
        http
                // API stateless consommee par un client porteur de jeton : pas de session
                // a proteger, donc pas de CSRF.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Seule la sonde de sante est ouverte : elle est interrogee par
                        // l'orchestrateur, qui ne porte pas de jeton.
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(roleJwtConverter)));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(originsAutorisees);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}
