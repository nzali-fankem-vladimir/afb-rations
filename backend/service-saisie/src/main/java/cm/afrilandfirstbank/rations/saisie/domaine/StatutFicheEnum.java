package cm.afrilandfirstbank.rations.saisie.domaine;

/**
 * Statut d'une fiche journalière (CLAUDE.md section 5).
 *
 * <p>Deux états seulement :
 *
 * <pre>
 *   (création) --> EN_SAISIE --> ENREGISTREE
 * </pre>
 *
 * <p>La contrainte {@code CHECK} de {@code fiche_journaliere} porte ces deux
 * valeurs. La consolidation mensuelle (RG-06) et la soumission (workflow) sont
 * hors du périmètre du Sprint 3.1 : ce cycle n'est pas encore piloté par une
 * machine à états ici.
 */
public enum StatutFicheEnum {

    /** Fiche du jour ouverte, lignes de prestation encore modifiables. */
    EN_SAISIE,

    /** Fiche close pour la journée, prête pour la consolidation mensuelle. */
    ENREGISTREE

}
