package cm.afrilandfirstbank.rations.audit.infrastructure;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import cm.afrilandfirstbank.rations.audit.domaine.AuditLog;

/**
 * Fragment de lecture du journal d'audit (etape 6). Ecrit a la main (Criteria
 * API, dans {@link AuditLogRechercheRepositoryImpl}) plutot que via
 * {@code JpaSpecificationExecutor} — ecarte a l'etape 4, apres que le test de
 * garde a revele qu'il porte des methodes {@code delete(Specification)} dans
 * les versions recentes de Spring Data JPA.
 *
 * <p><b>Aucune methode de suppression, ici non plus</b> : trois lectures sont
 * declarees (la troisieme, {@link #actionsDistinctes()}, ajoutee au
 * rattrapage post-7F.6), et rien de plus ne peut apparaitre sans une
 * modification deliberee de ce fichier.
 */
public interface AuditLogRechercheRepository {

    /** Recherche filtree et paginee, toujours triee sur {@code date_action} (CLAUDE.md section 9.2). */
    Page<AuditLog> rechercher(FiltreAuditEntrees filtre, Pageable pageable);

    /**
     * Journal complet d'un processus : {@code entite_cible = 'processus_mensuel'}
     * et {@code id_entite = idProcessus}, trie du premier evenement au dernier.
     *
     * <p><b>Ne couvre que le niveau workflow</b> — declenchement, soumission,
     * validation, retour, transmission, integration comptable — jamais le detail
     * des lignes de saisie (`ligne_prestation`, `fiche_journaliere`), qui vivent
     * dans un espace d'identifiants distinct de celui des processus. Elargir ce
     * filtre a {@code id_entite} seul creerait un risque de collision numerique
     * entre entites sans rapport (une ligne de prestation n°109 confondue avec un
     * processus n°109). Decision tranchee avec l'utilisateur, consignee dans
     * {@code docs/points-en-attente.md} et au contrat d'API section 8.
     */
    List<AuditLog> parProcessus(Long idProcessus);

    /**
     * Les codes d'action reellement portes par le journal, tries alpha, sans
     * doublon. Alimente la liste deroulante "Type d'action" du frontend
     * (retour utilisateur, rattrapage post-7F.6) : celle-ci reprenait
     * jusqu'ici les 30 codes de CLAUDE.md section 9.2 en dur cote client, une
     * copie que rien n'obligeait a suivre le backend si une septieme action
     * apparaissait un jour. Lire les valeurs REELLEMENT ecrites, plutot que la
     * documentation, est aussi plus honnete : un code jamais rencontre ne
     * propose pas une recherche qui rendrait toujours zero resultat.
     */
    List<String> actionsDistinctes();

}
