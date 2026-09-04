package cm.afrilandfirstbank.rations.audit.api.dto;

import java.time.LocalDateTime;

import cm.afrilandfirstbank.rations.audit.domaine.AuditLog;

/**
 * Une ligne du journal d'audit, rendue par les deux endpoints de lecture
 * (CLAUDE.md section 11). Reprend telles quelles les huit colonnes de
 * {@code audit_log} ; aucune n'est omise, y compris celles nulles
 * (`idUtilisateur`, `idEntite`) : un contrôle interne doit voir l'absence,
 * pas la deviner d'un champ manquant.
 */
public record AuditEntreeResponse(
        Long id,
        Long idUtilisateur,
        String serviceEmetteur,
        String action,
        String entiteCible,
        Long idEntite,
        LocalDateTime dateAction,
        String adresseIp,
        String detailJson) {

    public static AuditEntreeResponse depuis(AuditLog log) {
        return new AuditEntreeResponse(
                log.getId(),
                log.getIdUtilisateur(),
                log.getServiceEmetteur(),
                log.getAction(),
                log.getEntiteCible(),
                log.getIdEntite(),
                log.getDateAction(),
                log.getAdresseIp(),
                log.getDetailJson());
    }

}
