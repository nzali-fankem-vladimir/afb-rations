package cm.afrilandfirstbank.rations.saisie.api;

import java.time.LocalDateTime;

/**
 * Format d'erreur uniforme du module (CLAUDE.md §11, contrat d'API §1.4) :
 * {@code { timestamp, status, code, message, path }}.
 *
 * <p>Recopié à l'identique depuis service-identite puis service-grilles, comme
 * {@code PageResponse} au Sprint 2.2 et les énumérations au Sprint 2.1. La
 * duplication est assumée : {@code rations-audit-commun} est la seule
 * mutualisation de code du backend et son périmètre est vérifié au build
 * (CLAUDE.md §3 et §15).
 */
public record ErreurApiDto(
        LocalDateTime timestamp,
        int status,
        String code,
        String message,
        String path) {
}
