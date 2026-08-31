package cm.afrilandfirstbank.rations.saisie.api.dto;

import java.util.List;

import cm.afrilandfirstbank.rations.saisie.application.EtatConsolide;

/**
 * Vue de sortie de l'état mensuel consolidé d'un processus — la réponse de
 * {@code GET /saisie/processus/{id}/etat}, <b>endpoint interne</b> destiné au
 * service Workflow (RG-06, US-06, CT-11, Sprint 3.4).
 *
 * <h2>Ce DTO ne calcule rien, et c'est le point</h2>
 *
 * <p>Tous les agrégats — sous-totaux journaliers, total du mois, comptes de
 * lignes et de bénéficiaires — sont recopiés de {@link EtatConsolide}, produit
 * par {@code ConsolidationService}. Ce service est le seul endroit du module où
 * un montant mensuel est calculé. Le total rendu ici commandera l'aiguillage au
 * seuil de 100 000 XAF au Sprint 4 (RG-08) : une erreur d'un franc envoie un
 * dossier au mauvais niveau de validation.
 *
 * <h2>Ce que la réponse ne porte pas</h2>
 *
 * <p>Ni le statut du processus, ni son type, ni le {@code montant_total} déjà
 * porté par {@code processus_mensuel}. Ces données appartiennent au service
 * Workflow, qui reprend ce total et le porte lui-même : RG-06 est partagée entre
 * les deux services, aucun ne fait le travail de l'autre.
 *
 * <h2>Un état sans aucune journée est une réponse normale</h2>
 *
 * <p>Un processus dont l'agent n'a encore rien saisi est rendu avec
 * {@code journees} vide et {@code montantTotalFcfa} à zéro, en {@code 200} —
 * jamais en {@code 404}. Même parti qu'au Sprint 2.4 pour
 * {@code GET /grilles/active}. Dans ce cas seulement, {@code moisPaiement} et
 * {@code anneePaiement} sont nuls : ils sont lus sur les fiches, et il n'y en a
 * aucune. {@code codeUnite} reste renseigné — c'est l'unité sur laquelle
 * l'appelant a posé la question.
 *
 * @param montantTotalFcfa total du mois, entier en FCFA — jamais un flottant
 * @param journees détail par journée, trié par date croissante
 */
public record EtatConsolideResponse(
        Long idProcessus,
        String codeUnite,
        Integer moisPaiement,
        Integer anneePaiement,
        int nombreJournees,
        int nombreLignes,
        int nombreBeneficiaires,
        long montantTotalFcfa,
        List<JourneeConsolideeResponse> journees) {

    public static EtatConsolideResponse depuis(EtatConsolide etat) {
        List<JourneeConsolideeResponse> journees = etat.journees().stream()
                .map(JourneeConsolideeResponse::depuis)
                .toList();

        return new EtatConsolideResponse(
                etat.idProcessus(),
                etat.codeUnite(),
                etat.moisPaiement(),
                etat.anneePaiement(),
                etat.nombreJournees(),
                etat.nombreLignes(),
                etat.nombreBeneficiaires(),
                etat.montantTotalFcfa(),
                journees);
    }

}
