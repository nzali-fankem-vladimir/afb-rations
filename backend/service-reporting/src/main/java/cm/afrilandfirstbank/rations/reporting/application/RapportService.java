package cm.afrilandfirstbank.rations.reporting.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.reporting.domaine.EnTeteDemande;
import cm.afrilandfirstbank.rations.reporting.domaine.Rapport;
import cm.afrilandfirstbank.rations.reporting.domaine.Rapport.LigneRapport;
import cm.afrilandfirstbank.rations.reporting.domaine.Rapport.SousTotalAgence;
import cm.afrilandfirstbank.rations.reporting.domaine.Rapport.Synthese;
import cm.afrilandfirstbank.rations.reporting.domaine.SituationIntegration;

/**
 * Produit le rapport d'activité d'une période (Sprint 6.2, US-16, CT-32, CT-33).
 *
 * <h2>Le calcul unique de CT-32 vit ici, et nulle part ailleurs</h2>
 *
 * <p>{@link #produire} rend un {@link Rapport} dont tous les totaux sont déjà
 * calculés. Le contrôleur de consultation, {@code ExportPdfService} et
 * {@code ExportExcelService} reçoivent cet objet et se contentent de le rendre.
 * Aucune addition de montant ne se refait dans un générateur : trois chemins de
 * calcul distincts finiraient par diverger, et le rapport ne vaudrait plus rien
 * comme instrument de contrôle. Le test 6 du guide (écran = PDF = Excel) verrouille
 * cette propriété.
 *
 * <h2>Une seule source, un seul appel</h2>
 *
 * <p>Le rapport ne porte aucun critère de nature, de session ni de bénéficiaire :
 * {@link AgregationService#rechercher} n'interroge donc que le service Workflow, en
 * un appel, et jamais la Saisie. Il rend les en-têtes de {@code processus_mensuel}
 * pour la période et l'unité, déjà restreints à la portée de l'utilisateur du
 * jeton (résolue par le Workflow, jamais un paramètre — doctrine Sprint 6.1). La
 * borne de volume et ses refus ({@code 422 RECHERCHE_TROP_LARGE},
 * {@code 503 SERVICE_WORKFLOW_INDISPONIBLE}, {@code 403 UTILISATEUR_NON_HABILITE})
 * sont ceux de l'agrégation, réutilisés tels quels.
 *
 * <h2>Période sans données (CT-33)</h2>
 *
 * <p>Aucun état sur la période n'est pas une erreur : le rapport est rendu avec
 * {@code vide = true}, une {@link Synthese#vide()} tout à zéro et aucune ligne. Les
 * exports en tirent leur mention « Aucune activité enregistrée ».
 */
@Service
public class RapportService {

    private final AgregationService agregationService;

    public RapportService(AgregationService agregationService) {
        this.agregationService = agregationService;
    }

    /**
     * Le rapport de la période, pour une agence ou pour toutes.
     *
     * @param mois              mois de paiement, 1 à 12 (validé au contrôleur)
     * @param annee             année de paiement (validée au contrôleur)
     * @param codeUnite         l'agence demandée, ou {@code null} pour toute la portée
     * @param loginUtilisateur  login de l'utilisateur qui produit le rapport, pour l'en-tête
     * @param enteteAutorisation en-tête {@code Authorization} de l'utilisateur final, relayé tel quel
     */
    public Rapport produire(LocalDate dateDebut, LocalDate dateFin, String codeUnite,
            String loginUtilisateur,
            String enteteAutorisation) {

        CriteresRecherche criteres = new CriteresRecherche(dateDebut, dateFin, codeUnite, null, null, null);
        List<EnTeteDemande> enTetes = agregationService.rechercher(criteres, enteteAutorisation);

        List<LigneRapport> lignes = enTetes.stream()
                .map(RapportService::versLigne)
                .sorted(Comparator
                        .comparing(LigneRapport::codeUnite, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingLong(LigneRapport::idProcessus))
                .toList();

        List<SousTotalAgence> sousTotaux = codeUnite == null
                ? sousTotauxParAgence(lignes)
                : List.of();

        Synthese synthese = lignes.isEmpty() ? Synthese.vide() : synthetiser(lignes);

        return new Rapport(dateDebut, dateFin, codeUnite, LocalDateTime.now(), loginUtilisateur,
                lignes, sousTotaux, synthese, lignes.isEmpty());
    }

    private static LigneRapport versLigne(EnTeteDemande enTete) {
        return new LigneRapport(
                enTete.id(),
                enTete.codeUnite(),
                enTete.typeProcessus(),
                enTete.statut(),
                enTete.montantTotal(),
                enTete.transmisComptabilite(),
                enTete.situationIntegration(),
                enTete.dateCreation());
    }

    /**
     * Un cumul par agence, dans l'ordre des codes unité. Présent seulement dans un
     * rapport national : quand une agence est demandée, la synthèse suffit.
     */
    private static List<SousTotalAgence> sousTotauxParAgence(List<LigneRapport> lignes) {
        Map<String, long[]> cumul = new LinkedHashMap<>();
        for (LigneRapport ligne : lignes) {
            // index 0 : nombre d'états ; index 1 : montant cumulé.
            long[] agence = cumul.computeIfAbsent(cleAgence(ligne.codeUnite()), c -> new long[2]);
            agence[0]++;
            agence[1] += ligne.montantTotal();
        }

        List<SousTotalAgence> resultat = new ArrayList<>();
        cumul.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entree -> resultat.add(new SousTotalAgence(
                        entree.getKey(),
                        (int) entree.getValue()[0],
                        entree.getValue()[1])));
        return resultat;
    }

    /**
     * Les totaux généraux. « Envoyé » (parti sur le topic) et « rejeté » (refusé par
     * la comptabilité après coup) sont deux montants distincts, chacun sur sa ligne :
     * un état peut être envoyé et rejeté, et confondre les deux ferait lire un
     * montant refusé comme un montant payé (décision du 4 septembre 2026).
     */
    private static Synthese synthetiser(List<LigneRapport> lignes) {
        long total = 0;
        long envoye = 0;
        long nonEnvoye = 0;
        long rejete = 0;
        Map<String, Integer> parStatut = new LinkedHashMap<>();
        Map<SituationIntegration, Integer> parSituation = new LinkedHashMap<>();

        for (LigneRapport ligne : lignes) {
            total += ligne.montantTotal();
            if (ligne.envoyeComptabilite()) {
                envoye += ligne.montantTotal();
            } else {
                nonEnvoye += ligne.montantTotal();
            }
            if (ligne.situationIntegration() == SituationIntegration.REJETE) {
                rejete += ligne.montantTotal();
            }
            parStatut.merge(ligne.statut() == null ? "INCONNU" : ligne.statut(), 1, Integer::sum);
            parSituation.merge(ligne.situationIntegration(), 1, Integer::sum);
        }

        return new Synthese(lignes.size(), total, envoye, nonEnvoye, rejete, parStatut, parSituation);
    }

    private static String cleAgence(String codeUnite) {
        return codeUnite == null ? "(sans unite)" : codeUnite;
    }
}
