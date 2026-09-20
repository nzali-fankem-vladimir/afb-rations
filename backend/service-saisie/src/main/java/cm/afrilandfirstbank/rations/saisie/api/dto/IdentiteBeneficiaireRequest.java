package cm.afrilandfirstbank.rations.saisie.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Identité du bénéficiaire servi, telle que l'agent la saisit.
 *
 * <p><b>Il n'y a pas de référentiel de bénéficiaires.</b> Aucun enrôlement, aucun
 * import : un bénéficiaire est créé à la première saisie qui le concerne
 * (CLAUDE.md §4). L'agent fournit donc l'identité complète à chaque ligne, et
 * {@code ResolutionBeneficiaireService} retrouve ou crée la personne à partir du
 * <b>seul numéro de compte courant</b>
 * ({@code docs/decisions/2026-08-28-resolution-beneficiaire-et-incoherence-nom.md}).
 *
 * <p><b>Deux formats contraints.</b> {@code codeAgence} est un code du référentiel
 * des guichets, que le contrat d'API §1.1 fixe à cinq chiffres. Le numéro de compte
 * courant fait <b>onze chiffres</b>, règle établie par le métier le 9 septembre
 * 2026 (point T-02). Posée au Sprint 7F.6 : le résumé de la Maille 1 l'annonçait
 * comme faite, mais ce fichier n'avait jamais été modifié. C'est la donnée qui
 * décide <i>qui est payé</i> : une faute de frappe créerait un bénéficiaire fantôme
 * et créditerait un mauvais compte, sans erreur visible. Contrôle sur les nouvelles
 * saisies uniquement ; les comptes déjà en base ne sont pas corrigés (arbitrage du
 * 9 septembre 2026).
 *
 * <p><b>{@code codeAgence} n'est pas {@code codeUnite}.</b> Même format, rôles
 * opposés : l'agence de domiciliation du compte porte la ligne de <i>crédit</i>,
 * l'unité qui supporte la charge porte celle de <i>débit</i> (CLAUDE.md §4). Le
 * code unité n'apparaît jamais dans cette requête : il vient du processus.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IdentiteBeneficiaireRequest(

        @NotBlank(message = "le nom du bénéficiaire est obligatoire")
        @Size(max = 100, message = "le nom du bénéficiaire ne peut pas dépasser 100 caractères")
        String nom,

        // FACULTATIF : certains bénéficiaires n'ont pas de prénom. Absent ou blanc,
        // il est enregistré comme chaîne vide (Beneficiaire.normaliserPrenom).
        @Size(max = 100, message = "le prénom du bénéficiaire ne peut pas dépasser 100 caractères")
        String prenom,

        // @NotBlank garde un message distinct et plus clair sur un champ absent.
        @NotBlank(message = "le numéro de compte courant est obligatoire")
        @Pattern(regexp = "^[0-9]{11}$",
                message = "le numéro de compte courant doit comporter exactement onze chiffres, sans espace ni séparateur")
        String numCompteCourant,

        @NotBlank(message = "le code agence est obligatoire")
        @Pattern(regexp = "\\d{5}", message = "le code agence doit comporter exactement cinq chiffres")
        String codeAgence) {
}
