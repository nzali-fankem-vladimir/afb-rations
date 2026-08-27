package cm.afrilandfirstbank.rations.grilles.domaine;

/**
 * Nature d'une prestation servie a un agent de la garde armee (CLAUDE.md section 5).
 *
 * <p>Chaque grille tarifaire, comme chaque ligne de prestation au Sprint 3, est
 * RATION ou TRANSPORT exclusivement (RG-01). La contrainte {@code CHECK} de la
 * table {@code grille_tarifaire} porte les memes deux valeurs.
 *
 * <p>Duplication volontaire : {@code service-saisie} redefinit cette enumeration a
 * l'identique au Sprint 3.1 plutot que d'introduire une dependance entre services
 * ou un module partage. Voir
 * {@code docs/decisions/2026-08-27-partage-enumerations-nature-session.md}.
 */
public enum NatureEnum {

    RATION,
    TRANSPORT

}
