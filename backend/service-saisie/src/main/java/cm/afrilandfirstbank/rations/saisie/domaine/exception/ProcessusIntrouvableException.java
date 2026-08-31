package cm.afrilandfirstbank.rations.saisie.domaine.exception;

/**
 * Le processus mensuel designe n'existe pas : le service Workflow a repondu
 * {@code 404}.
 *
 * <p>Traduite en {@code 404 PROCESSUS_INTROUVABLE} par le gestionnaire d'erreurs
 * de l'API.
 *
 * <p><b>C'est ce refus qui remplace la cle etrangere absente.</b>
 * {@code fiche_journaliere.id_processus} designe une ligne de la base
 * {@code rations_workflow} : aucune contrainte referentielle n'est possible
 * entre deux bases. La fiche n'est donc creee qu'apres un {@code 200} de
 * {@code GET /processus/{id}} — une <i>reference verifiee a l'ecriture</i>, a
 * defaut d'une reference garantie par la base
 * ({@code docs/rattachement-processus.md} section 3).
 *
 * <p>Distinct d'une panne : ici le service Workflow <b>a repondu</b>, et sa
 * reponse est que ce processus n'existe pas. Confondre les deux ferait rendre
 * {@code 503} a une simple faute de frappe, et {@code 404} a un serveur a terre.
 */
public class ProcessusIntrouvableException extends RuntimeException {

    public ProcessusIntrouvableException(String message) {
        super(message);
    }

}
