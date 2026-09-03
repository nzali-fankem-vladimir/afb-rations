package cm.afrilandfirstbank.rations.transmission.application;

/**
 * Les trois issues d'un geste du verrou d'unicite, demande au service Workflow
 * (RG-13, Sprint 5.3).
 *
 * <p>Type scelle, sans champ booleen : le {@code switch} qui les traite est exhaustif, et
 * une quatrieme issue ajoutee plus tard ferait echouer la compilation la ou il faut y
 * penser — c'est-a-dire juste avant une publication irreversible.
 */
public sealed interface ResultatVerrou {

    /**
     * Le geste a abouti : la reservation est posee, ou la confirmation inscrite, ou la
     * reservation levee.
     */
    record VerrouTenu(String message) implements ResultatVerrou {
    }

    /**
     * L'etat etait <b>deja transmis</b> : la reservation est refusee, et rien ne doit
     * partir.
     *
     * <p><b>Ce n'est pas une erreur.</b> Un rejeu legitime existe — reprise apres
     * incident, double declenchement, appel manuel repete — et le traiter en panne
     * enverrait chercher un incident la ou le dispositif a precisement fonctionne.
     */
    record DejaTransmis(String message) implements ResultatVerrou {
    }

    /**
     * Le service Workflow n'a rien repondu d'exploitable : timeout, connexion refusee,
     * {@code 5xx}, corps illisible.
     *
     * <p><b>Refus conservateur</b> (doctrine Sprint 1.3) quand il s'agit d'une
     * reservation : sans verrou pose, rien ne part. Un {@code 404} ou un {@code 422} y
     * tombent aussi — l'etat n'existe pas ou n'est pas cloture, et la publication n'a pas
     * lieu davantage ; le message dit lequel.
     */
    record VerrouIndisponible(String motifTechnique) implements ResultatVerrou {
    }

}
