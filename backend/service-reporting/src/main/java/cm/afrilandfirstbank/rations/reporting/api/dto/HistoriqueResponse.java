package cm.afrilandfirstbank.rations.reporting.api.dto;

import java.util.List;

import cm.afrilandfirstbank.rations.reporting.domaine.HistoriqueDemande;

/**
 * Réponse de {@code GET /reporting/processus/{id}/historique} (Sprint 6.1,
 * CT-31).
 *
 * <p>L'en-tête voyage avec les étapes : le lecteur en a besoin pour situer le
 * dossier sans un second appel.
 */
public record HistoriqueResponse(
        Long idProcessus,
        Integer moisPaiement,
        Integer anneePaiement,
        String codeUnite,
        String statut,
        List<EtapeHistoriqueResponse> etapes) {

    public static HistoriqueResponse depuis(HistoriqueDemande historique) {
        return new HistoriqueResponse(
                historique.idProcessus(),
                historique.moisPaiement(),
                historique.anneePaiement(),
                historique.codeUnite(),
                historique.statut(),
                historique.etapes().stream().map(EtapeHistoriqueResponse::depuis).toList());
    }

}
