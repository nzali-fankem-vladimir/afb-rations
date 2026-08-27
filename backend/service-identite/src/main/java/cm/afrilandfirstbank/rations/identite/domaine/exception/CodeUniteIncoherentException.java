package cm.afrilandfirstbank.rations.identite.domaine.exception;

/**
 * Le code unite fourni est incoherent avec le role attribue (sous-sprint 1.2) :
 * un role a portee locale (AGENT_UNITE, CHEF_UNITE_DA) sans code unite. Se
 * traduit par un 400 : c'est une erreur de coherence de la requete, pas une
 * regle de gestion arbitrale (document maitre, section 7.1 ; sprint 1.2, section
 * 5, ne retient que 400/401/403/404/409 pour ce sous-sprint).
 */
public class CodeUniteIncoherentException extends RuntimeException {

    public CodeUniteIncoherentException(String message) {
        super(message);
    }

}
