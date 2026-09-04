package cm.afrilandfirstbank.rations.reporting.domaine.exception;

/**
 * La composition d'un export (PDF ou Excel) a échoué. {@code 500 EXPORT_IMPOSSIBLE}.
 *
 * <p>Même parti que {@code DocumentNonProduitException} du service Workflow
 * (Sprint 4.2) : un export partiel n'est jamais rendu, et l'échec n'est pas une
 * règle de gestion qui refuse — c'est une panne de production documentaire.
 */
public class ExportImpossibleException extends RuntimeException {

    public ExportImpossibleException(String message, Throwable cause) {
        super(message, cause);
    }

}
