package cm.afrilandfirstbank.rations.reporting.domaine;

/**
 * Ou en est un etat vis-a-vis de la comptabilite, en une valeur lisible.
 *
 * <p><b>Copie conforme de l'enumeration du meme nom de {@code service-transmission}</b>
 * (Sprint 5.3), sur le modele de {@link NatureEnum} et {@link SessionEnum}. Aucune
 * dependance entre services, aucun module partage : la seule mutualisation de code
 * du backend est {@code rations-audit-commun}, et son perimetre est verifie au build
 * (CLAUDE.md sections 3 et 15).
 *
 * <h2>Pourquoi le suivi la nomme, lui aussi</h2>
 *
 * <p>Le contrat n'expose que {@code statutIntegration}, a trois valeurs, et celui-ci
 * est <b>nul dans deux situations qui n'ont rien a voir</b> : un etat jamais transmis,
 * et un etat dont la publication n'a pas ete confirmee. Une liste de suivi qui
 * afficherait une case vide dans les deux cas laisserait le lecteur deviner.
 *
 * <p>La deduction se fait sur le couple ({@code transmisComptabilite},
 * {@code statutIntegration}), exactement comme au Sprint 5.3 — c'est la meme table de
 * deux colonnes, lue au meme endroit du cycle. La <b>phrase d'explication</b>, elle,
 * n'est pas recopiee : elle reste sur {@code GET /transmission/processus/{id}}, ou un
 * lecteur va chercher le detail d'un dossier. Une liste de suivi n'a pas a porter un
 * paragraphe par ligne.
 */
public enum SituationIntegration {

    /**
     * L'etat n'a jamais ete publie vers la comptabilite. Normal tant qu'il n'est pas
     * cloture ; sur un etat cloture, c'est une transmission manquee.
     */
    NON_TRANSMIS,

    /**
     * Le verrou de RG-13 a ete reserve, mais la publication n'a jamais ete confirmee.
     * Tres recent, c'est un envoi en cours ; ancien, c'est une issue incertaine que
     * le module ne rejoue jamais de lui-meme.
     */
    PUBLICATION_NON_CONFIRMEE,

    /** Publie, la comptabilite n'a pas encore accuse reception. */
    EN_ATTENTE_ACCUSE,

    /** La comptabilite a pris l'etat en charge et rendu une reference. */
    INTEGRE,

    /** La comptabilite a refuse l'etat, avec un motif. */
    REJETE;

    /**
     * Deduit la situation du couple de colonnes que porte {@code processus_mensuel}.
     *
     * <p>L'ordre des tests suit la chronologie de l'echange : d'abord « a-t-on
     * envoye ? », puis « qu'en sait-on ? ».
     *
     * @param statutIntegration valeur brute recue du service Workflow, lue en chaine
     *        pour rester tolerante a une valeur inconnue : un statut ajoute plus tard
     *        au module comptable ne doit pas faire echouer toute une page de suivi
     */
    public static SituationIntegration deduire(boolean transmis, String statutIntegration) {
        if (!transmis) {
            return NON_TRANSMIS;
        }
        if (statutIntegration == null || statutIntegration.isBlank()) {
            return PUBLICATION_NON_CONFIRMEE;
        }
        return switch (statutIntegration) {
            case "EN_ATTENTE" -> EN_ATTENTE_ACCUSE;
            case "INTEGRE" -> INTEGRE;
            case "REJETE" -> REJETE;
            // Un statut que ce service ne connait pas : l'etat est transmis, et
            // c'est tout ce qu'on peut affirmer sans inventer. Le mensonge le plus
            // tentant serait EN_ATTENTE_ACCUSE, qui affirmerait une attente dont on
            // ne sait rien.
            default -> PUBLICATION_NON_CONFIRMEE;
        };
    }

}
