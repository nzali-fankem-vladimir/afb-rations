package cm.afrilandfirstbank.rations.reporting.infrastructure.workflow;

import java.time.LocalDate;
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

import cm.afrilandfirstbank.rations.reporting.application.ResultatHistorique;
import cm.afrilandfirstbank.rations.reporting.application.ResultatRechercheDemandes;
import cm.afrilandfirstbank.rations.reporting.application.WorkflowLectureClient;
import cm.afrilandfirstbank.rations.reporting.domaine.EnTeteDemande;
import cm.afrilandfirstbank.rations.reporting.domaine.HistoriqueDemande;
import cm.afrilandfirstbank.rations.reporting.domaine.HistoriqueDemande.EtapeHistorique;

/**
 * Appels de {@code GET /processus/recherche} et
 * {@code GET /processus/{id}/historique}, endpoints internes du service Workflow
 * (Sprint 6.1).
 *
 * <h2>Le classement des issues est tout le travail de cette classe</h2>
 *
 * <p>Un {@code 403} veut dire « le service Workflow a repondu : cette personne n'a
 * pas ce droit », ce qui est un refus legitime a <b>relayer tel quel</b>. Un
 * {@code 404} veut dire « ce dossier n'existe pas ». Un {@code 500}, un delai
 * depasse ou une connexion refusee veulent dire « on ne sait pas ». Les confondre
 * enverrait un lecteur habilite reclamer un droit qu'il possede deja pendant que
 * l'incident resterait invisible — c'est la lecon des Sprints 2.2 et 5.3.
 *
 * <p>Le message de refus est <b>repris tel quel</b> quand le corps d'erreur en
 * porte un : le service qui detient la regle la formule mieux qu'une phrase
 * reecrite en chemin.
 */
@Component
public class WorkflowLectureHttpClient implements WorkflowLectureClient {

    private static final Logger journal = LoggerFactory.getLogger(WorkflowLectureHttpClient.class);

    private final RestClient clientRest;
    private final String urlServiceWorkflow;

    public WorkflowLectureHttpClient(RestClient.Builder constructeurRest,
            @Value("${app.workflow.url}") String urlServiceWorkflow) {
        this.urlServiceWorkflow = urlServiceWorkflow;
        this.clientRest = constructeurRest.baseUrl(urlServiceWorkflow).build();
    }

    @Override
    public ResultatRechercheDemandes rechercher(LocalDate dateDebut, LocalDate dateFin, String codeUnite,
            String statut, int limite, String enteteAutorisation) {

        exigerJeton(enteteAutorisation);

        try {
            ReponseWorkflow.Recherche reponse = clientRest.get()
                    .uri(uri -> construireUriRecherche(uri, dateDebut, dateFin, codeUnite, statut, limite))
                    .header(HttpHeaders.AUTHORIZATION, enteteAutorisation)
                    .retrieve()
                    .body(ReponseWorkflow.Recherche.class);

            return interpreterRecherche(reponse);

        } catch (RestClientResponseException reponseEnErreur) {
            if (reponseEnErreur.getStatusCode() == HttpStatus.FORBIDDEN) {
                return new ResultatRechercheDemandes.AccesRefuse(
                        messageDuRefus(reponseEnErreur, "vous n'avez pas de droit sur cette unite"));
            }
            journal.warn("Le service Workflow a repondu {} sur /processus/recherche.",
                    reponseEnErreur.getStatusCode());
            return new ResultatRechercheDemandes.ServiceIndisponible(
                    "reponse " + reponseEnErreur.getStatusCode());

        } catch (RestClientException panne) {
            journal.warn("Service Workflow injoignable a l'adresse {} sur /processus/recherche : {}",
                    urlServiceWorkflow, panne.getMessage());
            return new ResultatRechercheDemandes.ServiceIndisponible(
                    "appel a " + urlServiceWorkflow + " en echec : " + panne.getMessage());
        }
    }

    @Override
    public ResultatHistorique consulterHistorique(Long idProcessus, String enteteAutorisation) {
        exigerJeton(enteteAutorisation);

        try {
            ReponseWorkflow.Historique reponse = clientRest.get()
                    .uri("/processus/{id}/historique", idProcessus)
                    .header(HttpHeaders.AUTHORIZATION, enteteAutorisation)
                    .retrieve()
                    .body(ReponseWorkflow.Historique.class);

            return interpreterHistorique(reponse, idProcessus);

        } catch (RestClientResponseException reponseEnErreur) {
            if (reponseEnErreur.getStatusCode() == HttpStatus.NOT_FOUND) {
                return new ResultatHistorique.ProcessusIntrouvable(messageDuRefus(reponseEnErreur,
                        "aucun etat mensuel ne porte l'identifiant " + idProcessus));
            }
            if (reponseEnErreur.getStatusCode() == HttpStatus.FORBIDDEN) {
                return new ResultatHistorique.AccesRefuse(messageDuRefus(reponseEnErreur,
                        "vous n'avez pas de droit sur l'unite de ce dossier"));
            }
            journal.warn("Le service Workflow a repondu {} sur /processus/{}/historique.",
                    reponseEnErreur.getStatusCode(), idProcessus);
            return new ResultatHistorique.ServiceIndisponible(
                    "reponse " + reponseEnErreur.getStatusCode());

        } catch (RestClientException panne) {
            journal.warn("Service Workflow injoignable a l'adresse {} sur /processus/{}/historique : {}",
                    urlServiceWorkflow, idProcessus, panne.getMessage());
            return new ResultatHistorique.ServiceIndisponible(
                    "appel a " + urlServiceWorkflow + " en echec : " + panne.getMessage());
        }
    }

    private java.net.URI construireUriRecherche(UriBuilder uri, LocalDate dateDebut, LocalDate dateFin,
            String codeUnite, String statut, int limite) {

        uri.path("/processus/recherche").queryParam("limite", limite);
        if (dateDebut != null) {
            uri.queryParam("dateDebut", dateDebut);
        }
        if (dateFin != null) {
            uri.queryParam("dateFin", dateFin);
        }
        if (codeUnite != null) {
            uri.queryParam("codeUnite", codeUnite);
        }
        if (statut != null) {
            uri.queryParam("statut", statut);
        }
        return uri.build();
    }

    /**
     * Une reponse {@code 200} incomplete est traitee comme une panne, jamais comme
     * un resultat vide.
     *
     * <p>C'est la meme discipline que partout ailleurs dans le module : la valeur par
     * defaut la plus tentante — « pas de contenu, donc aucun etat » — serait la pire,
     * puisqu'elle est indiscernable d'une absence legitime de dossiers.
     */
    private ResultatRechercheDemandes interpreterRecherche(ReponseWorkflow.Recherche reponse) {
        if (reponse == null || reponse.nombreTotal() == null || reponse.tronque() == null) {
            journal.warn("Le service Workflow a repondu 200 sans resultat exploitable sur "
                    + "/processus/recherche (nombreTotal ou tronque absent).");
            return new ResultatRechercheDemandes.ServiceIndisponible(
                    "reponse 200 sans nombreTotal ni indicateur de troncature");
        }

        if (reponse.tronque()) {
            return new ResultatRechercheDemandes.TropDeResultats(reponse.nombreTotal());
        }

        List<ReponseWorkflow.EnTete> contenu =
                reponse.contenu() == null ? List.of() : reponse.contenu();

        return new ResultatRechercheDemandes.Obtenue(contenu.stream()
                .map(WorkflowLectureHttpClient::versEnTete)
                .toList());
    }

    private ResultatHistorique interpreterHistorique(ReponseWorkflow.Historique reponse,
            Long idProcessus) {

        if (reponse == null || reponse.idProcessus() == null) {
            journal.warn("Le service Workflow a repondu 200 sans historique exploitable sur "
                    + "/processus/{}/historique.", idProcessus);
            return new ResultatHistorique.ServiceIndisponible(
                    "reponse 200 sans identifiant de processus");
        }

        List<ReponseWorkflow.Etape> etapes = reponse.etapes() == null ? List.of() : reponse.etapes();

        return new ResultatHistorique.Obtenu(new HistoriqueDemande(
                reponse.idProcessus(),
                reponse.dateDebut(),
                reponse.dateFin(),
                reponse.codeUnite(),
                reponse.statut(),
                etapes.stream().map(WorkflowLectureHttpClient::versEtape).toList()));
    }

    private static EnTeteDemande versEnTete(ReponseWorkflow.EnTete brut) {
        return new EnTeteDemande(
                brut.id(),
                brut.dateDebut(),
                brut.dateFin(),
                brut.codeUnite(),
                brut.typeProcessus(),
                brut.montantTotal() == null ? 0 : brut.montantTotal(),
                brut.statut(),
                Boolean.TRUE.equals(brut.transmisComptabilite()),
                brut.statutIntegration(),
                brut.dateCreation());
    }

    private static EtapeHistorique versEtape(ReponseWorkflow.Etape brut) {
        return new EtapeHistorique(
                brut.ordreEtape() == null ? 0 : brut.ordreEtape(),
                brut.nomEtape(),
                brut.statutEtape(),
                brut.idActeur(),
                null,
                null,
                brut.motifRetour(),
                Boolean.TRUE.equals(brut.signee()),
                brut.dateAction());
    }

    /** Le message du service qui a refuse, ou un repli quand le corps n'en porte pas. */
    private String messageDuRefus(RestClientResponseException erreur, String repli) {
        try {
            ReponseWorkflow.Erreur corps = erreur.getResponseBodyAs(ReponseWorkflow.Erreur.class);
            if (corps != null && corps.message() != null && !corps.message().isBlank()) {
                return corps.message();
            }
        } catch (RuntimeException corpsIllisible) {
            journal.debug("Corps d'erreur du service Workflow illisible : {}",
                    corpsIllisible.getMessage());
        }
        return repli;
    }

    private void exigerJeton(String enteteAutorisation) {
        if (enteteAutorisation == null || enteteAutorisation.isBlank()) {
            throw new IllegalStateException(
                    "Aucun en-tete Authorization a relayer au service Workflow : "
                            + "l'appelant doit propager le jeton de l'utilisateur final.");
        }
    }

}
