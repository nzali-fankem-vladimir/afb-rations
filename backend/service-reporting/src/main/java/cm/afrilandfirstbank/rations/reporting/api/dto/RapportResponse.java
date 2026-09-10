package cm.afrilandfirstbank.rations.reporting.api.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import cm.afrilandfirstbank.rations.reporting.domaine.Rapport;
import cm.afrilandfirstbank.rations.reporting.domaine.SituationIntegration;

/**
 * Vue de sortie de {@code GET /reporting/rapports} (Sprint 6.2, US-16).
 *
 * <p>Reflet fidèle du {@link Rapport} du domaine : aucun calcul n'est refait ici,
 * la conversion est champ pour champ. C'est la <b>même</b> instance de rapport que
 * celle passée aux deux exports (CT-32) — l'écran, le PDF et l'Excel partent donc
 * des mêmes chiffres.
 *
 * <p>Les libellés de montants — « envoyé », « non envoyé », « rejeté » — sont
 * repris tels quels par le frontend : « envoyé » ne veut pas dire « payé » (un état
 * envoyé sur le topic peut être rejeté ensuite par la comptabilité).
 */
public record RapportResponse(
        LocalDate periodeDebut,
        LocalDate periodeFin,
        String codeUnite,
        LocalDateTime dateGeneration,
        String loginUtilisateur,
        boolean vide,
        List<LigneRapportResponse> lignes,
        List<SousTotalAgenceResponse> sousTotauxParAgence,
        SyntheseResponse synthese) {

    public static RapportResponse depuis(Rapport rapport) {
        return new RapportResponse(
                rapport.periodeDebut(),
                rapport.periodeFin(),
                rapport.codeUnite(),
                rapport.dateGeneration(),
                rapport.loginUtilisateur(),
                rapport.vide(),
                rapport.lignes().stream().map(LigneRapportResponse::depuis).toList(),
                rapport.sousTotauxParAgence().stream().map(SousTotalAgenceResponse::depuis).toList(),
                SyntheseResponse.depuis(rapport.synthese()));
    }

    /** Un état mensuel du rapport. */
    public record LigneRapportResponse(
            long idProcessus,
            String codeUnite,
            String typeProcessus,
            String statut,
            long montantTotal,
            boolean envoyeComptabilite,
            SituationIntegration situationIntegration,
            LocalDateTime dateCreation) {

        static LigneRapportResponse depuis(Rapport.LigneRapport ligne) {
            return new LigneRapportResponse(
                    ligne.idProcessus(),
                    ligne.codeUnite(),
                    ligne.typeProcessus(),
                    ligne.statut(),
                    ligne.montantTotal(),
                    ligne.envoyeComptabilite(),
                    ligne.situationIntegration(),
                    ligne.dateCreation());
        }
    }

    /** Cumul d'une agence — présent uniquement dans un rapport national. */
    public record SousTotalAgenceResponse(
            String codeUnite,
            int nombreEtats,
            long montantTotal) {

        static SousTotalAgenceResponse depuis(Rapport.SousTotalAgence sousTotal) {
            return new SousTotalAgenceResponse(
                    sousTotal.codeUnite(),
                    sousTotal.nombreEtats(),
                    sousTotal.montantTotal());
        }
    }

    /** Les totaux généraux (CT-32). */
    public record SyntheseResponse(
            int nombreEtats,
            long montantTotalPeriode,
            long montantEnvoyeComptabilite,
            long montantNonEnvoyeComptabilite,
            long montantRejeteComptabilite,
            Map<String, Integer> repartitionParStatut,
            Map<SituationIntegration, Integer> repartitionParSituation) {

        static SyntheseResponse depuis(Rapport.Synthese synthese) {
            return new SyntheseResponse(
                    synthese.nombreEtats(),
                    synthese.montantTotalPeriode(),
                    synthese.montantEnvoyeComptabilite(),
                    synthese.montantNonEnvoyeComptabilite(),
                    synthese.montantRejeteComptabilite(),
                    synthese.repartitionParStatut(),
                    synthese.repartitionParSituation());
        }
    }
}
