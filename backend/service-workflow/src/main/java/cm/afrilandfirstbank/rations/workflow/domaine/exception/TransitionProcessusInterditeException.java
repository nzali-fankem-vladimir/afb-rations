package cm.afrilandfirstbank.rations.workflow.domaine.exception;

/**
 * Une transition de statut non prevue par le diagramme ET01 a ete tentee sur un
 * processus mensuel (Sprint 4.1). Erreur explicite, jamais silencieuse : une
 * transition refusee ne doit pas laisser le processus dans un etat incoherent ni
 * retourner un booleen que l'appelant pourrait ignorer.
 *
 * <p>Exemples de refus :
 * <ul>
 *   <li>{@code EN_COURS_SAISIE -> EN_ATTENTE_DA} — saut de la soumission,
 *       contraire a RG-07 (validation sequentielle, aucun saut de niveau) ;</li>
 *   <li>{@code SOUMIS -> CLOTURE} — saut des validations DA et DR ;</li>
 *   <li>{@code RETOURNE -> EN_ATTENTE_DA} — saut de la resoumission par l'agent ;</li>
 *   <li>{@code CLOTURE -> *} — la cloture est definitive.</li>
 * </ul>
 *
 * <p><b>Le cas de la cloture merite d'etre nomme a part.</b> Autoriser une sortie
 * de {@code CLOTURE}, meme par commodite de correction, ouvrirait la porte a une
 * seconde transmission comptable, donc a un double paiement (RG-13). C'est
 * pourquoi le message d'erreur de ce cas precis dit non seulement que la
 * transition est interdite, mais pourquoi elle l'est : une correction sur une
 * periode close se fait par un etat COMPLEMENTAIRE, jamais par reouverture
 * (CLAUDE.md sections 7 et 15).
 */
public class TransitionProcessusInterditeException extends RuntimeException {

    public TransitionProcessusInterditeException(String message) {
        super(message);
    }

}
