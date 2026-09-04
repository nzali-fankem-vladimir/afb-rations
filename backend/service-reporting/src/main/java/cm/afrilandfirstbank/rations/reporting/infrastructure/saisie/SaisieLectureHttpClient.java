package cm.afrilandfirstbank.rations.reporting.infrastructure.saisie;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import cm.afrilandfirstbank.rations.reporting.application.ResultatIdentifiantsAvecLigne;
import cm.afrilandfirstbank.rations.reporting.application.SaisieLectureClient;
import cm.afrilandfirstbank.rations.reporting.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.reporting.domaine.SessionEnum;

/**
 * Appel de {@code GET /saisie/processus/recherche}, endpoint interne du service
 * Saisie (Sprint 6.1).
 *
 * <p>Meme facture que {@code WorkflowLectureHttpClient} : delais et absence de
 * reessai herites du {@code RestClient.Builder} partage, jeton de l'utilisateur
 * final relaye tel quel, et <b>refus distingue de panne</b>.
 *
 * <p>Un seul appel par recherche, quel que soit le nombre de resultats. C'est ce
 * que la strategie d'agregation du Sprint 6.1 garantit, et une implementation qui
 * boucle ici serait le defaut que le guide 6.1 nomme explicitement.
 */
@Component
public class SaisieLectureHttpClient implements SaisieLectureClient {

    private static final Logger journal = LoggerFactory.getLogger(SaisieLectureHttpClient.class);

    private final RestClient clientRest;
    private final String urlServiceSaisie;

    public SaisieLectureHttpClient(RestClient.Builder constructeurRest,
            @Value("${app.saisie.url}") String urlServiceSaisie) {
        this.urlServiceSaisie = urlServiceSaisie;
        this.clientRest = constructeurRest.baseUrl(urlServiceSaisie).build();
    }

    @Override
    public ResultatIdentifiantsAvecLigne identifiantsAvecLigne(Integer mois, Integer annee,
            NatureEnum nature, SessionEnum session, String beneficiaire,
            String enteteAutorisation) {

        if (enteteAutorisation == null || enteteAutorisation.isBlank()) {
            throw new IllegalStateException(
                    "Aucun en-tete Authorization a relayer au service Saisie : "
                            + "l'appelant doit propager le jeton de l'utilisateur final.");
        }

        try {
            ReponseRecherche reponse = clientRest.get()
                    .uri(uri -> construireUri(uri, mois, annee, nature, session, beneficiaire))
                    .header(HttpHeaders.AUTHORIZATION, enteteAutorisation)
                    .retrieve()
                    .body(ReponseRecherche.class);

            return interpreter(reponse);

        } catch (RestClientResponseException reponseEnErreur) {
            if (reponseEnErreur.getStatusCode() == HttpStatus.FORBIDDEN) {
                return new ResultatIdentifiantsAvecLigne.AccesRefuse(
                        "le service Saisie a refuse la lecture pour ce compte");
            }
            journal.warn("Le service Saisie a repondu {} sur /saisie/processus/recherche.",
                    reponseEnErreur.getStatusCode());
            return new ResultatIdentifiantsAvecLigne.ServiceIndisponible(
                    "reponse " + reponseEnErreur.getStatusCode());

        } catch (RestClientException panne) {
            journal.warn("Service Saisie injoignable a l'adresse {} sur /saisie/processus/recherche : {}",
                    urlServiceSaisie, panne.getMessage());
            return new ResultatIdentifiantsAvecLigne.ServiceIndisponible(
                    "appel a " + urlServiceSaisie + " en echec : " + panne.getMessage());
        }
    }

    private URI construireUri(UriBuilder uri, Integer mois, Integer annee,
            NatureEnum nature, SessionEnum session, String beneficiaire) {

        uri.path("/saisie/processus/recherche");
        if (mois != null) {
            uri.queryParam("mois", mois);
        }
        if (annee != null) {
            uri.queryParam("annee", annee);
        }
        if (nature != null) {
            uri.queryParam("nature", nature.name());
        }
        if (session != null) {
            uri.queryParam("session", session.name());
        }
        if (beneficiaire != null && !beneficiaire.isBlank()) {
            uri.queryParam("beneficiaire", beneficiaire);
        }
        return uri.build();
    }

    /**
     * Une liste vide est un resultat legitime — aucun etat ne porte de ligne
     * correspondante. Une reponse <b>absente</b>, elle, est une panne : le champ
     * {@code idsProcessus} est toujours present quand le service repond.
     */
    private ResultatIdentifiantsAvecLigne interpreter(ReponseRecherche reponse) {
        if (reponse == null || reponse.idsProcessus() == null) {
            journal.warn("Le service Saisie a repondu 200 sans idsProcessus exploitable.");
            return new ResultatIdentifiantsAvecLigne.ServiceIndisponible(
                    "reponse 200 sans liste d'identifiants");
        }
        return ResultatIdentifiantsAvecLigne.Obtenus.de(reponse.idsProcessus());
    }

    /** Charge de {@code GET /saisie/processus/recherche}, lue en tolerant reader. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ReponseRecherche(List<Long> idsProcessus) {
    }

}
