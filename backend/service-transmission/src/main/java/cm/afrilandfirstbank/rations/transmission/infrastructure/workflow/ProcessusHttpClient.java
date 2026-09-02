package cm.afrilandfirstbank.rations.transmission.infrastructure.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import cm.afrilandfirstbank.rations.transmission.application.EnTeteProcessus;
import cm.afrilandfirstbank.rations.transmission.application.ProcessusClient;
import cm.afrilandfirstbank.rations.transmission.application.ResultatProcessus;
import cm.afrilandfirstbank.rations.transmission.application.ResultatProcessus.ProcessusIntrouvable;
import cm.afrilandfirstbank.rations.transmission.application.ResultatProcessus.ProcessusObtenu;
import cm.afrilandfirstbank.rations.transmission.application.ResultatProcessus.ServiceWorkflowIndisponible;

/**
 * Interroge {@code GET /processus/{id}} du service Workflow, qui detient
 * {@code processus_mensuel} (contrat d'API section 5).
 *
 * <p><b>Par l'API, jamais par la base.</b> Aucune source de donnees vers
 * {@code rations_workflow} n'existe dans ce service, conformement au diagramme AR04 ;
 * la cartographie le verifie (critere du guide 5.1 section 9).
 *
 * <h2>Traduction des reponses</h2>
 *
 * <table>
 *   <tr><td>{@code 200} exploitable</td><td>{@link ProcessusObtenu}</td></tr>
 *   <tr><td>{@code 404}</td><td>{@link ProcessusIntrouvable} — un appel fautif, pas une panne</td></tr>
 *   <tr><td>tout le reste</td><td>{@link ServiceWorkflowIndisponible} — refus conservateur</td></tr>
 * </table>
 *
 * <p>Le {@code 404} est distingue a dessein. Le confondre avec une panne enverrait
 * chercher un incident d'infrastructure la ou il y a un identifiant errone — et
 * inversement, traiter une panne en « processus inconnu » ferait croire le dossier
 * disparu.
 *
 * <p>Le jeton de l'utilisateur final est relaye tel quel (doctrine Sprint 1.3) : ce
 * service ne s'authentifie pas avec un compte technique, aucune identite machine
 * n'existant au realm. La portee d'acces verifiee cote Workflow reste donc celle de la
 * personne a l'origine de la cloture.
 *
 * <p>Delais 2 s / 3 s, aucun reessai — bornes du {@code RestClient.Builder} du service.
 */
@Component
public class ProcessusHttpClient implements ProcessusClient {

    private static final Logger journal = LoggerFactory.getLogger(ProcessusHttpClient.class);

    private final RestClient clientRest;
    private final String urlServiceWorkflow;

    public ProcessusHttpClient(RestClient.Builder constructeurRest,
            @Value("${app.workflow.url}") String urlServiceWorkflow) {
        this.urlServiceWorkflow = urlServiceWorkflow;
        this.clientRest = constructeurRest.baseUrl(urlServiceWorkflow).build();
    }

    @Override
    public ResultatProcessus obtenir(Long idProcessus, String enteteAutorisation) {
        if (enteteAutorisation == null || enteteAutorisation.isBlank()) {
            throw new IllegalStateException(
                    "Aucun en-tete Authorization a relayer au service Workflow : l'appelant doit "
                            + "propager le jeton de l'utilisateur final.");
        }

        try {
            EnTeteProcessus enTete = clientRest.get()
                    .uri("/processus/{id}", idProcessus)
                    .header(HttpHeaders.AUTHORIZATION, enteteAutorisation)
                    .retrieve()
                    .body(EnTeteProcessus.class);

            return interpreter(enTete, idProcessus);

        } catch (RestClientResponseException reponseEnErreur) {
            if (reponseEnErreur.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                journal.warn("Le service Workflow ne connait pas le processus {} : la demande de "
                        + "transmission porte un identifiant errone.", idProcessus);
                return new ProcessusIntrouvable(idProcessus);
            }

            journal.warn("Le service Workflow a repondu {} pour le processus {}. Transmission "
                            + "refusee. Corps : {}",
                    reponseEnErreur.getStatusCode(), idProcessus,
                    reponseEnErreur.getResponseBodyAsString());
            return new ServiceWorkflowIndisponible("reponse " + reponseEnErreur.getStatusCode());

        } catch (RestClientException panne) {
            journal.warn("Service Workflow injoignable a l'adresse {} pour le processus {} : {}",
                    urlServiceWorkflow, idProcessus, panne.getMessage());
            return new ServiceWorkflowIndisponible(
                    "appel a " + urlServiceWorkflow + " en echec : " + panne.getMessage());
        }
    }

    /**
     * Verifie que la reponse porte de quoi construire une charge.
     *
     * <p><b>Un montant total absent est un refus, jamais un zero.</b> Les deux se
     * ressemblent et ne signifient pas la meme chose : l'un est un etat vide — que le
     * controle de completude nommera —, l'autre une lecture ratee. Publier sur une
     * lecture ratee enverrait a la comptabilite un etat a zero franc, qu'elle traiterait
     * comme un dossier legitime. Meme raisonnement qu'au Sprint 4.2 cote Workflow.
     */
    private ResultatProcessus interpreter(EnTeteProcessus enTete, Long idProcessus) {
        if (enTete == null) {
            journal.warn("Le service Workflow a repondu 200 sans corps pour le processus {}.",
                    idProcessus);
            return new ServiceWorkflowIndisponible("reponse 200 sans corps exploitable");
        }

        if (enTete.statut() == null || enTete.montantTotal() == null) {
            journal.warn("Reponse incomplete du service Workflow pour le processus {} "
                            + "(statut={}, montantTotal={}). Transmission refusee : une valeur "
                            + "absente ne doit pas se lire comme un zero.",
                    idProcessus, enTete.statut(), enTete.montantTotal());
            return new ServiceWorkflowIndisponible("reponse 200 sans statut ni montant exploitables");
        }

        return new ProcessusObtenu(enTete);
    }

}
