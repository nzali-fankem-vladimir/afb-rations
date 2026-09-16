package cm.afrilandfirstbank.rations.saisie.domaine.exception;

/**
 * <b>RG-15</b> : la prestation figure deja dans un <b>autre etat</b> de la meme
 * unite et de la meme periode. La ligne est refusee.
 *
 * <p>Traduite en {@code 409 DOUBLON_INTER_ETATS} par le gestionnaire d'erreurs
 * de l'API. {@code 409} comme {@link DoublonLigneException} : c'est bien une
 * duplication, pas une regle de gestion violee au sens large (distinction posee
 * au Sprint 2.3).
 *
 * <h2>Pourquoi un code distinct de {@code DOUBLON_LIGNE}</h2>
 *
 * <p>Parce que les deux refus appellent <b>deux gestes differents</b>, et que
 * c'est la ligne de conduite du module depuis les trois codes en {@code 403} du
 * Sprint 4.4.
 *
 * <ul>
 *   <li>{@code DOUBLON_LIGNE} : « vous l'avez deja saisie <i>ici</i> ». L'agent
 *       regarde la fiche qu'il a sous les yeux, et la ligne y est.</li>
 *   <li>{@code DOUBLON_INTER_ETATS} : « elle a deja ete servie <b>ailleurs</b> ».
 *       La ligne fautive est dans un dossier que l'agent ne consulte pas — le
 *       plus souvent l'etat d'origine, deja cloture et paye. Sous le code de
 *       RG-04, il chercherait dans sa propre fiche une ligne qui ne s'y trouve
 *       pas, et conclurait a un defaut du module.</li>
 * </ul>
 *
 * <p>Le message nomme donc <b>l'etat en conflit</b> en plus des quatre elements
 * de la combinaison : c'est la seule information qui permette a l'agent de
 * verifier par lui-meme, et au controle interne de refaire le rapprochement.
 */
public class DoublonInterEtatsException extends RuntimeException {

    public DoublonInterEtatsException(String message) {
        super(message);
    }

}
