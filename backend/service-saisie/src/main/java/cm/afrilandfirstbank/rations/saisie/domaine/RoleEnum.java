package cm.afrilandfirstbank.rations.saisie.domaine;

import java.util.Optional;

/**
 * Roles applicatifs du module (CLAUDE.md section 5).
 *
 * <p>Le role est porte par le realm Keycloak pour la securisation des routes, et
 * projete localement dans la table {@code utilisateurs} pour l'habilitation metier.
 * Cette enumeration fait reference : un role present dans un jeton mais absent
 * d'ici n'est pas un role du module.
 */
public enum RoleEnum {

    AGENT_UNITE,
    CHEF_UNITE_DA,
    DIRECTEUR_RESEAU_DR,
    ARH,
    DRH,
    ADMIN;

    /** Retourne le role correspondant au libelle, vide si le libelle est inconnu du module. */
    public static Optional<RoleEnum> depuisLibelle(String libelle) {
        if (libelle == null || libelle.isBlank()) {
            return Optional.empty();
        }
        for (RoleEnum role : values()) {
            if (role.name().equals(libelle)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }

}
