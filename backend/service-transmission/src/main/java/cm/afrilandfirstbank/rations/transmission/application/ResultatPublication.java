package cm.afrilandfirstbank.rations.transmission.application;

/**
 * Les deux issues d'une publication sur {@code rations.etat.valide}.
 *
 * <p><b>Il n'y a pas d'issue intermediaire, et c'est le point du sous-sprint.</b>
 * L'appelant doit pouvoir repondre par oui ou par non a « le broker a-t-il pris cet
 * evenement ? », parce que c'est de cette reponse que depend l'ecriture du drapeau
 * {@code transmis_comptabilite} (RG-13). Un « probablement » ferait poser le drapeau
 * sur un etat qui n'est peut-etre jamais parti : l'etat serait fige, repute transmis,
 * et jamais paye — sans que rien ne le signale.
 *
 * <p>C'est pourquoi la publication est <b>attendue</b>, contrairement a celle du
 * journal d'audit. Une trace d'audit perdue est un manque dans un journal ; un etat
 * valide perdu est un salaire non verse.
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
     * Le broker n'a rien accuse : injoignable, delai depasse, serialisation en echec,
     * interruption. Le drapeau de transmission <b>reste a faux</b> et l'anomalie est
     * signalee.
     */
    record PublicationEchouee(String motifTechnique) implements ResultatPublication {
    }

}
