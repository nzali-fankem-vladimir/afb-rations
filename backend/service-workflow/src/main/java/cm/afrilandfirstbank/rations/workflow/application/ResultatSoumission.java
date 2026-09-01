package cm.afrilandfirstbank.rations.workflow.application;

import cm.afrilandfirstbank.rations.workflow.domaine.EtapeWorkflow;
import cm.afrilandfirstbank.rations.workflow.domaine.PieceJointe;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;

/**
 * Ce qu'une soumission a produit : les trois ecritures, rendues ensemble.
 *
 * <p>Rendre le seul processus obligerait le controleur a relire la piece jointe
 * pour composer sa reponse, donc a poser une question a la base dont il connait
 * deja la reponse. Surtout, la reponse rendue a l'agent doit temoigner des
 * <b>trois</b> effets de son geste : l'etat a change de main, un document existe,
 * et il porte sa signature. C'est ce que CT-12 demande de verifier.
 *
 * @param processus l'etat, desormais {@code EN_ATTENTE_DA}, montant total reporte
 * @param pieceJointe le document, a une signature
 * @param etape le pas {@code SOUMISSION_AGENT}, {@code VALIDEE} et signe
 */
public record ResultatSoumission(
        ProcessusMensuel processus,
        PieceJointe pieceJointe,
        EtapeWorkflow etape) {
}
