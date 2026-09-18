package cm.afrilandfirstbank.rations.workflow.domaine.exception;

/**
 * Le code existe dans {@code parametre_systeme}, mais n'appartient pas a la
 * liste restreinte des parametres modifiables par cet endpoint (guide 7F.6,
 * etape 6, ajout backend scope, {@code ParametreAdminService.CODES_MODIFIABLES}).
 *
 * <p><b>{@code RATTRAPAGE_ACTIF} en est le cas notable.</b> Ce drapeau reste
 * gouverne par sa propre doctrine (CLAUDE.md section 7 : ouvert par migration,
 * ferme par un {@code UPDATE} pose hors de ce module en cas d'urgence) --
 * l'ouvrir a l'ecriture generique ici melangerait deux mecanismes de
 * gouvernance distincts derriere un seul formulaire.
 *
 * <p>{@code 422} et non {@code 403} : la ressource existe et le role de
 * l'appelant est le bon (ADMIN), c'est une regle de gestion qui refuse --
 * meme raisonnement qu'au Sprint 2.3 pour {@code TRANSITION_INTERDITE}.
 */
public class ParametreNonModifiableException extends RuntimeException {

    public ParametreNonModifiableException(String code) {
        super("Le parametre " + code + " n'est pas modifiable par cet endpoint. "
                + "Seuls SEUIL_AIGUILLAGE_DR, DELAI_REGULARISATION_JOURS et "
                + "COMPTE_CHARGE_RATIONS le sont.");
    }

}
