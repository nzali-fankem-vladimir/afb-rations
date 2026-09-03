package cm.afrilandfirstbank.rations.transmission.application;

/**
 * Port sortant : « ou en est cet etat vis-a-vis de la comptabilite ? » Question posee au
 * service Workflow, qui detient {@code processus_mensuel} (Sprint 5.3).
 *
 * <p><b>Par l'API, jamais par la base</b>, et sans cache : le statut d'integration change
 * a la reception d'un accuse, et servir une valeur memorisee ferait dire au suivi qu'un
 * etat attend encore alors qu'il est paye — ou l'inverse.
 *
 * <p>La <b>portee d'acces</b> est verifiee par l'appele, sur le jeton relaye : un chef
 * d'unite de {@code 00002} n'apprend rien d'un dossier de {@code 00007}. Ce service ne la
 * rejoue pas, il n'aurait rien a y ajouter.
 */
public interface IntegrationProcessusClient {

    /**
     * @param idProcessus l'etat dont on demande la suite comptable
     * @param enteteAutorisation en-tete {@code Authorization} de l'utilisateur final,
     *        relaye tel quel (doctrine Sprint 1.3)
     * @return l'une des trois issues, jamais {@code null}
     */
    ResultatIntegrationProcessus obtenir(Long idProcessus, String enteteAutorisation);

}
