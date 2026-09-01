package cm.afrilandfirstbank.rations.workflow.domaine.exception;

/**
 * Le service Identite n'a rien repondu d'exploitable : timeout, connexion
 * refusee, {@code 5xx}, corps illisible. <b>Refus conservateur</b> — l'operation
 * est refusee comme si le verdict etait negatif (doctrine Sprint 1.3,
 * {@code docs/appel-habilitation.md} section 3).
 *
 * <p>Rendue en {@code 503 SERVICE_IDENTITE_INDISPONIBLE}, et non en {@code 403} :
 * l'utilisateur possede peut-etre parfaitement le droit qu'il exerce ; un
 * {@code 403} l'enverrait reclamer une habilitation qu'il a deja, pendant que la
 * panne resterait invisible (decision Sprint 2.2).
 *
 * <p>Tracee en audit ({@code ACCES_REFUSE}, motif {@code IDENTITE_INDISPONIBLE}) :
 * un refus reste un refus, meme d'origine technique.
 */
public class ServiceIdentiteIndisponibleException extends RuntimeException {

    public ServiceIdentiteIndisponibleException(String message) {
        super(message);
    }

}
