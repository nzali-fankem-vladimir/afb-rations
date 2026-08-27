package cm.afrilandfirstbank.rations.identite.application;

import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.identite.domaine.PorteeAcces;
import cm.afrilandfirstbank.rations.identite.domaine.ResultatHabilitation;
import cm.afrilandfirstbank.rations.identite.domaine.Utilisateur;

/**
 * Repond a la question d'habilitation posee par un autre service : cet
 * utilisateur a-t-il le droit d'agir sur un dossier rattache a ce code unite ?
 * (convention d'appel : {@code docs/appel-habilitation.md}).
 *
 * <p>Facade mince : toute la logique de portee vit dans
 * {@link PorteeAccesService} (Sprint 1.1) et n'est pas dupliquee ici. Ce service
 * se contente d'appliquer la portee a un code unite precis et d'emballer le
 * resultat.
 *
 * <p>La resolution de l'utilisateur a partir du jeton reste au controleur, via
 * {@link UtilisateurCourantService}, comme pour {@code GET /identite/moi} : ce
 * service travaille sur un profil deja resolu.
 */
@Service
public class HabilitationService {

    private final PorteeAccesService porteeAccesService;

    public HabilitationService(PorteeAccesService porteeAccesService) {
        this.porteeAccesService = porteeAccesService;
    }

    /**
     * @param utilisateur profil local deja resolu depuis le jeton
     * @param codeUnite code unite du dossier vise, au referentiel des codes guichets
     * @return le verdict d'habilitation pour ce couple utilisateur / code unite
     */
    public ResultatHabilitation verifier(Utilisateur utilisateur, String codeUnite) {
        PorteeAcces portee = porteeAccesService.determinerPortee(utilisateur);
        return new ResultatHabilitation(
                utilisateur.getLogin(),
                utilisateur.getRole(),
                codeUnite,
                portee.couvre(codeUnite),
                portee.estNationale());
    }

}
