package cm.afrilandfirstbank.rations.transmission.infrastructure.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import cm.afrilandfirstbank.rations.transmission.application.ResultatMiseAJourIntegration;
import cm.afrilandfirstbank.rations.transmission.application.ResultatMiseAJourIntegration.AccuseContradictoire;
import cm.afrilandfirstbank.rations.transmission.application.ResultatMiseAJourIntegration.Applique;
import cm.afrilandfirstbank.rations.transmission.application.ResultatMiseAJourIntegration.DejaApplique;
import cm.afrilandfirstbank.rations.transmission.application.ResultatMiseAJourIntegration.ProcessusInconnu;
import cm.afrilandfirstbank.rations.transmission.application.ResultatMiseAJourIntegration.ProcessusNonTransmis;
import cm.afrilandfirstbank.rations.transmission.application.ResultatMiseAJourIntegration.ServiceWorkflowIndisponible;
import cm.afrilandfirstbank.rations.transmission.application.ResultatValidationAccuse.AccuseRecevable;
import cm.afrilandfirstbank.rations.transmission.application.StatutIntegrationClient;

/**
 * Appelle {@code PUT /processus/{id}/integration} du service Workflow, endpoint interne
 * du Sprint 5.2 (hors contrat passerelle).
 *
 * <p><b>Par l'API, jamais par la base</b> (diagramme AR04, guide 5.2 section 10). Aucune
 * source de donnees vers {@code rations_workflow} n'existe dans ce service.
 *
 * <h2>Traduction des reponses</h2>
 *
 * <table>
 *   <tr><td>{@code 200} + {@code resultat: APPLIQUE}</td><td>{@link Applique}</td></tr>
 *   <tr><td>{@code 200} + {@code resultat: DEJA_APPLIQUE}</td><td>{@link DejaApplique} — l'idempotence</td></tr>
 *   <tr><td>{@code 404}</td><td>{@link ProcessusInconnu}</td></tr>
 *   <tr><td>{@code 422}</td><td>{@link ProcessusNonTransmis}</td></tr>
 *   <tr><td>{@code 409}</td><td>{@link AccuseContradictoire}</td></tr>
 *   <tr><td>tout le reste</td><td>{@link ServiceWorkflowIndisponible} — <b>rejouable</b></td></tr>
 * </table>
 *
 * <p><b>Le classement par defaut est le prudent, mais dans l'autre sens qu'au Sprint
 * 5.1.</b> La-bas, ne pas savoir si un message etait parti interdisait de le rejouer :
 * un doute valait un double paiement possible. Ici, ne pas savoir si l'ecriture a abouti
 * commande au contraire de rejouer — l'ecriture est idempotente, la repeter ne produit
 * aucune ecriture comptable, et renoncer perdrait un statut de suivi. Le meme principe
 * de prudence, applique a deux operations de natures opposees, donne deux regles
 * opposees.
 *
 * <h2>Aucun jeton — et un secret qui ne sort jamais d'ici</h2>
 *
 * <p>Cet appel nait d'un message Kafka : aucun utilisateur n'est derriere lui, et le realm
 * ne porte aucun compte de service. Il presente donc un secret partage en en-tete,
 * dispositif provisoire arbitre au Sprint 5.2.
 *
 * <p><b>Sa valeur n'apparait dans aucun journal, aucun message d'erreur, aucune trace
 * d'audit</b> — pas meme sous forme de longueur ou de prefixe, qui sont deja des fuites.
 * Le garde-fou serait sans objet s'il fuyait par le canal meme cense le surveiller. Un
 * test de garde relit ce fichier pour le verifier.
 *
 * <p>Delais 2 s / 3 s, aucun reessai a ce niveau — les bornes du {@code RestClient.Builder}
 * du service. Le reessai, lui, est porte par le consommateur, qui seul sait qu'un echec est
 * temporaire.
 */
@Component
public class StatutIntegrationHttpClient implements StatutIntegrationClient {

    private static final Logger journal =
            LoggerFactory.getLogger(StatutIntegrationHttpClient.class);

    /**
     * En-tete portant le secret partage. <b>Seul le nom apparait ici et dans les
     * journaux ; la valeur, jamais.</b>
     */
    public static final String EN_TETE_CLE_INTERNE = "X-Cle-Interne";

    static final String RESULTAT_APPLIQUE = "APPLIQUE";
    static final String RESULTAT_DEJA_APPLIQUE = "DEJA_APPLIQUE";

    private final RestClient clientRest;
    private final String urlServiceWorkflow;
    private final String cleInterne;

    public StatutIntegrationHttpClient(RestClient.Builder constructeurRest,
            @Value("${app.workflow.url}") String urlServiceWorkflow,
            @Value("${app.workflow.cle-interne}") String cleInterne) {
        this.urlServiceWorkflow = urlServiceWorkflow;
        this.cleInterne = cleInterne;
        this.clientRest = constructeurRest.baseUrl(urlServiceWorkflow).build();
    }

    @Override
    public ResultatMiseAJourIntegration mettreAJour(AccuseRecevable accuse) {
        Long idProcessus = accuse.idProcessus();

        try {
            IntegrationReponse reponse = clientRest.put()
                    .uri("/processus/{id}/integration", idProcessus)
                    .header(EN_TETE_CLE_INTERNE, cleInterne)
                    .body(new IntegrationRequete(
                            accuse.statutIntegration().name(),
                            accuse.referenceComptable(),
                            accuse.dateTraitement() == null
                                    ? null
                                    : accuse.dateTraitement().toString(),
                            accuse.motif()))
                    .retrieve()
                    .body(IntegrationReponse.class);

            return interpreter(reponse, accuse);

        } catch (RestClientResponseException reponseEnErreur) {
            return interpreterRefus(reponseEnErreur, idProcessus);

        } catch (RestClientException panne) {
            journal.warn("Service Workflow injoignable a l'adresse {} pour l'accuse de l'etat "
                    + "{} : {}", urlServiceWorkflow, idProcessus, panne.getMessage());
            return new ServiceWorkflowIndisponible(idProcessus,
                    "appel a " + urlServiceWorkflow + " en echec : " + panne.getMessage());
        }
    }

    /**
     * Un {@code 200} dont le corps ne dit pas ce qui a ete fait est traite comme une
     * indisponibilite, donc rejoue.
     *
     * <p>Supposer {@code APPLIQUE} publierait une trace d'audit affirmant une ecriture
     * dont on ne sait rien ; supposer {@code DEJA_APPLIQUE} abandonnerait peut-etre un
     * accuse jamais applique. Rejouer est la seule issue qui n'invente rien — et elle est
     * sans risque, l'ecriture etant idempotente.
     */
    private ResultatMiseAJourIntegration interpreter(IntegrationReponse reponse,
            AccuseRecevable accuse) {

        Long idProcessus = accuse.idProcessus();

        if (reponse == null || reponse.resultat() == null) {
            journal.warn("Le service Workflow a repondu 200 sans resultat exploitable pour "
                    + "l'accuse de l'etat {}.", idProcessus);
            return new ServiceWorkflowIndisponible(idProcessus,
                    "reponse 200 sans resultat exploitable");
        }

        return switch (reponse.resultat()) {
            case RESULTAT_APPLIQUE -> new Applique(idProcessus, accuse.statutIntegration());
            case RESULTAT_DEJA_APPLIQUE ->
                    new DejaApplique(idProcessus, accuse.statutIntegration());
            default -> {
                journal.warn("Le service Workflow a rendu un resultat inconnu ({}) pour l'accuse "
                        + "de l'etat {}.", reponse.resultat(), idProcessus);
                yield new ServiceWorkflowIndisponible(idProcessus,
                        "resultat inconnu : " + reponse.resultat());
            }
        };
    }

    /**
     * Traduit un code de refus en issue.
     *
     * <p>Le corps de la reponse porte le message du service Workflow, qui nomme l'etat
     * courant et ce qui le contredit : il est repris tel quel, car c'est lui qui sera lu
     * six mois plus tard dans le journal d'audit. Le nom de l'en-tete du secret peut y
     * figurer, sa <b>valeur</b> jamais — le Workflow ne la renvoie pas.
     */
    private ResultatMiseAJourIntegration interpreterRefus(
            RestClientResponseException reponseEnErreur, Long idProcessus) {

        HttpStatus statut = HttpStatus.resolve(reponseEnErreur.getStatusCode().value());
        String corps = reponseEnErreur.getResponseBodyAsString();

        if (reponseEnErreur.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return new ProcessusInconnu(idProcessus);
        }
        if (reponseEnErreur.getStatusCode().isSameCodeAs(HttpStatus.UNPROCESSABLE_ENTITY)) {
            return new ProcessusNonTransmis(idProcessus,
                    "Le service Workflow refuse l'accuse : " + corps);
        }
        if (reponseEnErreur.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
            return new AccuseContradictoire(idProcessus,
                    "Le service Workflow refuse l'accuse : " + corps);
        }

        // 401 et 403 tombent ici : un secret partage refuse est une panne de
        // configuration, pas un accuse fautif. Rejouer laisse le temps de corriger la
        // variable d'environnement, et le prefixe de journal le dit.
        journal.warn("Le service Workflow a repondu {} pour l'accuse de l'etat {}. L'accuse sera "
                        + "rejoue. Corps : {}", statut, idProcessus, corps);
        return new ServiceWorkflowIndisponible(idProcessus,
                "reponse " + reponseEnErreur.getStatusCode());
    }

    /**
     * Corps envoye au service Workflow.
     *
     * <p>La date voyage en <b>chaine ISO 8601</b> et non en type temporel : le convertisseur
     * du service est celui de la charge comptable, et faire dependre la forme de cette date
     * d'un reglage Jackson partage reintroduirait exactement le defaut corrige au Sprint 2.2
     * sur {@code DeltaAudit}. Une chaine deja formee ne peut pas etre reformatee.
     */
    record IntegrationRequete(
            String statutIntegration,
            String referenceComptable,
            String dateTraitement,
            String motif) {
    }

    /** Ce que le service Workflow a fait de l'accuse. Tolerant reader. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record IntegrationReponse(Long idProcessus, String resultat, String statutIntegration) {
    }

}
