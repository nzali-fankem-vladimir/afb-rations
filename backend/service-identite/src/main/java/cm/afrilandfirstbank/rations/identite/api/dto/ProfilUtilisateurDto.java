package cm.afrilandfirstbank.rations.identite.api.dto;

import cm.afrilandfirstbank.rations.identite.domaine.RoleEnum;
import cm.afrilandfirstbank.rations.identite.domaine.Utilisateur;

/**
 * Profil de l'utilisateur courant, tel qu'expose par {@code GET /identite/moi}.
 *
 * <p>Vue de sortie volontairement reduite au strict necessaire au frontend :
 * l'entite JPA n'est jamais exposee (CLAUDE.md section 12).
 */
public record ProfilUtilisateurDto(
        Long id,
        String login,
        String nom,
        String prenom,
        RoleEnum role,
        String codeUnite) {

    public static ProfilUtilisateurDto depuis(Utilisateur utilisateur) {
        return new ProfilUtilisateurDto(
                utilisateur.getId(),
                utilisateur.getLogin(),
                utilisateur.getNom(),
                utilisateur.getPrenom(),
                utilisateur.getRole(),
                utilisateur.getCodeUnite());
    }

}
