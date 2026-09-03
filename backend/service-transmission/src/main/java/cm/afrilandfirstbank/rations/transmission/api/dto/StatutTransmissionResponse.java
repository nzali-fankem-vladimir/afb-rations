package cm.afrilandfirstbank.rations.transmission.api.dto;

import java.time.LocalDateTime;

import cm.afrilandfirstbank.rations.transmission.application.SituationIntegration;
import cm.afrilandfirstbank.rations.transmission.application.StatutIntegrationConsulte;
import cm.afrilandfirstbank.rations.transmission.domaine.StatutIntegrationEnum;

/**
 * Corps de {@code GET /transmission/processus/{id}} ({@code 200}), le seul endpoint de ce
 * service au contrat de la passerelle (contrat d'API section 7, US-15).
 *
 * <h2>Les trois champs du contrat, et deux de plus</h2>
 *
 * <p>Le contrat annonce le statut d'integration, la reference comptable, la date de
 * traitement et le motif en cas de rejet. Ils sont tous la, sous ces noms. S'y ajoutent
 * {@code situation} et {@code message}, en fin de record — ajout additif, comme
 * {@code motifRetour} au Sprint 4.4 et {@code manques} au Sprint 4.2.
 *
 * <p>Ils repondent a l'exigence du guide : « chaque cas doit produire une reponse
 * comprehensible, jamais un champ vide sans explication ». Quatre des cinq situations
 * rendent une reference comptable nulle, pour quatre raisons differentes — jamais
 * transmis, publication non confirmee, accuse non recu, rejet. Le seul statut du contrat
 * n'en distingue que deux.
 *
 * @param idProcessus l'etat consulte
 * @param situation ou en est reellement cet etat : le champ a lire en premier
 * @param statutIntegration le statut du contrat, nul tant que la comptabilite n'a rien dit
 * @param referenceComptable reference rendue par la comptabilite, nulle avant l'accuse
 * @param dateTraitement horodatage declare par l'accuse
 * @param motifIntegration motif d'un rejet, nul autrement
 * @param dateTransmission instant ou le module a engage la transmission
 * @param message la meme information en une phrase, toujours renseignee
 */
public record StatutTransmissionResponse(
        Long idProcessus,
        SituationIntegration situation,
        StatutIntegrationEnum statutIntegration,
        String referenceComptable,
        LocalDateTime dateTraitement,
        String motifIntegration,
        LocalDateTime dateTransmission,
        String message) {

    public static StatutTransmissionResponse de(StatutIntegrationConsulte consulte) {
        return new StatutTransmissionResponse(
                consulte.idProcessus(),
                consulte.situation(),
                consulte.statutIntegration(),
                consulte.referenceComptable(),
                consulte.dateTraitement(),
                consulte.motifIntegration(),
                consulte.dateTransmission(),
                consulte.message());
    }

}
