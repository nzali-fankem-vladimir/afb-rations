package cm.afrilandfirstbank.rations.reporting.domaine.exception;

/**
 * Le service Workflow n'a pas repondu. {@code 503 SERVICE_WORKFLOW_INDISPONIBLE}.
 *
 * <p>Aucun resultat n'est concevable sans lui : les en-tetes des etats viennent de sa
 * base et de nulle part ailleurs.
 */
public class ServiceWorkflowIndisponibleException extends RuntimeException {

    public ServiceWorkflowIndisponibleException(String message) {
        super(message);
    }

}
