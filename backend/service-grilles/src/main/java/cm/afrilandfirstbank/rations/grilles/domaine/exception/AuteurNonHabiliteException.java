package cm.afrilandfirstbank.rations.grilles.domaine.exception;

/**
 * Le jeton presente est valide, mais aucun profil local ouvert dans le module ne
 * lui correspond — ou ce profil est desactive.
 *
 * <p>Cas distinct d'un role insuffisant : ici l'utilisateur n'existe pas du tout
 * pour le module. C'est le 403 que renvoie {@code GET /identite/moi}
 * ({@code UTILISATEUR_NON_HABILITE}), releve tel quel par ce service.
 *
 * <p>L'habilitation au module reste un acte d'administration explicite : elle ne
 * decoule pas de la seule existence d'un compte a l'annuaire (CLAUDE.md
 * section 10, decision Sprint 0.4). Un jeton Keycloak portant le role ARH ne
 * suffit donc pas a creer une grille.
 *
 * <p>Trace en audit comme un refus d'acces (CT-04), au meme titre qu'un role
 * insuffisant.
 */
public class AuteurNonHabiliteException extends RuntimeException {

    public AuteurNonHabiliteException(String message) {
        super(message);
    }

}
