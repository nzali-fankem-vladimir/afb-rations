package cm.afrilandfirstbank.rations.workflow.api.dto;

import cm.afrilandfirstbank.rations.workflow.domaine.StatutIntegrationEnum;

import jakarta.validation.constraints.NotNull;

/**
 * Corps de {@code PUT /processus/{id}/integration}, endpoint <b>interne</b> du Sprint 5.2
 * (hors contrat passerelle).
 *
 * <p>Reprend les quatre valeurs de l'accuse comptable (contrat d'API section 7.2), moins
 * l'identifiant du processus, qui est dans le chemin.
 *
 * <h2>Le statut est ici une enumeration, et c'est voulu</h2>
 *
 * <p>Cote service Transmission, il voyage en chaine : le producteur de l'accuse est un
 * module externe, et une valeur inconnue doit pouvoir etre <i>vue</i> plutot que faire
 * echouer la deserialisation. Ici, l'appelant est notre propre service, qui a deja
 * controle la valeur : une valeur hors du domaine serait un defaut de <b>notre</b> code,
 * et un {@code 400} est la reponse juste. Deux appelants de natures differentes, deux
 * degres de tolerance.
 *
 * <h2>La date voyage en chaine ISO 8601</h2>
 *
 * <p>Elle est convertie une seule fois, dans {@code IntegrationComptableService}, au fuseau
 * du systeme. La faire porter par un type temporel la ferait dependre du convertisseur
 * Jackson de l'API, donc d'un {@code spring.jackson.*} pose dans un fichier de deploiement
 * — exactement le defaut corrige au Sprint 2.2 sur {@code DeltaAudit} et evite au Sprint
 * 5.1 sur la charge comptable.
 *
 * <p>Elle n'est <b>pas obligatoire</b> : un accuse sans date reste applicable, et la
 * colonne est nullable. Perdre le statut d'integration — l'information utile — parce que la
 * date manque serait un mauvais echange.
 *
 * @param motif motif du refus comptable. Non exige ici : la regle « un rejet porte un
 *        motif » est controlee par le service Transmission, au plus pres du message recu,
 *        et un accuse deja refuse la-bas n'arrive jamais jusqu'ici
 */
public record IntegrationComptableRequest(
        @NotNull(message = "Le statut d'integration est obligatoire.")
        StatutIntegrationEnum statutIntegration,
        String referenceComptable,
        String dateTraitement,
        String motif) {
}
