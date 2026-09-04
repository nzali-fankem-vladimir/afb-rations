package cm.afrilandfirstbank.rations.reporting.infrastructure.config;

import java.time.Duration;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Bornes de temps de <b>tous</b> les appels HTTP sortants du service Reporting :
 * vers le service Workflow, le service Saisie et le service Identite.
 *
 * <p>2 s de connexion, 5 s de lecture, <b>aucun reessai</b>. La connexion reprend
 * la convention du module (Sprints 2.2, 3.2, 4.x). La <b>lecture deroge</b> aux 3 s
 * habituelles, et par arithmetique, non par confort : les appeles resolvent
 * eux-memes la portee d'acces aupres du service Identite avant de repondre. Une
 * recherche cote Workflow enchaine donc un appel Identite (3 s au pire) puis deux
 * requetes SQL ; un delai de lecture de 3 s couperait la reponse au moment ou elle
 * arrive. C'est le meme raisonnement qu'au Sprint 5.1, ou le client vers le service
 * Transmission a recu 20 s parce que l'appele enchainait trois operations bornees.
 *
 * <p><b>Le pire cas d'une recherche est donc de 10 s</b> — Workflow 5 s, Saisie
 * 5 s —, et de 10 s pour un historique — Workflow 5 s, Identite 5 s. Le cas
 * nominal se compte en dizaines de millisecondes, et la cible du document maitre
 * porte sur la moyenne des operations courantes.
 *
 * <p><b>Aucun reessai.</b> Doctrine du Sprint 3.2 : reessayer doublerait l'attente
 * avant le meme refus, et masquerait une degradation au lieu de la rendre visible.
 * Le reessai borne du Sprint 5.1 etait l'exception, justifiee par un risque de
 * double paiement ; une lecture n'engage rien.
 *
 * <p><b>Portee prototype</b> : un {@code RestClient.Builder} est mutable et chaque
 * client y pose sa propre {@code baseUrl}. Un bean singleton ferait que le dernier
 * client construit ecraserait l'adresse des precedents — panne silencieuse et
 * deroutante, deja evitee ainsi aux Sprints 3.2 et 4.1.
 *
 * <p>Le constructeur est fourni explicitement plutot que par
 * {@code spring.http.client.*} : Spring Boot 4 a scinde ses modules et
 * l'auto-configuration de {@code RestClient.Builder} n'est entrainee par aucun
 * starter de ce service — ces proprietes y seraient du texte mort. Meme constat
 * qu'au Sprint 5.1 pour le bean {@code ObjectMapper}, absent lui aussi.
 */
@Configuration
public class ConfigurationAppelsSortants {

    /** Delai d'etablissement de la connexion TCP. */
    static final Duration DELAI_CONNEXION = Duration.ofSeconds(2);

    /** Delai d'attente de la reponse, une fois la connexion etablie. */
    static final Duration DELAI_LECTURE = Duration.ofSeconds(5);

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public RestClient.Builder constructeurRestSortant() {
        SimpleClientHttpRequestFactory fabrique = new SimpleClientHttpRequestFactory();
        fabrique.setConnectTimeout(DELAI_CONNEXION);
        fabrique.setReadTimeout(DELAI_LECTURE);

        return RestClient.builder().requestFactory(fabrique);
    }

}
