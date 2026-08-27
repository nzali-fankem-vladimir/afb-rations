package cm.afrilandfirstbank.rations.grilles.domaine.exception;

/**
 * Un rejet de grille a ete tente sans motif (RG-10 : motif obligatoire pour tout
 * rejet ou retour). Erreur explicite, distincte d'une transition interdite : ici
 * la transition EN_ATTENTE_DRH -> REJETEE est legitime, c'est l'absence de motif
 * qui la bloque.
 */
public class MotifRejetRequisException extends RuntimeException {

    public MotifRejetRequisException(String message) {
        super(message);
    }

}
