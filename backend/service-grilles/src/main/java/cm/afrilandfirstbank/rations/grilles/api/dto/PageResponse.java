package cm.afrilandfirstbank.rations.grilles.api.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Format de pagination de reference du projet (decision Sprint 1.2, document
 * maitre section 6).
 *
 * <p><b>Recopie a l'identique</b> depuis service-identite, ou il a ete fixe.
 * Cette duplication est assumee, comme celle de {@code NatureEnum} et
 * {@code SessionEnum} (decision Sprint 2.1,
 * {@code docs/decisions/2026-08-27-partage-enumerations-nature-session.md}) :
 * le module Maven {@code rations-audit-commun} est la seule mutualisation de code
 * du backend, et son perimetre — la publication d'audit — est verifie au build
 * (CLAUDE.md sections 3 et 15). Y ajouter un DTO de pagination est explicitement
 * interdit.
 *
 * <p>Les six champs et leurs noms font contrat vis-a-vis du frontend : ne pas les
 * modifier localement sans repercuter le changement dans tous les services.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean dernierePage) {

    public static <E, T> PageResponse<T> depuis(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }

}
