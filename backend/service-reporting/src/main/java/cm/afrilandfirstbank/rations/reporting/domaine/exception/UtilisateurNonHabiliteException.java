package cm.afrilandfirstbank.rations.reporting.domaine.exception;

/**
 * Refus de portee prononce par un service en amont, relaye tel quel.
 *
 * <p>{@code 403 UTILISATEUR_NON_HABILITE}. Distinct d'{@code ACCES_REFUSE}, qui designe
 * un role hors circuit : les deux appellent deux gestes differents (distinction du
 * Sprint 4.4).
 */
public class UtilisateurNonHabiliteException extends RuntimeException {

    public UtilisateurNonHabiliteException(String message) {
        super(message);
    }

}
