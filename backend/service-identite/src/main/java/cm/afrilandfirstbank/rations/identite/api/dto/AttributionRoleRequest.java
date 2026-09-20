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
 *
 * <p><b>{@code actif}</b> (facultatif) : absent, le statut du profil est inchangé.
 * {@code false} désactive le profil (il est refusé dès la requête suivante, le rôle
 * étant relu en base à chaque appel), {@code true} le réactive. Porté par cet
 * endpoint plutôt que par un endpoint dédié pour que rôle, unité et statut se
 * modifient dans une seule transaction, avec le même contrôle d'auto-modification.
 */

public record AttributionRoleRequest(

        @NotNull(message = "Le role est obligatoire.")
        RoleEnum role,

        @Pattern(regexp = "\\d{5}", message = "Le code unite doit comporter exactement cinq chiffres.")
        String codeUnite,

        Boolean actif) {

    /** Sans statut : le profil garde son statut actuel. */
    public AttributionRoleRequest(RoleEnum role, String codeUnite) {
        this(role, codeUnite, null);
    }

}
