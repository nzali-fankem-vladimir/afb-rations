package cm.afrilandfirstbank.rations.workflow.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cm.afrilandfirstbank.rations.workflow.domaine.EtapeWorkflow;

/**
 * Acces aux etapes du circuit de validation.
 *
 * <p>Aucune ecriture d'etape au Sprint 4.1 : ces recherches servent les
 * sous-sprints 4.2 a 4.4 (parcours d'un etat, dernier pas atteint, controle de
 * separation des taches RG-12).
 */
public interface EtapeWorkflowRepository extends JpaRepository<EtapeWorkflow, Long> {

    /** Parcours complet d'un etat, du premier pas au dernier. */
    List<EtapeWorkflow> findByIdProcessusOrderByOrdreEtape(Long idProcessus);

    /**
     * Dernier pas connu d'un etat — celui qui dit ou en est le circuit. Vide si
     * aucun pas n'a encore ete ouvert.
     */
    Optional<EtapeWorkflow> findFirstByIdProcessusOrderByOrdreEtapeDesc(Long idProcessus);

    /**
     * Etapes deja realisees par un acteur donne sur un processus.
     *
     * <p>Portera RG-12 au sous-sprint 4.4 : un meme utilisateur ne peut pas
     * cumuler la saisie et une validation, ni valider a deux niveaux, sur un meme
     * dossier. Une liste non vide ici signale que l'acteur a deja agi.
     */
    List<EtapeWorkflow> findByIdProcessusAndIdActeur(Long idProcessus, Long idActeur);

}
