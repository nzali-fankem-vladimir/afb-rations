package cm.afrilandfirstbank.rations.grilles.api.dto;

import java.time.LocalDate;

import cm.afrilandfirstbank.rations.grilles.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.SessionEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Corps de {@code POST /grilles} : proposition d'une grille tarifaire par
 * l'Analyste RH (contrat d'API section 4, US-13).
 *
 * <p><b>Ce que le client ne fournit pas, et pourquoi.</b>
 * <ul>
 *   <li><b>Le statut.</b> Il n'est jamais choisi par l'appelant : RG-14 impose le
 *       passage par {@code EN_ATTENTE_DRH}. Accepter un statut en entree
 *       ouvrirait la porte a une grille creee directement {@code ACTIVE}, donc
 *       applicable sans decision de la DRH.</li>
 *   <li><b>Le createur.</b> Il est deduit du jeton, jamais declare. Un
 *       identifiant d'auteur fourni par le client ne serait pas une trace, ce
 *       serait une affirmation.</li>
 *   <li><b>La date de fin.</b> Une grille naît sans borne de fin ; celle-ci est
 *       posee par le service de validation lorsqu'une remplacante est activee
 *       (Sprint 2.3).</li>
 * </ul>
 *
 * <p>{@code nature} et {@code session} sont typees par leurs enumerations : une
 * valeur hors domaine est refusee des la deserialisation, avant meme d'atteindre
 * le service, et traduite en 400 par {@code GestionnaireErreursApi}.
 *
 * @param nature RATION ou TRANSPORT (RG-01)
 * @param session JOUR ou SOIR (RG-02)
 * @param montantFcfa montant applique, entier en FCFA, strictement positif
 * @param dateDebut premier jour de validite de la grille proposee
 */
@Schema(description = "Proposition d'une grille tarifaire par l'Analyste RH.")
public record CreationGrilleRequest(

        @Schema(description = "Nature de la prestation.", example = "TRANSPORT", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "la nature est obligatoire (RATION ou TRANSPORT)")
        NatureEnum nature,

        @Schema(description = "Session concernee.", example = "SOIR", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "la session est obligatoire (JOUR ou SOIR)")
        SessionEnum session,

        /*
         * Integer et non int : un type primitif vaudrait 0 en l'absence du champ,
         * et @Positive rejetterait ce 0 avec le message d'un montant nul, alors
         * que le vrai defaut est un champ manquant. La distinction compte pour
         * l'utilisateur qui lit l'erreur.
         *
         * Entier et non decimal : un montant en FCFA n'a pas de centime, et un
         * type flottant introduirait un arrondi sur une somme payee.
         */
        @Schema(description = "Montant en FCFA, entier, strictement positif.", example = "3000",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "le montant est obligatoire")
        @Positive(message = "le montant doit etre strictement positif")
        Integer montantFcfa,

        @Schema(description = "Premier jour de validite, au format ISO 8601.", example = "2026-09-01",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "la date de debut est obligatoire")
        LocalDate dateDebut) {
}
