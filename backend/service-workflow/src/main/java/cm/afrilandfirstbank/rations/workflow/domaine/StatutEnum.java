package cm.afrilandfirstbank.rations.workflow.domaine;

/**
 * Statut d'avancement d'un processus mensuel (CLAUDE.md section 5).
 *
 * <p>Ces six valeurs sont les sommets du diagramme ET01 (CLAUDE.md section 7). Les
 * transitions autorisees entre elles sont portees par {@link TransitionProcessus},
 * pas par cette enumeration : ici on ne trouve que la liste, la machine a etats
 * dit ce qui est permis.
 *
 * <ul>
 *   <li>{@link #EN_COURS_SAISIE} — l'agent saisit les fiches journalieres.</li>
 *   <li>{@link #SOUMIS} — l'etat consolide est fige, signature agent en cours.</li>
 *   <li>{@link #EN_ATTENTE_DA} — transfere au Chef d'Unite pour validation.</li>
 *   <li>{@link #EN_ATTENTE_DR} — aiguille au Directeur Reseau (montant au-dela du
 *       seuil RG-08).</li>
 *   <li>{@link #RETOURNE} — renvoye a l'agent avec motif (RG-10, RG-11).</li>
 *   <li>{@link #CLOTURE} — <b>terminal</b> : aucune transition n'en repart. C'est
 *       ce qui garantit l'unicite de la transmission comptable (RG-13).</li>
 * </ul>
 */
public enum StatutEnum {

    EN_COURS_SAISIE,
    SOUMIS,
    EN_ATTENTE_DA,
    EN_ATTENTE_DR,
    RETOURNE,
    CLOTURE

}
