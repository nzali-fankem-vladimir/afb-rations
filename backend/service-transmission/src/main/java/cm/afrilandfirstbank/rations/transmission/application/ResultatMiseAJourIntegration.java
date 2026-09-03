package cm.afrilandfirstbank.rations.transmission.application;

import cm.afrilandfirstbank.rations.transmission.domaine.StatutIntegrationEnum;

/**
 * Les six issues d'une remontee de statut d'integration vers le service Workflow.
 *
 * <h2>Pourquoi elles sont six, et pourquoi c'est le Workflow qui les prononce</h2>
 *
 * <p>Trois de ces issues — {@link ProcessusInconnu}, {@link ProcessusNonTransmis},
 * {@link AccuseContradictoire} — demandent l'<b>etat courant</b> du processus. Cet etat
 * vit dans {@code processus_mensuel}, dans la base du service Workflow ; le service
 * Transmission n'a pas de base et n'accede jamais a celle du Workflow (AR04). Il ne peut
 * donc pas les trancher lui-meme, et il ne les devine pas : il pose la question, et le
 * Workflow repond en meme temps qu'il ecrit, <b>dans une seule transaction</b>.
 *
 * <p>C'est ce qui rend l'idempotence sure. Lire puis ecrire en deux appels laisserait
 * entre les deux une fenetre ou un second accuse pourrait s'intercaler ; ici la lecture,
 * la decision et l'ecriture sont un seul geste, et {@link DejaApplique} en est le compte
 * rendu.
 *
 * <h2>Definitif contre temporaire</h2>
 *
 * <p>Seule {@link ServiceWorkflowIndisponible} est <b>temporaire</b> : elle seule justifie
 * de rejouer le message. Les quatre autres refus sont definitifs — un JSON tronque le
 * restera, un processus inconnu ne naitra pas, un statut definitif ne se defera pas — et
 * rejouer ne ferait que bloquer la partition.
 *
 * <p>Type scelle : le {@code switch} qui traite ces issues est exhaustif, et une septieme
 * ajoutee plus tard ferait echouer la compilation au lieu de tomber dans une branche par
 * defaut. Meme discipline qu'au Sprint 5.1 pour {@code ResultatDemandeTransmission}, ou
 * une branche par defaut aurait pu rejouer une publication ambigue.
 */
public sealed interface ResultatMiseAJourIntegration {

    /** Le statut d'integration a ete ecrit sur le processus. */
    record Applique(Long idProcessus, StatutIntegrationEnum statutApplique)
            implements ResultatMiseAJourIntegration {
    }

    /**
     * Le processus portait deja exactement cet accuse — meme statut, meme reference, meme
     * date, meme motif. Rien n'a ete ecrit, et ce n'est pas une erreur : c'est
     * l'idempotence qui se voit.
     */
    record DejaApplique(Long idProcessus, StatutIntegrationEnum statutCourant)
            implements ResultatMiseAJourIntegration {
    }

    /**
     * {@code 404} : le service Workflow ne connait pas cet identifiant. Soit la
     * comptabilite s'est trompee, soit l'accuse s'adresse a un autre module.
     */
    record ProcessusInconnu(Long idProcessus) implements ResultatMiseAJourIntegration {
    }

    /**
     * {@code 422} : la comptabilite accuse reception d'un etat qui ne lui a jamais ete
     * envoye. Incoherence serieuse, d'un cote ou de l'autre.
     */
    record ProcessusNonTransmis(Long idProcessus, String message)
            implements ResultatMiseAJourIntegration {
    }

    /**
     * {@code 409} : l'accuse contredit un statut deja recu. Le Workflow n'a rien ecrit.
     */
    record AccuseContradictoire(Long idProcessus, String message)
            implements ResultatMiseAJourIntegration {
    }

    /**
     * Le service Workflow n'a rien repondu d'exploitable : timeout, connexion refusee,
     * {@code 5xx}, corps illisible. <b>Seule issue temporaire</b> : elle fait rejouer le
     * message.
     */
    record ServiceWorkflowIndisponible(Long idProcessus, String motifTechnique)
            implements ResultatMiseAJourIntegration {
    }

}
