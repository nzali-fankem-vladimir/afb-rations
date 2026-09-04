package cm.afrilandfirstbank.rations.workflow.application;

import java.util.List;

import cm.afrilandfirstbank.rations.workflow.domaine.EtapeWorkflow;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;

/**
 * L'historique complet d'un dossier : son en-tete et la suite de ses etapes, dans
 * l'ordre (Sprint 6.1, CT-31).
 *
 * <p>L'en-tete voyage avec les etapes parce que le lecteur en a besoin pour situer
 * le dossier — periode, unite, statut atteint — et que le rechercher par un second
 * appel serait un aller-retour pour une donnee deja chargee ici.
 */
public record HistoriqueProcessus(ProcessusMensuel processus, List<EtapeWorkflow> etapes) {

    public HistoriqueProcessus {
        etapes = List.copyOf(etapes);
    }

}
