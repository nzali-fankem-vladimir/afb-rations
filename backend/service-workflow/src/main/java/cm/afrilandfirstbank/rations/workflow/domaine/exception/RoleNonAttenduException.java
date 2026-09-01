package cm.afrilandfirstbank.rations.workflow.domaine.exception;

/**
 * L'appelant a un role du circuit, mais pas celui que le dossier attend a ce
 * stade (RG-07).
 *
 * <p>Un directeur reseau devant un etat {@code EN_ATTENTE_DA}, ou un chef d'unite
 * devant un etat {@code EN_ATTENTE_DR}. Rendue en {@code 403 ACCES_REFUSE}, code
 * deja au contrat : c'est bien un refus de role, et non un manque d'habilitation
 * sur l'unite ni un cumul de taches.
 *
 * <p>Sa raison d'etre est le <b>message</b> : le refus generique de Spring Security
 * dit « votre role ne permet pas cette action », ce qui est faux ici — le role
 * permet la validation, mais pas <i>a ce niveau-la</i>. Le message nomme donc le
 * niveau que le dossier attend reellement.
 */
public class RoleNonAttenduException extends RuntimeException {

    public RoleNonAttenduException(String message) {
        super(message);
    }

}
