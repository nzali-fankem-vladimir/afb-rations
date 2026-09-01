package cm.afrilandfirstbank.rations.workflow.infrastructure;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.TypeProcessusEnum;

/**
 * Acces aux processus mensuels.
 *
 * <p>{@code findById}, {@code save} et consorts viennent de {@link JpaRepository} :
 * le detail d'un processus (endpoint {@code GET /processus/{id}}) se sert
 * directement de {@code findById}.
 */
public interface ProcessusMensuelRepository extends JpaRepository<ProcessusMensuel, Long> {

    /**
     * Cherche le processus d'un type donne pour un couple unite / periode.
     *
     * <p>Sert au controle d'unicite de {@code POST /processus} : avant de creer un
     * {@link TypeProcessusEnum#NORMAL}, on verifie qu'aucun n'existe deja pour
     * cette unite et cette periode. L'index partiel {@code ux_processus_normal_par_periode}
     * (migration V1) garantit qu'il y en a au plus un — d'ou l'{@link Optional} —,
     * mais le refus, avec un message comprehensible nommant le processus existant,
     * doit venir du service (point de vigilance du guide 4.1).
     *
     * <p>Le type est un parametre : plusieurs {@link TypeProcessusEnum#COMPLEMENTAIRE}
     * peuvent partager le meme couple, ce que cette methode n'a pas a decider.
     */
    Optional<ProcessusMensuel> findByCodeUniteAndMoisPaiementAndAnneePaiementAndTypeProcessus(
            String codeUnite, Integer moisPaiement, Integer anneePaiement, TypeProcessusEnum typeProcessus);

    /**
     * Liste paginee des processus d'une unite dans un statut donne — suivi du
     * circuit cote Chef d'Unite ou Directeur Reseau.
     */
    Page<ProcessusMensuel> findByStatutAndCodeUnite(StatutEnum statut, String codeUnite, Pageable pagination);

}
