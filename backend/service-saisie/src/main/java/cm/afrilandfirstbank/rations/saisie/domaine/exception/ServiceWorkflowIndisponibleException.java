package cm.afrilandfirstbank.rations.saisie.domaine.exception;

/**
 * Le service Workflow n'a pas repondu de facon exploitable : delai depasse,
 * connexion refusee, {@code 5xx}, corps illisible, ou statut inconnu du present
 * service. L'operation est refusee — <b>refus technique</b>.
 *
 * <p><b>Refus conservateur</b> (fail-closed) : troisieme application de la
 * doctrine posee au Sprint 1.3 pour le service Identite et etendue au service
 * Grilles au Sprint 2.4. Accepter l'ecriture « en pariant que l'etat est
 * modifiable » reviendrait a laisser entrer une ligne dans un etat peut-etre
 * deja valide par le Chef d'Unite : le montant qu'il a valide ne serait alors
 * plus celui transmis a la comptabilite. C'est un defaut de controle interne,
 * pas une gene d'exploitation.
 *
 * <p>Traduite en {@code 503 SERVICE_WORKFLOW_INDISPONIBLE}, sur le modele de
 * {@code SERVICE_GRILLES_INDISPONIBLE} (Sprint 3.2) et de
 * {@code SERVICE_IDENTITE_INDISPONIBLE} (Sprint 2.2). Pas de {@code 422} : la
 * saisie de l'agent ne viole aucune regle, c'est le systeme qui ne peut pas
 * repondre.
 *
 * <p><b>Un statut inconnu passe par ici.</b> Si Workflow renvoie un statut que
 * {@code StatutProcessusEnum} ne connait pas, le service ne tombe pas mais ne
 * l'interprete pas non plus comme modifiable : tolerant a la lecture, ferme a la
 * decision.
 *
 * <p>Le motif technique est journalise, jamais affiche a l'agent.
 */
public class ServiceWorkflowIndisponibleException extends RuntimeException {

    public ServiceWorkflowIndisponibleException(String message) {
        super(message);
    }

}
