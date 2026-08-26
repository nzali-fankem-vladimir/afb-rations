package cm.afrilandfirstbank.rations.identite.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cm.afrilandfirstbank.rations.identite.api.dto.ProfilUtilisateurDto;
import cm.afrilandfirstbank.rations.identite.application.PorteeAccesService;
import cm.afrilandfirstbank.rations.identite.application.UtilisateurCourantService;
import cm.afrilandfirstbank.rations.identite.domaine.Utilisateur;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Endpoints d'identite et d'habilitation.
 *
 * <p>Ce controleur n'authentifie personne : il lit le jeton deja valide par le
 * Resource Server. Aucune route de login n'existe ni ne doit exister sur ce
 * module (CLAUDE.md sections 10 et 15).
 */
@RestController
@RequestMapping("/identite")
@Tag(name = "Identite", description = "Profil et habilitations de l'utilisateur courant")
public class IdentiteController {

    private final UtilisateurCourantService utilisateurCourantService;
    private final PorteeAccesService porteeAccesService;

    public IdentiteController(UtilisateurCourantService utilisateurCourantService,
            PorteeAccesService porteeAccesService) {
        this.utilisateurCourantService = utilisateurCourantService;
        this.porteeAccesService = porteeAccesService;
    }

    @GetMapping("/moi")
    @Operation(summary = "Profil de l'utilisateur courant",
            description = "Retourne le profil local associe au jeton presente. "
                    + "Accessible a tout utilisateur authentifie et habilite.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil de l'utilisateur courant"),
            @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expire"),
            @ApiResponse(responseCode = "403", description = "Aucun profil ouvert dans le module")
    })
    public ResponseEntity<ProfilUtilisateurDto> profilCourant(@AuthenticationPrincipal Jwt jeton) {
        Utilisateur utilisateur = utilisateurCourantService.resoudre(jeton);
        return ResponseEntity.ok(ProfilUtilisateurDto.depuis(utilisateur, porteeAccesService.determinerPortee(utilisateur)));
    }

}
