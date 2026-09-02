package cm.afrilandfirstbank.rations.transmission.domaine.exception;

/**
 * Le broker n'a pas accuse reception de l'etat valide ({@code 503 PUBLICATION_ECHOUEE}).
 *
 * <p>C'est le cas le plus delicat du sous-sprint : l'etat est cloture, donc fige, et il
 * n'est pas parti. L'appelant <b>ne doit surtout pas</b> poser le drapeau
 * {@code transmis_comptabilite} — un drapeau pose ici rendrait l'etat definitivement
 * impaye, le controle d'unicite de RG-13 refusant ensuite la vraie transmission comme un
 * doublon.
 *
 * <p>Le refus est donc explicite, journalise au prefixe reperable et trace en audit.
 */
public class PublicationEchoueeException extends RuntimeException {

    public PublicationEchoueeException(Long idProcessus, String motifTechnique) {
        super("L'etat " + idProcessus + " n'a pas pu etre publie sur le topic de l'etat valide ("
                + motifTechnique + "). L'etat reste CLOTURE et NON TRANSMIS : il devra etre "
                + "retransmis, faute de quoi ses beneficiaires ne seront pas payes.");
    }

}
