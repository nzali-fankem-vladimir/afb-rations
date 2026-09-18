package cm.afrilandfirstbank.rations.audit.infrastructure;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import cm.afrilandfirstbank.rations.audit.domaine.AuditLog;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Implementation de {@link AuditLogRechercheRepository}. Nommee
 * {@code AuditLogRechercheRepositoryImpl} par convention Spring Data : ce
 * suffixe est ce qui la fait reconnaitre comme l'implementation du fragment,
 * combinee automatiquement avec {@link AuditLogRepository} dans le meme
 * bean proxy.
 *
 * <p>Criteria API a la main, pas {@code JpaSpecificationExecutor} : voir la
 * javadoc de {@link AuditLogRechercheRepository}.
 */
public class AuditLogRechercheRepositoryImpl implements AuditLogRechercheRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<AuditLog> rechercher(FiltreAuditEntrees filtre, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<AuditLog> requete = cb.createQuery(AuditLog.class);
        Root<AuditLog> racine = requete.from(AuditLog.class);
        requete.select(racine)
                .where(predicats(filtre, cb, racine))
                .orderBy(cb.desc(racine.get("dateAction")));

        List<AuditLog> contenu = entityManager.createQuery(requete)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        CriteriaQuery<Long> requeteCompte = cb.createQuery(Long.class);
        Root<AuditLog> racineCompte = requeteCompte.from(AuditLog.class);
        requeteCompte.select(cb.count(racineCompte))
                .where(predicats(filtre, cb, racineCompte));
        long total = entityManager.createQuery(requeteCompte).getSingleResult();

        return new PageImpl<>(contenu, pageable, total);
    }

    @Override
    public List<AuditLog> parProcessus(Long idProcessus) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<AuditLog> requete = cb.createQuery(AuditLog.class);
        Root<AuditLog> racine = requete.from(AuditLog.class);

        requete.select(racine)
                .where(cb.equal(racine.get("entiteCible"), "processus_mensuel"),
                        cb.equal(racine.get("idEntite"), idProcessus))
                .orderBy(cb.asc(racine.get("dateAction")));

        return entityManager.createQuery(requete).getResultList();
    }

    @Override
    public List<String> actionsDistinctes() {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<String> requete = cb.createQuery(String.class);
        Root<AuditLog> racine = requete.from(AuditLog.class);

        requete.select(racine.get("action"))
                .distinct(true)
                .orderBy(cb.asc(racine.get("action")));

        return entityManager.createQuery(requete).getResultList();
    }

    private Predicate[] predicats(FiltreAuditEntrees filtre, CriteriaBuilder cb, Root<AuditLog> racine) {
        List<Predicate> predicats = new ArrayList<>();

        if (filtre.serviceEmetteur() != null) {
            predicats.add(cb.equal(racine.get("serviceEmetteur"), filtre.serviceEmetteur()));
        }
        if (filtre.action() != null) {
            predicats.add(cb.equal(racine.get("action"), filtre.action()));
        }
        if (filtre.entiteCible() != null) {
            predicats.add(cb.equal(racine.get("entiteCible"), filtre.entiteCible()));
        }
        if (filtre.idEntite() != null) {
            predicats.add(cb.equal(racine.get("idEntite"), filtre.idEntite()));
        }
        if (filtre.idUtilisateur() != null) {
            predicats.add(cb.equal(racine.get("idUtilisateur"), filtre.idUtilisateur()));
        }
        if (filtre.dateDebut() != null) {
            predicats.add(cb.greaterThanOrEqualTo(racine.get("dateAction"), filtre.dateDebut()));
        }
        if (filtre.dateFin() != null) {
            predicats.add(cb.lessThanOrEqualTo(racine.get("dateAction"), filtre.dateFin()));
        }

        return predicats.toArray(new Predicate[0]);
    }

}
