package cm.afrilandfirstbank.rations.transmission.domaine.exception;

/**
 * L'utilisateur a le bon role, mais pas la portee sur l'unite du dossier consulte
 * (RG-12, Sprint 5.3).
 *
 * <p>Rendue en {@code 403 UTILISATEUR_NON_HABILITE}, code distinct d'{@code ACCES_REFUSE}
 * — qui designe, lui, un role insuffisant. La distinction vient du Sprint 4.4 : les deux
 * refus appellent deux gestes differents, et les confondre enverrait un chef d'unite
 * reclamer une habilitation qu'il possede deja.
 *
 * <p>Ce service ne prononce pas ce refus lui-meme : il le <b>relaie</b>, le service
 * Workflow ayant verifie la portee sur le jeton transmis. Le relayer fidelement, plutot
 * que de tout ramener a un {@code 403} generique, evite de perdre en route la seule
 * information qui dise a l'utilisateur quoi faire.
 */
public class AccesHorsPorteeException extends RuntimeException {

    public AccesHorsPorteeException(String message) {
        super(message);
    }

}
