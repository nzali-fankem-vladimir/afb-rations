package cm.afrilandfirstbank.rations.workflow.domaine.exception;

/**
 * Un accuse comptable porte sur un etat qui n'a jamais ete transmis. Rendue en
 * {@code 422 PROCESSUS_NON_TRANSMIS} (Sprint 5.2).
 *
 * <p><b>{@code 422} et non {@code 409}</b> : rien n'est duplique, c'est une regle de
 * gestion qui refuse — meme raisonnement qu'au Sprint 2.3 pour {@code TRANSITION_INTERDITE}
 * et au Sprint 3.3 pour {@code ETAT_NON_MODIFIABLE}. La comptabilite ne peut pas avoir
 * traite un etat qu'on ne lui a jamais envoye.
 *
 * <p>Incoherence serieuse, d'un cote ou de l'autre : le refus est trace en audit par le
 * service Transmission, qui la releve, avec le prefixe de supervision
 * {@code ACCUSE INCOHERENT}. L'ignorer priverait d'un signal utile (guide 5.2 section 10).
 *
 * <p><b>Le drapeau de transmission n'est jamais pose a cette occasion.</b> Il ferait
 * croire a un envoi qui n'a pas eu lieu, et RG-13 refuserait ensuite le vrai comme un
 * doublon : l'etat resterait impaye a jamais.
 */
public class ProcessusNonTransmisException extends RuntimeException {

    public ProcessusNonTransmisException(String message) {
        super(message);
    }

}
