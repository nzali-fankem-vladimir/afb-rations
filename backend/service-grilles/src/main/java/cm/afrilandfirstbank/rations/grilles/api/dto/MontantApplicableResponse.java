package cm.afrilandfirstbank.rations.grilles.api.dto;

import java.time.LocalDate;

import cm.afrilandfirstbank.rations.grilles.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.ResolutionMontant;
import cm.afrilandfirstbank.rations.grilles.domaine.SessionEnum;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Reponse de {@code GET /grilles/active} : le montant applicable a une
 * prestation, et la grille qui l'a fourni (RG-03, Sprint 2.4).
 *
 * <p><b>L'indisponibilite est une reponse {@code 200}, pas une erreur.</b> Quand
 * aucune grille ne couvre la date, {@code disponible} vaut {@code false} et
 * {@code montantFcfa} vaut {@code null}. Meme parti que
 * {@code GET /identite/habilitation}, qui repond {@code 200} avec
 * {@code autorise: false} plutot qu'un {@code 403} (Sprint 1.3) : un endpoint
 * interne qui repond a une question metier ne code pas la reponse negative comme
 * une erreur de transport.
 *
 * <p>Cela laisse l'appelant distinguer trois situations qu'un {@code 404}
 * confondrait : montant trouve ; pas de tarif (refus metier
 * {@code 422 GRILLE_INDISPONIBLE}, message a l'agent) ; service injoignable
 * (refus technique). Voir {@code docs/appel-resolution-montant.md}.
 *
 * <p><b>{@code montantFcfa} est nul, jamais zero.</b> Un zero serait enregistre
 * comme un tarif : la ligne partirait en comptabilite a montant nul sans que
 * personne ne s'en apercoive. Avec {@code null}, un appelant qui ignorerait le
 * drapeau echoue bruyamment plutot que silencieusement.
 *
 * @param disponible vrai si une grille couvre la date demandee
 * @param nature nature demandee, rappelee pour que l'appelant n'ait pas a conserver sa requete
 * @param session session demandee
 * @param date date de la prestation, telle que demandee
 * @param montantFcfa montant applicable en FCFA, {@code null} si indisponible
 * @param idGrille grille d'ou vient le montant, {@code null} si indisponible
 * @param dateDebut premier jour de validite de cette grille
 * @param dateFin dernier jour de validite, nul si la grille n'a pas de terme pose
 */
@Schema(description = "Montant applicable a une prestation, ou indisponibilite explicite.")
public record MontantApplicableResponse(

        @Schema(description = "Faux si aucune grille ne couvre la date : la ligne doit alors etre refusee.",
                example = "true") boolean disponible,
        @Schema(example = "RATION") NatureEnum nature,
        @Schema(example = "JOUR") SessionEnum session,
        @Schema(description = "Date de la prestation, telle que demandee.", example = "2026-07-10") LocalDate date,
        @Schema(description = "Montant en FCFA. **Nul, jamais zero**, quand disponible vaut false.",
                example = "1500") Integer montantFcfa,
        @Schema(description = "Grille d'ou vient le montant, pour justifier la ligne figee.",
                example = "12") Long idGrille,
        @Schema(description = "Premier jour de validite de la grille retenue.", example = "2026-07-01") LocalDate dateDebut,
        @Schema(description = "Dernier jour de validite, nul si la grille est encore courante.",
                example = "2026-07-31") LocalDate dateFin) {

    public static MontantApplicableResponse depuis(ResolutionMontant resolution) {
        return new MontantApplicableResponse(
                resolution.disponible(),
                resolution.nature(),
                resolution.session(),
                resolution.date(),
                resolution.montantFcfa(),
                resolution.idGrille(),
                resolution.dateDebut(),
                resolution.dateFin());
    }

}
