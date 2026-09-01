package cm.afrilandfirstbank.rations.workflow.application;

import cm.afrilandfirstbank.rations.workflow.domaine.EtapeWorkflow;
import cm.afrilandfirstbank.rations.workflow.domaine.NiveauValidation;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;

/**
 * Ce qu'un retour laisse derriere lui : le processus passe a {@code RETOURNE},
 * l'etape {@code RETOURNEE} qui porte le motif, et le niveau depuis lequel le
 * retour a ete fait.
 *
 * <p>Aucune piece jointe : un retour n'estampe pas le document (RG-09 ne vaut que
 * pour les validations), et le fichier sera regenere a la resoumission.
 */
public record ResultatRetour(
        ProcessusMensuel processus,
        EtapeWorkflow etape,
        NiveauValidation niveauOrigine) {
}
