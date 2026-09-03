package cm.afrilandfirstbank.rations.workflow.domaine.exception;

/**
 * L'etat n'est pas cloture : rien ne peut etre reserve ni transmis a la comptabilite
 * (RG-13, Sprint 5.3).
 *
 * <p>Rendue en {@code 422 ETAT_NON_CLOTURE}, et non en {@code 409} : rien n'est duplique,
 * c'est une regle de gestion qui refuse — la distinction du Sprint 2.3 pour
 * {@code TRANSITION_INTERDITE}. Le meme code est deja rendu par le service Transmission
 * pour le meme refus vu de l'autre cote ; deux codes differents pour un seul fait
 * enverraient chercher deux causes.
 */
public class EtatNonClotureException extends RuntimeException {

    public EtatNonClotureException(String message) {
        super(message);
    }

}
