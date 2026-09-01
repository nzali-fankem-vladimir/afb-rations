package cm.afrilandfirstbank.rations.workflow.infrastructure.identite;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import cm.afrilandfirstbank.rations.workflow.domaine.RoleEnum;

/**
 * Reponse de {@code GET /identite/moi}, telle que le service Identite la rend
 * ({@code ProfilUtilisateurDto}).
 *
 * <p><b>Tolerant reader</b> : le service Identite peut enrichir sa reponse sans
 * casser ce service. Tolerance a la <i>lecture</i> seulement — les types sont
 * boites, un champ absent se lit {@code null} et fait refuser plutot que de
 * produire une valeur par defaut (meme discipline que {@code EtatConsolide},
 * decision Sprint 4.1 section 8).
 *
 * <p>{@code porteeAcces} est volontairement ignore : la portee d'acces est deja
 * tranchee par {@code GET /identite/habilitation}, qui en est le proprietaire. La
 * relire ici creerait une seconde interpretation de la meme regle.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProfilReponse(
        Long id,
        String login,
        String nom,
        String prenom,
        RoleEnum role,
        String codeUnite) {
}
