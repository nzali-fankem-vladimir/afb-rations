package cm.afrilandfirstbank.rations.saisie.domaine.exception;

/**
 * Le service Identite n'a pas repondu de facon exploitable : delai depasse,
 * connexion refusee, {@code 5xx}, corps illisible. L'operation est refusee.
 *
 * <p>Traduite en {@code 503 SERVICE_IDENTITE_INDISPONIBLE}, <b>et non en
 * {@code 403}</b> — parti pris du Sprint 2.2, repris tel quel : l'agent possede
 * le droit qu'il exerce ; un {@code 403} l'enverrait reclamer une habilitation
 * qu'il a deja, pendant que la panne resterait invisible.
 *
 * <p><b>Aucun cache d'un verdict positif anterieur</b> (doctrine Sprint 1.3) :
 * une habilitation peut avoir change entre deux appels — attribution de role,
 * changement de code unite, desactivation de profil. Autoriser sur une valeur
 * potentiellement obsolete contredirait RG-12 et le principe bancaire du refus
 * par defaut.
 *
 * <p>Ce refus est trace en audit au meme titre qu'un verdict negatif, avec un
 * motif distinct ({@code IDENTITE_INDISPONIBLE}), depuis
 * {@code GestionnaireErreursApi}.
 */
public class ServiceIdentiteIndisponibleException extends RuntimeException {

    public ServiceIdentiteIndisponibleException(String message) {
        super(message);
    }

}
