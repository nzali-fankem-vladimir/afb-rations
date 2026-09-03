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
 * @param motif ce qui a empeche la transmission, nul quand elle vient d'aboutir. Non nul
 *        avec {@code transmis} a vrai dans un seul cas : l'etat avait <b>deja</b> ete
 *        transmis et le verrou de RG-13 a refuse la seconde publication. Destine a etre lu
 *        par une personne, pas analyse par un programme
 * @param tentatives nombre de tentatives reellement faites. Deux au plus, et seulement
 *        pour des echecs dont on sait qu'aucun message n'est parti — voir
 *        {@link ResultatDemandeTransmission}
 */
public record ResultatTransmissionCloture(boolean transmis, String motif, int tentatives) {

    public static ResultatTransmissionCloture reussie(int tentatives) {
        return new ResultatTransmissionCloture(true, null, tentatives);
    }

    /**
     * L'etat avait deja ete transmis : le verrou de RG-13 a refuse la seconde publication
     * (Sprint 5.3).
     *
     * <p>{@code transmis} vaut <b>vrai</b>, et c'est la seule lecture honnete : du point
     * de vue du valideur comme de celui de la comptabilite, l'etat est bien parti — une
     * fois, et une seule. Le rendre faux enverrait quelqu'un le retransmettre, c'est-a-dire
     * exactement ce que RG-13 interdit.
     *
     * <p>Le motif est neanmoins renseigne, contrairement a une transmission ordinaire : le
     * valideur doit pouvoir distinguer « c'est parti a l'instant » de « c'etait deja
     * parti », et savoir depuis quand.
     */
    public static ResultatTransmissionCloture dejaTransmise(String message, int tentatives) {
        return new ResultatTransmissionCloture(true, message, tentatives);
    }

    public static ResultatTransmissionCloture echouee(String motif, int tentatives) {
        return new ResultatTransmissionCloture(false, motif, tentatives);
    }

}
