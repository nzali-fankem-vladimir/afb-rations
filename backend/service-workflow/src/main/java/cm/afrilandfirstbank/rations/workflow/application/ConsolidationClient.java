package cm.afrilandfirstbank.rations.workflow.application;

/**
 * Port sortant : « quel est l'etat mensuel consolide de ce processus ? » Question
 * posee au service Saisie, qui detient {@code fiche_journaliere} et
 * {@code ligne_prestation} (RG-06, {@code docs/appel-consolidation.md}).
 *
 * <p><b>Pourquoi une interface</b>, et non un appel direct depuis le service :
 * meme justification qu'au Sprint 3.2 cote Saisie pour
 * {@code ResolutionMontantClient}. L'appel est une dependance technique ; le
 * service applicatif doit pouvoir etre eprouve sans reseau, et
 * {@code ResultatConsolidation.ServiceSaisieIndisponible} ne doit pas faire
 * entrer une notion de panne HTTP dans les regles du workflow.
 *
 * <p><b>Aucun acces a la base du service Saisie.</b> L'echange passe par l'API et
 * rien d'autre : aucune source de donnees vers {@code rations_saisie} n'existe
 * dans ce service, et la cartographie le verifie (critere du guide 4.1
 * section 9).
 *
 * <p><b>Aucun cache.</b> Le total change a chaque ligne saisie ; servir une
 * valeur memorisee reviendrait a valider un etat qui n'est plus celui qu'on a lu
 * (section 6 de la convention).
 */
public interface ConsolidationClient {

    /**
     * @param idProcessus processus dont on demande l'etat
     * @param codeUnite code unite du processus, lu sur
     *        {@code processus_mensuel.code_unite}. <b>Obligatoire</b> : le service
     *        Saisie refuse l'appel sans lui ({@code 400}), et le recoupe contre le
     *        {@code code_unite} fige sur les fiches, qui fait autorite
     *        ({@code 403 UNITE_NON_CONCORDANTE} en cas de desaccord). C'est
     *        Workflow qui detient cette valeur — la lui faire deduire des fiches
     *        ferait disparaitre le controle de portee sur un etat vide
     *        (section 2.2 de la convention)
     * @param enteteAutorisation en-tete {@code Authorization} de l'utilisateur
     *        final, relaye tel quel
     * @return l'une des deux issues, jamais {@code null}
     */
    ResultatConsolidation consolider(Long idProcessus, String codeUnite, String enteteAutorisation);

}
