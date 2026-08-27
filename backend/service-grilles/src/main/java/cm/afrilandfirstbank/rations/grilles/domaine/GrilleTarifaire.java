package cm.afrilandfirstbank.rations.grilles.domaine;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Grille tarifaire : montant applicable a une prestation pour un couple
 * (nature, session), sur une periode de validite (CLAUDE.md section 4, RG-14).
 *
 * <p>La table {@code grille_tarifaire} et son index partiel existent en base
 * depuis le Sprint 0.5 ; cette entite s'y conforme, elle ne le pilote pas
 * (Hibernate en {@code ddl-auto: validate}). Les colonnes {@code date_validation}
 * et {@code motif_rejet} sont ajoutees par la migration additive {@code V3} du
 * Sprint 2.1.
 *
 * <p>Le cycle de statuts (BROUILLON, EN_ATTENTE_DRH, ACTIVE, REJETEE) est decrit
 * par le diagramme ET02. Cette entite porte l'etat d'UNE grille prise isolement ;
 * les transitions autorisees et interdites sont arbitrees par
 * {@link TransitionGrille}. La fermeture de l'ancienne grille lors d'une
 * activation (operation a deux lignes, transactionnelle) releve du service de
 * validation au Sprint 2.3, pas de cette classe.
 *
 * <p>{@code id_createur} (ARH) et {@code id_validateur} (DRH) referencent la table
 * {@code utilisateurs}, qui vit dans une autre base : identifiants simples, sans
 * cle etrangere inter-base.
 */
@Entity
@Table(name = "grille_tarifaire")
public class GrilleTarifaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "nature", nullable = false, length = 20)
    private NatureEnum nature;

    @Enumerated(EnumType.STRING)
    @Column(name = "session", nullable = false, length = 10)
    private SessionEnum session;

    /** Montant en FCFA, entier sans decimale (CLAUDE.md section 4). */
    @Column(name = "montant_fcfa", nullable = false)
    private Integer montantFcfa;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_validation", nullable = false, length = 20)
    private StatutGrilleEnum statutValidation;

    /** ARH auteur de la grille. Identifiant simple vers {@code utilisateurs} (base rations_identite). */
    @Column(name = "id_createur", nullable = false)
    private Long idCreateur;

    /** DRH ayant tranche. Nul tant que la grille n'a pas ete validee ou rejetee. */
    @Column(name = "id_validateur")
    private Long idValidateur;

    /** Debut de validite. Grille courante = statut ACTIVE et {@code dateFin} nulle. */
    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    /**
     * Fin de validite. {@code null} signifie grille courante, PAS grille sans fin :
     * la borne est posee lorsqu'une remplacante est activee (Sprint 2.3).
     */
    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation;

    /** Horodatage de la decision DRH (validation ou rejet). Nul avant. */
    @Column(name = "date_validation")
    private LocalDateTime dateValidation;

    /** Motif obligatoire au rejet DRH (RG-10). Nul hors statut REJETEE. */
    @Column(name = "motif_rejet", length = 255)
    private String motifRejet;

    protected GrilleTarifaire() {
        // requis par JPA
    }

    /**
     * Cree une grille au statut initial BROUILLON (transition « (creation) -> BROUILLON »
     * du diagramme ET02). Le montant est fige a la creation ; nature et session sont
     * typees, jamais des chaines libres.
     */
    public GrilleTarifaire(NatureEnum nature, SessionEnum session, Integer montantFcfa,
                           LocalDate dateDebut, Long idCreateur) {
        this.nature = nature;
        this.session = session;
        this.montantFcfa = montantFcfa;
        this.dateDebut = dateDebut;
        this.idCreateur = idCreateur;
        this.statutValidation = StatutGrilleEnum.BROUILLON;
    }

    @PrePersist
    void avantInsertion() {
        if (dateCreation == null) {
            dateCreation = LocalDateTime.now();
        }
    }

    // --- Mutateurs internes au domaine, orchestres par TransitionGrille -------
    // Volontairement en visibilite paquet : aucune couche au-dessus du domaine
    // ne change le statut d'une grille directement, tout passe par la machine a
    // etats qui verifie d'abord la legalite de la transition.

    void appliquerStatut(StatutGrilleEnum nouveauStatut) {
        this.statutValidation = nouveauStatut;
    }

    void enregistrerValidation(Long idValidateur, LocalDateTime instant) {
        this.idValidateur = idValidateur;
        this.dateValidation = instant;
    }

    void enregistrerRejet(String motif, LocalDateTime instant) {
        this.motifRejet = motif;
        this.dateValidation = instant;
    }

    void poserDateFin(LocalDate dateFin) {
        this.dateFin = dateFin;
    }

    // --- Lecture ------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public NatureEnum getNature() {
        return nature;
    }

    public SessionEnum getSession() {
        return session;
    }

    public Integer getMontantFcfa() {
        return montantFcfa;
    }

    public StatutGrilleEnum getStatutValidation() {
        return statutValidation;
    }

    public Long getIdCreateur() {
        return idCreateur;
    }

    public Long getIdValidateur() {
        return idValidateur;
    }

    public LocalDate getDateDebut() {
        return dateDebut;
    }

    public LocalDate getDateFin() {
        return dateFin;
    }

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

    public LocalDateTime getDateValidation() {
        return dateValidation;
    }

    public String getMotifRejet() {
        return motifRejet;
    }

}
