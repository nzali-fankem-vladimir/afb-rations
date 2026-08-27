package cm.afrilandfirstbank.rations.identite.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cm.afrilandfirstbank.rations.identite.api.dto.HabilitationResponse;
import cm.afrilandfirstbank.rations.identite.application.HabilitationService;
import cm.afrilandfirstbank.rations.identite.application.UtilisateurCourantService;
import cm.afrilandfirstbank.rations.identite.domaine.ResultatHabilitation;
import cm.afrilandfirstbank.rations.identite.domaine.Utilisateur;
import cm.afrilandfirstbank.rations.identite.domaine.exception.CodeUniteIncoherentException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Endpoint <b>interne</b> de verification d'habilitation, consomme par les
 * autres services (Sprint 1.3, convention {@code docs/appel-habilitation.md}).
 *
 * <p>Il ne fait pas partie des trois endpoints du contrat passerelle du service
 * Identite (CLAUDE.md section 11) : c'est un service rendu aux autres services,
 * pas une ressource metier exposee au frontend.
 *
 * <p>Protection : route OAuth2 ordinaire, ouverte a tout utilisateur
 * authentifie et habilite. Le service appelant relaie le jeton de l'utilisateur
 * final tel quel (choix B1) ; l'utilisateur est resolu depuis ce jeton comme
 * pour {@code GET /identite/moi}. Aucun compte de service : le realm n'en
 * comporte pas.
 */
@RestController
@RequestMapping("/identite/habilitation")
@Tag(name = "Habilitation inter-services",
        description = "Endpoint interne : un service demande si un utilisateur peut agir sur un code unite")
public class HabilitationController {

    private final HabilitationService habilitationService;
    private final UtilisateurCourantService utilisateurCourantService;

    public HabilitationController(HabilitationService habilitationService,
            UtilisateurCourantService utilisateurCourantService) {
        this.habilitationService = habilitationService;
        this.utilisateurCourantService = utilisateurCourantService;
    }

    @GetMapping
    @Operation(summary = "Cet utilisateur peut-il agir sur un dossier de ce code unite ?",
            description = """
                    Endpoint **interne**, destine aux autres services. Resout l'utilisateur depuis
                    le jeton presente, puis applique sa portee d'acces (Sprint 1.1) au code unite
                    demande.

                    **Role requis :** aucun en particulier. Le verdict depend du role, il n'en
                    exige pas un : AGENT_UNITE et CHEF_UNITE_DA sont limites a leur unite, les
                    autres roles ont une portee nationale.

                    **Propagation du jeton.** Le service appelant relaie tel quel l'en-tete
                    `Authorization` de l'utilisateur final. Il ne s'authentifie pas avec un
                    compte de service : le realm n'en comporte aucun.

                    **Deux obligations pour le consommateur** (`docs/appel-habilitation.md`) :
                    refuser l'action des que la reponse n'est pas un 200 avec `autorise: true`,
                    sans jamais mettre en cache un verdict positif ; et publier un evenement
                    d'audit `ACCES_REFUSE` sur verdict negatif comme sur indisponibilite. Ce
                    service ne trace pas ces refus : il repond a une question, il ne refuse pas
                    l'action.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verdict rendu",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "Autorise, portee locale", value = """
                                    {
                                      "login": "jean_mbarga",
                                      "role": "AGENT_UNITE",
                                      "codeUniteDemande": "00002",
                                      "autorise": true,
                                      "porteeNationale": false
                                    }"""),
                            @ExampleObject(name = "Refuse, unite hors portee", value = """
                                    {
                                      "login": "jean_mbarga",
                                      "role": "AGENT_UNITE",
                                      "codeUniteDemande": "00003",
                                      "autorise": false,
                                      "porteeNationale": false
                                    }"""),
                            @ExampleObject(name = "Autorise, portee nationale", value = """
                                    {
                                      "login": "agnes_tchinda",
                                      "role": "DRH",
                                      "codeUniteDemande": "00003",
                                      "autorise": true,
                                      "porteeNationale": true
                                    }""") })),
            @ApiResponse(responseCode = "400", description = "Parametre codeUnite absent ou mal forme",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "timestamp": "2026-08-27T09:12:00",
                              "status": 400,
                              "code": "CODE_UNITE_INCOHERENT",
                              "message": "Le parametre codeUnite est obligatoire et doit comporter exactement cinq chiffres.",
                              "path": "/identite/habilitation"
                            }"""))),
            @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expire",
                    content = @Content),
            @ApiResponse(responseCode = "403",
                    description = "Aucun profil local ouvert pour ce compte, ou profil desactive. Trace en audit.",
                    content = @Content)
    })
    public ResponseEntity<HabilitationResponse> verifier(
            @RequestParam(required = false) String codeUnite,
            @AuthenticationPrincipal Jwt jeton) {

        if (codeUnite == null || !codeUnite.matches("\\d{5}")) {
            throw new CodeUniteIncoherentException(
                    "Le parametre codeUnite est obligatoire et doit comporter exactement cinq chiffres.");
        }

        Utilisateur utilisateur = utilisateurCourantService.resoudre(jeton);
        ResultatHabilitation resultat = habilitationService.verifier(utilisateur, codeUnite);
        return ResponseEntity.ok(HabilitationResponse.depuis(resultat));
    }

}
