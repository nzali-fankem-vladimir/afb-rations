package cm.afrilandfirstbank.rations.reporting.domaine;

/**
 * Session de la prestation (CLAUDE.md section 5).
 *
 * <p>Chaque ligne de prestation est JOUR ou SOIR exclusivement (RG-02). La
 * contrainte {@code CHECK} de {@code ligne_prestation} porte les mêmes deux
 * valeurs.
 *
 * <p><b>Duplication volontaire.</b> Copie conforme de l'énumération du même nom de
 * {@code service-saisie} et {@code service-grilles} (Sprints 2.1 et 3.1). Voir
 * {@code docs/decisions/2026-08-27-partage-enumerations-nature-session.md}.
 */
public enum SessionEnum {

    JOUR,
    SOIR

}
