package cm.afrilandfirstbank.rations.grilles.api.dto;

import cm.afrilandfirstbank.rations.grilles.application.DecisionGrilleService.ResultatValidation;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Reponse de {@code POST /grilles/{id}/validation} : le resultat de la bascule,
 * pas seulement la grille validee.
 *
 * <p><b>Pourquoi deux grilles dans la reponse.</b> Une validation modifie deux
 * lignes : la proposition devient applicable, et celle qu'elle remplace cesse de
 * l'etre a la veille. Ne renvoyer que la premiere obligerait l'interface a
 * relire la liste pour comprendre ce qui vient de se passer, et priverait la DRH
 * d'une confirmation de ce qu'elle vient de faire — « le tarif TRANSPORT / SOIR
 * passe de 2500 a 3000 FCFA au 1er septembre, l'ancienne grille est close au
 * 31 aout ».
 *
 * <p>{@code ancienneFermee} est nul lorsqu'il s'agit de la premiere grille du
 * couple : rien n'a ete ferme, et c'est un cas normal, pas une anomalie.
 *
 * @param grille la grille devenue ACTIVE
 * @param ancienneFermee la grille close par cette validation, nulle s'il n'y en avait pas
 */
@Schema(description = "Resultat d'une validation : la grille activee et celle qu'elle remplace.")
public record ValidationGrilleResponse(

        @Schema(description = "Grille devenue applicable.") GrilleResponse grille,

        @Schema(description = "Grille fermee par cette validation. Nulle s'il s'agit de la "
                + "premiere grille du couple nature et session.")
        GrilleResponse ancienneFermee) {

    public static ValidationGrilleResponse depuis(ResultatValidation resultat) {
        return new ValidationGrilleResponse(
                GrilleResponse.depuis(resultat.validee()),
                resultat.ancienneFermee() == null
                        ? null
                        : GrilleResponse.depuis(resultat.ancienneFermee()));
    }

}
