package cm.afrilandfirstbank.rations.identite.api.dto;

import java.util.Set;

import cm.afrilandfirstbank.rations.identite.domaine.PorteeAcces;
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
        String codeUnite,
        PorteeAccesDto porteeAcces) {

    public static ProfilUtilisateurDto depuis(Utilisateur utilisateur, PorteeAcces portee) {
        return new ProfilUtilisateurDto(
                utilisateur.getId(),
                utilisateur.getLogin(),
                utilisateur.getNom(),
                utilisateur.getPrenom(),
                utilisateur.getRole(),
                utilisateur.getCodeUnite(),
                PorteeAccesDto.depuis(portee));
    }

    /** Vue de sortie de {@link PorteeAcces} : nationale, ou limitee a un ensemble de codes unite. */
    public record PorteeAccesDto(boolean nationale, Set<String> codesUnite) {

        public static PorteeAccesDto depuis(PorteeAcces portee) {
            return new PorteeAccesDto(portee.estNationale(), portee.codesUnite());
        }

    }

}
