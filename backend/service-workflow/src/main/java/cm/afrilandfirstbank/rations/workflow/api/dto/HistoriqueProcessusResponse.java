package cm.afrilandfirstbank.rations.workflow.api.dto;

import java.time.LocalDateTime;
import java.util.List;

import cm.afrilandfirstbank.rations.workflow.application.HistoriqueProcessus;
import cm.afrilandfirstbank.rations.workflow.domaine.EtapeWorkflow;
import cm.afrilandfirstbank.rations.workflow.domaine.NomEtapeEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEtapeEnum;

/**
 * Reponse de l'endpoint <b>interne</b> {@code GET /processus/{id}/historique}
 * (Sprint 6.1, CT-31).
 *
 * <h2>Toutes les etapes, y compris les passages repetes au meme niveau</h2>
 *
 * <p>Un dossier retourne puis resoumis repasse par les memes niveaux. Les etapes
 * sont rendues <b>dans l'ordre de leur rang</b>, sans regroupement ni ecrasement :
 * un historique qui n'afficherait que le dernier passage a chaque niveau cacherait
 * precisement le refus et sa correction, c'est-a-dire ce que le controle interne
 * vient chercher.
 *
 * <p>{@code idActeur} est rendu brut. C'est au service Reporting de le traduire en
 * login, par {@code GET /identite/utilisateurs/libelles} : le service Workflow ne
 * connait pas l'annuaire, et ouvrir un appel a Identite par etape serait exactement
 * le travers que l'agregation du Sprint 6.1 evite.
 */
public record HistoriqueProcessusResponse(
        Long idProcessus,
        Integer moisPaiement,
        Integer anneePaiement,
        String codeUnite,
        String statut,
        List<EtapeHistoriqueDto> etapes) {

    public static HistoriqueProcessusResponse depuis(HistoriqueProcessus historique) {
        return new HistoriqueProcessusResponse(
                historique.processus().getId(),
                historique.processus().getMoisPaiement(),
                historique.processus().getAnneePaiement(),
                historique.processus().getCodeUnite(),
                historique.processus().getStatut().name(),
                historique.etapes().stream().map(EtapeHistoriqueDto::depuis).toList());
    }

    /**
     * Une etape du circuit.
     *
     * @param ordreEtape rang, calcule {@code dernier + 1} depuis le Sprint 4.4 — c'est
     *        lui qui distingue deux passages au meme niveau
     * @param motifRetour renseigne pour les seules etapes {@code RETOURNEE} (RG-10)
     * @param signee l'etape porte-t-elle une signature (RG-09) ; l'empreinte elle-meme
     *        ne sort pas du service, elle n'aurait aucun sens hors du fichier qu'elle
     *        scelle et un historique n'est pas un outil de verification d'integrite
     */
    public record EtapeHistoriqueDto(
            int ordreEtape,
            NomEtapeEnum nomEtape,
            StatutEtapeEnum statutEtape,
            Long idActeur,
            String motifRetour,
            boolean signee,
            LocalDateTime dateAction) {

        public static EtapeHistoriqueDto depuis(EtapeWorkflow etape) {
            return new EtapeHistoriqueDto(
                    etape.getOrdreEtape(),
                    etape.getNomEtape(),
                    etape.getStatutEtape(),
                    etape.getIdActeur(),
                    etape.getMotifRetour(),
                    etape.getSignatureNumerique() != null && !etape.getSignatureNumerique().isBlank(),
                    etape.getDateCreation());
        }

    }

}
