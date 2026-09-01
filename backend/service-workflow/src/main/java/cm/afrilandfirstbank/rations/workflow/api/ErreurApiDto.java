package cm.afrilandfirstbank.rations.workflow.api;

import java.time.LocalDateTime;

/**
 * Format d'erreur uniforme du module (CLAUDE.md section 11, contrat d'API
 * section 1.4) : {@code { timestamp, status, code, message, path }}.
 *
 * <p>Recopie a l'identique depuis service-identite, service-grilles puis
 * service-saisie, comme {@code PageResponse} au Sprint 2.2 et les enumerations au
 * Sprint 2.1. La duplication est assumee : {@code rations-audit-commun} est la
 * seule mutualisation de code du backend et son perimetre est verifie au build
 * (CLAUDE.md sections 3 et 15).
 */
public record ErreurApiDto(
        LocalDateTime timestamp,
        int status,
        String code,
        String message,
        String path) {
}
