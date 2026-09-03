package cm.afrilandfirstbank.rations.workflow.infrastructure.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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

    /**
     * Chemin de l'endpoint interne de mise a jour du statut d'integration (Sprint 5.2).
     * Un seul chemin, nomme ici et nulle part ailleurs : sa portee doit se lire d'un coup
     * d'oeil.
     */
    static final String CHEMIN_INTEGRATION = "/processus/*/integration";

    private final List<String> originsAutorisees;
    private final String cleInterne;

    public SecurityConfig(@Value("${app.cors.allowed-origins}") List<String> originsAutorisees,
            @Value("${app.integration.cle-interne}") String cleInterne) {
        this.originsAutorisees = originsAutorisees;
        this.cleInterne = cleInterne;
    }

    /**
     * Chaine dediee a l'endpoint interne {@code PUT /processus/{id}/integration}
     * (Sprint 5.2).
     *
     * <h2>Pourquoi une chaine a part, et non une regle de plus dans la chaine principale</h2>
     *
     * <p>Cet endpoint <b>ne passe pas par OAuth2</b> : il est appele par le service
     * Transmission a la reception d'un accuse comptable, c'est-a-dire depuis un message
     * Kafka, ou aucun utilisateur n'existe et aucun jeton n'est donc relayable (doctrine
     * Sprint 1.3, qui suppose un utilisateur final). Le realm ne porte par ailleurs aucun
     * compte de service. Il est protege par un <b>secret partage</b>, dispositif provisoire
     * arbitre au Sprint 5.2.
     *
     * <p>Une chaine separee, plutot qu'une exception dans la chaine principale, pour que
     * cette difference soit <b>visible dans la structure</b> : les six endpoints du contrat
     * restent tous protoges par OAuth2 sans exception a lire entre les lignes, et
     * l'endpoint interne porte sa propre regle a cote de sa propre justification.
     *
     * <p>{@link Order} en tete : sans cela, la chaine principale, qui accepte toutes les
     * requetes, capterait celle-ci avant que celle-la ne soit consultee.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain chaineIntegrationInterne(HttpSecurity http) throws Exception {
        http
                .securityMatcher(CHEMIN_INTEGRATION)
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Le controle d'acces est entierement porte par le filtre du secret
                // partage : Spring Security n'a ici aucun jeton a valider.
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(new FiltreCleInterne(cleInterne),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
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
