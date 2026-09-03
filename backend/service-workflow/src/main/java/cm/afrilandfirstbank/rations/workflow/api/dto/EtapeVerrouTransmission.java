package cm.afrilandfirstbank.rations.workflow.api.dto;

/**
 * Les trois etapes du verrou d'unicite de transmission (RG-13, Sprint 5.3), telles que le
 * service Transmission les demande.
 *
 * <p>Elles s'enchainent toujours dans le meme ordre, et le nom de chacune dit a quel
 * moment de la publication elle se place :
 *
 * <pre>
 *   RESERVATION   avant de publier   -&gt; RESERVEE, ou DEJA_TRANSMISE et rien ne part
 *   CONFIRMATION  apres l'accuse du broker
 *   LIBERATION    apres un echec dont on a la PREUVE qu'aucun evenement n'est parti
 * </pre>
 *
 * <p>Une enumeration plutot qu'une chaine : une etape inconnue est refusee par la
 * deserialisation, en {@code 400}, plutot que de tomber dans une branche par defaut du
 * cote du service.
 */
public enum EtapeVerrouTransmission {

    /** Pose le verrou avant la publication. Aucune publication sans elle. */
    RESERVATION,

    /** Constate l'accuse du broker : l'etat passe en attente de l'accuse comptable. */
    CONFIRMATION,

    /**
     * Leve la reservation, uniquement sur la preuve qu'aucun evenement n'est parti. Un
     * echec ambigu ne libere jamais : on ne rejoue pas une transmission qui a pu partir.
     */
    LIBERATION
}
