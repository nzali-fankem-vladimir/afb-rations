package cm.afrilandfirstbank.rations.workflow.application;

import java.time.LocalDateTime;

import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutIntegrationEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.TransitionIntegration.Decision;

/**
 * Ce que le service a fait de l'accuse, et l'etat d'integration qui en resulte
 * (Sprint 5.2).
 *
 * <p>Les valeurs sont <b>recopiees</b> a la sortie de la transaction plutot que de laisser
 * remonter l'entite : le controleur lit alors une photographie du moment ou la decision a
 * ete prise, et non un objet dont l'etat pourrait avoir change depuis. Meme parti qu'au
 * Sprint 4.3 pour {@code ResultatValidation}.
 *
 * @param decision {@link Decision.Appliquer} ou {@link Decision.DejaApplique} — les deux
 *        autres issues de la table sont des refus et sortent en exception
 */
public record ResultatIntegrationComptable(
        Long idProcessus,
        Decision decision,
        StatutIntegrationEnum statutIntegration,
        String referenceComptable,
        LocalDateTime dateTraitement,
        String motifIntegration) {

    public static ResultatIntegrationComptable de(ProcessusMensuel processus, Decision decision) {
        return new ResultatIntegrationComptable(
                processus.getId(),
                decision,
                processus.getStatutIntegration(),
                processus.getReferenceComptable(),
                processus.getDateTraitement(),
                processus.getMotifIntegration());
    }

    /** Vrai quand une ecriture a reellement eu lieu — donc quand une trace est due. */
    public boolean aEcrit() {
        return decision instanceof Decision.Appliquer;
    }

}
