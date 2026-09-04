package cm.afrilandfirstbank.rations.reporting.domaine.exception;

/**
 * Le paramètre {@code periode} de {@code GET /reporting/demandes} n'est pas au
 * format {@code AAAA-MM} (Sprint 6.1).
 *
 * <p>{@code 400 PERIODE_INVALIDE} : la requête elle-même est mal formée, rien à
 * corriger côté état du système — l'appelant a simplement mal écrit le paramètre.
 */
public class PeriodeInvalideException extends RuntimeException {

    public PeriodeInvalideException(String message) {
        super(message);
    }

}
