package cm.afrilandfirstbank.rations.transmission.infrastructure.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import cm.afrilandfirstbank.rations.transmission.application.IntegrationProcessus;
import cm.afrilandfirstbank.rations.transmission.application.IntegrationProcessusClient;
import cm.afrilandfirstbank.rations.transmission.application.ResultatIntegrationProcessus;
import cm.afrilandfirstbank.rations.transmission.application.ResultatIntegrationProcessus.IntegrationObtenue;
import cm.afrilandfirstbank.rations.transmission.application.ResultatIntegrationProcessus.ProcessusInconnu;
import cm.afrilandfirstbank.rations.transmission.application.ResultatIntegrationProcessus.ServiceWorkflowIndisponible;
import cm.afrilandfirstbank.rations.transmission.domaine.exception.AccesHorsPorteeException;

/**
 * Interroge {@code GET /processus/{id}/integration}, l'endpoint interne du service
 * Workflow qui rend le bloc d'integration comptable (Sprint 5.3).
 *
 * <h2>Les refus d'acces sont relayes, pas absorbes</h2>
 *
 * <table>
 *   <tr><td>{@code 200}</td><td>{@link IntegrationObtenue}</td></tr>
 *   <tr><td>{@code 403 UTILISATEUR_NON_HABILITE}</td><td>{@link AccesHorsPorteeException} — hors portee</td></tr>
 *   <tr><td>{@code 403} autre</td><td>{@link AccessDeniedException} — role insuffisant</td></tr>
 *   <tr><td>{@code 404}</td><td>{@link ProcessusInconnu}</td></tr>
 *   <tr><td>tout le reste</td><td>{@link ServiceWorkflowIndisponible}</td></tr>
 * </table>
 *
 * <p>Le {@code 403} traverse jusqu'au client parce qu'il <b>dit quoi faire</b> : demander
 * une habilitation, ou s'adresser a quelqu'un d'autre. Le transformer en « service
 * indisponible » enverrait signaler une panne qui n'existe pas. Les deux exceptions sont
 * traduites et <b>tracees en audit</b> par le gestionnaire d'erreurs (CT-04), qui est le
 * point unique de convergence des refus de ce service.
 *
 * <p>Delais 2 s / 3 s, aucun reessai — bornes du {@code RestClient.Builder} du service.
 */
@Component
public class IntegrationProcessusHttpClient implements IntegrationProcessusClient {

    private static final Logger journal =
            LoggerFactory.getLogger(IntegrationProcessusHttpClient.class);

    /** Code du contrat designant un refus de portee, distinct d'un refus de role. */
    private static final String CODE_HORS_PORTEE = "UTILISATEUR_NON_HABILITE";

    private final RestClient clientRest;
    private final String urlServiceWorkflow;

    public IntegrationProcessusHttpClient(RestClient.Builder constructeurRest,
            @Value("${app.workflow.url}") String urlServiceWorkflow) {
        this.urlServiceWorkflow = urlServiceWorkflow;
        this.clientRest = constructeurRest.baseUrl(urlServiceWorkflow).build();
    }

    @Override
    public ResultatIntegrationProcessus obtenir(Long idProcessus, String enteteAutorisation) {
        if (enteteAutorisation == null || enteteAutorisation.isBlank()) {
            throw new IllegalStateException(
                    "Aucun en-tete Authorization a relayer au service Workflow : l'appelant doit "
                            + "propager le jeton de l'utilisateur final.");
        }

        try {
            IntegrationProcessus integration = clientRest.get()
                    .uri("/processus/{id}/integration", idProcessus)
                    .header(HttpHeaders.AUTHORIZATION, enteteAutorisation)
                    .retrieve()
                    .body(IntegrationProcessus.class);

            if (integration == null) {
                journal.warn("Le service Workflow a repondu 200 sans corps exploitable pour "
                        + "l'integration du processus {}.", idProcessus);
                return new ServiceWorkflowIndisponible("reponse 200 sans corps exploitable");
            }
            return new IntegrationObtenue(integration);

        } catch (RestClientResponseException reponseEnErreur) {
            return interpreterErreur(reponseEnErreur, idProcessus);

        } catch (RestClientException panne) {
            journal.warn("Service Workflow injoignable a l'adresse {} pour l'integration du "
                    + "processus {} : {}", urlServiceWorkflow, idProcessus, panne.getMessage());
            return new ServiceWorkflowIndisponible(
                    "appel a " + urlServiceWorkflow + " en echec : " + panne.getMessage());
        }
    }

    private ResultatIntegrationProcessus interpreterErreur(RestClientResponseException erreur,
            Long idProcessus) {

        if (erreur.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return new ProcessusInconnu(idProcessus);
        }

        if (erreur.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN)) {
            String corps = erreur.getResponseBodyAsString();
            if (corps.contains(CODE_HORS_PORTEE)) {
                throw new AccesHorsPorteeException(
                        "Vous n'avez pas de portee d'acces sur l'unite du processus " + idProcessus
                                + " : son statut d'integration comptable ne peut pas vous etre "
                                + "communique.");
            }
            throw new AccessDeniedException(
                    "Le role de l'utilisateur ne permet pas de consulter le statut d'integration "
                            + "du processus " + idProcessus + ".");
        }

        journal.warn("Le service Workflow a repondu {} pour l'integration du processus {}. "
                        + "Corps : {}",
                erreur.getStatusCode(), idProcessus, erreur.getResponseBodyAsString());
        return new ServiceWorkflowIndisponible("reponse " + erreur.getStatusCode());
    }

}
