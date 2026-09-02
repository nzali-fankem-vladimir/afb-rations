package cm.afrilandfirstbank.rations.transmission.domaine.exception;

/**
 * Le service Workflow ne connait pas ce processus ({@code 404 PROCESSUS_INTROUVABLE}).
 *
 * <p>Distinct d'une panne : un identifiant inconnu est un appel fautif, et les confondre
 * enverrait chercher un incident d'infrastructure la ou il y a une erreur d'appel.
 */
public class ProcessusIntrouvableException extends RuntimeException {

    public ProcessusIntrouvableException(Long idProcessus) {
        super("Aucun processus " + idProcessus + " n'est connu du service Workflow : la demande "
                + "de transmission porte un identifiant errone. Rien n'a ete publie.");
    }

}
