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
     * Grilles ACTIVE couvrant le couple (nature, session) a la date fournie.
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
     * pas, ce filtre renvoie <b>au plus une ligne</b>. Le non-chevauchement est
     * garanti cote ecriture au Sprint 2.3 : la {@code dateFin} de l'ancienne
     * grille est posee a la veille de la {@code dateDebut} de la remplacante.
     *
     * <p><b>Pourquoi une liste, et non un {@code Optional}</b> (Sprint 2.4). Un
     * {@code Optional} traduirait « deux grilles se chevauchent » en la meme
     * chose que « tout va bien » — il ne distingue que present et absent. Or
     * c'est precisement le nombre de lignes que la resolution du montant doit
     * connaitre : zero est une reponse d'indisponibilite legitime, une est le
     * cas nominal, deux est une incoherence de donnees qui doit etre refusee et
     * signalee, jamais arbitree en silence
     * ({@code ResolutionMontantService}). Renvoyer la liste laisse l'appelant
     * trancher ces trois cas ; un {@code Optional} lui en cacherait un.
     *
     * <p>Tri par {@code dateDebut} decroissante, pour que le message d'incoherence
     * puisse nommer les grilles en conflit dans un ordre stable.
     */
    @Query("""
            select g from GrilleTarifaire g
            where g.nature = :nature
              and g.session = :session
              and g.statutValidation = cm.afrilandfirstbank.rations.grilles.domaine.StatutGrilleEnum.ACTIVE
              and g.dateDebut <= :date
              and (g.dateFin is null or g.dateFin >= :date)
            order by g.dateDebut desc, g.id desc
            """)
    List<GrilleTarifaire> rechercherGrillesCouvrant(@Param("nature") NatureEnum nature,
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
     * Grille COURANTE du couple : ACTIVE et sans date de fin.
     *
     * <p>Distincte de {@link #rechercherGrillesCouvrant} : celle-ci demande « quelle
     * grille s'applique a telle date », celle-la demande « quelle grille est en
     * vigueur, sans terme pose ». Les deux different des qu'une grille a ete
     * fermee : une grille close reste ACTIVE, mais n'est plus courante.
     *
     * <p>Retourne au plus une ligne, garanti par l'index partiel
     * {@code ux_grille_active_par_couple}
     * ({@code WHERE statut_validation = 'ACTIVE' AND date_fin IS NULL}).
     *
     * <p>Sert au controle d'unicite du Sprint 2.2 : une nouvelle grille doit
     * debuter STRICTEMENT APRES la grille en vigueur. Comparer a
     * {@code rechercherGrillesCouvrant(date)} ne suffirait pas — une proposition
     * anti-datee avant le debut de la grille en vigueur ne serait couverte par
     * aucune grille a sa propre date de debut, donc passerait inapercue, alors
     * qu'elle reecrirait une periode deja servie.
     */
    @Query("""
            select g from GrilleTarifaire g
            where g.nature = :nature
              and g.session = :session
              and g.statutValidation = cm.afrilandfirstbank.rations.grilles.domaine.StatutGrilleEnum.ACTIVE
              and g.dateFin is null
            """)
    Optional<GrilleTarifaire> rechercherGrilleCourante(@Param("nature") NatureEnum nature,
                                                       @Param("session") SessionEnum session);

    /**
     * Propositions du couple en attente de la decision DRH.
     *
     * <p>Retourne une liste, non un {@code Optional}, alors que le controle
     * d'unicite du Sprint 2.2 garantit qu'il n'y en a jamais plus d'une : aucune
     * contrainte de base ne l'impose (l'index partiel ne porte que sur les
     * grilles ACTIVE). Une requete typee {@code Optional} echouerait alors sur une
     * exception technique si l'invariant venait a etre viole — par une reprise de
     * donnees, par exemple. Mieux vaut lire la situation que planter dessus.
     */
    @Query("""
            select g from GrilleTarifaire g
            where g.nature = :nature
              and g.session = :session
              and g.statutValidation = cm.afrilandfirstbank.rations.grilles.domaine.StatutGrilleEnum.EN_ATTENTE_DRH
            order by g.id asc
            """)
    List<GrilleTarifaire> rechercherPropositionsEnAttente(@Param("nature") NatureEnum nature,
                                                          @Param("session") SessionEnum session);

    /**
     * Liste paginee, filtre optionnel sur le statut : {@code statut} nul renvoie
     * toutes les grilles, sinon celles du statut demande.
     */
    @Query("select g from GrilleTarifaire g where (:statut is null or g.statutValidation = :statut)")
    Page<GrilleTarifaire> rechercherParStatut(@Param("statut") StatutGrilleEnum statut, Pageable pageable);

}
