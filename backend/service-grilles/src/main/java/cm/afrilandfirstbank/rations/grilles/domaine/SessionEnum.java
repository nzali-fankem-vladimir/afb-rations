package cm.afrilandfirstbank.rations.grilles.domaine;

/**
 * Session de la prestation (CLAUDE.md section 5).
 *
 * <p>Chaque grille tarifaire, comme chaque ligne de prestation au Sprint 3, est
 * JOUR ou SOIR exclusivement (RG-02). La contrainte {@code CHECK} de la table
 * {@code grille_tarifaire} porte les memes deux valeurs.
 *
 * <p>Duplication volontaire : {@code service-saisie} redefinit cette enumeration a
 * l'identique au Sprint 3.1. Voir
 * {@code docs/decisions/2026-08-27-partage-enumerations-nature-session.md}.
 */
public enum SessionEnum {

    JOUR,
    SOIR

}
