package cm.afrilandfirstbank.rations.identite.api.dto;

import java.time.LocalDateTime;

import cm.afrilandfirstbank.rations.identite.domaine.RoleEnum;
import cm.afrilandfirstbank.rations.identite.domaine.Utilisateur;

/**
 * Vue de sortie d'un utilisateur pour la liste d'administration (sous-sprint 1.2).
 *
 * <p>{@code subKeycloak} n'est jamais expose : c'est un detail d'implementation du
 * fournisseur d'identite, sans interet pour le client (CLAUDE.md section 10).
 */
public record UtilisateurResponse(
        Long id,
        String login,
        String nom,
        String prenom,
        String email,
        RoleEnum role,
        String codeUnite,
        boolean actif,
        LocalDateTime dateDernierAcces) {

    public static UtilisateurResponse depuis(Utilisateur utilisateur) {
        return new UtilisateurResponse(
                utilisateur.getId(),
                utilisateur.getLogin(),
                utilisateur.getNom(),
                utilisateur.getPrenom(),
                utilisateur.getEmail(),
                utilisateur.getRole(),
                utilisateur.getCodeUnite(),
                utilisateur.estActif(),
                utilisateur.getDateDernierAcces());
    }

}
