package cm.afrilandfirstbank.rations.saisie.domaine.exception;

/**
 * La ligne de prestation designee n'existe pas.
 *
 * <p>Traduite en {@code 404 LIGNE_INTROUVABLE} par le gestionnaire d'erreurs de
 * l'API, sur le modele de {@code FICHE_INTROUVABLE} (Sprint 3.2) et de
 * {@code GRILLE_INTROUVABLE} (Sprint 2.3).
 *
 * <p>Sert la modification et la suppression. Un {@code DELETE} sur une ligne
 * deja supprimee rend donc {@code 404} et non {@code 204} : la suppression
 * n'est pas rendue idempotente ici, parce qu'un agent qui supprime deux fois la
 * meme ligne se trompe probablement de ligne, et un {@code 204} le lui
 * cacherait.
 */
public class LigneIntrouvableException extends RuntimeException {

    public LigneIntrouvableException(String message) {
        super(message);
    }

}
