package cm.afrilandfirstbank.rations.reporting.api.dto;

import java.util.List;

/**
 * Format de pagination de reference du projet (sous-sprint 1.2, document maitre
 * section 6).
 *
 * <p>Recopie a l'identique depuis service-identite. Ce service pagine <b>en
 * memoire</b> — il n'a pas de base et donc pas de {@code org.springframework.data.domain.Page}
 * a partir duquel construire cette reponse — mais le format reste le meme pour ne
 * pas surprendre un client qui consomme deja les listes paginees des autres
 * services.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean dernierePage) {

    /**
     * Construit une page a partir d'une liste deja triee et deja filtree, en
     * decoupant en memoire.
     *
     * @param elements la liste complete, dans l'ordre final
     * @param page numero de page demande, 0-indexe ; une valeur negative est
     *        ramenee a 0
     * @param size taille de page demandee ; une valeur non positive est ramenee a 1
     */
    public static <T> PageResponse<T> depuis(List<T> elements, int page, int size) {
        int pageEffective = Math.max(page, 0);
        int tailleEffective = Math.max(size, 1);

        int total = elements.size();
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / tailleEffective);

        int debut = Math.min(pageEffective * tailleEffective, total);
        int fin = Math.min(debut + tailleEffective, total);

        boolean dernierePage = totalPages == 0 || pageEffective >= totalPages - 1;

        return new PageResponse<>(elements.subList(debut, fin), pageEffective, tailleEffective,
                total, totalPages, dernierePage);
    }

}
