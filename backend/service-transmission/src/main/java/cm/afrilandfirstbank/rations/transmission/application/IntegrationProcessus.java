package cm.afrilandfirstbank.rations.transmission.application;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import cm.afrilandfirstbank.rations.transmission.domaine.StatutIntegrationEnum;

/**
 * Le bloc d'integration comptable d'un etat, tel que rendu par
 * {@code GET /processus/{id}/integration} du service Workflow (Sprint 5.3).
 *
 * <h2>Six champs, et pas le dossier</h2>
 *
 * <p>Ce service n'a pas de base : la donnee vit sur {@code processus_mensuel}, dans la
 * base du service Workflow, et se lit par son API (diagramme AR04). L'endpoint interrogé
 * est <b>etroit a dessein</b> : il ne rend ni le montant, ni le motif du retour en cours,
 * ni le type de processus. La consultation du contrat est ouverte a l'ARH, dont la portee
 * est nationale (Sprint 1.1) ; lui servir le dossier complet de toutes les unites pour un
 * besoin de quatre champs aurait ete un elargissement gratuit.
 *
 * <h2>Tolerant reader</h2>
 *
 * <p>Le service Workflow peut enrichir sa reponse sans casser ce type. Types boites : un
 * champ manquant se lit {@code null} et sera presente comme une absence, jamais suppose.
 *
 * @param transmisComptabilite le module a-t-il publie cet etat vers la comptabilite ?
 * @param statutIntegration suite donnee par la comptabilite, nulle tant qu'aucun accuse
 *        n'est arrive
 * @param dateReservationTransmission instant de la reservation du verrou de RG-13. Avec un
 *        statut d'integration nul, son anciennete distingue un etat en transit normal
 *        d'une publication d'issue incertaine
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IntegrationProcessus(
        Long idProcessus,
        Boolean transmisComptabilite,
        StatutIntegrationEnum statutIntegration,
        String referenceComptable,
        LocalDateTime dateTraitement,
        String motifIntegration,
        LocalDateTime dateReservationTransmission) {

    /** Vrai si le module a effectivement revendique puis publie cet etat. */
    public boolean transmis() {
        return Boolean.TRUE.equals(transmisComptabilite);
    }

}
