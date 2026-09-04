package cm.afrilandfirstbank.rations.reporting.domaine.exception;

/**
 * Le paramètre {@code format} de {@code GET /reporting/rapports/export} ne vaut ni
 * {@code pdf} ni {@code excel} (Sprint 6.2, guide §6). {@code 422 FORMAT_EXPORT_INVALIDE}.
 */
public class FormatExportInvalideException extends RuntimeException {

    public FormatExportInvalideException(String message) {
        super(message);
    }

}
