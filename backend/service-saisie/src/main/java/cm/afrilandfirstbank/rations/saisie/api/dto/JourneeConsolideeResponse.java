package cm.afrilandfirstbank.rations.saisie.api.dto;

import java.time.LocalDate;
import java.util.List;

import cm.afrilandfirstbank.rations.saisie.application.EtatConsolide.JourneeConsolidee;
import cm.afrilandfirstbank.rations.saisie.domaine.StatutFicheEnum;

/**
 * Une journée de l'état mensuel consolidé : la fiche, ses lignes détaillées et
 * leur sous-total (RG-06, US-06, Sprint 3.4).
 *
 * <h2>Ce DTO ne calcule rien</h2>
 *
 * <p>{@code sousTotalFcfa} et {@code nombreLignes} sont recopiés de
 * {@link JourneeConsolidee}, produits par {@code ConsolidationService}. C'est
 * délibéré et c'est la différence avec {@link FicheResponse}, qui somme lui-même
 * les montants de sa fiche : sur le chemin de la consolidation, <b>le montant est
 * calculé en un seul endroit du module</b>, celui qui alimentera l'aiguillage au
 * seuil du Sprint 4 (RG-08). Recalculer ici ouvrirait un second chemin, avec sa
 * propre occasion de diverger.
 *
 * <h2>Le détail des lignes réutilise {@link LigneResponse}</h2>
 *
 * <p>Le DTO du Sprint 3.3 porte déjà tout ce qu'exigent US-06 et la charge
 * {@code rations.etat.valide} du contrat §7.1 : bénéficiaire développé avec son
 * numéro de compte et son code agence, nature, session, montant figé, grille
 * d'origine. Un second DTO de lecture pour la même donnée aurait divergé d'un
 * champ à la première évolution.
 *
 * @param nombreLignes lignes de cette journée (agrégat, recopié)
 * @param sousTotalFcfa somme des montants de la journée (agrégat, recopié) —
 *        entier en FCFA, jamais un flottant
 * @param lignes détail ligne à ligne, dans l'ordre de saisie
 */
public record JourneeConsolideeResponse(
        Long idFicheJournaliere,
        LocalDate dateJour,
        StatutFicheEnum statut,
        int nombreLignes,
        long sousTotalFcfa,
        List<LigneResponse> lignes) {

    public static JourneeConsolideeResponse depuis(JourneeConsolidee journee) {
        List<LigneResponse> lignes = journee.lignes().stream()
                .map(ligne -> LigneResponse.depuis(ligne.ligne(), ligne.beneficiaire()))
                .toList();

        return new JourneeConsolideeResponse(
                journee.idFicheJournaliere(),
                journee.dateJour(),
                journee.statut(),
                journee.nombreLignes(),
                journee.sousTotalFcfa(),
                lignes);
    }

}
