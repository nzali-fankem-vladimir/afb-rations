package cm.afrilandfirstbank.rations.workflow.api.dto;

import java.time.LocalDateTime;

import cm.afrilandfirstbank.rations.workflow.application.ResultatIntegrationComptable;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutIntegrationEnum;

/**
 * Reponse de {@code PUT /processus/{id}/integration} : <b>ce que le service a fait de
 * l'accuse</b>, et l'etat d'integration qui en resulte.
 *
 * <h2>Pourquoi le resultat est rendu, et non deduit du code HTTP</h2>
 *
 * <p>{@code APPLIQUE} et {@code DEJA_APPLIQUE} repondent tous deux {@code 200} : dans les
 * deux cas l'accuse a ete pris en compte, et le second n'est pas une erreur — c'est
 * l'idempotence qui fonctionne. Mais l'appelant doit les distinguer, car il ne publie une
 * trace d'audit que dans le premier cas. Sans ce champ, il ne pourrait pas le savoir et
 * publierait une trace a chaque rejeu de topic, faussant le journal d'audit.
 *
 * <p>Rendre {@code 204} pour l'un et {@code 200} pour l'autre aurait fonctionne, mais
 * aurait confie a un code de transport une information metier — et le module a pris
 * l'habitude inverse depuis le Sprint 2.4, ou {@code GET /grilles/active} rend
 * {@code 200 disponible: false} plutot qu'un {@code 404}.
 */
public record IntegrationComptableResponse(
        Long idProcessus,
        String resultat,
        StatutIntegrationEnum statutIntegration,
        String referenceComptable,
        LocalDateTime dateTraitement,
        String motifIntegration) {

    /** L'accuse a ete inscrit sur le processus. */
    public static final String APPLIQUE = "APPLIQUE";

    /** Le processus portait deja exactement cet accuse : rien n'a ete ecrit. */
    public static final String DEJA_APPLIQUE = "DEJA_APPLIQUE";

    public static IntegrationComptableResponse depuis(ResultatIntegrationComptable resultat) {
        return new IntegrationComptableResponse(
                resultat.idProcessus(),
                resultat.aEcrit() ? APPLIQUE : DEJA_APPLIQUE,
                resultat.statutIntegration(),
                resultat.referenceComptable(),
                resultat.dateTraitement(),
                resultat.motifIntegration());
    }

}
