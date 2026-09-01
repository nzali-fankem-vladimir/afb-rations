package cm.afrilandfirstbank.rations.workflow.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cm.afrilandfirstbank.rations.workflow.domaine.EtapeWorkflow;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEtapeEnum;

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
     * Dernier retour prononce sur un etat — l'etape qui porte le motif que l'agent
     * doit lire (US-11, RG-10).
     *
     * <p>Le dernier, et non le premier : un etat peut avoir ete retourne plusieurs
     * fois, et c'est la derniere correction demandee qui est d'actualite.
     */
    Optional<EtapeWorkflow> findFirstByIdProcessusAndStatutEtapeOrderByOrdreEtapeDesc(
            Long idProcessus, StatutEtapeEnum statutEtape);

    /**
     * Etapes deja realisees par un acteur donne sur un processus.
     *
     * <p><b>Non utilisee par RG-12</b>, contrairement a ce qu'annoncait le Sprint
     * 4.1. Le controle de separation des taches ne porte que sur le <i>cycle
     * courant</i> — les etapes posterieures a la derniere soumission — et doit donc
     * voir tout le parcours, y compris les etapes des autres acteurs, pour situer
     * cette soumission ({@code CycleValidation}). Une recherche par acteur ne le
     * permet pas. Conservee : elle repond a une autre question, « qu'a fait cette
     * personne sur ce dossier », qui interessera le reporting du Sprint 6.
     */
    List<EtapeWorkflow> findByIdProcessusAndIdActeur(Long idProcessus, Long idActeur);

}
