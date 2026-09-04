package cm.afrilandfirstbank.rations.audit.infrastructure;

import java.time.LocalDateTime;

/**
 * Criteres optionnels de {@code GET /audit/entrees}. Chaque champ nul est
 * ignore par {@link AuditLogRechercheRepositoryImpl#rechercher}.
 *
 * @param dateDebut borne inferieure incluse sur {@code date_action}
 * @param dateFin borne superieure incluse sur {@code date_action}
 */
public record FiltreAuditEntrees(
        String serviceEmetteur,
        String action,
        String entiteCible,
        Long idEntite,
        Long idUtilisateur,
        LocalDateTime dateDebut,
        LocalDateTime dateFin) {

}
