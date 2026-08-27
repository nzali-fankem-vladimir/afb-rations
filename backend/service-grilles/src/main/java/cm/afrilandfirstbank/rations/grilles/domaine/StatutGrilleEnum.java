package cm.afrilandfirstbank.rations.grilles.domaine;

/**
 * Statut du cycle de vie d'une grille tarifaire (CLAUDE.md section 5, RG-14).
 *
 * <p>Cycle a quatre etats, diagramme ET02 du document de conception :
 *
 * <pre>
 *   (creation) --> BROUILLON --> EN_ATTENTE_DRH --> ACTIVE
 *                                              \--> REJETEE
 * </pre>
 *
 * <p>La contrainte {@code CHECK} de {@code grille_tarifaire} porte ces quatre
 * valeurs. Les transitions autorisees et interdites sont portees par
 * {@link TransitionGrille} : une grille REJETEE ne revient jamais en BROUILLON,
 * elle est conservee pour l'historique.
 */
public enum StatutGrilleEnum {

    /** Grille en cours de preparation par l'Analyste RH, ajustable librement. */
    BROUILLON,

    /** Grille soumise, en attente de la decision de la DRH. */
    EN_ATTENTE_DRH,

    /** Grille validee par la DRH. Une seule ACTIVE par couple (nature, session). */
    ACTIVE,

    /** Grille refusee par la DRH, avec motif. Etat terminal, conserve pour l'historique. */
    REJETEE

}
