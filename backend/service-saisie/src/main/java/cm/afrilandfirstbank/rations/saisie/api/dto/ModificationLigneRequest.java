package cm.afrilandfirstbank.rations.saisie.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import cm.afrilandfirstbank.rations.saisie.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.SessionEnum;

import jakarta.validation.constraints.NotNull;

/**
 * Entrée de {@code PUT /saisie/lignes/{id}} : la nature et la session, seules
 * données modifiables d'une ligne.
 *
 * <h2>Ce que ce DTO ne permet pas de changer, et pourquoi</h2>
 *
 * <p><b>Le bénéficiaire.</b> Une erreur de destinataire se corrige en supprimant
 * la ligne et en la recréant — deux appels, tous deux tracés en audit. Le changer
 * en place ferait <i>muter silencieusement la personne payée</i> sur une ligne
 * existante ; la suppression suivie d'une création laisse deux traces distinctes
 * là où une modification n'en laisserait qu'une, ambiguë. Décision prise avec
 * l'utilisateur au Sprint 3.3, étape 1.
 *
 * <p><b>Le montant.</b> Même raison qu'à la création : il n'a aucune entrée
 * (RG-03). Changer la nature ou la session le fait <i>recalculer</i>, il ne se
 * saisit jamais.
 *
 * <p><b>La fiche.</b> Déplacer une ligne d'un jour à l'autre changerait la
 * journée de la prestation, donc le montant applicable et le périmètre du
 * contrôle de doublon. C'est une autre prestation, pas la même corrigée.
 *
 * <p>Les deux champs sont obligatoires : c'est un remplacement complet de l'état
 * modifiable, pas une mise à jour partielle. Un {@code PUT} qui n'enverrait que
 * la session laisserait planer un doute sur la nature — et le doute, ici, se
 * traduit en montant.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModificationLigneRequest(

        @NotNull(message = "la nature est obligatoire : RATION ou TRANSPORT")
        NatureEnum nature,

        @NotNull(message = "la session est obligatoire : JOUR ou SOIR")
        SessionEnum session) {
}
