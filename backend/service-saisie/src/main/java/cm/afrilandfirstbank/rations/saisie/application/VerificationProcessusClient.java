package cm.afrilandfirstbank.rations.saisie.application;

/**
 * Port sortant : « ce processus mensuel existe-t-il, et son etat est-il encore
 * modifiable ? » Question posee au service Workflow, proprietaire de
 * {@code processus_mensuel}.
 *
 * <p><b>Pourquoi un port dans la couche application</b>, et non un appel direct
 * depuis les services : meme justification qu'au Sprint 3.2 pour
 * {@code ResolutionMontantClient}. Le domaine porte les regles verifiables sans
 * rien demander a personne ; « cet etat est-il encore ouvert ? » est une
 * question posee a un autre service, et
 * {@code ResultatVerificationProcessus.ServiceWorkflowIndisponible} ferait
 * entrer une notion de panne reseau dans le domaine.
 *
 * <p>L'unique implantation est {@code VerificationProcessusHttpClient}. Une
 * seconde, {@code BouchonVerificationProcessus}, a existe sous le profil
 * {@code bouchon-workflow} tant que le service Workflow n'etait pas ecrit ; elle
 * a ete <b>supprimee au Sprint 4.1</b>, ou ce service est entre en fonction
 * (actions B-01 a B-03, {@code docs/dispositifs_provisoires.md}).
 *
 * @see ResultatVerificationProcessus
 */
public interface VerificationProcessusClient {

    /**
     * @param idProcessus identifiant du processus mensuel, tel que la fiche ou la
     *        requete le porte
     * @param enteteAutorisation en-tete {@code Authorization} de l'utilisateur
     *        final, relaye TEL QUEL (decision Sprint 1.3 : aucun compte de
     *        service, le realm n'en comporte aucun)
     * @return l'une des trois issues, jamais {@code null}, jamais d'exception
     *         levee pour une panne — c'est l'appelant qui decide du refus
     */
    ResultatVerificationProcessus verifier(Long idProcessus, String enteteAutorisation);

}
