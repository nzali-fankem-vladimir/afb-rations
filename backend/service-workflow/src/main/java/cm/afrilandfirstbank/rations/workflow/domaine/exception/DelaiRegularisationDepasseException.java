package cm.afrilandfirstbank.rations.workflow.domaine.exception;

/**
 * L'etat d'origine est clos depuis plus longtemps que le delai de regularisation
 * (Sprint 6bis.1). Rendue en {@code 422 DELAI_REGULARISATION_DEPASSE}.
 *
 * <p><b>{@code 422} et non {@code 409}</b> : rien n'est duplique, c'est une regle de
 * gestion qui refuse — la distinction du Sprint 2.3 pour {@code TRANSITION_INTERDITE}.
 *
 * <p>Le delai lui-meme vient de {@code DELAI_REGULARISATION_JOURS}, dont la valeur de
 * 90 jours est <b>explicitement provisoire</b> tant que le metier n'a pas tranche
 * (point M-02 de {@code docs/points-en-attente.md}). Le message nomme donc le delai
 * applique et la date de cloture de l'origine : sans les deux, l'agent ne peut ni
 * comprendre le refus, ni le contester aupres de la DRH.
 */
public class DelaiRegularisationDepasseException extends RuntimeException {

    public DelaiRegularisationDepasseException(String message) {
        super(message);
    }

}
