package cm.afrilandfirstbank.rations.transmission.application;

/**
 * Les trois issues d'une lecture d'en-tete aupres du service Workflow.
 *
 * <p><b>Trois et non deux</b>, contrairement a la consolidation : un identifiant
 * inconnu est ici une issue a part entiere. Une demande de transmission portant sur un
 * processus qui n'existe pas n'est pas une panne du reseau — c'est un appel fautif, et
 * les confondre enverrait chercher un incident d'infrastructure la ou il y a un
 * defaut d'appel.
 *
 * <p>Type scelle, sans champ booleen : la panne n'a pas de champ « etat » a lire a
 * moitie, et le {@code switch} qui les traite est exhaustif.
 */
public sealed interface ResultatProcessus {

    /** {@code 200} exploitable. Le statut de l'etat reste a verifier par l'appelant. */
    record ProcessusObtenu(EnTeteProcessus enTete) implements ResultatProcessus {
    }

    /** {@code 404} : le service Workflow ne connait pas cet identifiant. */
    record ProcessusIntrouvable(Long idProcessus) implements ResultatProcessus {
    }

    /**
     * Le service Workflow n'a rien repondu d'exploitable : timeout, connexion refusee,
     * {@code 4xx} autre que {@code 404}, {@code 5xx}, corps illisible. <b>Refus
     * conservateur</b> (doctrine Sprint 1.3) : on ne publie rien vers la comptabilite
     * sur une lecture incertaine.
     */
    record ServiceWorkflowIndisponible(String motifTechnique) implements ResultatProcessus {
    }

}
