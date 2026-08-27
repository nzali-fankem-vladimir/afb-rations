package cm.afrilandfirstbank.rations.grilles.domaine.exception;

/**
 * Une transition de statut non prevue par le diagramme ET02 a ete tentee sur une
 * grille (Sprint 2.1). Erreur explicite, jamais silencieuse : une transition
 * refusee ne doit pas laisser la grille dans un etat incoherent ni retourner un
 * booleen ignore par l'appelant.
 *
 * <p>Exemples : BROUILLON -> ACTIVE (saut de la validation DRH),
 * REJETEE -> BROUILLON (une grille rejetee est conservee pour l'historique),
 * ACTIVE -> EN_ATTENTE_DRH.
 */
public class TransitionGrilleInterditeException extends RuntimeException {

    public TransitionGrilleInterditeException(String message) {
        super(message);
    }

}
