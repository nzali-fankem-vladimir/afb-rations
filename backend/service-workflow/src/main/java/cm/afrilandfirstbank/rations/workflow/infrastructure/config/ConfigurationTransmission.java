package cm.afrilandfirstbank.rations.workflow.infrastructure.config;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

/**
 * Ce qui protege le service Workflow d'une panne de la chaine de transmission.
 *
 * <p>Deux beans, deux problemes distincts : un constructeur HTTP au delai adapte a un
 * appele qui travaille longtemps, et un <b>pool d'execution dedie</b> qui borne le nombre
 * de fils HTTP qu'une panne peut immobiliser.
 *
 * <h2>Pourquoi un pool dedie, alors que le valideur attend le resultat</h2>
 *
 * <p>Le valideur attend, c'est l'arbitrage du Sprint 5.1 : aucune reprise automatique
 * n'etant possible — le realm n'a pas de compte de service —, la reponse a sa validation
 * est le <b>seul</b> moment ou un humain apprend qu'un etat n'est pas parti.
 *
 * <p>Mais si chaque cloture faisait le travail sur son propre fil HTTP, une panne de Kafka
 * en fin de mois immobiliserait autant de fils de Tomcat qu'il y a de clotures
 * simultanees, et ralentirait des requetes sans aucun rapport — une consultation, une
 * saisie. Le pool dedie donne une garantie chiffrable : <b>au plus
 * {@value #FILS_DE_TRANSMISSION} fils bloques a la fois</b>, quoi qu'il arrive en aval.
 *
 * <h2>File de capacite NULLE, deliberement</h2>
 *
 * <p>Une file aggraverait le probleme au lieu de le regler. Avec quatre fils occupes 17 s
 * chacun et cinquante taches en attente, la derniere patienterait plus de trois minutes —
 * et son fil HTTP avec elle. La file nulle fait l'inverse : au-dela de quatre clotures
 * simultanees en panne, les suivantes sont <b>refusees immediatement</b>, la validation
 * repond aussitot « non transmis », et l'etat part en reprise. C'est le principe deja pose
 * pour l'audit au Sprint 1.3 : refuser plutot que faire attendre pour rien.
 *
 * <p>En fonctionnement normal, une transmission dure environ 150 ms : quatre fils suffisent
 * a plusieurs dizaines de clotures par seconde, ce qu'un module traitant une cinquantaine
 * d'unites par mois n'approchera jamais. Le pool ne devient limitant que pendant une panne
 * reelle — c'est-a-dire exactement quand on veut qu'il le devienne.
 *
 * <h2>Le rejet journalise puis leve, et surtout ne s'execute pas sur l'appelant</h2>
 *
 * <p>{@code CallerRunsPolicy} est exclue : elle ferait executer la transmission sur le fil
 * de la requete, c'est-a-dire rouvrirait par la fenetre la porte que ce pool ferme.
 *
 * <p>Le handler pose ici <b>journalise au prefixe {@value #PREFIXE_POOL_SATURE}, puis
 * leve</b>. La levee n'est pas un oubli : c'est le seul moyen que l'appelant apprenne le
 * rejet <i>immediatement</i>. Un handler muet laisserait la tache disparaitre sans que
 * personne ne l'attende jamais — le fil appelant patienterait alors jusqu'a sa borne
 * defensive, et la file nulle aurait produit exactement l'attente inutile qu'elle etait
 * censee supprimer. L'exception est rattrapee sur place par
 * {@code DeclenchementTransmission}, qui la traduit en « non transmis » : elle ne remonte
 * jamais au valideur, dont la validation est acquise.
 *
 * <p>C'est la difference avec le pool d'audit du Sprint 1.3, dont le handler ne leve
 * jamais : la, personne n'attend le resultat, et une levee serait remontee a travers le
 * commit d'une operation metier. Ici, quelqu'un attend, et doit savoir.
 *
 * <p>Le prefixe est distinct de {@code TRANSMISSION MANQUEE} a dessein : en supervision,
 * « Kafka est en panne » et « le pool a ete sature par un pic de fin de mois » appellent
 * deux reactions differentes, et se confondraient sous un prefixe unique.
 */
@Configuration
public class ConfigurationTransmission {

    private static final Logger journal = LoggerFactory.getLogger(ConfigurationTransmission.class);

    /**
     * Prefixe reperable en supervision pour un rejet par saturation du pool, distinct de
     * {@code TRANSMISSION MANQUEE} (panne de la chaine) et de {@code AUDIT PERDU}
     * (saturation du pool d'audit, Sprint 1.3).
     */
    public static final String PREFIXE_POOL_SATURE = "TRANSMISSION REJETEE POOL SATURE";

    /** Transmissions simultanees admises. Voir la justification en tete de classe. */
    public static final int FILS_DE_TRANSMISSION = 4;

    /** Delai d'etablissement de la connexion, valeur commune du module (Sprint 3.2). */
    static final Duration DELAI_CONNEXION = Duration.ofSeconds(2);

    /**
     * Delai de lecture, <b>35 s</b> : la seule derogation du module a la convention de 3 s.
     *
     * <p>Ce n'est pas un reglage d'environnement mais une consequence arithmetique. L'appele
     * enchaine cinq operations bornees, et le verrou de RG-13 en a ajoute deux au
     * Sprint 5.3 :
     *
     * <table>
     *   <caption>Budget de l'appele, pire cas par construction</caption>
     *   <tr><td>lecture de l'en-tete au service Workflow</td><td>5 s</td></tr>
     *   <tr><td>lecture du detail au service Saisie</td><td>5 s</td></tr>
     *   <tr><td><b>reservation du verrou</b> (Sprint 5.3)</td><td>5 s</td></tr>
     *   <tr><td>publication et attente de l'accuse du broker</td><td>7 s</td></tr>
     *   <tr><td><b>confirmation du verrou</b> (Sprint 5.3)</td><td>5 s</td></tr>
     *   <tr><td><b>total</b></td><td><b>27 s</b></td></tr>
     * </table>
     *
     * <p>Les 20 s du Sprint 5.1 seraient donc devenues trop courtes : elles auraient coupe
     * la reponse au moment ou elle importe le plus — apres la publication, avant la
     * confirmation —, transformant en situation <b>ambigue</b>, donc non reessayable, une
     * panne qui etait peut-etre parfaitement claire. Les 35 s sont une borne defensive que
     * le budget de l'appele rend en principe inatteignable.
     *
     * <p>Cas nominal inchange : environ 200 ms, les deux appels du verrou etant deux
     * ecritures sur une ligne indexee.
     */
    static final Duration DELAI_LECTURE = Duration.ofSeconds(35);

    /**
     * Constructeur HTTP reserve a l'appel de transmission, qualifie par son nom de bean.
     *
     * <p>Portee prototype, comme le constructeur par defaut : un {@code RestClient.Builder}
     * est mutable et chaque client y pose sa propre {@code baseUrl}. Le constructeur par
     * defaut porte {@code @Primary} depuis ce sous-sprint, pour que l'arrivee de ce
     * second bean ne rende pas ambigue l'injection des clients ecrits avant lui.
     */
    @Bean("constructeurRestTransmission")
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public RestClient.Builder constructeurRestTransmission() {
        SimpleClientHttpRequestFactory fabrique = new SimpleClientHttpRequestFactory();
        fabrique.setConnectTimeout(DELAI_CONNEXION);
        fabrique.setReadTimeout(DELAI_LECTURE);

        return RestClient.builder().requestFactory(fabrique);
    }

    @Bean("executeurTransmission")
    public ThreadPoolTaskExecutor executeurTransmission() {
        RejectedExecutionHandler rejetJournalise = (tache, executeur) -> {
            journal.warn("{} : les {} fils de transmission sont occupes, cette cloture n'est pas "
                            + "transmise pour l'instant. La cloture, elle, est acquise ; l'etat "
                            + "reste a reprendre.",
                    PREFIXE_POOL_SATURE, FILS_DE_TRANSMISSION);
            // On leve pour que l'appelant l'apprenne tout de suite plutot que d'attendre un
            // resultat qui ne viendra jamais. DeclenchementTransmission la rattrape.
            throw new RejectedExecutionException(
                    "Les " + FILS_DE_TRANSMISSION + " fils de transmission sont occupes.");
        };

        ThreadPoolTaskExecutor executeur = new ThreadPoolTaskExecutor();
        executeur.setCorePoolSize(FILS_DE_TRANSMISSION);
        executeur.setMaxPoolSize(FILS_DE_TRANSMISSION);
        // Capacite nulle : ThreadPoolTaskExecutor bascule alors sur une file synchrone,
        // qui rejette des que les quatre fils travaillent. On refuse plutot que
        // d'empiler -- ce n'est pas un oubli de dimensionnement, voir en tete de classe.
        executeur.setQueueCapacity(0);
        executeur.setThreadNamePrefix("transmission-");
        executeur.setRejectedExecutionHandler(rejetJournalise);
        // A l'arret, on laisse aboutir les transmissions en cours : quelques secondes
        // suffisent, et une transmission coupee en plein vol serait exactement la situation
        // ambigue que l'on cherche a eviter partout ailleurs.
        executeur.setWaitForTasksToCompleteOnShutdown(true);
        executeur.setAwaitTerminationSeconds(20);
        executeur.initialize();
        return executeur;
    }

}
