package cm.afrilandfirstbank.rations.identite.api.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Format de pagination de reference du projet (sous-sprint 1.2, document maitre
 * section 6).
 *
 * <p>Ce format est repris par toutes les listes paginees du backend (Sprints 3,
 * 4 et 6) : ne pas le modifier localement a un service sans repercuter le
 * changement partout.
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
