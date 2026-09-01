package cm.afrilandfirstbank.rations.workflow.domaine.exception;

/**
 * Un retour a l'agent a ete tente sans motif (RG-10 : motif obligatoire pour tout
 * rejet ou retour).
 *
 * <p>Distincte d'une transition interdite : ici la transition
 * {@code EN_ATTENTE_DA -> RETOURNE} ou {@code EN_ATTENTE_DR -> RETOURNE} est
 * parfaitement legitime, c'est l'absence de motif qui la bloque. Les confondre
 * ferait reprocher a l'utilisateur la mauvaise chose — meme distinction qu'au
 * Sprint 2.3 entre {@code TRANSITION_INTERDITE} et {@code MOTIF_OBLIGATOIRE}, qui
 * sont deja deux codes distincts du contrat d'API.
 *
 * <p>Le motif est exige <b>avant</b> la transition : un retour sans motif sur un
 * etat qui ne peut de toute facon pas etre retourne signale l'absence de motif
 * d'abord, et l'utilisateur corrige la premiere chose qu'on lui reproche.
 */
public class MotifRetourRequisException extends RuntimeException {

    public MotifRetourRequisException(String message) {
        super(message);
    }

}
