package cm.afrilandfirstbank.rations.workflow.api.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutIntegrationEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.TypeProcessusEnum;

/**
 * L'en-tete d'un etat mensuel tel qu'il voyage vers le service Reporting
 * (Sprint 6.1). Element de {@link RechercheProcessusResponse}.
 *
 * <h2>Ce qu'il porte, et pourquoi pas davantage</h2>
 *
 * <p>Exactement ce que {@code GET /reporting/demandes} doit afficher : la periode,
 * l'unite, le montant total, le statut d'avancement et le statut d'integration
 * comptable. Ni le motif de retour, ni le motif d'ouverture, ni la reference
 * comptable : une liste de suivi n'a pas a diffuser le detail de chaque dossier, et
 * chaque champ ajoute ici est un champ de plus a transporter des milliers de fois.
 *
 * <p><b>Distinct de {@link ProcessusResponse}</b>, qui rend le dossier complet a un
 * acteur du circuit sur {@code GET /processus/{id}}. Les deux pourraient se
 * ressembler ; les confondre ferait qu'un enrichissement du detail alourdirait
 * silencieusement toutes les recherches.
 *
 * <p>{@code statutIntegration} est <b>nul dans deux situations sans rapport</b> —
 * jamais transmis, et publication non confirmee (Sprint 5.3). C'est au Reporting de
 * les distinguer a partir de {@code transmisComptabilite}, qui voyage a cote pour
 * cette raison precise.
 */
public record EnTeteProcessusResponse(
        Long id,
        LocalDate dateDebut,
        LocalDate dateFin,
        String codeUnite,
        TypeProcessusEnum typeProcessus,
        int montantTotal,
        StatutEnum statut,
        boolean transmisComptabilite,
        StatutIntegrationEnum statutIntegration,
        LocalDateTime dateCreation) {

    public static EnTeteProcessusResponse depuis(ProcessusMensuel processus) {
        return new EnTeteProcessusResponse(
                processus.getId(),
                processus.getDateDebut(),
                processus.getDateFin(),
                processus.getCodeUnite(),
                processus.getTypeProcessus(),
                processus.getMontantTotal(),
                processus.getStatut(),
                processus.isTransmisComptabilite(),
                processus.getStatutIntegration(),
                processus.getDateCreation());
    }



}
