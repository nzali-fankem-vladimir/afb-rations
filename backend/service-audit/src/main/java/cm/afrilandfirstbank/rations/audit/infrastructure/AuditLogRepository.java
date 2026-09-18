package cm.afrilandfirstbank.rations.audit.infrastructure;

import cm.afrilandfirstbank.rations.audit.domaine.AuditLog;

/**
 * Acces au journal d'audit. Ecriture par le consommateur Kafka (etape 5),
 * lecture filtree et paginee par le fragment {@link AuditLogRechercheRepository}
 * (etape 6).
 *
 * <p><b>Aucune methode de suppression, y compris heritee.</b> Cette interface
 * n'etend ni {@code CrudRepository} ni {@code JpaRepository} : elle etend
 * seulement {@link RepositoryEcritureSeule} ({@code save}, {@code findById})
 * et {@link AuditLogRechercheRepository} (trois lectures, ecrites a la main en
 * Criteria API dans {@link AuditLogRechercheRepositoryImpl}).
 *
 * <p><b>Pas {@code JpaSpecificationExecutor}, deliberement.</b> Tente a
 * l'etape 4, ecarte par le test de garde lui-meme
 * ({@code AuditLogRepositorySansSuppressionTest}) : les versions recentes de
 * Spring Data JPA y ont ajoute {@code delete(PredicateSpecification)} et
 * {@code delete(DeleteSpecification)} — une suppression de masse, exactement
 * ce que ce repository doit exclure.
 */
public interface AuditLogRepository
        extends RepositoryEcritureSeule<AuditLog, Long>, AuditLogRechercheRepository {

}
