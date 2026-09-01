package cm.afrilandfirstbank.rations.workflow.domaine.exception;

/**
 * Le document de l'etat mensuel n'a pas pu etre produit : composition en echec,
 * ou ecriture sur le stockage en echec.
 *
 * <h2>Un seul type pour les deux causes, deliberement</h2>
 *
 * <p>Du point de vue de l'agent, elles sont identiques : le document n'existe pas,
 * <b>et rien n'a ete enregistre</b>. Deux exceptions distinctes produiraient la
 * meme reponse HTTP et le meme geste attendu. La cause technique reste dans le
 * message et dans la cause chainee, la ou le diagnostic se fait.
 *
 * <h2>Pourquoi cette exception garantit qu'aucune trace ne subsiste</h2>
 *
 * <p>La generation et l'ecriture ont lieu <b>hors transaction et avant elle</b>
 * (decision Sprint 4.2). Quand elle est levee, la transaction de soumission ne
 * s'est pas ouverte : ni {@code etape_workflow}, ni {@code piece_jointe}, ni
 * {@code montant_total}, ni changement de statut. C'est le sens de l'ordre retenu
 * — mieux vaut ne rien enregistrer qu'enregistrer une signature absente du
 * document.
 *
 * <p>Traduite en {@code 500 DOCUMENT_NON_PRODUIT} : ce n'est pas une maladresse de
 * l'agent ni une regle de gestion, c'est une defaillance du serveur, et le message
 * doit le dire plutot que de laisser croire a une erreur de saisie.
 */
public class DocumentNonProduitException extends RuntimeException {

    public DocumentNonProduitException(String message) {
        super(message);
    }

    public DocumentNonProduitException(String message, Throwable cause) {
        super(message, cause);
    }

}
