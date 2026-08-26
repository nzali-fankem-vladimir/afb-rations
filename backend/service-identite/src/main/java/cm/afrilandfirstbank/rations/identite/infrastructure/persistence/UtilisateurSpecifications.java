package cm.afrilandfirstbank.rations.identite.infrastructure.persistence;

import org.springframework.data.jpa.domain.Specification;

import cm.afrilandfirstbank.rations.identite.domaine.RoleEnum;
import cm.afrilandfirstbank.rations.identite.domaine.Utilisateur;

/**
 * Criteres combinables pour la recherche paginee d'utilisateurs (sous-sprints
 * 1.2 et 1.3). Chaque filtre nul est omis plutot que traduit en condition SQL.
 */
public final class UtilisateurSpecifications {

    private UtilisateurSpecifications() {
    }

    public static Specification<Utilisateur> avecFiltres(RoleEnum role, String codeUnite, Boolean actif) {
        return Specification.allOf(
                role(role),
                codeUnite(codeUnite),
                actif(actif));
    }

    private static Specification<Utilisateur> role(RoleEnum role) {
        return (racine, requete, cb) -> role == null ? null : cb.equal(racine.get("role"), role);
    }

    private static Specification<Utilisateur> codeUnite(String codeUnite) {
        return (racine, requete, cb) -> codeUnite == null ? null : cb.equal(racine.get("codeUnite"), codeUnite);
    }

    private static Specification<Utilisateur> actif(Boolean actif) {
        return (racine, requete, cb) -> actif == null ? null : cb.equal(racine.get("actif"), actif);
    }

}
