package cm.afrilandfirstbank.rations.transmission.infrastructure.workflow;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import cm.afrilandfirstbank.rations.transmission.application.ResultatPublication;
import cm.afrilandfirstbank.rations.transmission.application.ResultatVerrou;
import cm.afrilandfirstbank.rations.transmission.application.ResultatVerrou.DejaTransmis;
import cm.afrilandfirstbank.rations.transmission.application.ResultatVerrou.VerrouIndisponible;
import cm.afrilandfirstbank.rations.transmission.application.ResultatVerrou.VerrouTenu;
import cm.afrilandfirstbank.rations.transmission.application.VerrouTransmissionClient;

/**
 * Appelle {@code PUT /processus/{id}/transmission}, l'endpoint interne du verrou
 * d'unicite cote service Workflow (RG-13, Sprint 5.3, hors contrat passerelle).
 *
 * <h2>Par l'API, jamais par la base</h2>
 *
 * <p>Le drapeau {@code transmis_comptabilite} vit sur {@code processus_mensuel}, dans la
 * base du service Workflow. Aucune source de donnees vers {@code rations_workflow}
 * n'existe ici (diagramme AR04) : le verrou se demande, il ne se prend pas de force.
 *
 * <h2>Traduction des reponses</h2>
 *
 * <table>
 *   <tr><td>{@code 200}, resultat {@code RESERVEE} / {@code CONFIRMEE} / {@code LIBEREE}</td><td>{@link VerrouTenu}</td></tr>
 *   <tr><td>{@code 200}, resultat {@code DEJA_TRANSMISE}</td><td>{@link DejaTransmis} — rien ne doit partir</td></tr>
 *   <tr><td>{@code 200}, resultat {@code LIBERATION_REFUSEE}</td><td>{@link VerrouIndisponible} — la reservation reste posee</td></tr>
 *   <tr><td>tout le reste</td><td>{@link VerrouIndisponible} — refus conservateur</td></tr>
 * </table>
 *
 * <p><b>Le classement par defaut est le refus.</b> Un code inconnu, un corps illisible,
 * une panne reseau : aucun verrou n'est repute pose, donc rien ne part. C'est la doctrine
 * du refus conservateur du Sprint 1.3, appliquee a la seule operation irreversible du
 * module.
 *
 * <p>Le jeton de l'utilisateur final est relaye tel quel (doctrine Sprint 1.3) : aucune
 * identite machine n'existe au realm, et cet appel-ci nait bien d'une requete HTTP
 * portant un jeton — contrairement a celui de l'accuse comptable, ne d'un message Kafka.
 *
 * <p>Delais 2 s / 3 s, aucun reessai — bornes du {@code RestClient.Builder} du service.
 * Un reessai serait de surcroit inutile : une reservation qui echoue n'a rien reserve.
 */
@Component
public class VerrouTransmissionHttpClient implements VerrouTransmissionClient {

    private static final Logger journal =
            LoggerFactory.getLogger(VerrouTransmissionHttpClient.class);

    /** Valeur de {@code resultat} qui interdit toute publication. */
    private static final String DEJA_TRANSMISE = "DEJA_TRANSMISE";

    /** Valeur de {@code resultat} qui signale une liberation refusee. */
    private static final String LIBERATION_REFUSEE = "LIBERATION_REFUSEE";

    private final RestClient clientRest;
    private final String urlServiceWorkflow;

    public VerrouTransmissionHttpClient(RestClient.Builder constructeurRest,
            @Value("${app.workflow.url}") String urlServiceWorkflow) {
        this.urlServiceWorkflow = urlServiceWorkflow;
        this.clientRest = constructeurRest.baseUrl(urlServiceWorkflow).build();
    }

    @Override
    public ResultatVerrou reserver(Long idProcessus, String enteteAutorisation) {
        return appeler(idProcessus, Map.of("etape", "RESERVATION"), enteteAutorisation,
                "reservation");
    }

    @Override
    public ResultatVerrou confirmer(Long idProcessus, ResultatPublication.Publiee accuse,
            int nombreLignes, long montantTotal, String enteteAutorisation) {

        return appeler(idProcessus, Map.of(
                        "etape", "CONFIRMATION",
                        "topic", accuse.topic(),
                        "partition", accuse.partition(),
                        "offset", accuse.offset(),
                        "nombreLignes", nombreLignes,
                        "montantTotal", montantTotal),
                enteteAutorisation, "confirmation");
    }

    @Override
    public ResultatVerrou liberer(Long idProcessus, String motif, String enteteAutorisation) {
        return appeler(idProcessus, Map.of(
                        "etape", "LIBERATION",
                        "motif", motif == null ? "motif non precise" : motif),
                enteteAutorisation, "liberation");
    }

    // --- Appel --------------------------------------------------------------------

    private ResultatVerrou appeler(Long idProcessus, Map<String, Object> corps,
            String enteteAutorisation, String geste) {

        if (enteteAutorisation == null || enteteAutorisation.isBlank()) {
            return new VerrouIndisponible(
                    "aucun en-tete Authorization a relayer au service Workflow pour le geste "
                            + geste + " du verrou");
        }

        try {
            ReponseVerrou reponse = clientRest.put()
                    .uri("/processus/{id}/transmission", idProcessus)
                    .header(HttpHeaders.AUTHORIZATION, enteteAutorisation)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corps)
                    .retrieve()
                    .body(ReponseVerrou.class);

            return interpreter(reponse, idProcessus, geste);

        } catch (RestClientResponseException reponseEnErreur) {
            journal.warn("Le service Workflow a repondu {} au geste {} du verrou pour l'etat {}. "
                            + "Corps : {}",
                    reponseEnErreur.getStatusCode(), geste, idProcessus,
                    reponseEnErreur.getResponseBodyAsString());
            return new VerrouIndisponible("reponse " + reponseEnErreur.getStatusCode()
                    + " au geste " + geste + " : " + reponseEnErreur.getResponseBodyAsString());

        } catch (RestClientException panne) {
            journal.warn("Service Workflow injoignable a l'adresse {} pour le geste {} du verrou "
                    + "de l'etat {} : {}", urlServiceWorkflow, geste, idProcessus,
                    panne.getMessage());
            return new VerrouIndisponible("appel a " + urlServiceWorkflow + " en echec au geste "
                    + geste + " : " + panne.getMessage());
        }
    }

    private ResultatVerrou interpreter(ReponseVerrou reponse, Long idProcessus, String geste) {
        if (reponse == null || reponse.resultat() == null) {
            journal.warn("Le service Workflow a repondu 200 sans resultat exploitable au geste {} "
                    + "du verrou pour l'etat {}.", geste, idProcessus);
            return new VerrouIndisponible("reponse 200 sans resultat exploitable au geste " + geste);
        }

        if (DEJA_TRANSMISE.equals(reponse.resultat())) {
            return new DejaTransmis(reponse.message());
        }

        if (LIBERATION_REFUSEE.equals(reponse.resultat())) {
            // La comptabilite a repondu : l'evenement etait bien parti, la reservation
            // reste posee. Ce n'est pas une panne, mais ce n'est pas non plus un verrou
            // tenu — et l'appelant n'a rien a en faire d'autre que le tracer.
            return new VerrouIndisponible(reponse.message());
        }

        return new VerrouTenu(reponse.message());
    }

    /**
     * Corps rendu par l'endpoint du verrou.
     *
     * <p><b>Tolerant reader</b> : le service Workflow peut enrichir sa reponse sans casser
     * celle-ci. Seul {@code resultat} est lu pour decider — le reste sert le journal.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ReponseVerrou(Long idProcessus, String resultat, String message) {
    }

}
