package cm.afrilandfirstbank.rations.reporting.domaine;

/**
 * Statut d'avancement d'un état mensuel (CLAUDE.md section 5).
 *
 * <p><b>Duplication volontaire</b>, comme {@link NatureEnum} et {@link SessionEnum}
 * (Sprints 2.1 et 3.1) : copie conforme de l'énumération du même nom de
 * {@code service-workflow}, sans dépendance entre services. C'est un <b>filtre
 * d'entrée</b> de {@code GET /reporting/demandes} (Sprint 7F.5, CT-30) — à la
 * différence de {@code EnTeteDemande.statut}, qui voyage en chaîne parce qu'il est
 * seulement affiché : ici, une valeur inconnue doit être refusée en {@code 400},
 * jamais transmise telle quelle au service Workflow.
 */
public enum StatutEnum {

    EN_COURS_SAISIE,
    SOUMIS,
    EN_ATTENTE_DA,
    EN_ATTENTE_DR,
    RETOURNE,
    CLOTURE

}
