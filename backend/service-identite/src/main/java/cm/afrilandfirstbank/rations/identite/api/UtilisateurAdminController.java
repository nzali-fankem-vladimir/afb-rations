package cm.afrilandfirstbank.rations.identite.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cm.afrilandfirstbank.rations.identite.api.dto.AttributionRoleRequest;
import cm.afrilandfirstbank.rations.identite.api.dto.PageResponse;
import cm.afrilandfirstbank.rations.identite.api.dto.UtilisateurResponse;
import cm.afrilandfirstbank.rations.identite.application.UtilisateurAdminService;
import cm.afrilandfirstbank.rations.identite.application.UtilisateurCourantService;
import cm.afrilandfirstbank.rations.identite.domaine.RoleEnum;
import cm.afrilandfirstbank.rations.identite.domaine.Utilisateur;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Endpoints d'administration des profils locaux (sous-sprint 1.2).
 *
 * <p>L'attribution de role reste reservee au role ADMIN : l'annuaire porte
 * l'identite, ce module porte l'habilitation metier (CLAUDE.md section 10),
 * et aucun endpoint de creation de compte n'existe ici -- les comptes
 * viennent de l'annuaire, ce controleur n'attribue que des habilitations a
 * des comptes deja pre-provisionnes.
 *
 * <p><b>La LECTURE est ouverte a ARH et DRH depuis le rattrapage post-7F.6</b>
 * (retour utilisateur : le journal d'audit doit pouvoir se filtrer par
 * utilisateur, et ARH/DRH sont deja les deux roles habilites a le consulter,
 * CLAUDE.md section 11). Ouvrir la protection au niveau de la classe aurait
 * aussi ouvert l'attribution de role -- ecriture sensible, seule restee
 * @{@code PreAuthorize} sur sa propre methode.
 */
@RestController
@RequestMapping("/identite/utilisateurs")
@Tag(name = "Administration des utilisateurs", description = "Lecture : ADMIN, ARH, DRH. Ecriture : ADMIN seul.")
public class UtilisateurAdminController {

    /** Borne haute de la taille de page : sans borne, un appel excessif degrade le service. */
    private static final int TAILLE_PAGE_MAXIMALE = 100;

    private final UtilisateurAdminService utilisateurAdminService;
    private final UtilisateurCourantService utilisateurCourantService;

    public UtilisateurAdminController(UtilisateurAdminService utilisateurAdminService,
            UtilisateurCourantService utilisateurCourantService) {
        this.utilisateurAdminService = utilisateurAdminService;
        this.utilisateurCourantService = utilisateurCourantService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ARH', 'DRH')")
    @Operation(summary = "Liste paginee des utilisateurs",
            description = """
                    Filtrable par role, code unite et statut actif, les trois filtres etant
                    combinables et tous facultatifs.

                    **Role requis : ADMIN, ARH ou DRH.** Tout autre role recoit 403, et la
                    tentative est tracee en audit (CT-04). Ouvert a ARH et DRH pour alimenter le
                    filtre "Utilisateur" du journal d'audit, qu'ils sont deja habilites a
                    consulter -- lecture seule, l'attribution de role reste reservee a l'ADMIN.

                    Taille de page bornee a 100, quelle que soit la valeur demandee.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page d'utilisateurs",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "content": [
                                {
                                  "id": 12,
                                  "login": "jean_mbarga",
                                  "nom": "MBARGA",
                                  "prenom": "Jean",
                                  "email": "jean_mbarga@afrilandfirstbank.com",
                                  "role": "AGENT_UNITE",
                                  "codeUnite": "00002",
                                  "actif": true,
                                  "dateDernierAcces": "2026-08-27T08:41:12"
                                }
                              ],
                              "page": 0,
                              "size": 20,
                              "totalElements": 1,
                              "totalPages": 1,
                              "dernierePage": true
                            }"""))),
            @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expire",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Role different de ADMIN. Trace en audit.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "timestamp": "2026-08-27T09:12:00",
                              "status": 403,
                              "code": "ACCES_REFUSE",
                              "message": "Le role de l'utilisateur ne permet pas cette action.",
                              "path": "/identite/utilisateurs"
                            }""")))
    })
    public ResponseEntity<PageResponse<UtilisateurResponse>> lister(
            @RequestParam(required = false) RoleEnum role,
            @RequestParam(required = false) String codeUnite,
            @RequestParam(required = false) Boolean actif,
            @PageableDefault(size = 20)
            @SortDefault.SortDefaults({
                    @SortDefault(sort = "nom", direction = Sort.Direction.ASC),
                    @SortDefault(sort = "prenom", direction = Sort.Direction.ASC)
            })
            Pageable pageable) {

        Pageable pageableBornee = pageable.getPageSize() > TAILLE_PAGE_MAXIMALE
                ? PageRequest.of(pageable.getPageNumber(), TAILLE_PAGE_MAXIMALE, pageable.getSort())
                : pageable;

        Page<Utilisateur> page = utilisateurAdminService.lister(role, codeUnite, actif, pageableBornee);
        return ResponseEntity.ok(PageResponse.depuis(page, UtilisateurResponse::depuis));
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Attribution du role et du code unite",
            description = """
                    L'identite vient de l'annuaire, l'habilitation metier vient du module.

                    **Role requis : ADMIN.**

                    Trois controles de fond (decision Sprint 1.2) :
                    un administrateur ne peut pas se cibler lui-meme (409) ; le dernier
                    administrateur actif ne peut pas perdre le role ADMIN (409) ; un code unite
                    est obligatoire pour les roles a portee locale, AGENT_UNITE et
                    CHEF_UNITE_DA (400).

                    Aucune invalidation de session n'est necessaire : le module est stateless,
                    le role est relu en base a chaque requete et s'applique des l'appel suivant.

                    L'operation publie un evenement d'audit portant le delta avant/apres complet.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil mis a jour",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "id": 14,
                              "login": "marie_ngono",
                              "nom": "NGONO",
                              "prenom": "Marie",
                              "email": "marie_ngono@afrilandfirstbank.com",
                              "role": "CHEF_UNITE_DA",
                              "codeUnite": "00002",
                              "actif": true,
                              "dateDernierAcces": "2026-08-26T16:02:44"
                            }"""))),
            @ApiResponse(responseCode = "400",
                    description = "Requete invalide, ou code unite absent pour un role a portee locale",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "timestamp": "2026-08-27T09:12:00",
                              "status": 400,
                              "code": "CODE_UNITE_INCOHERENT",
                              "message": "Le code unite est obligatoire pour le role AGENT_UNITE.",
                              "path": "/identite/utilisateurs/14/role"
                            }"""))),
            @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expire",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Role different de ADMIN. Trace en audit.",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Utilisateur cible introuvable",
                    content = @Content),
            @ApiResponse(responseCode = "409",
                    description = "Auto-modification, ou retrait du dernier administrateur actif",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "timestamp": "2026-08-27T09:12:00",
                              "status": 409,
                              "code": "DERNIER_ADMINISTRATEUR",
                              "message": "Impossible de retirer le role ADMIN au dernier administrateur actif du systeme.",
                              "path": "/identite/utilisateurs/14/role"
                            }""")))
    })
    public ResponseEntity<UtilisateurResponse> attribuerRole(
            @PathVariable Long id,
            @Valid @RequestBody AttributionRoleRequest requete,
            @AuthenticationPrincipal Jwt jeton,
            HttpServletRequest requeteHttp) {

        Utilisateur appelant = utilisateurCourantService.resoudre(jeton);
        Utilisateur profilMisAJour = utilisateurAdminService.attribuerRole(
                id, requete, appelant, requeteHttp.getRemoteAddr());
        return ResponseEntity.ok(UtilisateurResponse.depuis(profilMisAJour));
    }

}
