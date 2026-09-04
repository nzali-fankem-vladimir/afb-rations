package cm.afrilandfirstbank.rations.reporting.api.dto;

import java.time.LocalDateTime;

import cm.afrilandfirstbank.rations.reporting.domaine.EnTeteDemande;
import cm.afrilandfirstbank.rations.reporting.domaine.SituationIntegration;

/**
 * Une ligne de {@code GET /reporting/demandes} (Sprint 6.1, US-15).
 *
 * <p>Cinq informations demandées par le guide (§6, étape 4) : la période, l'unité,
 * le montant total, le statut d'avancement et le statut d'intégration comptable.
 * {@code situationIntegration} est ajouté à côté, sur le même principe que la
 * consultation d'intégration du Sprint 5.3 : {@code statutIntegration} seul est nul
 * dans deux situations sans rapport (jamais transmis, publication non confirmée),
 * et une liste de suivi ne doit pas laisser deviner laquelle.
 */
public record DemandeResponse(
        Long idProcessus,
        Integer moisPaiement,
        Integer anneePaiement,
        String codeUnite,
        String typeProcessus,
        int montantTotal,
        String statut,
        boolean transmisComptabilite,
        String statutIntegration,
        SituationIntegration situationIntegration,
        LocalDateTime dateCreation) {

    public static DemandeResponse depuis(EnTeteDemande enTete) {
        return new DemandeResponse(
                enTete.id(),
                enTete.moisPaiement(),
                enTete.anneePaiement(),
                enTete.codeUnite(),
                enTete.typeProcessus(),
                enTete.montantTotal(),
                enTete.statut(),
                enTete.transmisComptabilite(),
                enTete.statutIntegration(),
                enTete.situationIntegration(),
                enTete.dateCreation());
    }

}
