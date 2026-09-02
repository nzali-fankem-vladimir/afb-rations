package cm.afrilandfirstbank.rations.transmission.domaine.exception;

/**
 * Le service Workflow n'a rien repondu d'exploitable
 * ({@code 503 SERVICE_WORKFLOW_INDISPONIBLE}).
 *
 * <p><b>Refus conservateur</b> (doctrine Sprint 1.3) : on ne publie rien vers la
 * comptabilite sur une lecture incertaine. {@code 503} et non {@code 403} ou
 * {@code 500}, pour la raison du Sprint 2.2 : l'appelant n'a rien fait de faux, et un
 * autre code l'enverrait chercher une faute inexistante pendant que la panne resterait
 * invisible.
 */
public class ServiceWorkflowIndisponibleException extends RuntimeException {

    public ServiceWorkflowIndisponibleException(Long idProcessus, String motifTechnique) {
        super("Le service Workflow est momentanement indisponible : la transmission de l'etat "
                + idProcessus + " est refusee par precaution (" + motifTechnique + "). Rien n'a "
                + "ete publie, et l'etat reste non transmis.");
    }

}
