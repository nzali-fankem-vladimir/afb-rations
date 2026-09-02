package cm.afrilandfirstbank.rations.workflow.infrastructure.transmission;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import cm.afrilandfirstbank.rations.workflow.application.ResultatDemandeTransmission;
import cm.afrilandfirstbank.rations.workflow.application.ResultatDemandeTransmission.EchecApresTentative;
import cm.afrilandfirstbank.rations.workflow.application.ResultatDemandeTransmission.EchecAvantPublication;
import cm.afrilandfirstbank.rations.workflow.application.ResultatDemandeTransmission.Transmise;
import cm.afrilandfirstbank.rations.workflow.application.TransmissionClient;

/**
 * Appelle {@code POST /transmission/processus/{id}}, l'endpoint interne du service
 * Transmission (Sprint 5.1, hors contrat passerelle).
 *
 * <h2>Toute la subtilite est dans la traduction des reponses</h2>
 *
 * <p>Ce client ne se contente pas de distinguer succes et echec : il doit dire si un
 * message a pu partir vers la comptabilite, car c'est cela — et rien d'autre — qui decide
 * si un reessai est permis. Le service Transmission a ete concu pour rendre cette
 * information lisible dans son code d'erreur.
 *
 * <table>
 *   <tr><th>Reponse</th><th>Issue</th><th>Raison</th></tr>
 *   <tr><td>{@code 200}</td><td>{@link Transmise}</td><td>le broker a accuse reception</td></tr>
 *   <tr><td>{@code 503 SERVICE_WORKFLOW_INDISPONIBLE}</td><td>{@link EchecAvantPublication}</td><td>l'en-tete n'a pas pu etre lu : aucun envoi</td></tr>
 *   <tr><td>{@code 503 SERVICE_SAISIE_INDISPONIBLE}</td><td>{@link EchecAvantPublication}</td><td>le detail n'a pas pu etre lu : aucun envoi</td></tr>
 *   <tr><td>connexion refusee / hote injoignable</td><td>{@link EchecAvantPublication}</td><td>la requete n'a jamais atteint le service</td></tr>
 *   <tr><td>{@code 503 PUBLICATION_ECHOUEE}</td><td>{@link EchecApresTentative}</td><td><b>ambigu</b> : l'accuse a pu se perdre apres ecriture</td></tr>
 *   <tr><td>delai de lecture depasse</td><td>{@link EchecApresTentative}</td><td><b>ambigu</b> : ne pas savoir n'est pas savoir que non</td></tr>
 *   <tr><td>tout le reste ({@code 4xx}, {@code 500})</td><td>{@link EchecApresTentative}</td><td>deterministe : un reessai echouerait a l'identique</td></tr>
 * </table>
 *
 * <p><b>Le classement par defaut est le prudent.</b> Un code d'erreur inconnu tombe dans
 * {@link EchecApresTentative}, donc sans reessai : entre risquer un double paiement et
 * risquer un etat non transmis, on choisit celui qui se voit et se repare.
 *
 * <h2>Distinguer une connexion refusee d'un delai depasse</h2>
 *
 * <p>Les deux sont des {@link ResourceAccessException}, et c'est precisement la ou la
 * prudence se joue. Une connexion refusee prouve que la requete n'est jamais partie ; un
 * delai de lecture depasse prouve seulement qu'on n'a pas eu la reponse — la requete, elle,
 * a pu etre traitee entierement. On les separe donc sur la nature de la cause, et l'on
 * classe dans le prudent tout ce qu'on ne sait pas identifier.
 *
 * <h2>Delai de lecture : 20 s, et non 3 s</h2>
 *
 * <p>C'est le seul appel sortant du module qui deroge a la convention 2 s / 3 s
 * (Sprint 3.2), et ce n'est pas un reglage d'environnement : l'appele fait lui-meme trois
 * operations bornees — lecture de l'en-tete (5 s), lecture du detail (5 s), publication
 * (7 s) —, soit <b>17 s au pire</b> par construction. Un delai de 3 s couperait la
 * reponse au moment precis ou elle importe le plus, et transformerait chaque panne en
 * situation ambigue, donc non reessayable. Les 20 s sont une borne defensive que le budget
 * de l'appele rend en principe inatteignable.
 */
@Component
public class TransmissionHttpClient implements TransmissionClient {

    private static final Logger journal = LoggerFactory.getLogger(TransmissionHttpClient.class);

    /** Codes du service Transmission qui attestent qu'aucun envoi n'a eu lieu. */
    private static final String CODE_WORKFLOW_INDISPONIBLE = "SERVICE_WORKFLOW_INDISPONIBLE";
    private static final String CODE_SAISIE_INDISPONIBLE = "SERVICE_SAISIE_INDISPONIBLE";

    private final RestClient clientRest;
    private final String urlServiceTransmission;

    public TransmissionHttpClient(
            @Qualifier("constructeurRestTransmission") RestClient.Builder constructeurRest,
            @Value("${app.transmission.url}") String urlServiceTransmission) {
        this.urlServiceTransmission = urlServiceTransmission;
        this.clientRest = constructeurRest.baseUrl(urlServiceTransmission).build();
    }

    @Override
    public ResultatDemandeTransmission demanderTransmission(Long idProcessus,
            String enteteAutorisation) {

        if (enteteAutorisation == null || enteteAutorisation.isBlank()) {
            // Sans jeton a relayer, rien ne peut partir : aucune identite machine n'existe
            // au realm. C'est un echec anterieur a toute publication, mais qu'un reessai
            // ne resoudra pas — le classement prudent evite une boucle inutile.
            return new EchecApresTentative(
                    "aucun en-tete Authorization a relayer au service Transmission");
        }

        try {
            ReponseTransmission reponse = clientRest.post()
                    .uri("/transmission/processus/{id}", idProcessus)
                    .header(HttpHeaders.AUTHORIZATION, enteteAutorisation)
                    .retrieve()
                    .body(ReponseTransmission.class);

            return interpreter(reponse, idProcessus);

        } catch (RestClientResponseException reponseEnErreur) {
            return interpreterErreur(reponseEnErreur, idProcessus);

        } catch (ResourceAccessException accesImpossible) {
            return interpreterAccesImpossible(accesImpossible, idProcessus);

        } catch (RestClientException panne) {
            journal.warn("Appel de transmission de l'etat {} en echec, cause indeterminee : {}",
                    idProcessus, panne.getMessage());
            return new EchecApresTentative("echec indetermine : " + panne.getMessage());
        }
    }

    // --- Traduction ---------------------------------------------------------------

    private ResultatDemandeTransmission interpreter(ReponseTransmission reponse, Long idProcessus) {
        if (reponse == null || reponse.topic() == null) {
            // Un 200 illisible ne prouve pas qu'il ne s'est rien passe : le service a pu
            // publier et mal rendre sa reponse. Prudent, donc sans reessai.
            journal.warn("Le service Transmission a repondu 200 sans corps exploitable pour "
                    + "l'etat {}. On ignore si l'evenement est parti.", idProcessus);
            return new EchecApresTentative("reponse 200 sans corps exploitable");
        }

        return new Transmise(reponse.topic(), reponse.partition(), reponse.offset(),
                reponse.nombreLignes(), reponse.montantTotal());
    }

    private ResultatDemandeTransmission interpreterErreur(RestClientResponseException erreur,
            Long idProcessus) {

        String corps = erreur.getResponseBodyAsString();
        boolean avantToutEnvoi = erreur.getStatusCode().isSameCodeAs(HttpStatus.SERVICE_UNAVAILABLE)
                && (corps.contains(CODE_WORKFLOW_INDISPONIBLE)
                        || corps.contains(CODE_SAISIE_INDISPONIBLE));

        journal.warn("Le service Transmission a repondu {} pour l'etat {} ({}). Corps : {}",
                erreur.getStatusCode(), idProcessus,
                avantToutEnvoi ? "aucun envoi n'a eu lieu, reessai possible"
                        : "reessai interdit par prudence",
                corps);

        String motif = "reponse " + erreur.getStatusCode() + " : " + corps;
        return avantToutEnvoi ? new EchecAvantPublication(motif) : new EchecApresTentative(motif);
    }

    /**
     * Separe ce qui n'est jamais parti de ce qu'on ne sait pas.
     *
     * <p>Une connexion refusee ou un hote inconnu prouvent que la requete n'a pas atteint
     * le service : rien n'a pu etre publie. Un delai de lecture depasse ne prouve rien —
     * la requete a pu etre traitee de bout en bout et l'evenement partir. Tout ce qui n'est
     * pas identifie comme « jamais parti » est classe prudemment.
     */
    private ResultatDemandeTransmission interpreterAccesImpossible(ResourceAccessException echec,
            Long idProcessus) {

        Throwable cause = echec.getCause();
        boolean jamaisParti = cause instanceof java.net.ConnectException
                || cause instanceof java.net.UnknownHostException;

        if (jamaisParti) {
            journal.warn("Service Transmission injoignable a l'adresse {} pour l'etat {} : {}. "
                            + "Aucun evenement n'est parti, un reessai est sans danger.",
                    urlServiceTransmission, idProcessus, cause.getMessage());
            return new EchecAvantPublication("service Transmission injoignable a "
                    + urlServiceTransmission + " : " + cause.getMessage());
        }

        journal.warn("Aucune reponse du service Transmission pour l'etat {} : {}. On ignore si "
                        + "l'evenement est parti, aucun reessai ne sera tente.",
                idProcessus, echec.getMessage());
        return new EchecApresTentative("aucune reponse du service Transmission : "
                + echec.getMessage());
    }

}
