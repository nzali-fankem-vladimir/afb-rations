package cm.afrilandfirstbank.rations.workflow.domaine;

/**
 * Etat d'une etape du circuit de validation (CLAUDE.md section 5).
 *
 * <ul>
 *   <li>{@link #EN_ATTENTE} — l'acteur n'a pas encore agi.</li>
 *   <li>{@link #VALIDEE} — l'acteur a valide, signature apposee (RG-09).</li>
 *   <li>{@link #RETOURNEE} — l'acteur a retourne l'etat a l'agent, motif
 *       obligatoire (RG-10, RG-11).</li>
 * </ul>
 */
public enum StatutEtapeEnum {

    EN_ATTENTE,
    VALIDEE,
    RETOURNEE

}
