package cm.afrilandfirstbank.rations.workflow.domaine.exception;

/**
 * L'en-tete du secret partage est absent ou ne correspond pas. Rendue en
 * {@code 401 CLE_INTERNE_INVALIDE} (Sprint 5.2).
 *
 * <p><b>Sa valeur ne figure nulle part.</b> Ni le secret recu, ni le secret attendu, ni
 * leur longueur, ni leur prefixe — un prefixe est deja une fuite. Le message ne nomme que
 * l'en-tete manquant ou refuse. Un garde-fou qui fuirait par le journal cense le
 * surveiller ne serait pas un garde-fou.
 *
 * <p>{@code 401} et non {@code 403} : c'est l'authentification de l'appelant qui manque,
 * non un droit qui lui serait refuse. Le service Transmission traite d'ailleurs ce refus
 * comme une <b>indisponibilite</b> et rejoue : un secret mal configure est une panne de
 * deploiement, pas un accuse fautif, et le rejeu laisse le temps de corriger la variable
 * d'environnement.
 */
public class CleInterneInvalideException extends RuntimeException {

    public CleInterneInvalideException(String message) {
        super(message);
    }

}
