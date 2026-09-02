package cm.afrilandfirstbank.rations.transmission.infrastructure.saisie;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import cm.afrilandfirstbank.rations.transmission.application.ConsolidationClient;
import cm.afrilandfirstbank.rations.transmission.application.EtatConsolide;
import cm.afrilandfirstbank.rations.transmission.application.ResultatConsolidation;
import cm.afrilandfirstbank.rations.transmission.application.ResultatConsolidation.EtatObtenu;
import cm.afrilandfirstbank.rations.transmission.application.ResultatConsolidation.ServiceSaisieIndisponible;

/**
 * Interroge {@code GET /saisie/processus/{id}/etat?codeUnite=...} du service Saisie —
 * l'unique endpoint interne de ce service (Sprint 3.4,
 * {@code docs/appel-consolidation.md}).
 *
 * <p>C'est le <b>second</b> consommateur de cet endpoint, apres le service Workflow. Il
 * honore la meme convention, avec le meme parametre obligatoire et la meme lecture des
 * reponses ; les deux clients sont volontairement jumeaux, chacun dans son service
 * (CLAUDE.md sections 3 et 15).
 *
 * <h2>Traduction des reponses</h2>
 *
 * <table>
 *   <tr><td>{@code 200} exploitable</td><td>{@link EtatObtenu}</td></tr>
 *   <tr><td>{@code 200}, zero journee, total 0</td><td>{@link EtatObtenu} — le refus revient au controle de completude</td></tr>
 *   <tr><td>tout le reste</td><td>{@link ServiceSaisieIndisponible} — refus conservateur</td></tr>
 * </table>
 *
 * <p>« Tout le reste » inclut les {@code 4xx}, qui signaleraient un defaut de <b>ce</b>
 * service : {@code 400} si {@code codeUnite} manquait, {@code 403
 * UNITE_NON_CONCORDANTE} si l'unite declaree n'etait pas celle figee sur les fiches. Ils
 * sont journalises avec leur statut et leur corps, precisement pour que le diagnostic
 * prenne une minute.
 *
 * <p><b>Aucune charge partielle ne part.</b> Une reponse illisible refuse la
 * transmission au lieu de publier ce qu'on a pu lire : un detail ampute produirait des
 * beneficiaires impayes que rien ne signalerait.
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
                    "Aucun en-tete Authorization a relayer au service Saisie : l'appelant doit "
                            + "propager le jeton de l'utilisateur final.");
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

            return interpreter(etat, idProcessus, codeUnite);

        } catch (RestClientResponseException reponseEnErreur) {
            journal.warn("Le service Saisie a repondu {} pour le processus {} (unite declaree {}). "
                            + "Transmission refusee. Corps : {}",
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
     * Verifie que la reponse porte un detail exploitable.
     *
     * <p>Le total du service Saisie sert de <b>second temoin</b> au controle de
     * coherence : son absence prive ce controle d'un de ses trois appuis, on refuse
     * plutot que de le mener a deux.
     */
    private ResultatConsolidation interpreter(EtatConsolide etat, Long idProcessus,
            String codeUnite) {

        if (etat == null) {
            journal.warn("Le service Saisie a repondu 200 sans corps pour le processus {} "
                    + "(unite declaree {}).", idProcessus, codeUnite);
            return new ServiceSaisieIndisponible("reponse 200 sans corps exploitable");
        }

        if (etat.montantTotalFcfa() == null || etat.nombreLignes() == null) {
            journal.warn("Reponse incomplete du service Saisie pour le processus {} "
                            + "(montantTotalFcfa={}, nombreLignes={}). Transmission refusee : une "
                            + "valeur absente ne doit pas se lire comme un zero.",
                    idProcessus, etat.montantTotalFcfa(), etat.nombreLignes());
            return new ServiceSaisieIndisponible("reponse 200 sans total exploitable");
        }

        return new EtatObtenu(etat);
    }

}
