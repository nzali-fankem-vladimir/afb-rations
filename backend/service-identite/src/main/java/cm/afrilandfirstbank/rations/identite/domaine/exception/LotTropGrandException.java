package cm.afrilandfirstbank.rations.identite.domaine.exception;

/**
 * Un lot d'identifiants soumis a {@code GET /identite/utilisateurs/libelles}
 * depasse la borne admise (Sprint 6.1).
 *
 * <p>Refus en {@code 400 LOT_TROP_GRAND} : la requete elle-meme est mal formee,
 * l'appelant n'a rien a corriger dans l'etat du systeme — il a simplement demande
 * trop d'un coup. Distinct d'un {@code 422}, qui designe dans ce module une regle
 * de gestion qui refuse une operation par ailleurs bien formee.
 */
public class LotTropGrandException extends RuntimeException {

    public LotTropGrandException(String message) {
        super(message);
    }

}
