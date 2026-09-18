package cm.afrilandfirstbank.rations.workflow.domaine.exception;

/**
 * Aucun parametre systeme ne porte ce code (guide 7F.6, etape 6, ajout backend
 * scope). 404, sans trace de refus : ce n'est pas une tentative hors perimetre,
 * c'est une adresse qui ne correspond a aucune ligne de {@code parametre_systeme}.
 */
public class ParametreIntrouvableException extends RuntimeException {

    public ParametreIntrouvableException(String code) {
        super("Aucun parametre systeme ne porte le code " + code + ".");
    }

}
