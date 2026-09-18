package cm.afrilandfirstbank.rations.grilles.domaine.exception;

/**
 * L'appelant est un Analyste RH valide, mais n'est pas l'auteur de la grille
 * qu'il tente de retirer (rattrapage post-7F.6, demande n°7 de la vérification visuelle
 * du Sprint 7F.6). Rendue en {@code 403 GRILLE_NON_PROPRIETAIRE}.
 *
 * <p>Distincte de {@code UTILISATEUR_NON_HABILITE} (aucun profil local) et de
 * {@code ACCES_REFUSE} (rôle insuffisant) : ici le rôle est le bon et le
 * profil existe, mais la ressource précise appartient à quelqu'un d'autre.
 * Un troisième code pour un troisième geste, même doctrine que
 * {@code SEPARATION_TACHES} côté service Workflow (RG-12, CLAUDE.md §15) :
 * confondre les trois laisserait un Analyste RH réclamer indéfiniment une
 * habilitation qu'il possède déjà.
 *
 * <p>Trace en audit comme un refus d'accès (CT-04).
 */
public class GrilleNonProprietaireException extends RuntimeException {

    public GrilleNonProprietaireException(Long idGrille) {
        super("La grille " + idGrille + " n'a pas été proposée par cet Analyste RH : "
                + "seul son auteur peut la retirer.");
    }

}
