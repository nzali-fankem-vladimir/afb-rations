package cm.afrilandfirstbank.rations.workflow.infrastructure.config;

import java.time.Duration;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Bornes de temps de <b>tous</b> les appels HTTP sortants du service Workflow :
 * vers le service Identite (habilitation) et vers le service Saisie
 * (consolidation).
 *
 * <p>2 s de connexion, 3 s de lecture, <b>aucun reessai</b>. Ce sont les valeurs
 * du service Grilles (Sprint 2.2) et du service Saisie (Sprint 3.2) : une seule
 * convention d'appel sortant dans le module, plutot que trois valeurs proches
 * qu'un lecteur prendrait pour des differences intentionnelles. Voir
 * {@code docs/decisions/2026-08-31-delais-et-reessai-des-appels-sortants.md}.
 *
 * <p>Ces bornes valent dans <b>tous les profils</b> : c'est une doctrine d'appel,
 * pas un reglage d'environnement. Seules les adresses ({@code app.identite.url},
 * {@code app.saisie.url}) varient selon l'environnement.
 *
 * <p><b>Aucun reessai.</b> Un echec refuse l'operation immediatement. Le refus
 * conservateur est la regle du module (Sprint 1.3) : reessayer ne ferait que
 * doubler le temps d'attente avant le meme refus, et masquerait une degradation
 * au lieu de la rendre visible.
 *
 * <p><b>Portee prototype</b> : un {@code RestClient.Builder} est mutable et
 * chaque client y pose sa propre {@code baseUrl}. Un bean singleton ferait que le
 * client de consolidation ecraserait l'adresse du client d'habilitation — panne
 * silencieuse et deroutante. Meme precaution qu'au Sprint 3.2.
 *
 * <p>Le constructeur est fourni explicitement plutot que par
 * {@code spring.http.client.*} : Spring Boot 4 a scinde ses modules et
 * l'auto-configuration de {@code RestClient.Builder} n'est entrainee par aucun
 * starter de ce service — ces proprietes y seraient du texte mort.
 */
@Configuration
public class ConfigurationAppelsSortants {

    /** Delai d'etablissement de la connexion TCP. */
    static final Duration DELAI_CONNEXION = Duration.ofSeconds(2);

    /** Delai d'attente de la reponse, une fois la connexion etablie. */
    static final Duration DELAI_LECTURE = Duration.ofSeconds(3);

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public RestClient.Builder constructeurRestSortant() {
        SimpleClientHttpRequestFactory fabrique = new SimpleClientHttpRequestFactory();
        fabrique.setConnectTimeout(DELAI_CONNEXION);
        fabrique.setReadTimeout(DELAI_LECTURE);

        return RestClient.builder().requestFactory(fabrique);
    }

}
