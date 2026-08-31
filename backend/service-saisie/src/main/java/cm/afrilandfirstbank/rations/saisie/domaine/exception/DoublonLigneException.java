package cm.afrilandfirstbank.rations.saisie.domaine.exception;

/**
 * <b>RG-04</b> : le beneficiaire figure deja sur cette journee pour cette nature
 * et cette session. La ligne est refusee.
 *
 * <p>Traduite en {@code 409 DOUBLON_LIGNE} par le gestionnaire d'erreurs de
 * l'API (Sprint 3.3) — code et statut prevus par le contrat d'API, section 3.
 * {@code 409} et non {@code 422} : c'est bien une duplication, pas une regle de
 * gestion violee au sens large.
 *
 * <p><b>Le message nomme les quatre elements du conflit</b> — beneficiaire,
 * journee, nature, session. Un simple « doublon » laisserait l'agent chercher
 * laquelle de ses lignes est en cause, et surtout lui cacherait que la nature ou
 * la session distinguent deux lignes legitimes.
 */
public class DoublonLigneException extends RuntimeException {

    public DoublonLigneException(String message) {
        super(message);
    }

}
