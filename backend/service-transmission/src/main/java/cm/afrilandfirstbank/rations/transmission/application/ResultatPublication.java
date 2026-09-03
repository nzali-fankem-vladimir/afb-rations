package cm.afrilandfirstbank.rations.transmission.application;

/**
 * Les trois issues d'une publication sur {@code rations.etat.valide}.
 *
 * <h2>Deux echecs, et la difference decide du sort du verrou de RG-13</h2>
 *
 * <p>Au Sprint 5.1, un seul echec suffisait : l'appelant devait repondre par oui ou par
 * non a « le broker a-t-il pris cet evenement ? », parce que de cette reponse dependait
 * l'ecriture du drapeau {@code transmis_comptabilite}.
 *
 * <p>Le Sprint 5.3 renverse l'ordre — le drapeau est <b>reserve avant</b> de publier — et
 * la question devient : faut-il rendre la reservation ? Elle ne se tranche que sur la
 * <b>preuve</b> qu'aucun evenement n'est parti :
 *
 * <table>
 *   <caption>Ce que chaque issue autorise</caption>
 *   <tr><th>Issue</th><th>Un message a-t-il atteint le broker ?</th><th>Verrou</th></tr>
 *   <tr><td>{@link Publiee}</td><td>oui, confirme</td><td><b>confirme</b></td></tr>
 *   <tr><td>{@link EchecAvantEnvoi}</td><td><b>non</b>, rien n'a quitte la machine</td><td><b>libere</b> : une reprise est possible</td></tr>
 *   <tr><td>{@link EchecIssueIncertaine}</td><td><b>peut-etre</b></td><td><b>conserve</b> : on ne rejoue pas ce qui a pu partir</td></tr>
 * </table>
 *
 * <p>Le classement par defaut est le prudent : tout ce qui n'est pas <i>prouve</i>
 * anterieur a l'envoi est incertain. Entre risquer un double paiement et risquer un etat
 * repute transmis qu'une personne devra reprendre, on choisit le second — il se voit,
 * grace a l'horodatage de la reservation, et il se repare.
 *
 * <p>C'est le symetrique exact de {@code ResultatDemandeTransmission} cote service
 * Workflow, ou la meme distinction commande le reessai. La regle est la meme des deux
 * cotes : <b>ne jamais republier ce qui a pu partir</b>.
 */
public sealed interface ResultatPublication {

    /**
     * Le broker a accuse reception, avec les repliques synchronisees (acks=all).
     *
     * @param topic topic effectivement servi
     * @param partition partition retenue par la cle de partition
     * @param offset position du message, reprise dans la trace d'audit : elle permet
     *        de retrouver l'evenement exact sur le broker six mois plus tard
     */
    record Publiee(String topic, int partition, long offset) implements ResultatPublication {
    }

    /**
     * Rien n'a quitte la machine, et on le sait : la charge n'a pas pu etre convertie en
     * JSON, ou {@code send()} a leve des l'appel — broker injoignable, metadonnees du
     * topic introuvables avant {@code max.block.ms}. Le message n'a jamais ete remis au
     * client Kafka.
     *
     * <p>Seule issue qui autorise a <b>liberer</b> la reservation du verrou.
     */
    record EchecAvantEnvoi(String motifTechnique) implements ResultatPublication {
    }

    /**
     * Le message a ete remis au client Kafka et l'on ignore ce qu'il en est advenu :
     * delai d'accuse depasse, echec de livraison, attente interrompue. Un accuse peut se
     * perdre <b>apres</b> que le broker a ecrit le message.
     *
     * <p>La reservation <b>reste posee</b> : republier produirait potentiellement un
     * second jeu d'ecritures comptables pour les memes beneficiaires. L'etat devient
     * reperable par l'anciennete de sa reservation, et se leve a la main apres
     * verification du topic.
     */
    record EchecIssueIncertaine(String motifTechnique) implements ResultatPublication {
    }

}
