package cm.afrilandfirstbank.rations.workflow.api.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import cm.afrilandfirstbank.rations.workflow.application.ResultatSoumission;
import cm.afrilandfirstbank.rations.workflow.domaine.EtapeWorkflow;
import cm.afrilandfirstbank.rations.workflow.domaine.PieceJointe;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;

/**
 * Reponse a {@code POST /processus/{id}/soumission} (US-07, CT-12).
 *
 * <p>Elle temoigne des <b>trois</b> effets du geste, que CT-12 demande de
 * verifier : l'etat a change de main, un document existe, et il porte la
 * signature de l'agent. Rendre le seul statut laisserait l'agent sans preuve que
 * sa piece justificative a bien ete produite.
 *
 * @param montantTotalFcfa <b>le montant desormais porte par le processus</b>,
 *        recopie de l'etat consolide. Contrairement a {@code GET /processus/{id}/etat},
 *        cette reponse n'en porte qu'un seul : apres soumission, l'ecriture est
 *        fermee cote Saisie, et le montant calcule ne peut plus differer du
 *        montant enregistre (decision Sprint 4.1 section 6)
 */
public record SoumissionResponse(
        Long idProcessus,
        String statut,
        String codeUnite,
        LocalDate dateDebut,
        LocalDate dateFin,
        int montantTotalFcfa,
        PieceJointeResponse pieceJointe,
        EtapeResponse etape) {

    /**
     * Le document produit.
     *
     * @param cheminFichier chemin <b>relatif</b> a la racine de stockage. La racine
     *        est une donnee de configuration et ne sort jamais du service : la
     *        divulguer exposerait l'arborescence du serveur sans rien apporter
     * @param nombreSignatures signatures reellement ecrites dans le fichier. Vaut
     *        {@code 1} apres la soumission
     */
    public record PieceJointeResponse(
            Long id,
            String cheminFichier,
            String typeMime,
            int nombreSignatures,
            LocalDateTime dateCreation) {

        static PieceJointeResponse depuis(PieceJointe pieceJointe) {
            return new PieceJointeResponse(
                    pieceJointe.getId(),
                    pieceJointe.getCheminFichier(),
                    pieceJointe.getTypeMime(),
                    pieceJointe.getNombreSignatures(),
                    pieceJointe.getDateCreation());
        }
    }

    /**
     * Le pas de circuit enregistre.
     *
     * @param signatureNumerique empreinte du document au moment de la signature.
     *        Rendue pour que l'agent, ou un controle interne, puisse recouper la
     *        piece archivee sans passer par la base
     */
    public record EtapeResponse(
            Long id,
            int ordreEtape,
            String nomEtape,
            String statutEtape,
            String signatureNumerique,
            LocalDateTime dateCreation) {

        static EtapeResponse depuis(EtapeWorkflow etape) {
            return new EtapeResponse(
                    etape.getId(),
                    etape.getOrdreEtape(),
                    String.valueOf(etape.getNomEtape()),
                    String.valueOf(etape.getStatutEtape()),
                    etape.getSignatureNumerique(),
                    etape.getDateCreation());
        }
    }

    public static SoumissionResponse depuis(ResultatSoumission resultat) {
        ProcessusMensuel processus = resultat.processus();
        return new SoumissionResponse(
                processus.getId(),
                String.valueOf(processus.getStatut()),
                processus.getCodeUnite(),
                processus.getDateDebut(),
                processus.getDateFin(),
                processus.getMontantTotal(),
                PieceJointeResponse.depuis(resultat.pieceJointe()),
                EtapeResponse.depuis(resultat.etape()));
    }

}
