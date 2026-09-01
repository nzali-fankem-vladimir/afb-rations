package cm.afrilandfirstbank.rations.workflow.domaine.exception;

/**
 * Le service Saisie n'a rien repondu d'exploitable a la demande de consolidation :
 * timeout, connexion refusee, {@code 5xx}, corps illisible.
 *
 * <p><b>Refus conservateur</b>, comme pour Identite (Sprint 1.3) et Grilles
 * (Sprint 2.4) : sans consolidation, il n'y a pas de montant total
 * ({@code docs/appel-consolidation.md} section 6, point C-02 du Sprint 3.4).
 *
 * <p>Rendue en {@code 503 SERVICE_SAISIE_INDISPONIBLE}, code retenu par symetrie
 * avec l'existant et annonce par la convention d'appel.
 *
 * <p><b>Ce que ce refus protege.</b> Aux sous-sprints suivants, il interdira
 * d'enregistrer un {@code montant_total} partiel, a zero ou repris d'une lecture
 * anterieure. Un montant faux autour du seuil de 100 000 XAF envoie le dossier au
 * mauvais niveau de validation, et rien ne le signale.
 */
public class ServiceSaisieIndisponibleException extends RuntimeException {

    public ServiceSaisieIndisponibleException(String message) {
        super(message);
    }

}
