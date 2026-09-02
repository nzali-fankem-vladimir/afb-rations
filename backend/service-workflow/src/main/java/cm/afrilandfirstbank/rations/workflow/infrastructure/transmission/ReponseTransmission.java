package cm.afrilandfirstbank.rations.workflow.infrastructure.transmission;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Corps rendu par {@code POST /transmission/processus/{id}} en cas de succes.
 *
 * <p>Jumeau de {@code TransmissionResponse} cote service Transmission, ecrit contre lui.
 * Duplication assumee, doctrine du module : {@code rations-audit-commun} est la seule
 * mutualisation de code du backend, et son perimetre est verifie au build (CLAUDE.md
 * sections 3 et 15).
 *
 * <p><b>Tolerant reader</b> : le service Transmission peut enrichir sa reponse sans casser
 * celle-ci. Les types sont boites pour qu'un champ manquant se lise {@code null} et soit
 * refuse, jamais suppose — un {@code offset} absent lu comme {@code 0} designerait le
 * premier message du topic, ce qui est faux et trompeur a la relecture.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReponseTransmission(
        Long idProcessus,
        String topic,
        int partition,
        long offset,
        int nombreLignes,
        long montantTotal) {
}
