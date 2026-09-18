package cm.afrilandfirstbank.rations.grilles.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code POST /grilles/{id}/retrait} : motif du retrait par
 * l'Analyste RH auteur de la proposition (rattrapage post-7F.6, demande n°7 de la
 * vérification visuelle du Sprint 7F.6).
 *
 * <p>Même exigence que {@link RejetGrilleRequest} et pour la même raison
 * (RG-10) : la grille repasse par le statut {@code REJETEE}
 * ({@code TransitionGrille.rejeter}, qui refuse déjà un motif vide), et le
 * motif reste utile même quand l'auteur du retrait est celui-là même qui avait
 * proposé la grille -- il explique dans l'historique pourquoi cette proposition
 * précise ne sera jamais tranchée par la Directrice RH.
 *
 * @param motif raison du retrait, non vide, 255 caractères au plus
 */
@Schema(description = "Motif du retrait d'une grille par son auteur, avant décision de la DRH.")
public record RetraitGrilleRequest(

        @Schema(description = "Raison du retrait, conservée dans l'historique.",
                example = "Erreur de montant, je repropose une grille corrigée",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "le motif du retrait est obligatoire (RG-10)")
        @Size(max = 255, message = "le motif ne peut depasser 255 caracteres")
        String motif) {
}
