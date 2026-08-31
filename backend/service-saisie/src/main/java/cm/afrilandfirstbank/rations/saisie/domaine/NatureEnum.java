package cm.afrilandfirstbank.rations.saisie.domaine;

/**
 * Nature d'une prestation servie à un agent de la garde armée (CLAUDE.md section 5).
 *
 * <p>Chaque ligne de prestation est RATION ou TRANSPORT exclusivement (RG-01). La
 * contrainte {@code CHECK} de {@code ligne_prestation} porte les mêmes deux
 * valeurs.
 *
 * <p><b>Duplication volontaire.</b> Copie conforme de l'énumération du même nom de
 * {@code service-grilles} (Sprint 2.1). Aucune dépendance entre services, aucun
 * module partagé : le garde-fou réel est la contrainte {@code CHECK} de chaque
 * base. Voir
 * {@code docs/decisions/2026-08-27-partage-enumerations-nature-session.md}.
 */
public enum NatureEnum {

    RATION,
    TRANSPORT

}
