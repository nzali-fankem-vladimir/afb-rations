package cm.afrilandfirstbank.rations.saisie.infrastructure.workflow;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import cm.afrilandfirstbank.rations.saisie.application.ResultatVerificationProcessus;
import cm.afrilandfirstbank.rations.saisie.application.ResultatVerificationProcessus.ProcessusIntrouvable;
import cm.afrilandfirstbank.rations.saisie.application.ResultatVerificationProcessus.ProcessusVerifie;
import cm.afrilandfirstbank.rations.saisie.application.ResultatVerificationProcessus.ServiceWorkflowIndisponible;
import cm.afrilandfirstbank.rations.saisie.application.VerificationProcessusClient;
import cm.afrilandfirstbank.rations.saisie.domaine.StatutProcessusEnum;

/**
 * Interroge {@code GET /processus/{id}} du service Workflow.
 *
 * <h2>Ce client est écrit contre un service qui n'existe pas encore</h2>
 *
 * <p>L'ordre d'implémentation place Saisie au Sprint 3 et Workflow au Sprint 4.
 * Ce client est donc <b>codé selon un contrat écrit, non vérifié en
 * intégration</b> : l'URL, le mapping JSON, la traduction des codes d'erreur ne
 * seront confrontés au vrai service qu'au Sprint 4
 * ({@code docs/rattachement-processus.md} §6). Aucun document du Sprint 3 ne doit
 * affirmer le contraire.
 *
 * <p>C'est pourquoi il est testé contre {@code MockRestServiceServer} et non par
 * un mock de sa propre classe : mocker le client ne testerait rien de ce qui peut
 * casser. D'où le {@code RestClient.Builder} <b>injecté</b> — un builder
 * construit en dur, comme le fait {@code ClientIdentite} du service Grilles,
 * rendrait le bouchon impossible à attacher.
 *
 * <h2>Traduction des réponses</h2>
 *
 * <table>
 *   <tr><td>{@code 200} exploitable</td><td>{@link ProcessusVerifie}</td></tr>
 *   <tr><td>{@code 404}</td><td>{@link ProcessusIntrouvable} — Workflow a répondu</td></tr>
 *   <tr><td>tout le reste</td><td>{@link ServiceWorkflowIndisponible} — refus conservateur</td></tr>
 * </table>
 *
 * <p>« Tout le reste » inclut les {@code 401} et {@code 403}, qui ne devraient
 * pas survenir : le jeton relayé est celui de l'utilisateur final, déjà validé
 * par le présent service. S'ils survenaient, les ranger dans le refus technique
 * est la stricte application de la convention — <i>toute réponse autre qu'un 200
 * exploitable → refus</i> — et ils sont journalisés avec leur statut.
 *
 * <p>Délais 2 s / 3 s et <b>aucun réessai</b> : bornes du bean
 * {@code RestClient.Builder} du service, doctrine tranchée au Sprint 3.2
 * ({@code docs/decisions/2026-08-31-delais-et-reessai-des-appels-sortants.md}).
 * Un réessai porterait à six secondes le seul contrôle de statut, sur un chemin
 * qui en empile déjà trois.
 */
@Component
@Profile("!" + BouchonVerificationProcessus.PROFIL)
public class VerificationProcessusHttpClient implements VerificationProcessusClient {

    private static final Logger journal =
            LoggerFactory.getLogger(VerificationProcessusHttpClient.class);

    private final RestClient clientRest;
    private final String urlServiceWorkflow;

    public VerificationProcessusHttpClient(RestClient.Builder constructeurRest,
                                           @Value("${app.workflow.url}") String urlServiceWorkflow) {
        this.urlServiceWorkflow = urlServiceWorkflow;
        this.clientRest = constructeurRest.baseUrl(urlServiceWorkflow).build();
    }

    @Override
    public ResultatVerificationProcessus verifier(Long idProcessus, String enteteAutorisation) {
        if (enteteAutorisation == null || enteteAutorisation.isBlank()) {
            throw new IllegalStateException(
                    "Aucun en-tete Authorization a relayer au service Workflow : "
                            + "l'appelant doit propager le jeton de l'utilisateur final.");
        }

        try {
            ProcessusReponse reponse = clientRest.get()
                    .uri("/processus/{id}", idProcessus)
                    .header(HttpHeaders.AUTHORIZATION, enteteAutorisation)
                    .retrieve()
                    .body(ProcessusReponse.class);

            return interpreter(reponse, idProcessus);

        } catch (RestClientResponseException reponseEnErreur) {
            if (reponseEnErreur.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                return new ProcessusIntrouvable(idProcessus);
            }
            journal.warn("Le service Workflow a repondu {} pour le processus {}. Operation refusee.",
                    reponseEnErreur.getStatusCode(), idProcessus);
            return new ServiceWorkflowIndisponible("reponse " + reponseEnErreur.getStatusCode());

        } catch (RestClientException panne) {
            journal.warn("Service Workflow injoignable a l'adresse {} pour le processus {} : {}",
                    urlServiceWorkflow, idProcessus, panne.getMessage());
            return new ServiceWorkflowIndisponible(
                    "appel a " + urlServiceWorkflow + " en echec : " + panne.getMessage());
        }
    }

    /**
     * Vérifie que la réponse porte réellement ce dont la Saisie a besoin.
     *
     * <p>Chaque champ manquant produit un refus technique, jamais une valeur par
     * défaut. Un {@code codeUnite} nul rendrait la portée d'accès inarbitrable —
     * et l'admettre reviendrait à écrire sur une unité inconnue.
     */
    private ResultatVerificationProcessus interpreter(ProcessusReponse reponse, Long idProcessus) {
        if (reponse == null) {
            journal.warn("Le service Workflow a repondu 200 sans corps pour le processus {}.",
                    idProcessus);
            return new ServiceWorkflowIndisponible("reponse 200 sans corps exploitable");
        }

        Optional<StatutProcessusEnum> statut = StatutProcessusEnum.depuisLibelle(reponse.statut());
        if (statut.isEmpty()) {
            journal.warn("Statut de processus inconnu recu du service Workflow pour le processus {} "
                            + "(valeur recue : {}). Operation refusee : un statut non reconnu n'est "
                            + "jamais tenu pour modifiable.",
                    idProcessus, reponse.statut());
            return new ServiceWorkflowIndisponible("statut inconnu : " + reponse.statut());
        }

        if (reponse.codeUnite() == null || reponse.codeUnite().isBlank()
                || reponse.moisPaiement() == null || reponse.anneePaiement() == null) {
            journal.warn("Reponse incomplete du service Workflow pour le processus {} "
                            + "(codeUnite={}, mois={}, annee={}). Operation refusee.",
                    idProcessus, reponse.codeUnite(), reponse.moisPaiement(), reponse.anneePaiement());
            return new ServiceWorkflowIndisponible("reponse 200 sans unite ni periode exploitables");
        }

        return new ProcessusVerifie(idProcessus, statut.get(), reponse.codeUnite(),
                reponse.moisPaiement(), reponse.anneePaiement());
    }

}
