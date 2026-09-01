package cm.afrilandfirstbank.rations.workflow.api;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import cm.afrilandfirstbank.rations.workflow.application.ManqueCompletude;

/**
 * Format d'erreur uniforme du module (CLAUDE.md section 11, contrat d'API
 * section 1.4) : {@code { timestamp, status, code, message, path }}.
 *
 * <p>Recopie a l'identique depuis service-identite, service-grilles puis
 * service-saisie, comme {@code PageResponse} au Sprint 2.2 et les enumerations au
 * Sprint 2.1. La duplication est assumee : {@code rations-audit-commun} est la
 * seule mutualisation de code du backend et son perimetre est verifie au build
 * (CLAUDE.md sections 3 et 15).
 *
 * <h2>Le champ manques, ajoute au Sprint 4.2</h2>
 *
 * <p>CT-13 exige qu'une soumission incomplete soit refusee <b>en listant les
 * manques</b>, et l'etape 2 du guide precise « pour que l'interface les affiche a
 * l'agent ». Les cinq champs du contrat restent presents, au meme nom et au meme
 * type ; {@code manques} est <b>absent du JSON</b> partout ailleurs, grace a
 * {@link JsonInclude.Include#NON_EMPTY}. L'ajout est purement additif : aucun
 * consommateur existant ne casse.
 *
 * <p>Une concatenation des manques dans {@code message} aurait laisse le format
 * rigoureusement intact, mais le frontend (Sprint 7F) ne pourrait ni rendre une
 * liste, ni renvoyer l'agent vers la journee fautive, sans decouper de la prose a
 * la main. Ce serait une liste pour l'oeil, pas pour l'interface.
 *
 * <p><b>Ajoute au seul service Workflow</b> : les trois autres n'ont rien a y
 * mettre, et le champ y serait toujours absent. A porter au contrat d'API et a
 * CLAUDE.md section 11 a la cloture du Sprint 4 (point K-03 de
 * {@code docs/controles-completude.md}).
 *
 * @param manques les manques constates, uniquement sur un refus
 *        {@code 422 ETAT_INCOMPLET} ; nul ou vide partout ailleurs
 */
public record ErreurApiDto(
        LocalDateTime timestamp,
        int status,
        String code,
        String message,
        String path,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<ManqueCompletude> manques) {

    /** Erreur ordinaire, sans liste de manques. */
    public ErreurApiDto(LocalDateTime timestamp, int status, String code, String message,
            String path) {
        this(timestamp, status, code, message, path, null);
    }

}
