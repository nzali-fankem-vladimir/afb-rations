package cm.afrilandfirstbank.rations.workflow.domaine.exception;

/**
 * Aucun processus mensuel ne porte cet identifiant. Rendue en
 * {@code 404 PROCESSUS_INTROUVABLE}.
 *
 * <p>Non tracee en audit : une erreur d'usage (404) est une maladresse, pas une
 * tentative ({@code docs/publication-audit.md} section 4).
 */
public class ProcessusIntrouvableException extends RuntimeException {

    public ProcessusIntrouvableException(Long idProcessus) {
        super("Aucun processus mensuel ne porte l'identifiant " + idProcessus + ".");
    }

}
