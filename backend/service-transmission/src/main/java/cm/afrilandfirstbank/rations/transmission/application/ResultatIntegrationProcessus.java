package cm.afrilandfirstbank.rations.transmission.application;

/**
 * Les trois issues d'une lecture du bloc d'integration aupres du service Workflow
 * (Sprint 5.3).
 *
 * <p>Meme decoupage que {@link ResultatProcessus} au Sprint 5.1, et pour la meme raison :
 * un identifiant inconnu n'est pas une panne du reseau. Les confondre enverrait chercher
 * un incident d'infrastructure la ou il y a une faute de frappe dans une URL.
 *
 * <p>Le <b>refus d'acces</b> ne figure pas ici : il est relaye tel quel au client, en
 * {@code 403}. Le traduire en « indisponible » ferait croire a une panne a quelqu'un qui
 * consulte simplement un dossier hors de sa portee.
 */
public sealed interface ResultatIntegrationProcessus {

    /** {@code 200} exploitable. */
    record IntegrationObtenue(IntegrationProcessus integration)
            implements ResultatIntegrationProcessus {
    }

    /** {@code 404} : le service Workflow ne connait pas cet identifiant. */
    record ProcessusInconnu(Long idProcessus) implements ResultatIntegrationProcessus {
    }

    /**
     * Le service Workflow n'a rien repondu d'exploitable. <b>Refus conservateur</b>
     * (doctrine Sprint 1.3) : on ne devine pas un statut de paiement.
     */
    record ServiceWorkflowIndisponible(String motifTechnique)
            implements ResultatIntegrationProcessus {
    }

}
