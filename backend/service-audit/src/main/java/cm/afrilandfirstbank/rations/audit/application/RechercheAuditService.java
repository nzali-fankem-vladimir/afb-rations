package cm.afrilandfirstbank.rations.audit.application;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.audit.domaine.AuditLog;
import cm.afrilandfirstbank.rations.audit.infrastructure.AuditLogRepository;
import cm.afrilandfirstbank.rations.audit.infrastructure.FiltreAuditEntrees;

/**
 * Lecture du journal d'audit pour les deux endpoints du contrat (CLAUDE.md
 * section 11). Aucune ecriture ici : l'alimentation se fait exclusivement par
 * le consommateur Kafka de l'etape 5.
 *
 * <h2>Le tri sur {@code date_action} n'est pas negociable</h2>
 *
 * <p>Delibere : le tri n'est <b>jamais</b> pris en parametre de requete, ni
 * transmis a Spring Data via un {@code Sort} construit par l'appelant. La
 * seule valeur possible est {@link Sort#by} sur {@code dateAction},
 * construite ici. Un {@code Pageable} Spring-MVC classique (via
 * {@code @PageableDefault}) laisserait un client forcer {@code ?sort=id} et
 * recreer exactement le piege documente au Sprint 6.3 : l'ordre d'arrivee sur
 * le topic n'est pas l'ordre des faits (pool {@code @Async} multi-thread cote
 * producteurs).
 */
@Service
public class RechercheAuditService {

    /** Taille de page maximale, meme convention que {@code UtilisateurAdminController} (Sprint 1.2). */
    static final int TAILLE_PAGE_MAXIMALE = 200;
    static final int TAILLE_PAGE_PAR_DEFAUT = 20;

    private final AuditLogRepository repository;

    public RechercheAuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** {@code GET /audit/entrees} : recherche filtree et paginee, la plus recente en tete. */
    public Page<AuditLog> rechercher(String serviceEmetteur, String action, String entiteCible,
            Long idEntite, Long idUtilisateur, LocalDateTime dateDebut, LocalDateTime dateFin,
            int page, int size) {

        int tailleBornee = size <= 0 ? TAILLE_PAGE_PAR_DEFAUT : Math.min(size, TAILLE_PAGE_MAXIMALE);
        int pageBornee = Math.max(page, 0);

        Pageable pageable = PageRequest.of(pageBornee, tailleBornee, Sort.by(Sort.Direction.DESC, "dateAction"));

        FiltreAuditEntrees filtre = new FiltreAuditEntrees(
                serviceEmetteur, action, entiteCible, idEntite, idUtilisateur, dateDebut, dateFin);

        return repository.rechercher(filtre, pageable);
    }

    /**
     * {@code GET /audit/processus/{id}} : journal complet d'un processus, niveau
     * workflow uniquement (voir {@code AuditLogRechercheRepository}), du premier
     * evenement au dernier.
     */
    public List<AuditLog> consulterProcessus(Long idProcessus) {
        return repository.parProcessus(idProcessus);
    }

}
