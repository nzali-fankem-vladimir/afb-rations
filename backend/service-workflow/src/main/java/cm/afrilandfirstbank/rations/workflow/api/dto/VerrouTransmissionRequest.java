package cm.afrilandfirstbank.rations.workflow.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Corps de {@code PUT /processus/{id}/transmission}, l'endpoint <b>interne</b> du verrou
 * d'unicite (RG-13, Sprint 5.3).
 *
 * <h2>Un seul endpoint, trois etapes</h2>
 *
 * <p>Les trois gestes du verrou — reserver, confirmer, liberer — portent sur la <b>meme</b>
 * ressource : le drapeau de transmission d'un etat. Trois endpoints distincts auraient
 * decoupe en trois routes ce qui est un seul mecanisme, et le lecteur aurait du les
 * rassembler mentalement pour comprendre l'ordre dans lequel ils s'appellent. Une etape
 * nommee dans le corps laisse cet ordre visible en un seul endroit.
 *
 * <p><b>{@code PUT} et non {@code POST}</b> : chacune des trois etapes est idempotente —
 * une reservation deja posee rend {@code DEJA_TRANSMISE}, une confirmation rejouee
 * n'ecrit rien, une liberation deja faite non plus. Le verbe le dit avant tout
 * commentaire, comme au Sprint 5.2 pour {@code PUT /processus/{id}/integration}.
 *
 * @param etape le geste demande, obligatoire
 * @param topic topic effectivement servi, pour la trace d'audit de la confirmation
 * @param partition partition retenue par la cle de partition
 * @param offset position du message : avec la partition, elle designe l'evenement de
 *        maniere unique et permet de le relire tant que la retention le garde
 * @param nombreLignes lignes effectivement parties, temoin repris dans l'audit
 * @param montantTotal montant effectivement parti, meme usage
 * @param motif ce qui a empeche la publication, pour la trace d'une liberation. Une
 *        liberation sans motif serait une reouverture inexpliquee du verrou de RG-13
 */
public record VerrouTransmissionRequest(
        @NotNull(message = "L'etape du verrou de transmission est obligatoire.")
        EtapeVerrouTransmission etape,
        String topic,
        Integer partition,
        Long offset,
        Integer nombreLignes,
        Long montantTotal,
        String motif) {
}
