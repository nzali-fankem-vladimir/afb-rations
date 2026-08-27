package cm.afrilandfirstbank.rations.grilles.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cm.afrilandfirstbank.rations.grilles.api.dto.CreationGrilleRequest;
import cm.afrilandfirstbank.rations.grilles.api.dto.GrilleResponse;
import cm.afrilandfirstbank.rations.grilles.api.dto.PageResponse;
import cm.afrilandfirstbank.rations.grilles.application.GrilleService;
import cm.afrilandfirstbank.rations.grilles.domaine.GrilleTarifaire;
import cm.afrilandfirstbank.rations.grilles.domaine.StatutGrilleEnum;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Endpoints des grilles tarifaires (contrat d'API section 4).
 *
 * <p>Perimetre du sous-sprint 2.2 : ce que fait l'<b>Analyste RH</b>. La
 * validation et le rejet par la Directrice RH relevent du sous-sprint 2.3 ; on
 * ne trouvera donc ici <b>aucun endpoint de decision</b>. Cette absence est
 * voulue : une grille qui deviendrait applicable sans passer par la DRH
 * contredirait RG-14.
 */
@RestController
@RequestMapping("/grilles")
@Tag(name = "Grilles tarifaires",
        description = "Cycle de vie des grilles tarifaires (RG-14). Cote Analyste RH.")
public class GrilleController {

    private final GrilleService grilleService;

    public GrilleController(GrilleService grilleService) {
        this.grilleService = grilleService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ARH', 'DRH')")
    @Operation(summary = "Liste les grilles tarifaires",
            description = """
                    Liste paginee, filtrable par statut.

                    **Roles requis :** ARH ou DRH. Les deux voient la meme liste : l'ARH pour
                    suivre ses propositions, la DRH pour trouver celles qu'elle doit trancher.
                    Une grille tarifaire n'est rattachee a aucun code unite — elle vaut
                    nationalement — donc aucune restriction de portee ne s'applique.

                    **Tri par defaut :** nature, puis session, puis date de debut decroissante.
                    Un parametre `sort` explicite le remplace.

                    **Format de pagination :** celui de reference du projet (Sprint 1.2).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page de grilles",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "content": [
                                {
                                  "id": 29,
                                  "nature": "TRANSPORT",
                                  "session": "SOIR",
                                  "montantFcfa": 3000,
                                  "dateDebut": "2026-09-01",
                                  "dateFin": null,
                                  "statutValidation": "EN_ATTENTE_DRH",
                                  "createur": "NKOLO Claire",
                                  "validateur": null,
                                  "dateCreation": "2026-08-27T09:12:00",
                                  "dateValidation": null,
                                  "motifRejet": null
                                }
                              ],
                              "page": 0,
                              "size": 10,
                              "totalElements": 1,
                              "totalPages": 1,
                              "dernierePage": true
                            }"""))),
            @ApiResponse(responseCode = "400", description = "Statut hors enumeration", content = @Content),
            @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expire", content = @Content),
            @ApiResponse(responseCode = "403", description = "Role autre qu'ARH ou DRH. Trace en audit.", content = @Content)
    })
    public ResponseEntity<PageResponse<GrilleResponse>> lister(
            @RequestParam(required = false) StatutGrilleEnum statut,
            @PageableDefault(size = 20) Pageable pagination) {

        Page<GrilleTarifaire> page = grilleService.lister(statut, pagination);
        return ResponseEntity.ok(PageResponse.depuis(page, GrilleResponse::depuis));
    }

    @PostMapping
    @PreAuthorize("hasRole('ARH')")
    @Operation(summary = "Cree une grille et la soumet a la Directrice RH",
            description = """
                    Enregistre une grille tarifaire au statut **EN_ATTENTE_DRH**, en un seul appel.

                    **Role requis :** ARH.

                    La grille est **sans effet sur les saisies** tant que la DRH n'a pas tranche :
                    le montant applique aux prestations du jour reste celui de la grille en
                    vigueur (CT-25). Aucun brouillon n'est conserve : chaque appel est un
                    engagement (decision Sprint 2.2).

                    **Conflits refuses (409, RG-14) :**
                    - une proposition attend deja la DRH sur ce couple nature et session ;
                    - la date de debut n'est pas posterieure a celle de la grille en vigueur.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Grille creee et soumise"),
            @ApiResponse(responseCode = "400", description = "Champ manquant, montant nul ou negatif, nature ou session hors enumeration",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expire", content = @Content),
            @ApiResponse(responseCode = "403", description = "Role autre qu'ARH, ou aucun profil ouvert dans le module. Trace en audit.",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "Conflit d'unicite RG-14",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "timestamp": "2026-08-27T09:12:00",
                              "status": 409,
                              "code": "GRILLE_ACTIVE_EXISTANTE",
                              "message": "Une grille RATION / JOUR est active depuis le 01/08/2026, a 1500 FCFA. Une nouvelle grille doit prendre effet APRES cette date ; le 01/08/2026 demande recouvrirait une periode deja servie.",
                              "path": "/grilles"
                            }"""))),
            @ApiResponse(responseCode = "503", description = "Service Identite injoignable : l'auteur n'a pas pu etre identifie, rien n'a ete enregistre",
                    content = @Content)
    })
    public ResponseEntity<GrilleResponse> creer(
            @Valid @RequestBody CreationGrilleRequest requete,
            // Le jeton de l'ARH est relaye tel quel au service Identite, qui doit
            // resoudre son identifiant local (decision Sprint 1.3 : aucun compte
            // de service, on relaie l'identite de l'utilisateur final).
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation,
            HttpServletRequest requeteHttp) {

        GrilleTarifaire grille = grilleService.creerEtSoumettre(
                requete, enteteAutorisation, requeteHttp.getRemoteAddr());

        return ResponseEntity.status(HttpStatus.CREATED).body(GrilleResponse.depuis(grille));
    }

}
