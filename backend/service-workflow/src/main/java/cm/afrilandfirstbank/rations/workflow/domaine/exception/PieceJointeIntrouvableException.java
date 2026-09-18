package cm.afrilandfirstbank.rations.workflow.domaine.exception;

/**
 * Le processus existe et la portée d'accès est acquise, mais aucune pièce
 * jointe n'a encore été produite pour lui : l'état n'a jamais été soumis.
 * Rendue en {@code 404 PIECE_JOINTE_INTROUVABLE}.
 *
 * <p>Distincte de {@link DocumentNonProduitException} (500), qui signale une
 * <b>panne d'écriture ou de relecture disque</b> alors que la base atteste
 * qu'un fichier existe. Ici, la base elle-même ne connaît aucune pièce jointe :
 * ce n'est pas une panne, c'est un dossier qui n'a simplement pas encore de
 * document (même raisonnement que les autres refus en 404 du contrôleur,
 * {@code ProcessusIntrouvableException}).
 *
 * <p>Non tracée en audit : une erreur d'usage (404) est une maladresse, pas
 * une tentative ({@code docs/publication-audit.md} section 4).
 */
public class PieceJointeIntrouvableException extends RuntimeException {

    public PieceJointeIntrouvableException(Long idProcessus) {
        super("Aucun document n'a encore été produit pour le processus " + idProcessus
                + " : l'état n'a jamais été soumis.");
    }

}
