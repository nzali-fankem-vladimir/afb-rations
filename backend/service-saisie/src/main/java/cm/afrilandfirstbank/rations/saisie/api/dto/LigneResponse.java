package cm.afrilandfirstbank.rations.saisie.api.dto;

import java.time.LocalDateTime;

import cm.afrilandfirstbank.rations.saisie.domaine.Beneficiaire;
import cm.afrilandfirstbank.rations.saisie.domaine.LignePrestation;
import cm.afrilandfirstbank.rations.saisie.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.SessionEnum;

/**
 * Vue de sortie d'une ligne de prestation, rendue par les trois endpoints qui en
 * produisent une ({@code POST} et {@code PUT} sur {@code /saisie/lignes}) et par
 * la consultation {@code GET /saisie/fiches/{id}/lignes}.
 *
 * <h2>Un seul DTO, surensemble de l'exemple du contrat</h2>
 *
 * <p>L'exemple du contrat d'API §3 rend {@code id}, {@code idBeneficiaire},
 * {@code nature}, {@code session} et {@code montantApplique} : ils sont tous ici,
 * au même nom. S'y ajoutent le bénéficiaire développé — exigé par l'étape 5 du
 * Sprint 3.3, faute de quoi l'interface devrait résoudre elle-même chaque
 * identifiant — et {@code idGrille}.
 *
 * <p>Deux DTO, l'un pour l'écriture et l'autre pour la lecture, auraient divergé
 * d'un champ à la première évolution ; l'écart aurait alors été impossible à
 * expliquer autrement que par son histoire.
 *
 * <p><b>{@code idGrille} accompagne toujours le montant.</b> C'est la
 * justification de {@code montantApplique} : sans elle, un montant contesté des
 * mois plus tard ne peut être rattaché à aucune décision tarifaire (dette du
 * Sprint 2.4, colonne ajoutée au 3.1, renseignée au 3.2).
 *
 * <p>Aucune entité JPA n'est exposée : {@link LignePrestation} et
 * {@link Beneficiaire} restent dans le domaine (CLAUDE.md §12).
 */
public record LigneResponse(
        Long id,
        Long idFicheJournaliere,
        Long idBeneficiaire,
        BeneficiaireResume beneficiaire,
        NatureEnum nature,
        SessionEnum session,
        Integer montantApplique,
        Long idGrille,
        LocalDateTime dateCreation) {

    /**
     * Identité du bénéficiaire servi, telle qu'elle est enregistrée — et non
     * telle qu'elle a été saisie sur cette ligne. Les deux peuvent différer : le
     * bénéficiaire est reconnu par son seul numéro de compte, et un écart de nom
     * est signalé sans jamais écraser l'existant (Sprint 3.1). C'est bien
     * l'enregistré qui fait foi pour la mise en paiement.
     */
    public record BeneficiaireResume(
            Long id,
            String nom,
            String prenom,
            String numCompteCourant,
            String codeAgence) {

        public static BeneficiaireResume depuis(Beneficiaire beneficiaire) {
            return new BeneficiaireResume(
                    beneficiaire.getId(),
                    beneficiaire.getNom(),
                    beneficiaire.getPrenom(),
                    beneficiaire.getNumCompteCourant(),
                    beneficiaire.getCodeAgence());
        }
    }

    public static LigneResponse depuis(LignePrestation ligne, Beneficiaire beneficiaire) {
        return new LigneResponse(
                ligne.getId(),
                ligne.getIdFicheJournaliere(),
                ligne.getIdBeneficiaire(),
                BeneficiaireResume.depuis(beneficiaire),
                ligne.getNature(),
                ligne.getSession(),
                ligne.getMontantApplique(),
                ligne.getIdGrille(),
                ligne.getDateCreation());
    }

}
