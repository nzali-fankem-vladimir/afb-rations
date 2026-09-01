package cm.afrilandfirstbank.rations.workflow.infrastructure.saisie;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import cm.afrilandfirstbank.rations.workflow.application.ConsolidationClient;
import cm.afrilandfirstbank.rations.workflow.application.EtatConsolide;
import cm.afrilandfirstbank.rations.workflow.application.ResultatConsolidation;
import cm.afrilandfirstbank.rations.workflow.application.ResultatConsolidation.EtatObtenu;
import cm.afrilandfirstbank.rations.workflow.application.ResultatConsolidation.ServiceSaisieIndisponible;

/**
 * Interroge {@code GET /saisie/processus/{id}/etat?codeUnite=...} du service
 * Saisie — l'unique endpoint interne de ce service (Sprint 3.4).
 *
 * <p>Ce client honore la convention ecrite au Sprint 3.4
 * ({@code docs/appel-consolidation.md}), qui a ete redigee <i>pour lui</i>. C'est
 * la difference avec le chemin inverse : {@code VerificationProcessusHttpClient},
 * cote Saisie, a ete ecrit contre un service qui n'existait pas encore. Ici
 * l'appele existe, tourne, et a ete verifie a la main au Sprint 3.4.
 *
 * <h2>Traduction des reponses</h2>
 *
 * <table>
 *   <tr><td>{@code 200} exploitable</td><td>{@link EtatObtenu}</td></tr>
 *   <tr><td>{@code 200}, zero journee, total 0</td><td>{@link EtatObtenu} — cas <b>normal</b>, pas une erreur</td></tr>
 *   <tr><td>tout le reste</td><td>{@link ServiceSaisieIndisponible} — refus conservateur</td></tr>
 * </table>
 *
 * <p>« Tout le reste » inclut les {@code 4xx}, qui signaleraient un defaut de
 * <b>ce</b> service : {@code 400} si {@code codeUnite} manquait, {@code 403
 * UNITE_NON_CONCORDANTE} si Workflow declarait une unite qui n'est pas celle des
 * fiches. Ils sont journalises avec leur statut et leur corps, precisement pour
 * que ce diagnostic prenne une minute — le service Saisie, lui, les trace deja en
 * audit.
 *
 * <p>Le jeton de l'utilisateur final est relaye tel quel (doctrine Sprint 1.3) :
 * la portee d'acces verifiee cote Saisie est celle de la personne reellement a
 * l'origine de la demande, pas celle d'un compte technique.
 *
 * <p>Delais 2 s / 3 s, aucun reessai — bornes du {@code RestClient.Builder} du
 * service.
 */
@Component
public class ConsolidationHttpClient implements ConsolidationClient {

    private static final Logger journal = LoggerFactory.getLogger(ConsolidationHttpClient.class);

    private final RestClient clientRest;
    private final String urlServiceSaisie;

    public ConsolidationHttpClient(RestClient.Builder constructeurRest,
            @Value("${app.saisie.url}") String urlServiceSaisie) {
        this.urlServiceSaisie = urlServiceSaisie;
        this.clientRest = constructeurRest.baseUrl(urlServiceSaisie).build();
    }

    @Override
    public ResultatConsolidation consolider(Long idProcessus, String codeUnite,
            String enteteAutorisation) {

        if (enteteAutorisation == null || enteteAutorisation.isBlank()) {
            throw new IllegalStateException(
                    "Aucun en-tete Authorization a relayer au service Saisie : "
                            + "l'appelant doit propager le jeton de l'utilisateur final.");
        }

        try {
            EtatConsolide etat = clientRest.get()
                    .uri(constructeur -> constructeur
                            .path("/saisie/processus/{id}/etat")
                            .queryParam("codeUnite", codeUnite)
                            .build(idProcessus))
                    .header(HttpHeaders.AUTHORIZATION, enteteAutorisation)
                    .retrieve()
                    .body(EtatConsolide.class);

            return interpreter(etat, idProcessus);

        } catch (RestClientResponseException reponseEnErreur) {
            journal.warn("Le service Saisie a repondu {} pour le processus {} (unite declaree {}). "
                            + "Consolidation refusee. Corps : {}",
                    reponseEnErreur.getStatusCode(), idProcessus, codeUnite,
                    reponseEnErreur.getResponseBodyAsString());
            return new ServiceSaisieIndisponible("reponse " + reponseEnErreur.getStatusCode());

        } catch (RestClientException panne) {
            journal.warn("Service Saisie injoignable a l'adresse {} pour le processus {} : {}",
                    urlServiceSaisie, idProcessus, panne.getMessage());
            return new ServiceSaisieIndisponible(
                    "appel a " + urlServiceSaisie + " en echec : " + panne.getMessage());
        }
    }

    /**
     * Verifie que la reponse porte reellement un total exploitable.
     *
     * <p><b>Un {@code montantTotalFcfa} nul est un refus, jamais un zero.</b> Zero
     * est une valeur legitime — celle d'un etat vide — et l'absence de valeur ne
     * doit surtout pas s'y confondre : les deux commanderaient le meme aiguillage
     * (« sous le seuil »), l'un a juste titre, l'autre par accident. C'est
     * exactement le montant partiel que la convention interdit d'enregistrer
     * (section 6).
     */
    private ResultatConsolidation interpreter(EtatConsolide etat, Long idProcessus) {
        if (etat == null) {
            journal.warn("Le service Saisie a repondu 200 sans corps pour le processus {}.",
                    idProcessus);
            return new ServiceSaisieIndisponible("reponse 200 sans corps exploitable");
        }

        if (etat.montantTotalFcfa() == null || etat.nombreLignes() == null) {
            journal.warn("Reponse incomplete du service Saisie pour le processus {} "
                            + "(montantTotalFcfa={}, nombreLignes={}). Consolidation refusee : "
                            + "une valeur absente ne doit pas se lire comme un zero.",
                    idProcessus, etat.montantTotalFcfa(), etat.nombreLignes());
            return new ServiceSaisieIndisponible("reponse 200 sans total exploitable");
        }

        return new EtatObtenu(etat);
    }

}
