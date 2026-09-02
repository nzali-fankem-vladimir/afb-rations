package cm.afrilandfirstbank.rations.transmission.api;

import java.time.LocalDateTime;

/**
 * Format d'erreur uniforme du module (CLAUDE.md section 11, contrat d'API section 1.4) :
 * {@code { timestamp, status, code, message, path }}.
 *
 * <p>Recopie a l'identique depuis service-identite, service-grilles, service-saisie puis
 * service-workflow, comme {@code PageResponse} au Sprint 2.2 et les enumerations au
 * Sprint 2.1. Duplication assumee : {@code rations-audit-commun} est la seule
 * mutualisation de code du backend, et son perimetre est verifie au build (CLAUDE.md
 * sections 3 et 15).
 *
 * <p><b>Cinq champs, pas six.</b> Le service Workflow porte un champ facultatif
 * {@code manques} depuis le Sprint 4.2, parce que CT-13 exigeait que le <b>frontend</b>
 * puisse rendre les manques un a un et renvoyer l'agent vers la journee fautive. Ici, le
 * seul consommateur est un service : personne n'a a les afficher. Les anomalies d'une
 * charge refusee sont donc enumerees dans {@code message}, chacune precedee de son code,
 * et conservees telles quelles dans le journal d'audit.
 */
public record ErreurApiDto(
        LocalDateTime timestamp,
        int status,
        String code,
        String message,
        String path) {
}
