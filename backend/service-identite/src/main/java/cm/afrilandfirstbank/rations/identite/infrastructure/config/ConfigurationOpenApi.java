package cm.afrilandfirstbank.rations.identite.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Documentation Springdoc du service Identite (Sprint 1.3).
 *
 * <p>La page est ouverte sans jeton (CLAUDE.md section 17, decision Sprint 0.6),
 * mais les routes qu'elle decrit sont toutes protegees. Le schema de securite
 * declare ici fait apparaitre le bouton « Authorize » de Swagger UI : sans lui,
 * tout essai depuis le navigateur repondrait 401, et la documentation ne
 * servirait qu'a lire.
 */
@Configuration
public class ConfigurationOpenApi {

    private static final String SCHEMA_JETON = "jetonKeycloak";

    @Bean
    public OpenAPI openApiIdentite() {
        return new OpenAPI()
                .info(new Info()
                        .title("Service Identite et habilitations")
                        .version("Sprint 1.3")
                        .description("""
                                Projection locale des comptes annuaire, roles applicatifs, code unite
                                et verification d'habilitation.

                                **Authentification.** Le service n'emet aucun jeton et ne stocke aucun
                                mot de passe : il valide les jetons emis par Keycloak (Authorization
                                Code + PKCE). Aucune route de login n'existe ici.

                                **Habilitation.** Un jeton valide ne suffit pas : un profil local doit
                                avoir ete ouvert par un administrateur, sinon la reponse est 403.

                                **Erreurs.** Format uniforme
                                `{ timestamp, status, code, message, path }`.

                                **Refus traces.** Toute tentative d'action hors perimetre produit un
                                evenement d'audit sur `rations.audit.evenement` (CT-04).
                                """))
                .addSecurityItem(new SecurityRequirement().addList(SCHEMA_JETON))
                .components(new Components().addSecuritySchemes(SCHEMA_JETON,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Jeton d'acces emis par le realm Keycloak afb-rations-dev.")));
    }

}
