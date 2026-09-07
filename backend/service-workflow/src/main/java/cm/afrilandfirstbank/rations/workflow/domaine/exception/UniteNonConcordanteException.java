package cm.afrilandfirstbank.rations.workflow.domaine.exception;

/**
 * Le code unite declare dans la demande d'ouverture ne correspond pas a celui de
 * l'etat d'origine (Sprint 6bis.1). Rendue en {@code 403 UNITE_NON_CONCORDANTE},
 * et <b>tracee en audit</b> comme les autres refus d'acces (CT-04).
 *
 * <h2>Meme nom, meme code et meme raisonnement que cote Saisie</h2>
 *
 * <p>Le service Saisie oppose deja ce refus depuis le Sprint 3.4, quand le code
 * unite <i>declare</i> par un appelant ne correspond pas a celui <i>fige</i> sur les
 * fiches. La regle est la meme ici, avec l'etat d'origine pour autorite : <b>un
 * parametre fourni par l'appelant ne se croit pas sur parole</b>.
 *
 * <h2>Pourquoi 403, et pourquoi ce refus est trace</h2>
 *
 * <p>Le scenario concret est une tentative d'acces inter-unite : un agent habilite
 * sur {@code 00002} demande l'ouverture d'un complementaire en declarant
 * {@code 00002}, sur un etat d'origine qui appartient en realite a {@code 00007}. Le
 * controle de portee, qui porte sur l'unite <b>reelle</b> de l'origine, l'aurait de
 * toute facon arrete ; ce refus-ci ferme le cas inverse — une declaration qui ne
 * correspond pas au dossier vise.
 *
 * <p>Rien ne permet de distinguer au moment du refus un defaut d'un client et un
 * debordement de perimetre delibere. La doctrine du refus conservateur (Sprint 1.3)
 * tranche : refuser, <b>et tracer</b>. La trace {@code ACCES_REFUSE} est publiee par
 * {@code GestionnaireErreursApi}, seul point de convergence des refus d'acces du
 * service : si elle n'y etait pas, le refus ne serait trace nulle part (CLAUDE.md
 * sections 9.2 et 15).
 */
public class UniteNonConcordanteException extends RuntimeException {

    public UniteNonConcordanteException(String message) {
        super(message);
    }

}
