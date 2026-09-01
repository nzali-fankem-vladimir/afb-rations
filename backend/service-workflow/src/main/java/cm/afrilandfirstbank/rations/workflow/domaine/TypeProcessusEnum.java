package cm.afrilandfirstbank.rations.workflow.domaine;

/**
 * Type d'un processus mensuel (CLAUDE.md section 5).
 *
 * <ul>
 *   <li>{@link #NORMAL} — le cycle mensuel courant d'une unite. Un seul par
 *       couple (code_unite, mois, annee), garanti par l'index partiel
 *       {@code ux_processus_normal_par_periode} (migration V1).</li>
 *   <li>{@link #COMPLEMENTAIRE} — regularisation d'un oubli sur une periode close,
 *       referencant l'etat d'origine jamais rouvert. <b>Non ouvert au Sprint 4</b> :
 *       la fonctionnalite releve du Sprint 6bis, sous reserve de confirmation
 *       metier ({@code docs/dispositifs_provisoires.md}). Le drapeau
 *       {@code RATTRAPAGE_ACTIF} de {@code parametre_systeme} la ferme tant que
 *       le metier n'a pas tranche.</li>
 * </ul>
 */
public enum TypeProcessusEnum {

    NORMAL,
    COMPLEMENTAIRE

}
