package cm.afrilandfirstbank.rations.saisie.domaine.exception;

/**
 * Le service Grilles n'a pas repondu de facon exploitable : delai depasse,
 * connexion refusee, {@code 4xx}, {@code 5xx}, corps illisible. La ligne est
 * refusee — <b>refus technique</b>.
 *
 * <p><b>Refus conservateur</b> (fail-closed) : doctrine posee au Sprint 1.3 pour
 * le service Identite, etendue au service Grilles au Sprint 2.4. Aucune ligne
 * n'est enregistree sans montant connu, en attente d'une valorisation
 * ulterieure — {@code ligne_prestation.montant_applique} n'admet pas de valeur
 * absente, et une ligne a montant incertain est exactement le risque que RG-03
 * ecarte.
 *
 * <p>Traduite en {@code 503 SERVICE_GRILLES_INDISPONIBLE} par le gestionnaire
 * d'erreurs de l'API (Sprint 3.3), <b>et non en {@code 422}</b> : meme parti
 * qu'au Sprint 2.2 pour {@code SERVICE_IDENTITE_INDISPONIBLE}. Un {@code 422}
 * dirait a l'agent que sa saisie viole une regle de gestion, et l'enverrait
 * reclamer un tarif a l'ARH pendant qu'un serveur est a terre — la panne
 * resterait invisible.
 *
 * <p>Le motif technique ({@code cause}) est journalise, jamais affiche a
 * l'agent : une adresse de service ou une trace d'infrastructure ne lui apprend
 * rien qu'il puisse utiliser.
 */
public class ServiceGrillesIndisponibleException extends RuntimeException {

    public ServiceGrillesIndisponibleException(String message) {
        super(message);
    }

}
