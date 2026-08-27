package cm.afrilandfirstbank.rations.identite.domaine.exception;

/**
 * La modification retirerait le role ADMIN au dernier administrateur actif du
 * systeme (sous-sprint 1.2, decision
 * {@code docs/decisions/2026-08-26-attribution-role-administrateur.md}). Se
 * traduit par un 409.
 */
public class DernierAdministrateurException extends RuntimeException {

    public DernierAdministrateurException(String message) {
        super(message);
    }

}
