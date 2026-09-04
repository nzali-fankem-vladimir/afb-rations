package cm.afrilandfirstbank.rations.workflow.infrastructure.identite;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import cm.afrilandfirstbank.rations.workflow.application.PorteeAccesUtilisateur;
import cm.afrilandfirstbank.rations.workflow.application.PorteeClient;
import cm.afrilandfirstbank.rations.workflow.application.ResultatPortee;
import cm.afrilandfirstbank.rations.workflow.application.ResultatPortee.PorteeObtenue;
import cm.afrilandfirstbank.rations.workflow.application.ResultatPortee.ProfilAbsent;
import cm.afrilandfirstbank.rations.workflow.application.ResultatPortee.ServiceIdentiteIndisponible;
import cm.afrilandfirstbank.rations.workflow.infrastructure.identite.PorteeReponse.PorteeAccesReponse;

/**
 * Lit {@code porteeAcces} sur {@code GET /identite/moi} (Sprint 6.1).
 *
 * <p>Meme facture que {@link ProfilHttpClient} et {@link HabilitationHttpClient} :
 * delais et absence de reessai herites du {@code RestClient.Builder} partage
 * (Sprint 3.2), jeton de l'utilisateur final relaye tel quel (doctrine Sprint 1.3),
 * et <b>4xx distingue de 5xx</b> — un refus n'est pas une panne.
 */
@Component
public class PorteeHttpClient implements PorteeClient {

    private static final Logger journal = LoggerFactory.getLogger(PorteeHttpClient.class);

    private final RestClient clientRest;
    private final String urlServiceIdentite;

    public PorteeHttpClient(RestClient.Builder constructeurRest,
            @Value("${app.identite.url}") String urlServiceIdentite) {
        this.urlServiceIdentite = urlServiceIdentite;
        this.clientRest = constructeurRest.baseUrl(urlServiceIdentite).build();
    }

    @Override
    public ResultatPortee obtenir(String enteteAutorisation) {
        if (enteteAutorisation == null || enteteAutorisation.isBlank()) {
            throw new IllegalStateException(
                    "Aucun en-tete Authorization a relayer au service Identite : "
                            + "l'appelant doit propager le jeton de l'utilisateur final.");
        }

        try {
            PorteeReponse reponse = clientRest.get()
                    .uri("/identite/moi")
                    .header(HttpHeaders.AUTHORIZATION, enteteAutorisation)
                    .retrieve()
                    .body(PorteeReponse.class);

            return interpreter(reponse);

        } catch (RestClientResponseException reponseEnErreur) {
            if (reponseEnErreur.getStatusCode().is4xxClientError()) {
                journal.debug("Le service Identite a repondu {} sur /identite/moi : aucun profil.",
                        reponseEnErreur.getStatusCode());
                return new ProfilAbsent(
                        "aucun profil actif n'est ouvert dans le module pour ce compte");
            }
            journal.warn("Le service Identite a repondu {} sur /identite/moi. Recherche refusee.",
                    reponseEnErreur.getStatusCode());
            return new ServiceIdentiteIndisponible("reponse " + reponseEnErreur.getStatusCode());

        } catch (RestClientException panne) {
            journal.warn("Service Identite injoignable a l'adresse {} sur /identite/moi : {}",
                    urlServiceIdentite, panne.getMessage());
            return new ServiceIdentiteIndisponible(
                    "appel a " + urlServiceIdentite + " en echec : " + panne.getMessage());
        }
    }

    /**
     * Une reponse {@code 200} sans portee exploitable est traitee comme une panne.
     *
     * <p>C'est la meme discipline que {@code ProfilHttpClient} : un champ absent ne
     * se remplace pas par une valeur par defaut. Ici la valeur par defaut la plus
     * tentante — « portee vide, donc zero resultat » — serait la pire : la recherche
     * rendrait une page vide, indiscernable d'une absence legitime de dossiers, et
     * l'utilisateur conclurait qu'il n'y a rien a voir.
     */
    private ResultatPortee interpreter(PorteeReponse reponse) {
        PorteeAccesReponse portee = reponse == null ? null : reponse.porteeAcces();

        if (portee == null || portee.nationale() == null) {
            journal.warn("Le service Identite a repondu 200 sans porteeAcces exploitable sur "
                    + "/identite/moi. Recherche refusee.");
            return new ServiceIdentiteIndisponible("reponse 200 sans porteeAcces exploitable");
        }

        Set<String> codes = portee.codesUnite() == null ? Set.of() : portee.codesUnite();
        return new PorteeObtenue(new PorteeAccesUtilisateur(portee.nationale(), codes));
    }

}
