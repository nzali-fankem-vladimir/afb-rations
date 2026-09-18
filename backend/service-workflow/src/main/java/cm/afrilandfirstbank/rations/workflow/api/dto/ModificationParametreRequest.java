package cm.afrilandfirstbank.rations.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code PUT /parametres/{code}} (guide 7F.6, etape 6, ajout backend
 * scope). Un seul champ, comme {@code RejetGrilleRequest} et
 * {@code RetourRequest} : le code vient de l'URL, l'auteur du jeton, la date
 * n'est pas portee par {@code parametre_systeme} (CLAUDE.md section 4).
 *
 * <p>{@code @NotBlank} et non {@code @NotNull} : une chaine d'espaces n'est
 * pas une valeur exploitable, quel que soit le parametre cible. Le format
 * precis (entier positif ou texte libre) est verifie en aval par
 * {@code ParametreAdminService}, qui seul connait la distinction par code.
 */
public record ModificationParametreRequest(

        @NotBlank(message = "la valeur est obligatoire")
        @Size(max = 255, message = "la valeur ne peut pas depasser 255 caracteres")
        String valeur) {
}
