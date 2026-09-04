package cm.afrilandfirstbank.rations.reporting.domaine.exception;

/**
 * La recherche depasse la borne de volume : il y a des resultats, mais trop pour etre montres.
 *
 * <p>Refus en {@code 422 RECHERCHE_TROP_LARGE}, jamais une page vide. Le message nomme
 * le nombre trouve, la borne et l'action attendue : une page vide ferait conclure a une
 * absence de dossiers alors qu'il y en a des milliers qui ne sont pas montres.
 */
public class RechercheTropLargeException extends RuntimeException {

    public RechercheTropLargeException(String message) {
        super(message);
    }

}
