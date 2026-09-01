package cm.afrilandfirstbank.rations.workflow.domaine.exception;

/**
 * Le seuil d'aiguillage (RG-08) n'a pas pu etre lu : parametre absent, desactive,
 * ou portant une valeur qui n'est pas un nombre entier positif.
 *
 * <h2>Pourquoi cette exception existe plutot qu'une valeur de repli</h2>
 *
 * <p>Une valeur par defaut dans le code reintroduirait exactement ce que RG-08
 * interdit : un seuil qui ne vient pas de {@code parametre_systeme} (CLAUDE.md
 * section 15). Un aiguillage sur une valeur inventee serait de surcroit
 * <b>invisible</b> — le circuit continuerait de tourner, en appliquant un niveau
 * d'approbation que personne n'a decide.
 *
 * <p>Le module refuse donc, et le dit. C'est le meme parti qu'au Sprint 2.4 pour
 * {@code INCOHERENCE_GRILLE} : devant une configuration qu'il ne sait pas
 * interpreter, le service <b>signale, il n'arbitre jamais</b>.
 *
 * <h2>Un point de defaillance unique, assume et surveille</h2>
 *
 * <p>Consequence directe : un parametre supprime ou mal saisi bloque
 * <b>l'ensemble</b> du circuit de validation. Le compromis a ete tranche
 * explicitement au Sprint 4.3 — une erreur de configuration visible tout de suite
 * vaut mieux que des aiguillages faux pendant des semaines — et inscrit comme
 * point de surveillance prioritaire en production dans
 * {@code docs/points-en-attente.md}.
 */
public class SeuilIndisponibleException extends RuntimeException {

    public SeuilIndisponibleException(String message) {
        super(message);
    }

    public SeuilIndisponibleException(String message, Throwable cause) {
        super(message, cause);
    }

}
