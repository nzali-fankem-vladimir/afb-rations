package cm.afrilandfirstbank.rations.workflow.api.dto;

import java.util.List;

import cm.afrilandfirstbank.rations.workflow.application.ResultatRechercheProcessus;

/**
 * Reponse de l'endpoint <b>interne</b> {@code GET /processus/recherche}
 * (Sprint 6.1).
 *
 * <p>Trois champs, et le second est le plus important : {@code tronque} dit
 * explicitement « il y en a {@code nombreTotal}, je ne te les envoie pas », ce
 * qu'un simple contenu vide ne saurait pas dire. Le consommateur ne peut donc pas
 * confondre un dossier absent avec un dossier retenu.
 *
 * @param nombreTotal etats correspondant aux criteres, dans la portee de l'appelant
 * @param tronque le volume depasse la limite demandee ; {@code contenu} est vide
 * @param contenu en-tetes tries du plus recent au plus ancien
 */
public record RechercheProcessusResponse(
        long nombreTotal,
        boolean tronque,
        List<EnTeteProcessusResponse> contenu) {

    public static RechercheProcessusResponse depuis(ResultatRechercheProcessus resultat) {
        return new RechercheProcessusResponse(
                resultat.nombreTotal(),
                resultat.tronque(),
                resultat.contenu().stream().map(EnTeteProcessusResponse::depuis).toList());
    }

}
