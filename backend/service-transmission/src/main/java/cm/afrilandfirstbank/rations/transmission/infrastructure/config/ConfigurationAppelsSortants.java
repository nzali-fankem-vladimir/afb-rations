package cm.afrilandfirstbank.rations.transmission.infrastructure.config;

import java.time.Duration;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Bornes de temps des appels HTTP sortants du service Transmission : vers le service
 * Workflow (en-tete du processus) et vers le service Saisie (detail consolide).
 *
 * <p>2 s de connexion, 3 s de lecture, <b>aucun reessai</b>. Ce sont les valeurs des
 * services Grilles (Sprint 2.2), Saisie (Sprint 3.2) et Workflow (Sprint 4.1) : une
 * seule convention d'appel sortant dans le module, plutot que quatre valeurs proches
 * qu'un lecteur prendrait pour des differences intentionnelles. Voir
 * {@code docs/decisions/2026-08-31-delais-et-reessai-des-appels-sortants.md}.
 *
 * <p>Ces bornes valent dans <b>tous les profils</b> : c'est une doctrine d'appel, pas un
 * reglage d'environnement. Seules les adresses ({@code app.workflow.url},
 * {@code app.saisie.url}) varient selon l'environnement.
 *
 * <p><b>Aucun reessai au niveau du transport.</b> Un echec de lecture refuse la
 * transmission immediatement, et la suite est decidee par le service applicatif, qui
 * sait, lui, ce qu'un echec veut dire ici : un etat cloture non transmis, a signaler.
 * Un reessai muet a ce niveau masquerait une degradation au lieu de la rendre visible.
 *
 * <p><b>Portee prototype</b> : un {@code RestClient.Builder} est mutable et chaque
 * client y pose sa propre {@code baseUrl}. Un bean singleton ferait que le second client
 * ecraserait l'adresse du premier — panne silencieuse et deroutante. Meme precaution
 * qu'aux Sprints 3.2 et 4.1.
 *
 * <p>Le constructeur de requetes est fourni explicitement plutot que par
 * {@code spring.http.client.*} : Spring Boot 4 a scinde ses modules et
 * l'auto-configuration de {@code RestClient.Builder} n'est entrainee par aucun starter
 * de ce service — ces proprietes y seraient du texte mort.
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
