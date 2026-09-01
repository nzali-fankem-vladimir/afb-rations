package cm.afrilandfirstbank.rations.workflow.api.dto;

import java.util.List;

import cm.afrilandfirstbank.rations.workflow.application.EtatConsolide;
import cm.afrilandfirstbank.rations.workflow.application.ProcessusService.EtatProcessus;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.TypeProcessusEnum;

/**
 * Corps de {@code GET /processus/{id}/etat} — l'endpoint public du contrat
 * (section 5, US-06).
 *
 * <p>Les deux moities de RG-06 reunies : ce que <b>Workflow</b> detient — statut,
 * type, montant porte, indicateur de transmission — et ce que <b>Saisie</b>
 * produit — journees, lignes, sous-totaux, total du mois
 * ({@code docs/appel-consolidation.md} section 1).
 *
 * <h2>Deux montants, et il faut les distinguer</h2>
 *
 * <p>{@link #montantTotalPorte} est la valeur enregistree sur
 * {@code processus_mensuel.montant_total} ; {@link #montantTotalFcfa} est le
 * total calcule a l'instant par le service Saisie.
 *
 * <p>Ils different legitimement <b>tant que l'etat n'a pas ete soumis</b> : le
 * montant porte vaut alors {@code 0}, puisque c'est la soumission (Sprint 4.2)
 * qui y reporte le total. Apres soumission, l'ecriture est fermee cote Saisie et
 * les deux valeurs coincident — c'est ce qui rend l'aiguillage RG-08 stable entre
 * le moment ou le valideur voit le montant et celui ou il decide (section 7 de la
 * convention).
 *
 * <p>Les fondre en un seul champ ferait disparaitre cette information : on ne
 * saurait plus si le chiffre affiche est celui qui engage le circuit ou une
 * photographie de la saisie en cours.
 *
 * <h2>Le detail est recopie, jamais recalcule</h2>
 *
 * <p>{@code montantTotalFcfa}, les sous-totaux et les lignes viennent tels quels
 * de la reponse de Saisie. Workflow ne readditionne rien : il n'existe qu'un
 * chemin de calcul, dans {@code ConsolidationService} cote Saisie, et une seconde
 * somme posee ici pourrait diverger sans que rien ne le signale (decision
 * Sprint 3.4).
 */
public record EtatProcessusResponse(

        // --- Ce que Workflow detient ---
        Long idProcessus,
        StatutEnum statut,
        TypeProcessusEnum typeProcessus,
        String codeUnite,
        Integer moisPaiement,
        Integer anneePaiement,
        int montantTotalPorte,
        boolean transmisComptabilite,

        // --- Ce que Saisie produit ---
        Integer nombreJournees,
        Integer nombreLignes,
        Integer nombreBeneficiaires,
        Long montantTotalFcfa,
        List<EtatConsolide.Journee> journees) {

    public static EtatProcessusResponse depuis(EtatProcessus etat) {
        ProcessusMensuel processus = etat.processus();
        EtatConsolide consolidation = etat.consolidation();

        return new EtatProcessusResponse(
                processus.getId(),
                processus.getStatut(),
                processus.getTypeProcessus(),
                // Unite et periode viennent du processus, jamais de la consolidation :
                // celle-ci les rend nulles quand aucune fiche n'existe encore, alors
                // que le processus, lui, les connait toujours.
                processus.getCodeUnite(),
                processus.getMoisPaiement(),
                processus.getAnneePaiement(),
                processus.getMontantTotal(),
                processus.isTransmisComptabilite(),
                consolidation.nombreJournees(),
                consolidation.nombreLignes(),
                consolidation.nombreBeneficiaires(),
                consolidation.montantTotalFcfa(),
                consolidation.journees() == null ? List.of() : consolidation.journees());
    }

}
