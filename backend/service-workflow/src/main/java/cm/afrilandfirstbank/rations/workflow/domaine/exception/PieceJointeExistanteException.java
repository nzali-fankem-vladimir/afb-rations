package cm.afrilandfirstbank.rations.workflow.domaine.exception;

/**
 * Un document a deja ete genere pour ce processus.
 *
 * <h2>Le controle applicatif double la contrainte de base, il ne la remplace pas</h2>
 *
 * <p>{@code piece_jointe.id_processus} porte une contrainte {@code UNIQUE} depuis
 * la migration V1 : la regle « un seul document par processus » est garantie par la
 * base. Mais une violation d'integrite produirait un message technique illisible
 * la ou l'agent attend une phrase — c'est exactement ce que le point de vigilance
 * section 10 du guide 4.2 demande d'eviter.
 *
 * <p>Le service verifie donc en amont, ce qui a un second effet : <b>aucun fichier
 * n'est ecrit</b> pour une soumission qu'on va refuser. Sans ce controle, le
 * document serait genere, ecrit sur le disque, puis rejete au moment du commit,
 * laissant un fichier orphelin a chaque tentative.
 *
 * <p>La contrainte de base reste le filet en cas de course entre deux requetes
 * simultanees, traduite elle aussi en {@code 409} par
 * {@code GestionnaireErreursApi} — meme dispositif qu'au Sprint 4.1 pour
 * {@code PROCESSUS_EXISTANT}.
 *
 * <p>Rendue en <b>{@code 409 PIECE_JOINTE_EXISTANTE}</b> : quelque chose est bien
 * duplique, ce qui est la distinction posee au Sprint 2.3 entre conflit d'unicite
 * et regle de gestion.
 */
public class PieceJointeExistanteException extends RuntimeException {

    public PieceJointeExistanteException(String message) {
        super(message);
    }

}
