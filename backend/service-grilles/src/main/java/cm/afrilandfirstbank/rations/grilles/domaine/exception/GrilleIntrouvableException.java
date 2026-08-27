package cm.afrilandfirstbank.rations.grilles.domaine.exception;

/**
 * Aucune grille ne porte l'identifiant demande (Sprint 2.3).
 *
 * <p>Distincte d'une transition interdite : ici il n'y a rien a trancher, la
 * ligne n'existe pas. La DRH a suivi un lien perime, ou l'identifiant a ete
 * saisi a la main. Traduite en 404 par {@code GestionnaireErreursApi}.
 */
public class GrilleIntrouvableException extends RuntimeException {

    public GrilleIntrouvableException(String message) {
        super(message);
    }

}
