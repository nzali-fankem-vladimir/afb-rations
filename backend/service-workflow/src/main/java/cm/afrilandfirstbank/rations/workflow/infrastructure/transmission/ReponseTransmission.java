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
        String resultat,
        String message,
        String topic,
        Integer partition,
        Long offset,
        int nombreLignes,
        long montantTotal) {

    /**
     * Valeur de {@link #resultat} signifiant que le verrou de RG-13 a refuse une seconde
     * publication : rien n'est parti, et c'est le comportement attendu (Sprint 5.3).
     *
     * <p>Une chaine comparee ici plutot qu'une enumeration partagee : les deux services
     * ne partagent aucun code hors {@code rations-audit-commun} (CLAUDE.md sections 3
     * et 15), et c'est un contrat de <b>protocole</b>, comme le sont deja
     * {@code SERVICE_WORKFLOW_INDISPONIBLE} et les autres codes d'erreur lus par ce
     * client. Le test qui verrouille les noms de champs verrouille aussi celui-ci.
     */
    public static final String DEJA_TRANSMIS = "DEJA_TRANSMIS";

    /** Vrai quand l'etat etait deja transmis et qu'aucune seconde publication n'a eu lieu. */
    public boolean etatDejaTransmis() {
        return DEJA_TRANSMIS.equals(resultat);
    }

}
