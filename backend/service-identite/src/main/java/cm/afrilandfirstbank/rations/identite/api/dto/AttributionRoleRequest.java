package cm.afrilandfirstbank.rations.identite.api.dto;

import cm.afrilandfirstbank.rations.identite.domaine.RoleEnum;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Entree de {@code PUT /identite/utilisateurs/{id}/role} (sous-sprint 1.2).
 *
 * <p>{@code codeUnite} n'est ici que valide en forme (cinq chiffres, quand
 * renseigne). Sa presence obligatoire pour un role a portee locale (AGENT_UNITE,
 * CHEF_UNITE_DA) est une regle de coherence entre deux champs, pas une contrainte
 * de forme isolee : elle est verifiee dans {@code UtilisateurAdminService}, qui
 * s'appuie sur la meme notion de portee que {@link
 * cm.afrilandfirstbank.rations.identite.application.PorteeAccesService}.
 */
public record AttributionRoleRequest(

        @NotNull(message = "Le role est obligatoire.")
        RoleEnum role,

        @Pattern(regexp = "\\d{5}", message = "Le code unite doit comporter exactement cinq chiffres.")
        String codeUnite) {

}
