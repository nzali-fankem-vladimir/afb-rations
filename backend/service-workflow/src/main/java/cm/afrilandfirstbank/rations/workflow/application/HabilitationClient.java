package cm.afrilandfirstbank.rations.workflow.application;

/**
 * Port sortant : « cet utilisateur peut-il agir sur un dossier de ce code
 * unite ? » Question posee au service Identite, proprietaire de la portee d'acces
 * (RG-12, Sprint 1.1).
 *
 * <p><b>Le service Workflow est le premier consommateur reel de cet endpoint</b>
 * (guide 4.1 section 0.2). Le {@code code_unite} de l'utilisateur n'est pas dans
 * le jeton Keycloak : il est gere localement par le service Identite (CLAUDE.md
 * section 10). Un consommateur ne peut donc pas trancher seul le cas d'un role a
 * portee locale — la dependance existe quel que soit le design, et l'endpoint
 * dedie fixe seulement <i>ou s'execute la regle</i> : dans le service qui en est
 * proprietaire, plutot que recopiee six fois (decision Sprint 1.3,
 * {@code docs/appel-habilitation.md}).
 *
 * <p><b>Obligation attachee a ce port :</b> tout verdict negatif, et toute
 * indisponibilite du service Identite, doivent produire un evenement d'audit
 * {@code ACCES_REFUSE}. Le service Identite ne trace pas ces refus — il repond a
 * une question, il ne refuse pas l'action. Si le consommateur ne publie pas, le
 * refus n'est trace <b>nulle part</b> et l'exigence CT-04 n'est pas tenue, en
 * silence (CLAUDE.md sections 9.2 et 15). La publication est centralisee dans
 * {@code GestionnaireErreursApi}, seul point ou tous les refus convergent.
 */
public interface HabilitationClient {

    /**
     * @param codeUnite code unite du DOSSIER concerne — celui du processus, lu
     *        sur {@code processus_mensuel} ou porte par la demande de
     *        declenchement ; jamais une valeur choisie par le service pour se
     *        donner raison
     * @param enteteAutorisation en-tete {@code Authorization} de l'utilisateur
     *        final, relaye tel quel (decision Sprint 1.3 : le realm ne comporte
     *        aucun compte de service)
     * @return l'une des trois issues, jamais {@code null} ; une panne reseau est
     *         une issue, pas une exception — c'est l'appelant qui decide du refus
     */
    ResultatHabilitationUnite verifier(String codeUnite, String enteteAutorisation);

}
