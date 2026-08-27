package cm.afrilandfirstbank.rations.grilles.infrastructure.identite;

/**
 * Le service Identite n'a pas repondu : timeout, connexion refusee, 5xx, ou
 * reponse illisible.
 *
 * <p><b>Consequence : l'action metier est refusee</b> — refus conservateur,
 * doctrine du Sprint 1.3 ({@code docs/appel-habilitation.md} section 3). Aucun
 * repli, aucun cache d'une reponse anterieure, aucune valeur par defaut pour
 * l'auteur : une grille dont on ne sait pas qui l'a creee n'a pas sa place dans
 * une piece de controle interne.
 *
 * <p>Traduite en <b>503</b>, non en 403 (decision Sprint 2.2). L'ARH possede le
 * droit qu'il exerce ; lui repondre « acces refuse » l'enverrait reclamer a
 * l'administrateur une habilitation qu'il a deja, pendant que la vraie panne
 * resterait invisible. Le refus est le meme, le diagnostic rendu est juste.
 */
public class IdentiteIndisponibleException extends RuntimeException {

    public IdentiteIndisponibleException(String message, Throwable cause) {
        super(message, cause);
    }

    public IdentiteIndisponibleException(String message) {
        super(message);
    }

}
