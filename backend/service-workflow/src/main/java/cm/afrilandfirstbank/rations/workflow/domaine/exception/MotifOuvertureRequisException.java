package cm.afrilandfirstbank.rations.workflow.domaine.exception;

/**
 * Une ouverture d'etat complementaire a ete demandee sans motif (Sprint 6bis.1).
 * Rendue en {@code 422 MOTIF_OBLIGATOIRE}.
 *
 * <h2>Meme code que le retour sans motif, deliberement</h2>
 *
 * <p>{@code MOTIF_OBLIGATOIRE} est deja le code du contrat pour le retour d'un
 * processus sans motif (RG-10), repris tel quel au Sprint 2.3 pour le rejet d'une
 * grille : <b>meme regle, meme code</b>. Une exception distincte de
 * {@link MotifRetourRequisException} parce que les deux messages n'ont rien a se
 * dire — l'un explique a un valideur ce que l'agent doit corriger, l'autre explique
 * a un agent pourquoi il doit justifier une regularisation.
 *
 * <h2>Ce que le motif porte ici</h2>
 *
 * <p>Il n'existe <b>pas d'entite Reclamation</b> dans ce module : le signalement du
 * beneficiaire est un evenement externe au systeme (CLAUDE.md section 7). Le motif
 * d'ouverture est donc la <b>seule</b> trace de ce qui a declenche la
 * regularisation, et c'est ce qu'un controle interne lira six mois plus tard pour
 * comprendre pourquoi une periode close a recu un paiement complementaire.
 *
 * <p>Comme le motif de retour au Sprint 4.4, le controle porte sur le <b>contenu
 * utile</b> : une suite d'espaces est un champ present et un motif absent.
 */
public class MotifOuvertureRequisException extends RuntimeException {

    public MotifOuvertureRequisException(String message) {
        super(message);
    }

}
