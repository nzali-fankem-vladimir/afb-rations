package cm.afrilandfirstbank.rations.identite.domaine.exception;

/**
 * Un administrateur a cible son propre profil sur
 * {@code PUT /identite/utilisateurs/{id}/role} (sous-sprint 1.2, decision
 * {@code docs/decisions/2026-08-26-attribution-role-administrateur.md}). Se
 * traduit par un 409 : la requete est valide en soi, c'est son application a
 * l'auteur lui-meme qui est refusee.
 */
public class AutoModificationInterditeException extends RuntimeException {

    public AutoModificationInterditeException(String message) {
        super(message);
    }

}
