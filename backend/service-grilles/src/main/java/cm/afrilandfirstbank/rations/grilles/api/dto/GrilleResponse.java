package cm.afrilandfirstbank.rations.grilles.api.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import cm.afrilandfirstbank.rations.grilles.domaine.GrilleTarifaire;
import cm.afrilandfirstbank.rations.grilles.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.SessionEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.StatutGrilleEnum;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Vue de sortie d'une grille tarifaire. L'entite JPA n'est jamais exposee
 * (CLAUDE.md section 12).
 *
 * <p><b>Createur et validateur sont des libelles, pas des identifiants.</b> La
 * base porte {@code id_createur} et {@code id_validateur}, qui referencent la
 * table {@code utilisateurs} d'une AUTRE base. Renvoyer « cree par 4 » serait
 * exact et inexploitable ; aller chercher le nom a chaque lecture supposerait un
 * appel reseau par ligne affichee vers le service Identite, sur le chemin de
 * consultation.
 *
 * <p>Le libelle est donc recopie dans la ligne au moment de l'acte, depuis
 * {@code GET /identite/moi} (decision Sprint 2.2,
 * {@code docs/decisions/2026-08-27-libelle-acteur-grille.md}). Consequence
 * assumee : il est FIGE. Il dit qui a agi tel qu'il etait connu ce jour-la, ce
 * qui est precisement ce qu'on attend d'une piece de controle interne.
 *
 * @param createur nom lisible de l'ARH auteur
 * @param validateur nom lisible de la DRH ayant tranche, nul avant sa decision
 */
@Schema(description = "Grille tarifaire, telle qu'exposee par l'API.")
public record GrilleResponse(

        @Schema(example = "29") Long id,
        @Schema(example = "TRANSPORT") NatureEnum nature,
        @Schema(example = "SOIR") SessionEnum session,
        @Schema(description = "Montant en FCFA, entier.", example = "3000") Integer montantFcfa,
        @Schema(example = "2026-09-01") LocalDate dateDebut,
        @Schema(description = "Nul tant que la grille n'a pas ete remplacee.", example = "null") LocalDate dateFin,
        @Schema(example = "EN_ATTENTE_DRH") StatutGrilleEnum statutValidation,
        @Schema(description = "Nom lisible de l'ARH auteur.", example = "NKOLO Claire") String createur,
        @Schema(description = "Nom lisible de la DRH, nul avant decision.", example = "null") String validateur,
        @Schema(example = "2026-08-27T09:12:00") LocalDateTime dateCreation,
        @Schema(description = "Horodatage de la decision DRH, nul avant.", example = "null") LocalDateTime dateValidation,
        @Schema(description = "Motif obligatoire au rejet (RG-10), nul hors statut REJETEE.", example = "null") String motifRejet) {

    public static GrilleResponse depuis(GrilleTarifaire grille) {
        return new GrilleResponse(
                grille.getId(),
                grille.getNature(),
                grille.getSession(),
                grille.getMontantFcfa(),
                grille.getDateDebut(),
                grille.getDateFin(),
                grille.getStatutValidation(),
                libelleOuRepli(grille.getLibelleCreateur(), grille.getIdCreateur()),
                libelleOuRepli(grille.getLibelleValidateur(), grille.getIdValidateur()),
                grille.getDateCreation(),
                grille.getDateValidation(),
                grille.getMotifRejet());
    }

    /**
     * Repli lorsque le libelle n'a pas ete recopie — grille anterieure au Sprint
     * 2.2, ou libelle indisponible au moment de l'acte.
     *
     * <p>On affiche alors l'identifiant technique plutot que rien : une trace
     * moins lisible reste une trace, tandis qu'un champ vide laisserait croire
     * que la grille n'a pas d'auteur. Un identifiant nul, en revanche, signifie
     * reellement « personne n'a encore agi » (cas du validateur avant decision
     * DRH), et se traduit par {@code null}.
     */
    private static String libelleOuRepli(String libelle, Long identifiant) {
        if (libelle != null && !libelle.isBlank()) {
            return libelle;
        }
        return identifiant == null ? null : "utilisateur #" + identifiant;
    }

}
