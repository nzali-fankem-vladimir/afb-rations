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
 * <p><b>Formats contraints : un seul.</b> {@code codeAgence} est un code du
 * référentiel des guichets, que le contrat d'API §1.1 fixe à cinq chiffres. Le
 * numéro de compte courant, lui, n'a de format arrêté dans aucun document du
 * projet : le contraindre ici sur une intuition rejetterait des comptes valides,
 * et c'est la donnée qui décide <i>qui est payé</i>. Seule sa longueur est bornée,
 * par la colonne (VARCHAR(20), migration V1).
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

        @NotBlank(message = "le prénom du bénéficiaire est obligatoire")
        @Size(max = 100, message = "le prénom du bénéficiaire ne peut pas dépasser 100 caractères")
        String prenom,

        @NotBlank(message = "le numéro de compte courant est obligatoire")
        @Size(max = 20, message = "le numéro de compte courant ne peut pas dépasser 20 caractères")
        String numCompteCourant,

        @NotBlank(message = "le code agence est obligatoire")
        @Pattern(regexp = "\\d{5}", message = "le code agence doit comporter exactement cinq chiffres")
        String codeAgence) {
}
