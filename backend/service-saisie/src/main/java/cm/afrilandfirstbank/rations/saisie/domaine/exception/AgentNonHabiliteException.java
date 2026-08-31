package cm.afrilandfirstbank.rations.saisie.domaine.exception;

/**
 * L'agent n'a pas le droit d'agir sur un dossier de ce code unite : le service
 * Identite a repondu, et sa reponse est negative — {@code autorise = false}, ou
 * aucun profil local ouvert pour ce compte.
 *
 * <p>Traduite en {@code 403 UTILISATEUR_NON_HABILITE}, meme code qu'au
 * Sprint 2.2 cote Grilles.
 *
 * <p><b>C'est RG-12 sur le chemin de la saisie.</b> Un agent qui pourrait saisir
 * sur l'unite d'un autre fausserait toute la chaine comptable : le code unite
 * porte la ligne de debit, donc l'unite qui supporte la charge. La portee varie
 * par role — {@code AGENT_UNITE} et {@code CHEF_UNITE_DA} sont limites a leur
 * propre code unite, les autres roles ont une portee nationale (Sprint 1.1).
 *
 * <p><b>Le refus doit etre publie en audit par CE service.</b> Le service
 * Identite ne trace pas les verdicts negatifs de
 * {@code GET /identite/habilitation} : il repond a une question, il ne refuse
 * pas l'action — c'est le consommateur qui refuse. Si le consommateur ne publie
 * pas, le refus n'est trace nulle part et l'exigence CT-04 n'est pas tenue, en
 * silence (CLAUDE.md section 9.2, {@code docs/appel-habilitation.md} section 4).
 * La publication a lieu dans {@code GestionnaireErreursApi}, seul point ou tous
 * les refus convergent (doctrine Sprint 1.3).
 */
public class AgentNonHabiliteException extends RuntimeException {

    public AgentNonHabiliteException(String message) {
        super(message);
    }

}
