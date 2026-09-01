package cm.afrilandfirstbank.rations.workflow.domaine.exception;

/**
 * Le service Identite a repondu, et sa reponse est que cet utilisateur n'a pas de
 * portee sur l'unite concernee — ou qu'aucun profil local n'est ouvert pour son
 * compte (RG-12).
 *
 * <p>Rendue en {@code 403 UTILISATEUR_NON_HABILITE} et <b>tracee en audit</b>
 * ({@code ACCES_REFUSE}, motif {@code HABILITATION_ABSENTE}) : le service
 * Identite ne trace pas ses propres verdicts negatifs, c'est au consommateur de
 * le faire (CLAUDE.md section 9.2).
 *
 * <p>Distincte de {@link ServiceIdentiteIndisponibleException} : ici Identite a
 * repondu. L'utilisateur doit demander une habilitation, pas reessayer plus tard.
 */
public class AgentNonHabiliteException extends RuntimeException {

    public AgentNonHabiliteException(String message) {
        super(message);
    }

}
