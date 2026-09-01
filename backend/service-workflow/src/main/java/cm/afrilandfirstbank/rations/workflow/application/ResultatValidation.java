package cm.afrilandfirstbank.rations.workflow.application;

import cm.afrilandfirstbank.rations.workflow.domaine.EtapeWorkflow;
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
 */
public record ResultatValidation(
        ProcessusMensuel processus,
        PieceJointe pieceJointe,
        EtapeWorkflow etape,
        ResultatAiguillage aiguillage) {
}
