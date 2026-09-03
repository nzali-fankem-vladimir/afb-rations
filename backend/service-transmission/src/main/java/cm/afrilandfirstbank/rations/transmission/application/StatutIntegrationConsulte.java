package cm.afrilandfirstbank.rations.transmission.application;

import java.time.LocalDateTime;

import cm.afrilandfirstbank.rations.transmission.domaine.StatutIntegrationEnum;

/**
 * Ce que la consultation du statut d'integration rend, situation nommee comprise
 * (Sprint 5.3, contrat d'API section 7).
 *
 * @param idProcessus l'etat consulte
 * @param situation ou en est reellement cet etat, en une valeur lisible. <b>C'est le
 *        champ a lire en premier</b> : il distingue les deux situations qu'un statut
 *        d'integration nul ne sait pas separer
 * @param statutIntegration le statut du contrat, tel quel : {@code EN_ATTENTE},
 *        {@code INTEGRE}, {@code REJETE}, ou nul si la comptabilite n'a rien dit
 * @param referenceComptable reference produite par le module de comptabilisation. Nulle
 *        tant qu'aucun accuse n'est arrive — ce module n'en fabrique jamais (CLAUDE.md
 *        section 8)
 * @param dateTraitement horodatage du traitement comptable, declare par l'accuse
 * @param motifIntegration motif accompagnant un accuse de rejet, nul autrement
 * @param dateTransmission instant ou le module a engage la transmission. Nul si l'etat
 *        n'a jamais ete transmis
 * @param message la meme information en une phrase, destinee a une personne. <b>Toujours
 *        renseignee</b> : le guide exige qu'aucun champ vide ne reste sans explication, et
 *        quatre des cinq situations rendent une reference comptable nulle pour quatre
 *        raisons differentes
 */
public record StatutIntegrationConsulte(
        Long idProcessus,
        SituationIntegration situation,
        StatutIntegrationEnum statutIntegration,
        String referenceComptable,
        LocalDateTime dateTraitement,
        String motifIntegration,
        LocalDateTime dateTransmission,
        String message) {
}
