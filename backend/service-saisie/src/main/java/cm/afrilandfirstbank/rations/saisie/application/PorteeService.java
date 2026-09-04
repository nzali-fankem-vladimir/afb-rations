package cm.afrilandfirstbank.rations.saisie.application;

import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.saisie.application.ResultatPortee.PorteeObtenue;
import cm.afrilandfirstbank.rations.saisie.application.ResultatPortee.ProfilAbsent;
import cm.afrilandfirstbank.rations.saisie.application.ResultatPortee.ServiceIdentiteIndisponible;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.AgentNonHabiliteException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.ServiceIdentiteIndisponibleException;

/**
 * Porte unique de la resolution de portee <b>en ensemble d'unites</b>
 * (Sprint 6.1).
 *
 * <p>Pendant de {@code HabilitationService} du service Workflow pour la recherche : celui-ci demande
 * « cette personne peut-elle agir sur l'unite X », celui-la « quelles unites
 * peut-elle voir ». Les deux passent par le service Identite et refusent de la
 * meme facon — seul un verdict explicite laisse passer.
 *
 * <p><b>La portee n'est jamais un parametre de requete.</b> Elle est resolue ici,
 * depuis le jeton, et par ce service seul. C'est ce qui rend l'endpoint interne de
 * recherche inforgeable : un appel direct sur le port 8082 ne peut pas s'attribuer
 * des unites, puisqu'il n'existe aucun champ ou les declarer. C'est la doctrine du
 * Sprint 3.4 — « un parametre fourni par l'appelant ne se croit pas sur parole » —
 * poussee un cran plus loin : le parametre n'existe pas.
 */
@Service
public class PorteeService {

    private final PorteeClient porteeClient;

    public PorteeService(PorteeClient porteeClient) {
        this.porteeClient = porteeClient;
    }

    /**
     * Rend les unites visibles par l'utilisateur du jeton.
     *
     * <p><b>Jamais mis en cache</b> : une affectation peut changer entre deux
     * recherches.
     *
     * @throws AgentNonHabiliteException aucun profil local ouvert ({@code 403})
     * @throws ServiceIdentiteIndisponibleException Identite muet ({@code 503})
     */
    public PorteeAccesUtilisateur exigerPortee(String enteteAutorisation) {
        ResultatPortee resultat = porteeClient.obtenir(enteteAutorisation);

        return switch (resultat) {
            case PorteeObtenue obtenue -> obtenue.portee();

            case ProfilAbsent refus -> throw new AgentNonHabiliteException(
                    "Aucun profil n'est ouvert pour votre compte dans ce module : " + refus.motif()
                            + ". Rapprochez-vous de l'administrateur du module.");

            case ServiceIdentiteIndisponible panne -> throw new ServiceIdentiteIndisponibleException(
                    "Le service Identite est momentanement indisponible ; la recherche est refusee "
                            + "par precaution (" + panne.motifTechnique() + "). Reessayez dans un instant.");
        };
    }

}
