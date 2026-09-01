package cm.afrilandfirstbank.rations.workflow.infrastructure.identite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import cm.afrilandfirstbank.rations.workflow.application.ActeurSignataire;
import cm.afrilandfirstbank.rations.workflow.application.ProfilClient;
import cm.afrilandfirstbank.rations.workflow.application.ResultatProfil;
import cm.afrilandfirstbank.rations.workflow.application.ResultatProfil.ProfilAbsent;
import cm.afrilandfirstbank.rations.workflow.application.ResultatProfil.ProfilObtenu;
import cm.afrilandfirstbank.rations.workflow.application.ResultatProfil.ServiceIdentiteIndisponible;

/**
 * Appel de {@code GET /identite/moi}.
 *
 * <p>Meme facture que {@link HabilitationHttpClient}, et pour les memes raisons :
 * les delais et l'absence de reessai viennent du {@code RestClient.Builder}
 * partage ({@code ConfigurationAppelsSortants}, Sprint 3.2), le jeton de
 * l'utilisateur final est relaye tel quel (doctrine Sprint 1.3), et une panne est
 * distinguee d'un refus.
 *
 * <p><b>La distinction 4xx / 5xx est la seule chose delicate ici.</b> Un
 * {@code 403} veut dire « le service Identite a repondu : ce compte n'a pas de
 * profil ouvert dans le module », ce qui est un refus legitime. Un {@code 500},
 * un delai depasse ou une connexion refusee veulent dire « on ne sait pas », ce
 * qui doit rester visible comme une panne. Les confondre enverrait un agent
 * parfaitement habilite reclamer un droit qu'il possede deja, pendant que
 * l'incident resterait invisible.
 */
@Component
public class ProfilHttpClient implements ProfilClient {

    private static final Logger journal = LoggerFactory.getLogger(ProfilHttpClient.class);

    private final RestClient clientRest;
    private final String urlServiceIdentite;

    public ProfilHttpClient(RestClient.Builder constructeurRest,
            @Value("${app.identite.url}") String urlServiceIdentite) {
        this.urlServiceIdentite = urlServiceIdentite;
        this.clientRest = constructeurRest.baseUrl(urlServiceIdentite).build();
    }

    @Override
    public ResultatProfil obtenir(String enteteAutorisation) {
        if (enteteAutorisation == null || enteteAutorisation.isBlank()) {
            throw new IllegalStateException(
                    "Aucun en-tete Authorization a relayer au service Identite : "
                            + "l'appelant doit propager le jeton de l'utilisateur final.");
        }

        try {
            ProfilReponse reponse = clientRest.get()
                    .uri("/identite/moi")
                    .header(HttpHeaders.AUTHORIZATION, enteteAutorisation)
                    .retrieve()
                    .body(ProfilReponse.class);

            return interpreter(reponse);

        } catch (RestClientResponseException reponseEnErreur) {
            if (reponseEnErreur.getStatusCode().is4xxClientError()) {
                journal.debug("Le service Identite a repondu {} sur /identite/moi : aucun profil.",
                        reponseEnErreur.getStatusCode());
                return new ProfilAbsent(
                        "aucun profil actif n'est ouvert dans le module pour ce compte");
            }
            journal.warn("Le service Identite a repondu {} sur /identite/moi. Operation refusee.",
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
     * Une reponse {@code 200} incomplete est traitee comme une panne, pas comme un
     * profil.
     *
     * <p>{@code id}, {@code login} et {@code role} sont tous les trois
     * indispensables : le premier alimente {@code etape_workflow.id_acteur}, qui
     * est {@code NOT NULL} ; les deux autres composent la mention imprimee sur le
     * document. Enregistrer une etape avec un identifiant nul ferait echouer
     * l'insertion en base bien plus loin, avec un message technique illisible ; et
     * une mention sans login signerait le document au nom de personne.
     */
    private ResultatProfil interpreter(ProfilReponse reponse) {
        if (reponse == null || reponse.id() == null
                || reponse.login() == null || reponse.login().isBlank()
                || reponse.role() == null) {
            journal.warn("Le service Identite a repondu 200 sans profil exploitable sur "
                    + "/identite/moi (id, login ou role absent).");
            return new ServiceIdentiteIndisponible(
                    "reponse 200 sans identifiant, login ou role exploitable");
        }
        return new ProfilObtenu(
                new ActeurSignataire(reponse.id(), reponse.login(), reponse.role()));
    }

}
