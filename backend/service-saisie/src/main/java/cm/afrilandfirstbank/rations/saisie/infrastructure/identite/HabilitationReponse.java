package cm.afrilandfirstbank.rations.saisie.infrastructure.identite;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Corps de {@code GET /identite/habilitation?codeUnite=...} (service Identite,
 * Sprint 1.3, {@code docs/appel-habilitation.md} section 1).
 *
 * <p>Contrairement a {@link cm.afrilandfirstbank.rations.saisie.infrastructure.workflow.ProcessusReponse},
 * ce contrat-la est <b>verifie</b> : le service Identite existe depuis le
 * Sprint 1, et {@code HabilitationResponse} est sa vue de sortie reelle.
 *
 * <p>{@code autorise} est un {@code Boolean} boite : absent, il vaut
 * {@code null} et l'appelant refuse. Un {@code boolean} primitif vaudrait
 * {@code false}, ce qui donnerait ici le bon resultat par chance — et le mauvais
 * le jour ou un champ deviendrait « autorise sauf si ». Le refus doit venir
 * d'une decision lue, pas d'une valeur par defaut du langage.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HabilitationReponse(
        String login,
        String role,
        String codeUniteDemande,
        Boolean autorise,
        Boolean porteeNationale) {
}
