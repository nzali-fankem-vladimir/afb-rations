package cm.afrilandfirstbank.rations.audit.infrastructure;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Forme du message lu sur {@code rations.audit.evenement}, propre au service
 * Audit (CLAUDE.md section 15 : ce service ne depend pas de
 * {@code rations-audit-commun} et ne connait donc pas
 * {@code EvenementAudit}). Les huit champs correspondent au contrat de fil
 * documente par cette derniere, dans le meme ordre — pure coincidence de
 * lecture, pas un couplage : rien ici n'importe le module commun.
 *
 * <h2>Tolerant reader</h2>
 *
 * <p>Tous les champs sont nullables au niveau Java, y compris ceux que
 * {@code AuditLog} exige non nuls ({@code action}, {@code entiteCible},
 * {@code dateAction}, {@code serviceEmetteur}) : c'est au consommateur de
 * decider quoi faire d'un champ obligatoire absent (rejet trace, offset
 * avance), pas a la deserialisation d'echouer a leur place.
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} : un champ ajoute plus
 * tard au contrat de fil (evolution additive, CLAUDE.md section 9.2) ne fait
 * pas echouer la lecture d'un message par ailleurs valide.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageAuditEntrant(
        Long idUtilisateur,
        String serviceEmetteur,
        String action,
        String entiteCible,
        Long idEntite,
        LocalDateTime dateAction,
        String adresseIp,
        String detailJson) {

    /**
     * @return le premier champ obligatoire absent ou vide, ou {@code null} si
     *         le message est complet. Un seul motif a la fois : suffisant pour
     *         agir (rejeter, tracer), inutile de tous les enumerer.
     */
    String premierChampObligatoireManquant() {
        if (action == null || action.isBlank()) {
            return "action";
        }
        if (entiteCible == null || entiteCible.isBlank()) {
            return "entiteCible";
        }
        if (dateAction == null) {
            return "dateAction";
        }
        if (serviceEmetteur == null || serviceEmetteur.isBlank()) {
            return "serviceEmetteur";
        }
        return null;
    }

}
