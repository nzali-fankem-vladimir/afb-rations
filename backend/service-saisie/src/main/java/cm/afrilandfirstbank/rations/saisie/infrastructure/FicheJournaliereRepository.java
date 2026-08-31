package cm.afrilandfirstbank.rations.saisie.infrastructure;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cm.afrilandfirstbank.rations.saisie.domaine.FicheJournaliere;

/**
 * Accès aux fiches journalières (base {@code rations_saisie}).
 */
@Repository
public interface FicheJournaliereRepository extends JpaRepository<FicheJournaliere, Long> {

    /**
     * Fiche d'un jour donné au sein d'un processus mensuel.
     *
     * <p>Porte <b>RG-05</b> (fiche vierge par jour) : avant d'ouvrir une fiche, on
     * vérifie ici s'il en existe déjà une pour ce couple. L'unicité
     * {@code (id_processus, date_jour)} en base garantit qu'il n'y en a jamais
     * plus d'une — d'où {@code Optional}.
     */
    Optional<FicheJournaliere> findByIdProcessusAndDateJour(Long idProcessus, LocalDate dateJour);

    /**
     * Toutes les fiches d'un processus mensuel, tous jours confondus. Base de la
     * consolidation mensuelle par unité (RG-06, sous-sprint 3.4). Tri laissé à
     * l'appelant.
     */
    List<FicheJournaliere> findByIdProcessus(Long idProcessus);

}
