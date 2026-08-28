package cm.afrilandfirstbank.rations.grilles.api;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cm.afrilandfirstbank.rations.grilles.api.dto.CreationGrilleRequest;
import cm.afrilandfirstbank.rations.grilles.api.dto.GrilleResponse;
import cm.afrilandfirstbank.rations.grilles.api.dto.MontantApplicableResponse;
import cm.afrilandfirstbank.rations.grilles.api.dto.PageResponse;
import cm.afrilandfirstbank.rations.grilles.api.dto.RejetGrilleRequest;
import cm.afrilandfirstbank.rations.grilles.api.dto.ValidationGrilleResponse;
import cm.afrilandfirstbank.rations.grilles.application.DecisionGrilleService;
import cm.afrilandfirstbank.rations.grilles.application.GrilleService;
import cm.afrilandfirstbank.rations.grilles.application.ResolutionMontantService;
import cm.afrilandfirstbank.rations.grilles.domaine.GrilleTarifaire;
import cm.afrilandfirstbank.rations.grilles.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.SessionEnum;
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
 * <p>Deux acteurs, deux jeux d'endpoints, et la separation est celle de RG-14 :
 * l'<b>Analyste RH</b> propose ({@code GET} et {@code POST /grilles}, Sprint
 * 2.2), la <b>Directrice RH</b> tranche ({@code POST /grilles/{id}/validation}
 * et {@code /rejet}, Sprint 2.3). Aucun role ne fait les deux : un ARH qui
 * validerait ses propres propositions annulerait le double regard que la regle
 * institue.
 */
@RestController
@RequestMapping("/grilles")
@Tag(name = "Grilles tarifaires",
        description = "Cycle de vie des grilles tarifaires (RG-14). Proposition par l'ARH, "
                + "decision par la DRH.")
public class GrilleController {

    private final GrilleService grilleService;
    private final DecisionGrilleService decisionGrilleService;
    private final ResolutionMontantService resolutionMontantService;

    public GrilleController(GrilleService grilleService,
            DecisionGrilleService decisionGrilleService,
            ResolutionMontantService resolutionMontantService) {
        this.grilleService = grilleService;
        this.decisionGrilleService = decisionGrilleService;
        this.resolutionMontantService = resolutionMontantService;
    }

    // --- Resolution du montant applicable (Sprint 2.4, RG-03) ---------------

    @GetMapping("/active")
    @Operation(summary = "Montant applicable a une prestation, a la date de cette prestation",
            description = """
                    Repond a la question que pose le service Saisie avant de figer une ligne :
                    **quel montant s'applique a cette nature et cette session, a cette date ?**
                    Le montant n'est jamais saisi ni transmis par le client, il est repris d'ici
                    (RG-03).

                    **A la date de la prestation, pas a la date du jour.** Une saisie du 10
                    juillet effectuee le 27 aout est tarifee au montant de juillet. Le parametre
                    `date` est donc **obligatoire** : lui donner une valeur par defaut ferait
                    produire un montant plausible mais faux a tout appelant qui l'oublierait pour
                    une saisie retroactive, sans declencher la moindre erreur.

                    **Role requis :** aucun en particulier — un jeton valide suffit. Le montant
                    retourne est un bareme de reference, sans information nominative, deja
                    lisible par `GET /grilles` pour l'ARH et la DRH. Meme parti que
                    `GET /identite/habilitation` (Sprint 1.3). Le service appelant relaie tel
                    quel l'en-tete `Authorization` de l'utilisateur final ; il ne s'authentifie
                    pas avec un compte de service, le realm n'en comporte aucun.

                    **Aucune grille ne couvre la date ?** La reponse reste **200**, avec
                    `disponible: false` et `montantFcfa: null` — jamais zero, qui serait
                    enregistre comme un tarif. C'est au service Saisie de traduire cette reponse
                    en refus de ligne (`422 GRILLE_INDISPONIBLE`, US-05 et CT-10). Voir
                    `docs/appel-resolution-montant.md`.

                    Une grille `EN_ATTENTE_DRH` ou `REJETEE` n'est jamais retenue (CT-25) : une
                    proposition non tranchee est sans effet sur les saisies, meme apres sa propre
                    date de debut.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Verdict rendu : montant applicable, ou indisponibilite explicite",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "Montant trouve", value = """
                                    {
                                      "disponible": true,
                                      "nature": "RATION",
                                      "session": "JOUR",
                                      "date": "2026-07-10",
                                      "montantFcfa": 1500,
                                      "idGrille": 12,
                                      "dateDebut": "2026-07-01",
                                      "dateFin": "2026-07-31"
                                    }"""),
                            @ExampleObject(name = "Aucune grille ne couvre la date", value = """
                                    {
                                      "disponible": false,
                                      "nature": "RATION",
                                      "session": "JOUR",
                                      "date": "2020-01-01",
                                      "montantFcfa": null,
                                      "idGrille": null,
                                      "dateDebut": null,
                                      "dateFin": null
                                    }""")})),
            @ApiResponse(responseCode = "400",
                    description = "Parametre manquant, nature ou session hors enumeration, date hors format AAAA-MM-JJ",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expire", content = @Content),
            @ApiResponse(responseCode = "500",
                    description = "Incoherence de donnees : plusieurs grilles actives couvrent la meme date. "
                            + "Aucun montant n'est retourne plutot qu'un montant arbitre.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "timestamp": "2026-08-27T14:22:00",
                              "status": 500,
                              "code": "INCOHERENCE_GRILLE",
                              "message": "Plusieurs grilles actives couvrent RATION / JOUR au 2026-07-10 (#30 [2026-07-01 -> sans terme] 2000 FCFA, #12 [2026-01-01 -> sans terme] 1500 FCFA). Le montant applicable ne peut pas etre determine sans arbitrage : aucune valeur n'est retournee plutot qu'une valeur possiblement fausse. Signalez cette incoherence a l'administrateur.",
                              "path": "/grilles/active"
                            }""")))
    })
    public ResponseEntity<MontantApplicableResponse> resoudreMontant(
            @RequestParam NatureEnum nature,
            @RequestParam SessionEnum session,
            // Obligatoire, et sans valeur par defaut : voir la description.
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return ResponseEntity.ok(MontantApplicableResponse.depuis(
                resolutionMontantService.resoudre(nature, session, date)));
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

    // --- Decision de la Directrice RH (Sprint 2.3, US-14) -------------------

    @PostMapping("/{id}/validation")
    @PreAuthorize("hasRole('DRH')")
    @Operation(summary = "Valide une grille en attente et la rend applicable",
            description = """
                    Passe la grille au statut **ACTIVE** et **ferme celle qu'elle remplace**.

                    **Role requis :** DRH. La validation est refusee a l'ARH, y compris a
                    l'auteur de la proposition : c'est le double regard voulu par RG-14.

                    **Ce qu'il advient de l'ancienne grille.** Si le couple nature et session
                    portait deja une grille en vigueur, elle est close a **la veille de la date
                    de debut de la nouvelle** — et non a la date de la decision. Les periodes
                    s'enchainent ainsi sans trou ni chevauchement, ce dont depend la resolution
                    du montant a une date passee. L'ancienne grille conserve le statut ACTIVE
                    avec une `dateFin` renseignee : la fermeture n'est pas un statut, c'est une
                    borne. La reponse la renvoie dans `ancienneFermee`, nulle s'il s'agissait de
                    la premiere grille du couple.

                    **Atomicite.** Fermeture et activation sont dans une seule transaction : les
                    deux aboutissent ou aucune. Le couple n'est jamais laisse sans grille en
                    vigueur, ni avec deux.

                    **Effet immediat** (CT-29) : des le commit, `GET /grilles/active` renvoie la
                    nouvelle grille, et les saisies suivantes appliquent son montant.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Grille validee, ancienne fermee le cas echeant",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "grille": {
                                "id": 29,
                                "nature": "TRANSPORT",
                                "session": "SOIR",
                                "montantFcfa": 3000,
                                "dateDebut": "2026-09-01",
                                "dateFin": null,
                                "statutValidation": "ACTIVE",
                                "createur": "NKOLO Claire",
                                "validateur": "TCHINDA Agnes",
                                "dateValidation": "2026-08-27T11:04:00",
                                "motifRejet": null
                              },
                              "ancienneFermee": {
                                "id": 12,
                                "nature": "TRANSPORT",
                                "session": "SOIR",
                                "montantFcfa": 2500,
                                "dateDebut": "2026-01-01",
                                "dateFin": "2026-08-31",
                                "statutValidation": "ACTIVE",
                                "createur": "NKOLO Claire",
                                "validateur": "TCHINDA Agnes",
                                "dateValidation": "2025-12-20T15:41:00",
                                "motifRejet": null
                              }
                            }"""))),
            @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expire", content = @Content),
            @ApiResponse(responseCode = "403", description = "Role autre que DRH, ou aucun profil ouvert dans le module. Trace en audit.",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Aucune grille ne porte cet identifiant", content = @Content),
            @ApiResponse(responseCode = "422", description = "Statut incompatible : seule une grille EN_ATTENTE_DRH peut etre validee",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "timestamp": "2026-08-27T11:04:00",
                              "status": 422,
                              "code": "TRANSITION_INTERDITE",
                              "message": "Transition de statut interdite : ACTIVE -> ACTIVE. Depuis ACTIVE, transitions autorisees : [].",
                              "path": "/grilles/29/validation"
                            }"""))),
            @ApiResponse(responseCode = "503", description = "Service Identite injoignable : le validateur n'a pas pu etre identifie, rien n'a ete modifie",
                    content = @Content)
    })
    public ResponseEntity<ValidationGrilleResponse> valider(
            @PathVariable Long id,
            // Meme relais qu'a la creation : le jeton de la DRH sert a resoudre
            // son identifiant local, que Keycloak ne porte pas (decision 1.3).
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation,
            HttpServletRequest requeteHttp) {

        return ResponseEntity.ok(ValidationGrilleResponse.depuis(
                decisionGrilleService.valider(id, enteteAutorisation, requeteHttp.getRemoteAddr())));
    }

    @PostMapping("/{id}/rejet")
    @PreAuthorize("hasRole('DRH')")
    @Operation(summary = "Rejette une grille en attente, avec motif",
            description = """
                    Passe la grille au statut **REJETEE** et enregistre le motif.

                    **Role requis :** DRH.

                    **La grille en vigueur n'est pas touchee.** Un rejet dit « ce tarif ne
                    s'appliquera pas », pas « il n'y a plus de tarif ». Le couple nature et
                    session conserve sa grille active et les saisies continuent au montant
                    inchange.

                    **Motif obligatoire (RG-10)**, et non vide : une chaine d'espaces est
                    refusee. Sans explication, l'ARH ne sait pas quoi corriger et reproposera
                    la meme grille.

                    Le rejet est definitif : une grille REJETEE ne revient jamais en brouillon,
                    elle est conservee pour l'historique. L'ARH repart d'une nouvelle
                    proposition.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Grille rejetee, motif enregistre"),
            @ApiResponse(responseCode = "400", description = "Motif absent ou vide", content = @Content),
            @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expire", content = @Content),
            @ApiResponse(responseCode = "403", description = "Role autre que DRH, ou aucun profil ouvert dans le module. Trace en audit.",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Aucune grille ne porte cet identifiant", content = @Content),
            @ApiResponse(responseCode = "422", description = "Motif non exploitable (MOTIF_OBLIGATOIRE) ou statut incompatible (TRANSITION_INTERDITE)",
                    content = @Content),
            @ApiResponse(responseCode = "503", description = "Service Identite injoignable : rien n'a ete modifie", content = @Content)
    })
    public ResponseEntity<GrilleResponse> rejeter(
            @PathVariable Long id,
            @Valid @RequestBody RejetGrilleRequest requete,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation,
            HttpServletRequest requeteHttp) {

        GrilleTarifaire grille = decisionGrilleService.rejeter(
                id, requete.motif(), enteteAutorisation, requeteHttp.getRemoteAddr());

        return ResponseEntity.ok(GrilleResponse.depuis(grille));
    }

}
