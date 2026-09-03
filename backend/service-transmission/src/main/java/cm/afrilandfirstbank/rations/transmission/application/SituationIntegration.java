package cm.afrilandfirstbank.rations.transmission.application;

/**
 * Ou en est un etat vis-a-vis de la comptabilite, en une valeur lisible (Sprint 5.3,
 * contrat d'API section 7).
 *
 * <h2>Pourquoi une situation en plus du statut d'integration</h2>
 *
 * <p>Le contrat n'expose que {@code statutIntegration}, a trois valeurs. Or celui-ci est
 * <b>nul</b> dans deux situations qui n'ont rien a voir : un etat jamais transmis, et un
 * etat dont la publication n'a pas ete confirmee. Rendre un champ nul avec la meme
 * apparence dans les deux cas laisserait le lecteur deviner — exactement ce que le guide
 * interdit (« jamais un champ vide sans explication »).
 *
 * <p>Cette enumeration nomme donc les cinq situations reellement distinctes, et le statut
 * d'integration du contrat reste rendu tel quel a cote.
 */
public enum SituationIntegration {

    /**
     * L'etat n'a jamais ete publie vers la comptabilite. Normal tant qu'il n'est pas
     * cloture ; anormal sur un etat cloture, ou il signale une transmission manquee
     * (requete de supervision du Sprint 5.1).
     */
    NON_TRANSMIS,

    /**
     * Le verrou de RG-13 a ete reserve, mais la publication n'a jamais ete confirmee.
     *
     * <p>Deux lectures, que l'anciennete de la reservation separe : quelques secondes,
     * c'est une transmission en cours ; plusieurs heures, c'est une publication d'issue
     * incertaine que personne n'a levee. Le module ne rejoue jamais de lui-meme dans ce
     * cas — republier risquerait un double paiement.
     */
    PUBLICATION_NON_CONFIRMEE,

    /** Publie, la comptabilite n'a pas encore accuse reception. */
    EN_ATTENTE_ACCUSE,

    /** La comptabilite a pris l'etat en charge et rendu une reference. */
    INTEGRE,

    /** La comptabilite a refuse l'etat, avec un motif. */
    REJETE
}
