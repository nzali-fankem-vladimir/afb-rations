package cm.afrilandfirstbank.rations.workflow.application;

/**
 * Ce que la cloture a donne du cote de la comptabilite, rendu au valideur dans la reponse
 * de sa validation.
 *
 * <p><b>C'est le seul signal qu'un humain recoit.</b> Aucune reprise automatique n'est
 * possible — le realm {@code afb-rations-dev} n'a qu'un client public,
 * {@code serviceAccountsEnabled: false}, et une tache programmee n'aurait aucun jeton a
 * relayer aux appels de la chaine. La personne qui cloture est donc, aujourd'hui, la seule
 * a pouvoir constater qu'un etat n'est pas parti. Ne rien lui dire reviendrait a laisser un
 * etat fige et impaye passer inapercu jusqu'a ce que quelqu'un s'en plaigne.
 *
 * @param transmis vrai si et seulement si le broker a accuse reception. Faux couvre aussi
 *        bien une panne qu'un rejet par saturation du pool : dans les deux cas
 *        {@code transmis_comptabilite} reste a faux et l'etat est a reprendre
 * @param motif ce qui a empeche la transmission, nul quand elle a abouti. Destine a etre
 *        lu par une personne, pas analyse par un programme
 * @param tentatives nombre de tentatives reellement faites. Deux au plus, et seulement
 *        pour des echecs dont on sait qu'aucun message n'est parti — voir
 *        {@link ResultatDemandeTransmission}
 */
public record ResultatTransmissionCloture(boolean transmis, String motif, int tentatives) {

    public static ResultatTransmissionCloture reussie(int tentatives) {
        return new ResultatTransmissionCloture(true, null, tentatives);
    }

    public static ResultatTransmissionCloture echouee(String motif, int tentatives) {
        return new ResultatTransmissionCloture(false, motif, tentatives);
    }

}
