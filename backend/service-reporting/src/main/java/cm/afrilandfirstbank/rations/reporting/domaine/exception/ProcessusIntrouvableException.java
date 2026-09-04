package cm.afrilandfirstbank.rations.reporting.domaine.exception;

/**
 * Aucun etat mensuel ne porte cet identifiant. {@code 404 PROCESSUS_INTROUVABLE}.
 *
 * <p>Distinct d'un refus de portee : un lecteur legitime ne doit pas reclamer une
 * habilitation qu'il possede deja pour un dossier qui n'existe pas.
 */
public class ProcessusIntrouvableException extends RuntimeException {

    public ProcessusIntrouvableException(String message) {
        super(message);
    }

}
