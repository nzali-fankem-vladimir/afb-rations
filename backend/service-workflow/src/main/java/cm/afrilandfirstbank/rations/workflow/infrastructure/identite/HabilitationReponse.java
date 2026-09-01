package cm.afrilandfirstbank.rations.workflow.infrastructure.identite;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Corps de {@code GET /identite/habilitation?codeUnite=...} (service Identite,
 * Sprint 1.3, {@code docs/appel-habilitation.md} section 1).
 *
 * <p>Contrat <b>verifie</b> : le service Identite existe depuis le Sprint 1 et
 * rend reellement ces cinq champs. Recopie ici plutot que mutualisee, comme
 * {@code StatutProcessusEnum} cote Saisie — {@code rations-audit-commun} est la
 * seule mutualisation du backend (CLAUDE.md sections 3 et 15).
 *
 * <p>{@code autorise} est un {@code Boolean} boite : absent, il vaut {@code null}
 * et l'appelant refuse. Un {@code boolean} primitif vaudrait {@code false}, ce qui
 * donnerait ici le bon resultat <i>par chance</i> — et le mauvais le jour ou un
 * champ deviendrait « autorise sauf si ». Le refus doit venir d'une decision lue,
 * pas d'une valeur par defaut du langage.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HabilitationReponse(
        String login,
        String role,
        String codeUniteDemande,
        Boolean autorise,
        Boolean porteeNationale) {
}
