package cm.afrilandfirstbank.rations.identite.api.dto;

import cm.afrilandfirstbank.rations.identite.domaine.ResultatHabilitation;
import cm.afrilandfirstbank.rations.identite.domaine.RoleEnum;

/**
 * Vue de sortie de {@code GET /identite/habilitation} (Sprint 1.3, convention
 * {@code docs/appel-habilitation.md}).
 *
 * <p>Traduction directe de {@link ResultatHabilitation} : le domaine ne
 * traverse jamais la frontiere HTTP (CLAUDE.md section 12).
 */
public record HabilitationResponse(
        String login,
        RoleEnum role,
        String codeUniteDemande,
        boolean autorise,
        boolean porteeNationale) {

    public static HabilitationResponse depuis(ResultatHabilitation resultat) {
        return new HabilitationResponse(
                resultat.login(),
                resultat.role(),
                resultat.codeUniteDemande(),
                resultat.autorise(),
                resultat.porteeNationale());
    }

}
