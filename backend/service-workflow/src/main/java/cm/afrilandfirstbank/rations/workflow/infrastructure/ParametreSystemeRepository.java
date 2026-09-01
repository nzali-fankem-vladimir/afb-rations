package cm.afrilandfirstbank.rations.workflow.infrastructure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cm.afrilandfirstbank.rations.workflow.domaine.ParametreSysteme;

/**
 * Acces aux parametres systeme (seuil d'aiguillage RG-08, drapeaux de
 * fonctionnalite).
 */
public interface ParametreSystemeRepository extends JpaRepository<ParametreSysteme, Long> {

    /**
     * Parametre actif portant ce code. Un parametre desactive
     * ({@code actif = false}) est ignore : c'est le moyen de retirer un reglage
     * sans supprimer sa ligne ni son historique.
     */
    Optional<ParametreSysteme> findByCodeAndActifTrue(String code);

}
