package cm.afrilandfirstbank.rations.audit.infrastructure;

import java.util.Optional;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * Contrat restreint pour un journal immuable : ecrire une fois, relire,
 * jamais supprimer ni modifier (CLAUDE.md sections 3 et 4, guide de ce sprint,
 * etape 4).
 *
 * <p>Etend directement {@link Repository}, le marqueur racine de Spring Data,
 * qui ne declare <b>aucune methode</b>. C'est deliberement plus restrictif que
 * {@code CrudRepository} ou {@code JpaRepository} : ceux-ci exposent
 * {@code deleteById}, {@code delete}, {@code deleteAll} et leurs variantes,
 * heritees, jamais explicitement voulues par un service metier qui ne les
 * appelle pas. Ici, seules {@link #save} et {@link #findById} existent parce
 * qu'elles sont explicitement declarees ci-dessous — aucune suppression ne
 * peut redevenir accessible par un import direct de {@code JpaRepository}
 * ailleurs dans ce module, puisque {@link
 * cm.afrilandfirstbank.rations.audit.infrastructure.AuditLogRepository} est
 * l'unique repository du service et n'etend que celui-ci.
 *
 * <p>{@code @NoRepositoryBean} : cette interface n'est pas un repository a
 * elle seule, elle sert de base a {@code AuditLogRepository}.
 */
@NoRepositoryBean
public interface RepositoryEcritureSeule<T, ID> extends Repository<T, ID> {

    T save(T entite);

    Optional<T> findById(ID id);

}
