package cm.afrilandfirstbank.rations.workflow.api.dto;

/**
 * Corps de {@code GET /parametres/fonctionnalites} : ce que le frontend a le droit
 * d'afficher (Sprint 6bis.1, {@code docs/dispositifs_provisoires.md} section 1.4).
 *
 * <pre>
 *   { "rattrapageActif": false }
 * </pre>
 *
 * <h2>Un seul champ aujourd'hui, et c'est voulu</h2>
 *
 * <p>La forme est celle du dispositif, au nom pres : un objet, un champ par
 * fonctionnalite pilotee par drapeau. Rendre un booleen nu
 * ({@code false}) aurait ferme la porte : ajouter un second drapeau aurait alors
 * change le <b>type</b> de la reponse, donc casse le frontend, la ou un champ
 * supplementaire est ignore par un lecteur tolerant.
 *
 * <h2>Ce que ce champ ne remplace pas</h2>
 *
 * <p>Il sert a <b>masquer</b> une entree de menu, pas a autoriser quoi que ce soit.
 * Le controle qui compte est celui du backend, en tete de
 * {@code OuvertureComplementaireService} : un frontend modifie, ou un appel direct a
 * {@code POST /processus}, se heurte au meme refus. Le masquage n'est qu'un confort
 * d'usage — meme principe qu'au Sprint 7F.3 pour le filtrage de la barre laterale.
 */
public record FonctionnalitesActivesResponse(boolean rattrapageActif) {
}
