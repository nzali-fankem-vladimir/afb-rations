package cm.afrilandfirstbank.rations.identite.domaine.exception;

/**
 * Le porteur du jeton est authentifie par Keycloak, mais aucun profil local ne lui
 * a ete ouvert par un administrateur, ou son profil a ete desactive.
 *
 * <p>Se traduit par un 403 : l'authentification a reussi, c'est l'habilitation
 * metier qui manque (document maitre, section 7.1).
 */
public class UtilisateurNonHabiliteException extends RuntimeException {

    public UtilisateurNonHabiliteException(String message) {
        super(message);
    }

}
