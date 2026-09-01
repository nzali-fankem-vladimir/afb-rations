package cm.afrilandfirstbank.rations.workflow.api.dto;

import java.time.LocalDateTime;

import cm.afrilandfirstbank.rations.workflow.application.ResultatAiguillage;
import cm.afrilandfirstbank.rations.workflow.application.ResultatValidation;
import cm.afrilandfirstbank.rations.workflow.domaine.EtapeWorkflow;
import cm.afrilandfirstbank.rations.workflow.domaine.PieceJointe;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;

/**
 * Reponse a {@code POST /processus/{id}/validation} (US-08, US-09, CT-14, CT-15).
 *
 * <h2>Les cinq champs du contrat, aux memes noms et aux memes types</h2>
 *
 * <p>{@code idProcessus}, {@code statut}, {@code montantTotal}, {@code aiguillage}
 * et {@code seuilApplique} sont exactement ceux de l'exemple du contrat d'API
 * section 5. Deux blocs de temoignage s'y ajoutent — {@code pieceJointe} et
 * {@code etape} —, sur le modele additif du champ {@code manques} au Sprint 4.2 :
 * les champs du contrat restent presents et inchanges, l'ajout ne retire rien.
 *
 * <h2>Pourquoi la reponse rend le seuil</h2>
 *
 * <p>Le contrat l'exige, et pour une raison de fond : le chef d'unite doit voir
 * <b>sur quelle regle</b> son dossier a ete aiguille. Rendre le seul statut lui
 * laisserait constater qu'un etat monte au directeur reseau sans pouvoir savoir si
 * c'est parce que le seuil a change ou parce que son montant a bouge — et il
 * n'aurait, pour trancher, qu'a interroger un administrateur.
 *
 * @param aiguillage {@code SOUS_SEUIL_CLOTURE_DIRECTE} ou
 *        {@code ENVOI_DIRECTEUR_RESEAU}
 * @param seuilApplique la valeur lue dans {@code parametre_systeme} <b>pour cette
 *        decision</b>, telle qu'elle etait a cet instant
 */
public record ValidationResponse(
        Long idProcessus,
        String statut,
        int montantTotal,
        String aiguillage,
        Long seuilApplique,
        PieceJointeResponse pieceJointe,
        EtapeResponse etape) {

    /**
     * Le document, apres apposition du visa.
     *
     * @param nombreSignatures signatures <b>reellement ecrites dans le fichier</b>.
     *        Vaut {@code 2} apres la validation du chef d'unite : le document est
     *        enrichi, jamais regenere, et le compteur ne repart donc jamais a un
     */
    public record PieceJointeResponse(
            Long id,
            String cheminFichier,
            int nombreSignatures,
            LocalDateTime dateDerniereModification) {

        static PieceJointeResponse depuis(PieceJointe pieceJointe) {
            return new PieceJointeResponse(
                    pieceJointe.getId(),
                    pieceJointe.getCheminFichier(),
                    pieceJointe.getNombreSignatures(),
                    pieceJointe.getDateDerniereModification());
        }
    }

    /** Le pas de circuit enregistre : {@code VALIDATION_DA}, {@code VALIDEE}, signe. */
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

    /**
     * <p><b>{@code aiguillage} et {@code seuilApplique} sont nuls au second niveau.</b>
     * Apres le visa du directeur reseau il n'y a plus d'echelon : aucun aiguillage
     * n'a lieu, et le seuil n'est meme pas lu. Les deux champs restent presents au
     * meme nom et au meme type — les rendre absents obligerait le frontend a
     * distinguer « champ absent » de « champ nul » — mais ils ne portent aucune
     * valeur, parce qu'il n'y a rien a rapporter. Inventer une troisieme valeur
     * d'aiguillage pour la cloture de second niveau aurait fait croire a une
     * comparaison montant / seuil qui n'a pas eu lieu.
     */
    public static ValidationResponse depuis(ResultatValidation resultat) {
        ProcessusMensuel processus = resultat.processus();
        ResultatAiguillage aiguillage = resultat.aiguillage();

        return new ValidationResponse(
                processus.getId(),
                String.valueOf(processus.getStatut()),
                processus.getMontantTotal(),
                aiguillage == null ? null : String.valueOf(aiguillage.decision()),
                aiguillage == null ? null : aiguillage.seuilApplique(),
                PieceJointeResponse.depuis(resultat.pieceJointe()),
                EtapeResponse.depuis(resultat.etape()));
    }

}
