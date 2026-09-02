package cm.afrilandfirstbank.rations.transmission.domaine.exception;

/**
 * Le service Saisie n'a rien repondu d'exploitable
 * ({@code 503 SERVICE_SAISIE_INDISPONIBLE}).
 *
 * <p><b>Refus conservateur.</b> Le detail des lignes n'a qu'une source ; sans elle, la
 * seule alternative au refus serait de publier une charge amputee, qui produirait des
 * beneficiaires impayes sans que rien ne le signale. Un etat non transmis se voit et se
 * reprend ; un etat transmis a moitie, non.
 */
public class ServiceSaisieIndisponibleException extends RuntimeException {

    public ServiceSaisieIndisponibleException(Long idProcessus, String motifTechnique) {
        super("Le service Saisie est momentanement indisponible : le detail de l'etat "
                + idProcessus + " n'a pas pu etre lu (" + motifTechnique + "). La transmission "
                + "est refusee plutot que de publier une charge incomplete. L'etat reste non "
                + "transmis.");
    }

}
