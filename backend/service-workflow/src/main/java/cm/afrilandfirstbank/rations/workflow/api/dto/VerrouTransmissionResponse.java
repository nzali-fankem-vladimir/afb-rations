package cm.afrilandfirstbank.rations.workflow.api.dto;

import java.time.LocalDateTime;

import cm.afrilandfirstbank.rations.workflow.application.ResultatVerrouTransmission;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutIntegrationEnum;

/**
 * Corps de {@code PUT /processus/{id}/transmission} ({@code 200}).
 *
 * <h2>{@code 200} dans les deux cas, et le champ {@code resultat} tranche</h2>
 *
 * <p>Une reservation refusee parce que l'etat est deja transmis n'est <b>pas une
 * erreur</b> : un rejeu legitime existe — reprise apres incident, appel repete — et lui
 * opposer un {@code 409} ferait croire a une panne, ou pousserait a reessayer. Le service
 * Transmission lit {@code resultat} et sait s'il doit publier. C'est l'idiome du
 * Sprint 5.2, ou {@code APPLIQUE} et {@code DEJA_APPLIQUE} partagent le meme {@code 200}.
 *
 * @param idProcessus l'etat concerne
 * @param resultat le geste effectivement accompli : {@code RESERVEE} autorise la
 *        publication, {@code DEJA_TRANSMISE} l'interdit
 * @param message ce qui s'est passe, en clair, destine a une personne
 * @param transmisComptabilite drapeau de RG-13 apres le geste
 * @param statutIntegration statut d'integration apres le geste. <b>Nul avec un drapeau a
 *        vrai signifie « reserve, publication non confirmee »</b> — l'etat intermediaire
 *        que la supervision surveille
 * @param dateReservation instant de la reservation en vigueur. C'est son anciennete qui
 *        distingue un etat en transit normal d'une publication d'issue incertaine
 */
public record VerrouTransmissionResponse(
        Long idProcessus,
        ResultatVerrouTransmission.Resultat resultat,
        String message,
        boolean transmisComptabilite,
        StatutIntegrationEnum statutIntegration,
        LocalDateTime dateReservation) {

    public static VerrouTransmissionResponse de(Long idProcessus,
            ResultatVerrouTransmission resultat) {
        return new VerrouTransmissionResponse(
                idProcessus,
                resultat.resultat(),
                resultat.message(),
                resultat.transmisComptabilite(),
                resultat.statutIntegration(),
                resultat.dateReservation());
    }

}
