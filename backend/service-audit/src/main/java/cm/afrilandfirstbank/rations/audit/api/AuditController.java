package cm.afrilandfirstbank.rations.audit.api;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cm.afrilandfirstbank.rations.audit.api.dto.AuditEntreeResponse;
import cm.afrilandfirstbank.rations.audit.api.dto.PageResponse;
import cm.afrilandfirstbank.rations.audit.application.RechercheAuditService;
import cm.afrilandfirstbank.rations.audit.domaine.AuditLog;

/**
 * Les endpoints de lecture du journal d'audit (CLAUDE.md section 11 -- deux au
 * contrat d'origine, un troisieme ajoute au rattrapage post-7F.6, voir
 * {@link #listerActions()}). <b>Aucun endpoint d'ecriture</b> : l'alimentation
 * se fait exclusivement par le topic {@code rations.audit.evenement} (etape 5).
 *
 * <h2>Roles</h2>
 *
 * <p>{@code ARH}, {@code DRH}, {@code ADMIN} uniquement — decision tranchee
 * avec l'utilisateur a l'etape 6 de ce sprint, par cohérence avec la portee
 * nationale deja retenue pour la consultation de controle interne (Sprint
 * 1.1). Le Directeur Reseau en est explicitement exclu tant que sa portee
 * nationale reste provisoire (voir {@code docs/points-en-attente.md}) ; les
 * roles a portee locale ({@code AGENT_UNITE}, {@code CHEF_UNITE_DA}) n'ont pas
 * vocation a lire le journal complet du module.
 *
 * <h2>Tri</h2>
 *
 * <p>Toujours sur {@code date_action}, jamais sur {@code id} ni sur l'offset
 * Kafka — voir {@link RechercheAuditService}. Aucun parametre {@code sort}
 * n'est accepte : le client ne peut pas le forcer.
 */
@RestController
@RequestMapping("/audit")
public class AuditController {

    private final RechercheAuditService rechercheAuditService;

    public AuditController(RechercheAuditService rechercheAuditService) {
        this.rechercheAuditService = rechercheAuditService;
    }

    /**
     * Recherche filtree et paginee (contrat d'API section 11). Tous les
     * criteres sont optionnels et se combinent en ET.
     */
    @GetMapping("/entrees")
    @PreAuthorize("hasAnyRole('ARH', 'DRH', 'ADMIN')")
    public ResponseEntity<PageResponse<AuditEntreeResponse>> rechercher(
            @RequestParam(required = false) String serviceEmetteur,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entiteCible,
            @RequestParam(required = false) Long idEntite,
            @RequestParam(required = false) Long idUtilisateur,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<AuditLog> resultat = rechercheAuditService.rechercher(
                serviceEmetteur, action, entiteCible, idEntite, idUtilisateur, dateDebut, dateFin, page, size);

        return ResponseEntity.ok(PageResponse.depuis(resultat, AuditEntreeResponse::depuis));
    }

    /**
     * Journal complet d'un processus, niveau workflow uniquement (voir
     * {@code AuditLogRechercheRepository} et {@code docs/points-en-attente.md}).
     * Trie du premier evenement au dernier sur {@code date_action}.
     */
    @GetMapping("/processus/{id}")
    @PreAuthorize("hasAnyRole('ARH', 'DRH', 'ADMIN')")
    public ResponseEntity<List<AuditEntreeResponse>> consulterProcessus(@PathVariable Long id) {
        return ResponseEntity.ok(
                rechercheAuditService.consulterProcessus(id).stream()
                        .map(AuditEntreeResponse::depuis)
                        .toList());
    }

    /**
     * Troisieme endpoint du contrat (rattrapage post-7F.6, retour utilisateur :
     * "je veux savoir si les types d'action en liste deroulante sont bien
     * tires du backend"). Avant cet ajout ils ne l'etaient pas : le frontend
     * portait une copie figee des codes de CLAUDE.md section 9.2.
     *
     * <p>Rend les codes <b>reellement presents</b> dans {@code audit_log},
     * jamais une enumeration statique — il n'en existe d'ailleurs aucune cote
     * backend, {@code action} n'etant qu'une chaine litterale publiee par
     * chacun des six services. Une base vide rend une liste vide, jamais une
     * erreur.
     */
    @GetMapping("/actions")
    @PreAuthorize("hasAnyRole('ARH', 'DRH', 'ADMIN')")
    public ResponseEntity<List<String>> listerActions() {
        return ResponseEntity.ok(rechercheAuditService.actionsDisponibles());
    }

}
