package cm.afrilandfirstbank.rations.grilles.api;

import java.time.LocalDateTime;

/**
 * Format d'erreur uniforme du module (CLAUDE.md section 11).
 * Meme structure pour tous les services, quel que soit le code HTTP.
 */
public record ErreurApiDto(
        LocalDateTime timestamp,
        int status,
        String code,
        String message,
        String path) {
}
