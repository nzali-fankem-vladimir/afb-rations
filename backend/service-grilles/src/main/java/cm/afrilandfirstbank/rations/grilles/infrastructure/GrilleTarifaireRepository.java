package cm.afrilandfirstbank.rations.grilles.infrastructure;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cm.afrilandfirstbank.rations.grilles.domaine.GrilleTarifaire;
import cm.afrilandfirstbank.rations.grilles.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.SessionEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.StatutGrilleEnum;

/**
 * Acces aux grilles tarifaires (base rations_grilles).
 *
 * <p>Porte les recherches dont les sous-sprints suivants auront besoin :
 * <ul>
 *   <li>liste par statut, pour les ecrans de suivi ARH / DRH (2.2, 2.3) ;</li>
 *   <li>resolution de la grille active a une date donnee, piece maitresse de
 *       RG-03 au sous-sprint 2.4 ;</li>
 *   <li>controle d'existence d'une grille active sur un couple, garde-fou de
 *       RG-14 au sous-sprint 2.2 ;</li>
 *   <li>liste paginee a filtre optionnel sur le statut, format de pagination de
 *       reference du projet ({@code PageResponse}, decision Sprint 1.2).</li>
 * </ul>
 */
@Repository
public interface GrilleTarifaireRepository extends JpaRepository<GrilleTarifaire, Long> {

    /** Toutes les grilles d'un statut donne. Tri laisse a l'appelant. */
    List<GrilleTarifaire> findByStatutValidation(StatutGrilleEnum statut);

    /**
     * Grille ACTIVE couvrant le couple (nature, session) a la date fournie.
     *
     * <p>Bornes de dates, relues et validees au Sprint 2.1 (les deux inclusives) :
     * <ul>
     *   <li>{@code dateDebut <= date} : la grille s'applique des son premier jour
     *       de validite ;</li>
     *   <li>{@code dateFin is null} : grille courante, PAS grille expiree — une
     *       grille sans remplacante reste valide indefiniment ;</li>
     *   <li>{@code dateFin >= date} : la grille s'applique encore son dernier jour
     *       de validite.</li>
     * </ul>
     *
     * <p>Tant que les periodes de deux grilles d'un meme couple ne se chevauchent
     * pas, ce filtre renvoie au plus une ligne. Le non-chevauchement est garanti
     * cote ecriture au Sprint 2.3 : la {@code dateFin} de l'ancienne grille sera
     * posee a la veille de la {@code dateDebut} de la remplacante (question de
     * bornage explicitement tranchee dans ce sous-sprint 2.3).
     */
    @Query("""
            select g from GrilleTarifaire g
            where g.nature = :nature
              and g.session = :session
              and g.statutValidation = cm.afrilandfirstbank.rations.grilles.domaine.StatutGrilleEnum.ACTIVE
              and g.dateDebut <= :date
              and (g.dateFin is null or g.dateFin >= :date)
            """)
    Optional<GrilleTarifaire> rechercherGrilleActive(@Param("nature") NatureEnum nature,
                                                     @Param("session") SessionEnum session,
                                                     @Param("date") LocalDate date);

    /**
     * Vrai s'il existe une grille ACTIVE pour ce couple (nature, session), quelle
     * que soit la date. Sert au controle d'unicite de RG-14 (Sprint 2.2) : jamais
     * deux grilles actives sur un meme couple.
     */
    boolean existsByNatureAndSessionAndStatutValidation(NatureEnum nature,
                                                        SessionEnum session,
                                                        StatutGrilleEnum statut);

    /**
     * Liste paginee, filtre optionnel sur le statut : {@code statut} nul renvoie
     * toutes les grilles, sinon celles du statut demande.
     */
    @Query("select g from GrilleTarifaire g where (:statut is null or g.statutValidation = :statut)")
    Page<GrilleTarifaire> rechercherParStatut(@Param("statut") StatutGrilleEnum statut, Pageable pageable);

}
