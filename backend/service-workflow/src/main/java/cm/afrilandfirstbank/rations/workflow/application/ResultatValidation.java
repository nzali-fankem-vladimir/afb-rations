package cm.afrilandfirstbank.rations.workflow.application;

import cm.afrilandfirstbank.rations.workflow.domaine.EtapeWorkflow;
import cm.afrilandfirstbank.rations.workflow.domaine.NiveauValidation;
import cm.afrilandfirstbank.rations.workflow.domaine.PieceJointe;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;

/**
 * Ce qu'une validation a produit, rendu d'un seul tenant.
 *
 * <p>Meme parti que {@link ResultatSoumission} au Sprint 4.2 : le controleur ne
 * doit pas avoir a relire la base pour composer sa reponse, et le chef d'unite doit
 * pouvoir constater les trois effets de son geste — l'etat a change de statut, une
 * etape est enregistree, et le document porte une signature de plus.
 *
 * @param processus l'etat, desormais {@code CLOTURE} ou {@code EN_ATTENTE_DR}
 * @param pieceJointe le document, a deux signatures apres la validation du DA
 * @param etape le pas {@code VALIDATION_DA}, {@code VALIDEE} et signe
 * @param aiguillage la decision RG-08, avec le montant et le seuil qui l'ont
 *        produite. <b>C'est elle qui justifie le statut</b> : sans elle, la reponse
 *        dirait ce qui s'est passe sans dire pourquoi
 * @param transmission ce que la mise a disposition comptable a donne (Sprint 5.1).
 *        <b>Nul quand la validation ne cloture pas</b> — un etat aiguille vers le
 *        directeur reseau n'a rien a transmettre —, renseigne aux deux points ou la
 *        cloture survient. Champ ajoute <b>en fin de record</b>, comme {@code motifRetour}
 *        au Sprint 4.4 : les cinq champs anterieurs gardent leur nom, leur type et leur
 *        ordre
 */
public record ResultatValidation(
        ProcessusMensuel processus,
        PieceJointe pieceJointe,
        EtapeWorkflow etape,
        NiveauValidation niveau,
        /** Nul au second niveau : aucun aiguillage n'a lieu apres le visa du DR. */
        ResultatAiguillage aiguillage,
        /** Nul quand la validation ne cloture pas l'etat. */
        ResultatTransmissionCloture transmission) {

    /**
     * Le resultat tel que l'enregistrement le produit : la transmission n'a pas encore eu
     * lieu, puisqu'elle suit le commit.
     */
    public ResultatValidation(ProcessusMensuel processus, PieceJointe pieceJointe,
            EtapeWorkflow etape, NiveauValidation niveau, ResultatAiguillage aiguillage) {
        this(processus, pieceJointe, etape, niveau, aiguillage, null);
    }

    /**
     * Le meme resultat, complete de ce que la transmission a donne.
     *
     * <p>Recopie plutot que mutation : le resultat de l'enregistrement reste ce qu'il etait
     * au moment du commit, et l'on ne peut pas lui attribuer apres coup une transmission
     * qui aurait echoue.
     */
    public ResultatValidation avecTransmission(ResultatTransmissionCloture transmission) {
        return new ResultatValidation(
                processus, pieceJointe, etape, niveau, aiguillage, transmission);
    }

}
