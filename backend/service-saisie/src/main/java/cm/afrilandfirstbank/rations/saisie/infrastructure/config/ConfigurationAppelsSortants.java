package cm.afrilandfirstbank.rations.saisie.infrastructure.config;

import java.time.Duration;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Bornes de temps de <b>tous</b> les appels HTTP sortants du service Saisie.
 *
 * <p>Decision Sprint 3.2, consignee dans
 * {@code docs/decisions/2026-08-31-delais-et-reessai-des-appels-sortants.md}.
 *
 * <h2>Les valeurs, et pourquoi celles-la</h2>
 *
 * <p>2 s de connexion, 3 s de lecture : exactement celles deja retenues pour
 * {@code ClientIdentite} du service Grilles (Sprint 2.2). Une seule convention
 * d'appel sortant pour tout le module vaut mieux que deux valeurs proches qu'il
 * faudrait ensuite justifier separement. 3 s de lecture correspond a la cible de
 * temps de reponse du document maitre. Sans borne explicite, le comportement par
 * defaut de la JVM laisserait un agent devant un ecran fige pendant une minute
 * avant d'apprendre que sa ligne n'a pas ete enregistree.
 *
 * <p>Ces bornes valent dans <b>tous les profils</b> : ce sont une doctrine
 * d'appel, pas un reglage d'environnement. Seule l'adresse du service appele
 * ({@code app.grilles.url}) varie selon l'environnement.
 *
 * <h2>Aucun reessai</h2>
 *
 * <p><b>Un appel, un verdict.</b> Un echec refuse la ligne immediatement, sans
 * second essai. Trois raisons :
 *
 * <ol>
 *   <li>{@code ClientIdentite} ne reessaie pas non plus : une seule doctrine
 *       d'appel sortant dans le module, plutot qu'une regle par client.</li>
 *   <li>Le Sprint 3.1 a acte <b>trois dependances synchrones empilees par ligne
 *       saisie</b> (Grilles, Workflow, Identite). Un reessai sur chacune ferait
 *       passer le pire cas de 9 a 18 secondes, loin de la cible de 3 s.</li>
 *   <li>L'agent est <b>devant son ecran</b> : il resaisit lui-meme, et son geste
 *       est deja le reessai. Un reessai automatique n'ajouterait qu'une chose —
 *       masquer une degradation du service Grilles au lieu de la rendre
 *       visible.</li>
 * </ol>
 *
 * <p>Le refus reste conservateur : aucune ligne n'est enregistree sans montant
 * connu (RG-03, {@code docs/appel-resolution-montant.md} section 2).
 *
 * <h2>Pourquoi une classe de configuration, et non {@code spring.http.client.*}</h2>
 *
 * <p>Spring Boot 4 a scinde ses modules : l'auto-configuration de
 * {@code RestClient.Builder} et les proprietes {@code spring.http.client.*}
 * vivent dans {@code spring-boot-restclient} / {@code spring-boot-http-client},
 * qu'aucun starter de ce service n'entraine. Il n'existe donc <b>aucun</b> bean
 * {@code RestClient.Builder} par defaut ici, et ces proprietes seraient lues
 * comme du texte mort. Le constructeur est fourni explicitement plutot que
 * d'ajouter deux dependances pour retrouver un reglage que trois lignes
 * expriment.
 *
 * <h2>Pourquoi un bean injecte, et non un constructeur cree dans le client</h2>
 *
 * <p>{@code ClientIdentite} (service Grilles) construit son {@code RestClient}
 * en dur. Ce service ne le peut pas : {@code MockRestServiceServer} ne peut
 * s'attacher qu'a un {@code RestClient.Builder} qu'on lui passe. Contrainte
 * relevee des le Sprint 3.1 ({@code docs/rattachement-processus.md} section 6),
 * pour que les clients sortants restent testables sans reseau.
 */
@Configuration
public class ConfigurationAppelsSortants {

    /** Delai d'etablissement de la connexion TCP. */
    static final Duration DELAI_CONNEXION = Duration.ofSeconds(2);

    /** Delai d'attente de la reponse, une fois la connexion etablie. */
    static final Duration DELAI_LECTURE = Duration.ofSeconds(3);

    /**
     * Constructeur de client REST borne en temps, partage par les clients
     * sortants du service.
     *
     * <p><b>Portee prototype</b> : un {@code RestClient.Builder} est mutable, et
     * chaque client y pose sa propre {@code baseUrl}. Un bean singleton ferait
     * que le deuxieme client ecraserait l'adresse du premier — le jour ou
     * {@code ClientWorkflow} rejoindra {@code ResolutionMontantHttpClient}, la
     * panne serait silencieuse et deroutante.
     */
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public RestClient.Builder constructeurRestSortant() {
        SimpleClientHttpRequestFactory fabrique = new SimpleClientHttpRequestFactory();
        fabrique.setConnectTimeout(DELAI_CONNEXION);
        fabrique.setReadTimeout(DELAI_LECTURE);

        return RestClient.builder().requestFactory(fabrique);
    }

}
