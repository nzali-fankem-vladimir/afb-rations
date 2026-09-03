package cm.afrilandfirstbank.rations.transmission.application;

/**
 * Port sortant : « puis-je publier cet etat, et moi seul ? » Question posee au service
 * Workflow, qui detient {@code processus_mensuel} et donc le drapeau de RG-13.
 *
 * <h2>Pourquoi le verrou n'est pas ici</h2>
 *
 * <p>Ce service <b>n'a pas de base</b> (CLAUDE.md section 3). Un verrou porte par une
 * memoire locale — cache, table temporaire, compteur en memoire — disparaitrait au
 * premier redemarrage, c'est-a-dire au moment precis ou un rejeu est le plus probable ;
 * et deux instances de ce service ne partageraient rien du tout. La seule source de
 * verite est {@code processus_mensuel}, et la seule facon de serialiser deux demandes
 * concurrentes est de les faire passer par la ligne de cette table.
 *
 * <p>C'est le raisonnement du Sprint 5.2 pour l'idempotence de l'accuse comptable,
 * applique a l'autre sens de l'echange.
 *
 * <h2>Trois gestes, et le second n'est pas facultatif</h2>
 *
 * <pre>
 *   reserver   avant de publier   -&gt; VerrouTenu, ou DejaTransmis et rien ne part
 *   confirmer  apres l'accuse du broker
 *   liberer    echec PROUVE sans envoi : une reprise redevient possible
 * </pre>
 *
 * <p>Sans la confirmation, l'etat resterait indefiniment « reserve, issue inconnue » et
 * la supervision le signalerait comme un incident alors que tout s'est bien passe.
 *
 * <p><b>Pourquoi une interface</b>, comme {@link ProcessusClient} et
 * {@link ConsolidationClient} : la regle d'unicite doit pouvoir etre eprouvee sans
 * reseau, et une notion de panne HTTP n'a rien a faire dans les regles de transmission.
 */
public interface VerrouTransmissionClient {

    /**
     * Pose le verrou avant publication. <b>Aucune publication sans un {@code VerrouTenu}
     * en retour.</b>
     *
     * @param enteteAutorisation en-tete {@code Authorization} de l'utilisateur final,
     *        relaye tel quel (doctrine Sprint 1.3)
     */
    ResultatVerrou reserver(Long idProcessus, String enteteAutorisation);

    /**
     * Constate l'accuse du broker : l'etat passe en attente de l'accuse comptable.
     *
     * @param accuse la position du message sur le broker, reprise dans la trace d'audit
     *        publiee par le service Workflow
     */
    ResultatVerrou confirmer(Long idProcessus, ResultatPublication.Publiee accuse,
            int nombreLignes, long montantTotal, String enteteAutorisation);

    /**
     * Leve la reservation, <b>uniquement</b> lorsqu'il est prouve qu'aucun evenement
     * n'est parti.
     *
     * @param motif ce qui a echoue, inscrit dans la trace d'audit : un drapeau de RG-13
     *        qui revient a faux rouvre la porte a une publication, cela ne se fait pas
     *        sans explication
     */
    ResultatVerrou liberer(Long idProcessus, String motif, String enteteAutorisation);

}
