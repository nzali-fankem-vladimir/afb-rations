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

    /**
     * Parametre portant ce code, <b>actif ou non</b> (Sprint 6bis.1).
     *
     * <p>Elle ne sert pas a lire une valeur — {@link #findByCodeAndActifTrue(String)}
     * reste la seule lecture qui autorise quoi que ce soit. Elle sert a <b>distinguer
     * deux silences</b> : une ligne absente de la table, et une ligne presente mais
     * desactivee. Les deux ferment une fonctionnalite ; seule la premiere signale que
     * quelque chose a disparu de la base. Sans cette distinction, une suppression
     * accidentelle eteindrait la regularisation pour toujours, sans qu'aucun journal
     * ne s'en apercoive ({@code FonctionnaliteService}).
     */
    Optional<ParametreSysteme> findByCode(String code);

}
