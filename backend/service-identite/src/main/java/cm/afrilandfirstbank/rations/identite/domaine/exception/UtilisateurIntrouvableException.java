package cm.afrilandfirstbank.rations.identite.domaine.exception;

/**
 * Aucun profil local ne correspond a l'identifiant demande (sous-sprint 1.2,
 * {@code PUT /identite/utilisateurs/{id}/role}). Se traduit par un 404.
 */
public class UtilisateurIntrouvableException extends RuntimeException {

    public UtilisateurIntrouvableException(Long id) {
        super("Aucun utilisateur ne correspond a l'identifiant " + id + ".");
    }

}
