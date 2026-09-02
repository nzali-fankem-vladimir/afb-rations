package cm.afrilandfirstbank.rations.transmission.domaine.exception;

import java.util.List;
import java.util.stream.Collectors;

import cm.afrilandfirstbank.rations.transmission.application.AnomalieCharge;

/**
 * La charge n'est pas publiable en l'etat ({@code 500 CHARGE_INCOMPLETE}).
 *
 * <h2>Pourquoi {@code 500} et non {@code 422}</h2>
 *
 * <p>Un {@code 422} dirait a l'appelant : « corrigez votre dossier ». Or il n'y a rien a
 * corriger — l'etat est cloture, donc fige, et l'agent ne peut plus rien y toucher. Une
 * charge incomplete a ce stade signale un defaut du module lui-meme : une donnee perdue
 * en route, un controle de completude de la soumission contourne, ou deux services qui ne
 * parlent pas du meme dossier. Meme parti qu'au Sprint 2.4 pour
 * {@code INCOHERENCE_GRILLE} et au Sprint 4.3 pour {@code SEUIL_INDISPONIBLE} : le
 * service refuse et signale, il n'arbitre jamais.
 *
 * <h2>Les anomalies voyagent dans le message, pas dans un sixieme champ</h2>
 *
 * <p>Le Sprint 4.2 avait ajoute {@code manques} au format d'erreur parce que CT-13
 * exigeait que le <b>frontend</b> puisse les rendre un a un et renvoyer l'agent vers la
 * journee fautive. Ici le consommateur est un service, pas une interface : personne n'a a
 * les afficher. Les cinq champs du contrat restent donc exactement les cinq champs du
 * contrat, et les anomalies sont enumerees dans le message, separees, chacune precedee de
 * son code.
 *
 * <p>Elles sont <b>aussi</b> journalisees au prefixe reperable {@code CHARGE INCOMPLETE}
 * et tracees en audit : un refus de transmission doit rester lisible six mois plus tard,
 * dans une base qu'aucun service metier ne peut reecrire.
 */
public class ChargeIncompleteException extends RuntimeException {

    private final transient List<AnomalieCharge> anomalies;

    public ChargeIncompleteException(Long idProcessus, List<AnomalieCharge> anomalies) {
        super("La charge de l'etat " + idProcessus + " n'est pas publiable : " + anomalies.size()
                + " anomalie(s). " + resumer(anomalies));
        this.anomalies = List.copyOf(anomalies);
    }

    public List<AnomalieCharge> getAnomalies() {
        return anomalies;
    }

    private static String resumer(List<AnomalieCharge> anomalies) {
        return anomalies.stream()
                .map(anomalie -> "[" + anomalie.code() + "] " + anomalie.message())
                .collect(Collectors.joining(" | "));
    }

}
