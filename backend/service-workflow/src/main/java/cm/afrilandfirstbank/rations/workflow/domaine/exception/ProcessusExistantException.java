package cm.afrilandfirstbank.rations.workflow.domaine.exception;

/**
 * Un processus NORMAL existe deja pour ce couple unite / periode. Rendue en
 * {@code 409 PROCESSUS_EXISTANT}.
 *
 * <p><b>Le refus vient du service, pas seulement de la base.</b> L'index partiel
 * {@code ux_processus_normal_par_periode} (migration V1) garantit l'unicite, mais
 * une contrainte violee produirait un message technique illisible. Le message
 * ci-dessous nomme le processus deja ouvert et son statut : l'agent sait alors
 * qu'il doit rejoindre un dossier existant, pas en creer un autre (point de
 * vigilance du guide 4.1).
 *
 * <p>{@code 409} et non {@code 422} : quelque chose est bien duplique — c'est
 * exactement la distinction posee au Sprint 2.3 entre les conflits d'unicite et
 * les regles de gestion.
 */
public class ProcessusExistantException extends RuntimeException {

    public ProcessusExistantException(String message) {
        super(message);
    }

}
