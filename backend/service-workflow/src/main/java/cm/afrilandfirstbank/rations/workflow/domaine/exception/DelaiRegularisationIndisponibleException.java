package cm.afrilandfirstbank.rations.workflow.domaine.exception;

/**
 * Le delai de regularisation ({@code DELAI_REGULARISATION_JOURS}) n'a pas pu etre
 * lu : parametre absent, desactive, ou valeur qui n'est pas un entier positif
 * (Sprint 6bis.1). Rendue en {@code 500 DELAI_REGULARISATION_INDISPONIBLE}.
 *
 * <h2>Jumelle de {@link SeuilIndisponibleException}, et pour les memes raisons</h2>
 *
 * <p>Ce parametre est stocke en texte, comme le seuil d'aiguillage, et il est aussi
 * susceptible d'etre malforme — negatif, non numerique, avec un separateur de
 * milliers. Aucune valeur de repli n'est appliquee : deviner ouvrirait ou fermerait
 * la regularisation au hasard, sur une periode close ou un paiement a deja eu lieu.
 *
 * <p><b>{@code 500} et non {@code 422}</b> : l'agent n'a commis aucune erreur et n'a
 * rien a corriger dans sa demande, c'est la configuration du module qui est en
 * defaut. Le presenter comme un refus metier l'enverrait chercher une faute dans un
 * dossier qui n'en a pas — meme parti qu'au Sprint 4.3 pour le seuil, et qu'au
 * Sprint 2.4 pour {@code INCOHERENCE_GRILLE}.
 *
 * <p>A ne pas confondre avec {@link DelaiRegularisationDepasseException}, qui dit
 * l'inverse : la configuration est saine, et c'est l'etat d'origine qui est trop
 * ancien.
 */
public class DelaiRegularisationIndisponibleException extends RuntimeException {

    public DelaiRegularisationIndisponibleException(String message) {
        super(message);
    }

    public DelaiRegularisationIndisponibleException(String message, Throwable cause) {
        super(message, cause);
    }

}
