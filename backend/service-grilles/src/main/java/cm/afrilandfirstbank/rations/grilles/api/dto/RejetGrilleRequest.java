package cm.afrilandfirstbank.rations.grilles.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code POST /grilles/{id}/rejet} : motif de la decision de la
 * Directrice RH (contrat d'API section 4, RG-10, US-14).
 *
 * <p><b>Un seul champ, obligatoire.</b> Le motif n'est pas une courtoisie : sans
 * lui, l'ARH voit sa proposition refusee sans savoir quoi corriger, et
 * reproposera vraisemblablement la meme. C'est ce que RG-10 impose pour tout
 * rejet ou retour du module.
 *
 * <p>{@code @NotBlank} et non {@code @NotNull} : une chaine d'espaces satisfait
 * le second et ne dit rien. La regle est reprise plus loin par
 * {@code TransitionGrille.rejeter}, qui refuserait aussi un motif vide — la
 * duplication est voulue. Ici le refus est un 400 de forme, lisible par le
 * frontend avant meme l'appel ; la-bas, c'est la garantie que la regle tienne
 * quel que soit l'appelant.
 *
 * <p>Le validateur et l'horodatage ne sont pas fournis : ils sont deduits du
 * jeton et de l'instant de la decision. Un validateur declare par le client
 * serait une affirmation, pas une trace.
 *
 * @param motif explication de la decision, non vide, 255 caracteres au plus
 *              (longueur de la colonne {@code motif_rejet})
 */
@Schema(description = "Motif du rejet d'une grille par la Directrice RH.")
public record RejetGrilleRequest(

        @Schema(description = "Explication du refus, transmise a l'Analyste RH.",
                example = "Montant superieur au bareme en vigueur",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "le motif du rejet est obligatoire (RG-10)")
        @Size(max = 255, message = "le motif ne peut depasser 255 caracteres")
        String motif) {
}
