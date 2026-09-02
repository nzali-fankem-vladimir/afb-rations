package cm.afrilandfirstbank.rations.transmission.application;

/**
 * Port sortant : « quel est l'en-tete de ce processus, et ou en est-il ? » Question
 * posee au service Workflow, qui detient {@code processus_mensuel}.
 *
 * <p><b>Pourquoi une interface</b>, et non un appel direct : meme justification qu'au
 * Sprint 3.2 pour {@code ResolutionMontantClient} et au Sprint 3.4 pour
 * {@code ConsolidationClient}. L'appel est une dependance technique ; la construction
 * de la charge doit pouvoir etre eprouvee sans reseau, et
 * {@code ResultatProcessus.ServiceWorkflowIndisponible} ne doit pas faire entrer une
 * notion de panne HTTP dans les regles de transmission.
 *
 * <p><b>Aucun cache.</b> Le statut et le drapeau de transmission changent, et servir
 * une valeur memorisee reviendrait a publier vers la comptabilite un etat qui n'est
 * plus celui qu'on a lu — ou a le publier deux fois.
 */
public interface ProcessusClient {

    /**
     * @param idProcessus l'etat dont on demande l'en-tete
     * @param enteteAutorisation en-tete {@code Authorization} de l'utilisateur final,
     *        relaye tel quel (doctrine Sprint 1.3) : ce service ne s'authentifie pas
     *        avec un compte technique, aucune identite machine n'existant au realm
     * @return l'une des trois issues, jamais {@code null}
     */
    ResultatProcessus obtenir(Long idProcessus, String enteteAutorisation);

}
