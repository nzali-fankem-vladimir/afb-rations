package cm.afrilandfirstbank.rations.audit.api.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Format de pagination de reference du projet (Sprint 1.2, document maitre
 * section 6), recopie a l'identique depuis service-identite — comme
 * {@code NatureEnum}/{@code SessionEnum} au Sprint 2.1, ou {@code PageResponse}
 * lui-meme dans service-grilles (Sprint 2.2). Duplication assumee :
 * {@code rations-audit-commun} est la seule mutualisation du backend.
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
