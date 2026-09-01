package cm.afrilandfirstbank.rations.workflow.infrastructure.identite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import cm.afrilandfirstbank.rations.workflow.application.HabilitationClient;
import cm.afrilandfirstbank.rations.workflow.application.ResultatHabilitationUnite;
import cm.afrilandfirstbank.rations.workflow.application.ResultatHabilitationUnite.AgentHabilite;
import cm.afrilandfirstbank.rations.workflow.application.ResultatHabilitationUnite.AgentNonHabilite;
import cm.afrilandfirstbank.rations.workflow.application.ResultatHabilitationUnite.ServiceIdentiteIndisponible;

/**
 * Interroge {@code GET /identite/habilitation?codeUnite=...} du service Identite
 * (endpoint interne du Sprint 1.3, hors contrat passerelle).
 *
 * <h2>Le jeton de l'utilisateur final est relaye tel quel</h2>
 *
 * <p>Le service ne s'authentifie pas avec un compte de service : le realm
 * {@code afb-rations-dev} n'en comporte aucun — un seul client, public,
 * {@code serviceAccountsEnabled: false} — et le mapper d'audience place deja
 * {@code aud: rations-api} dans le jeton utilisateur. La question posee est
 * « <b>cet utilisateur</b> a-t-il le droit ? » : elle se repond a partir de son
 * propre jeton ({@code docs/appel-habilitation.md} section 2).
 *
 * <h2>Traduction des reponses</h2>
 *
 * <table>
 *   <tr><td>{@code 200}, {@code autorise = true}</td><td>{@link AgentHabilite}</td></tr>
 *   <tr><td>{@code 200}, {@code autorise = false}</td><td>{@link AgentNonHabilite} — verdict metier</td></tr>
 *   <tr><td>{@code 4xx}</td><td>{@link AgentNonHabilite} — aucun profil local ouvert</td></tr>
 *   <tr><td>tout le reste</td><td>{@link ServiceIdentiteIndisponible} — refus conservateur</td></tr>
 * </table>
 *
 * <p>Le {@code 403} rejoint le verdict negatif et non la panne : Identite a
 * <b>repondu</b>, et sa reponse est que ce compte n'a pas de profil dans le
 * module. Les confondre ferait rendre {@code 503} — « reessayez plus tard » — a
 * un utilisateur qui doit en realite demander une habilitation a l'administrateur
 * (distinction posee au Sprint 2.2 entre {@code UTILISATEUR_NON_HABILITE} et
 * {@code SERVICE_IDENTITE_INDISPONIBLE}).
 *
 * <p><b>Aucune mise en cache</b>, meme d'un verdict positif (section 3 de la meme
 * note). Delais 2 s / 3 s, aucun reessai.
 */
@Component
public class HabilitationHttpClient implements HabilitationClient {

    private static final Logger journal = LoggerFactory.getLogger(HabilitationHttpClient.class);

    private final RestClient clientRest;
    private final String urlServiceIdentite;

    public HabilitationHttpClient(RestClient.Builder constructeurRest,
            @Value("${app.identite.url}") String urlServiceIdentite) {
        this.urlServiceIdentite = urlServiceIdentite;
        this.clientRest = constructeurRest.baseUrl(urlServiceIdentite).build();
    }

    @Override
    public ResultatHabilitationUnite verifier(String codeUnite, String enteteAutorisation) {
        if (enteteAutorisation == null || enteteAutorisation.isBlank()) {
            throw new IllegalStateException(
                    "Aucun en-tete Authorization a relayer au service Identite : "
                            + "l'appelant doit propager le jeton de l'utilisateur final.");
        }

        try {
            HabilitationReponse reponse = clientRest.get()
                    .uri(constructeur -> constructeur
                            .path("/identite/habilitation")
                            .queryParam("codeUnite", codeUnite)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, enteteAutorisation)
                    .retrieve()
                    .body(HabilitationReponse.class);

            return interpreter(reponse, codeUnite);

        } catch (RestClientResponseException reponseEnErreur) {
            if (reponseEnErreur.getStatusCode().is4xxClientError()) {
                journal.debug("Le service Identite a repondu {} pour l'unite {} : verdict negatif.",
                        reponseEnErreur.getStatusCode(), codeUnite);
                return new AgentNonHabilite(
                        "aucun profil actif n'est ouvert dans le module pour ce compte");
            }
            journal.warn("Le service Identite a repondu {} pour l'unite {}. Operation refusee.",
                    reponseEnErreur.getStatusCode(), codeUnite);
            return new ServiceIdentiteIndisponible("reponse " + reponseEnErreur.getStatusCode());

        } catch (RestClientException panne) {
            journal.warn("Service Identite injoignable a l'adresse {} pour l'unite {} : {}",
                    urlServiceIdentite, codeUnite, panne.getMessage());
            return new ServiceIdentiteIndisponible(
                    "appel a " + urlServiceIdentite + " en echec : " + panne.getMessage());
        }
    }

    /**
     * Un corps absent ou un {@code autorise} nul est un refus <b>technique</b>,
     * jamais une autorisation : le verdict doit etre lu, pas suppose.
     */
    private ResultatHabilitationUnite interpreter(HabilitationReponse reponse, String codeUnite) {
        if (reponse == null || reponse.autorise() == null) {
            journal.warn("Le service Identite a repondu 200 sans verdict exploitable pour l'unite {}.",
                    codeUnite);
            return new ServiceIdentiteIndisponible("reponse 200 sans champ autorise exploitable");
        }

        if (!reponse.autorise()) {
            return new AgentNonHabilite(
                    "role " + reponse.role() + " sans portee sur l'unite " + codeUnite);
        }

        return new AgentHabilite(reponse.login(), reponse.role(), codeUnite);
    }

}
