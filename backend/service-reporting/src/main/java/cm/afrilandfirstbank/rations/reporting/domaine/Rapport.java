package cm.afrilandfirstbank.rations.reporting.domaine;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Le rapport d'activité d'une période, tel qu'il sera affiché, exporté en PDF et
 * exporté en Excel (Sprint 6.2, US-16).
 *
 * <h2>Un seul calcul, trois usages (CT-32)</h2>
 *
 * <p>Cet objet est produit par {@code RapportService} et par lui seul. Tous ses
 * totaux — synthèse, sous-totaux par agence — sont <b>déjà calculés</b> au moment
 * où il est rendu. Le contrôleur de consultation, {@code ExportPdfService} et
 * {@code ExportExcelService} le reçoivent et ne font que le <b>lire</b> : aucune
 * addition de montant ne se refait ailleurs. Trois chemins de calcul distincts
 * produiraient tôt ou tard trois chiffres différents, et le rapport perdrait sa
 * valeur de contrôle.
 *
 * <p>L'objet est immuable : records, listes recopiées à la construction. Un
 * générateur ne peut donc pas le modifier en le parcourant.
 *
 * <h2>Le grain est l'état mensuel</h2>
 *
 * <p>Une {@link LigneRapport} est un {@code processus_mensuel}, jamais une ligne de
 * prestation ni un bénéficiaire. Le rapport pilote et contrôle les <b>états</b> de
 * paiement ; le détail d'un dossier se consulte par
 * {@code GET /reporting/processus/{id}/historique} (Sprint 6.1).
 *
 * @param periodeMois       mois de paiement, 1 à 12
 * @param periodeAnnee      année de paiement
 * @param codeUnite         l'agence du rapport, ou {@code null} pour « toutes les unités »
 * @param dateGeneration    instant de production du rapport
 * @param loginUtilisateur  login de l'utilisateur qui a produit le rapport
 * @param lignes            une entrée par état de la période et de la portée, triées
 * @param sousTotauxParAgence  vide quand {@code codeUnite} est fourni (une seule agence)
 * @param synthese          les totaux généraux, voir CT-32 et la décision du 4 septembre
 * @param vide              aucun état sur la période : rapport vide signalé (CT-33),
 *                          jamais une erreur
 */
public record Rapport(
        int periodeMois,
        int periodeAnnee,
        String codeUnite,
        LocalDateTime dateGeneration,
        String loginUtilisateur,
        List<LigneRapport> lignes,
        List<SousTotalAgence> sousTotauxParAgence,
        Synthese synthese,
        boolean vide) {

    public Rapport {
        lignes = List.copyOf(lignes);
        sousTotauxParAgence = List.copyOf(sousTotauxParAgence);
    }

    /** Un état mensuel dans le rapport. Les valeurs sont recopiées de l'en-tête du Workflow. */
    public record LigneRapport(
            long idProcessus,
            String codeUnite,
            String typeProcessus,
            String statut,
            long montantTotal,
            boolean envoyeComptabilite,
            SituationIntegration situationIntegration,
            LocalDateTime dateCreation) {
    }

    /**
     * Le cumul d'une agence, présent uniquement dans un rapport multi-agences
     * (aucun {@code codeUnite} demandé).
     */
    public record SousTotalAgence(
            String codeUnite,
            int nombreEtats,
            long montantTotal) {
    }

    /**
     * Les totaux généraux du rapport (CT-32, décision du 4 septembre 2026).
     *
     * <h2>« Envoyé » n'est pas « payé »</h2>
     *
     * <p>{@code montantEnvoyeComptabilite} est la somme des montants des états dont
     * le message est <b>parti sur le topic</b> {@code rations.etat.valide}. Il ne
     * dit rien de ce que la comptabilité en a fait : un état envoyé peut avoir été
     * <b>rejeté</b> après coup. {@code montantRejeteComptabilite} isole ce cas sur
     * une ligne à part — il ne faut pas le déduire mentalement de la répartition
     * détaillée. Les libellés « envoyé » / « rejeté » / « non envoyé » sont repris
     * tels quels par les deux exports et par le frontend (Sprint 7F).
     *
     * @param montantTotalPeriode        Σ des montants de tous les états
     * @param montantEnvoyeComptabilite  Σ des montants des états {@code envoyeComptabilite = true} ;
     *                                   c'est le chiffre que le test 7 de CT-32 confronte à la transmission
     * @param montantNonEnvoyeComptabilite  Σ des montants des états {@code envoyeComptabilite = false}
     * @param montantRejeteComptabilite  Σ des montants des états dont la situation vaut {@code REJETE}
     * @param repartitionParStatut       nombre d'états par statut d'avancement
     * @param repartitionParSituation    nombre d'états par situation d'intégration
     */
    public record Synthese(
            int nombreEtats,
            long montantTotalPeriode,
            long montantEnvoyeComptabilite,
            long montantNonEnvoyeComptabilite,
            long montantRejeteComptabilite,
            Map<String, Integer> repartitionParStatut,
            Map<SituationIntegration, Integer> repartitionParSituation) {

        public Synthese {
            repartitionParStatut = Map.copyOf(repartitionParStatut);
            repartitionParSituation = Map.copyOf(repartitionParSituation);
        }

        /** La synthèse d'un rapport sans aucun état : tout à zéro, aucune répartition. */
        public static Synthese vide() {
            return new Synthese(0, 0L, 0L, 0L, 0L, Map.of(), Map.of());
        }
    }
}
