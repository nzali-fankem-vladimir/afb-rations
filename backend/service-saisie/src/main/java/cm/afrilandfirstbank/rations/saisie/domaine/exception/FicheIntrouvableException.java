package cm.afrilandfirstbank.rations.saisie.domaine.exception;

/**
 * La fiche journaliere designee n'existe pas.
 *
 * <p>Traduite en {@code 404 FICHE_INTROUVABLE} par le gestionnaire d'erreurs de
 * l'API (Sprint 3.3), sur le modele de {@code GRILLE_INTROUVABLE} (Sprint 2.3).
 *
 * <p>La fiche n'est pas un detail de rattachement : elle <b>porte la journee</b>
 * de la prestation. Sans elle, ni RG-04 (unicite sur la journee) ni RG-03 (le
 * tarif en vigueur ce jour-la) ne peuvent etre evalues. Prendre la date du jour
 * comme repli serait la faute que {@code docs/appel-resolution-montant.md}
 * section 3 interdit explicitement : elle passerait inapercue tant que toutes
 * les saisies portent sur la journee courante, et produirait un montant faux des
 * la premiere saisie retroactive.
 */
public class FicheIntrouvableException extends RuntimeException {

    public FicheIntrouvableException(String message) {
        super(message);
    }

}
