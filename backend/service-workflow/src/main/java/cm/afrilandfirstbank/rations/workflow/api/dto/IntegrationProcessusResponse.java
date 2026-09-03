package cm.afrilandfirstbank.rations.workflow.api.dto;

import java.time.LocalDateTime;

import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutIntegrationEnum;

/**
 * Corps de {@code GET /processus/{id}/integration}, l'endpoint <b>interne</b> qui sert la
 * consultation du statut d'integration comptable (Sprint 5.3, contrat d'API section 7).
 *
 * <h2>Six champs, et pas un de plus — c'est le point de cet endpoint</h2>
 *
 * <p>Le contrat ouvre {@code GET /transmission/processus/{id}} aux « roles ARH et
 * circuit », alors que {@code GET /processus/{id}} est reserve aux seuls roles du circuit.
 * La solution paresseuse aurait ete d'ajouter l'ARH aux roles de ce dernier. Elle a ete
 * ecartee : cet endpoint rend le dossier complet — montant total, motif du retour en
 * cours, type, periode — et l'ARH ayant une portee <b>nationale</b> (Sprint 1.1), elle
 * aurait obtenu la lecture integrale de tous les dossiers de toutes les unites pour un
 * besoin qui n'en demandait que quatre champs.
 *
 * <p>Le module a deja tranche ainsi ailleurs : {@code GET /identite/habilitation}
 * (Sprint 1.3) ne rend pas un profil, il repond a une question precise. Le suivi complet
 * releve du service Reporting (Sprint 6), lui aussi ouvert a l'ARH — et deux chemins
 * d'acces au meme dossier, l'un par Workflow, l'autre par Reporting, finiraient par
 * diverger.
 *
 * @param idProcessus l'etat concerne
 * @param transmisComptabilite le module a-t-il publie cet etat vers la comptabilite ?
 * @param statutIntegration suite donnee par la comptabilite. <b>Nul avec un drapeau a
 *        vrai</b> signifie « publication non confirmee » ; nul avec un drapeau a faux,
 *        « jamais transmis »
 * @param referenceComptable reference produite par le module de comptabilisation, jamais
 *        fabriquee ici (CLAUDE.md section 8)
 * @param dateTraitement horodatage du traitement comptable, declare par l'accuse
 * @param motifIntegration motif accompagnant un accuse de rejet
 * @param dateReservationTransmission instant de la reservation : son anciennete distingue
 *        un etat en transit normal d'une publication d'issue incertaine
 */
public record IntegrationProcessusResponse(
        Long idProcessus,
        boolean transmisComptabilite,
        StatutIntegrationEnum statutIntegration,
        String referenceComptable,
        LocalDateTime dateTraitement,
        String motifIntegration,
        LocalDateTime dateReservationTransmission) {

    public static IntegrationProcessusResponse depuis(ProcessusMensuel processus) {
        return new IntegrationProcessusResponse(
                processus.getId(),
                processus.isTransmisComptabilite(),
                processus.getStatutIntegration(),
                processus.getReferenceComptable(),
                processus.getDateTraitement(),
                processus.getMotifIntegration(),
                processus.getDateReservationTransmission());
    }

}
