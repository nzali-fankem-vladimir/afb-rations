package cm.afrilandfirstbank.rations.workflow.domaine.exception;

/**
 * Cet utilisateur a deja agi sur la version du dossier qui est actuellement dans
 * le circuit : il ne peut pas y agir une seconde fois (RG-12).
 *
 * <p>Rendue en {@code 403 SEPARATION_TACHES}, code prevu par le contrat d'API
 * section 5, et <b>tracee en audit</b> ({@code ACCES_REFUSE}, motif
 * {@code SEPARATION_TACHES}).
 *
 * <h2>Pourquoi un code distinct de {@code ACCES_REFUSE} et de
 * {@code UTILISATEUR_NON_HABILITE}</h2>
 *
 * <p>Les trois refus sont des {@code 403}, et c'est leur seul point commun. Ils
 * demandent trois gestes differents a qui les recoit :
 *
 * <ul>
 *   <li>{@code ACCES_REFUSE} — « votre role ne permet pas cette action » : la
 *       personne s'est trompee d'ecran, ou son role n'est pas celui qu'elle
 *       croit.</li>
 *   <li>{@code UTILISATEUR_NON_HABILITE} — « vous n'avez pas de droit sur cette
 *       unite » : la personne doit demander une habilitation a
 *       l'administrateur.</li>
 *   <li>{@code SEPARATION_TACHES} — « vous avez deja agi sur ce dossier » : la
 *       personne a bien le role et bien la portee, <b>et rien ne lui manque</b>.
 *       C'est le dossier qui doit changer de mains.</li>
 * </ul>
 *
 * <p>Les confondre laisserait un chef d'unite reclamer indefiniment une
 * habilitation qu'il possede deja, sans jamais comprendre ce qui le bloque.
 */
public class SeparationTachesException extends RuntimeException {

    public SeparationTachesException(String message) {
        super(message);
    }

}
