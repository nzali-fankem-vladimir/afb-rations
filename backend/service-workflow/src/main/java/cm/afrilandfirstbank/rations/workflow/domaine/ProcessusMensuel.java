package cm.afrilandfirstbank.rations.workflow.domaine;

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
 * Processus mensuel : l'objet central du module. Il porte la periode, l'unite qui
 * supporte la charge, le statut d'avancement, le montant total consolide et
 * l'indicateur de transmission comptable (CLAUDE.md section 4).
 *
 * <h2>Strictement conforme au dictionnaire et a la table du Sprint 0.5</h2>
 *
 * <p>La table {@code processus_mensuel} existe depuis la migration V1 ; Hibernate
 * tourne en {@code ddl-auto: validate} et ne cree rien. Les colonnes sont donc
 * exactement celles du dictionnaire (CLAUDE.md section 4) plus la convention
 * transverse {@code date_creation} (Sprint 0.7) : ni {@code date_declenchement},
 * ni {@code date_cloture}, ni {@code id_createur}. Le « qui a declenche » et le
 * « quand » sont portes par le journal d'audit (etape de declenchement, Sprint
 * 4.1) et par les lignes {@code etape_workflow} des sous-sprints 4.2 et suivants.
 *
 * <h2>id_processus_origine — auto-reference, jamais une association JPA chargee</h2>
 *
 * <p>Renseigne uniquement pour un {@link TypeProcessusEnum#COMPLEMENTAIRE} : il
 * pointe vers l'etat clos jamais rouvert. La colonne porte une vraie cle
 * etrangere en base (meme table, meme base), mais on la modelise en identifiant
 * simple : rien dans ce sous-sprint ne remonte la chaine, et une
 * {@code @ManyToOne} ferait charger un second processus a chaque lecture sans
 * usage.
 *
 * <h2>Mutation du statut</h2>
 *
 * <p>Le seul mutateur, {@link #appliquerStatut(StatutEnum)}, est en visibilite
 * paquet : aucune couche au-dessus du domaine ne change le statut sans que
 * {@link TransitionProcessus} ait juge la transition legale au prealable — meme
 * discipline que {@code GrilleTarifaire} au Sprint 2.1.
 */
@Entity
@Table(name = "processus_mensuel")
public class ProcessusMensuel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mois_paiement", nullable = false)
    private Integer moisPaiement;

    @Column(name = "annee_paiement", nullable = false)
    private Integer anneePaiement;

    /**
     * Unite qui supporte la charge (ligne de <i>debit</i>). Format du referentiel
     * des codes guichets Afriland ({@code VARCHAR(5)}). A ne jamais confondre
     * avec {@code beneficiaires.code_agence}, l'agence de domiciliation du compte
     * credite (CLAUDE.md section 4).
     */
    @Column(name = "code_unite", nullable = false, length = 5)
    private String codeUnite;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_processus", nullable = false, length = 20)
    private TypeProcessusEnum typeProcessus;

    /**
     * Reference vers l'etat d'origine, non nulle uniquement pour un
     * {@link TypeProcessusEnum#COMPLEMENTAIRE}. Identifiant simple, pas
     * d'association JPA.
     */
    @Column(name = "id_processus_origine")
    private Long idProcessusOrigine;

    @Column(name = "motif_ouverture", length = 255)
    private String motifOuverture;

    /**
     * Montant total consolide, en FCFA entiers. Reste a {@code 0} tant que l'etat
     * n'a pas ete soumis : c'est la soumission (Sprint 4.2) qui y reporte le
     * total rendu par le service Saisie (RG-06, {@code docs/appel-consolidation.md}).
     */
    @Column(name = "montant_total", nullable = false)
    private int montantTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutEnum statut;

    /**
     * Vrai une fois l'etat valide publie sur {@code rations.etat.valide}. Verrou
     * de RG-13 : un etat n'est transmis qu'une fois. Positionne au Sprint 5.
     */
    @Column(name = "transmis_comptabilite", nullable = false)
    private boolean transmisComptabilite;

    /** Horodatage technique d'audit (convention transverse, Sprint 0.7). */
    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation;

    protected ProcessusMensuel() {
        // requis par JPA
    }

    /**
     * Declenche un processus <b>NORMAL</b> pour une unite et une periode donnees.
     *
     * <p><b>Visibilite paquet, deliberement.</b> La creation est la premiere des
     * neuf transitions d'ET01 ; elle passe donc par
     * {@link TransitionProcessus#declencher(Integer, Integer, String)}, comme les
     * huit autres passent par la machine a etats. Un constructeur public
     * laisserait un service applicatif faire naitre un processus sans que la
     * machine l'ait vu — l'invariant « toute transition passe par
     * {@link TransitionProcessus} » deviendrait une convention au lieu d'etre
     * verifie par le compilateur.
     *
     * <p>Statut initial {@link StatutEnum#EN_COURS_SAISIE} : l'agent va saisir ses
     * fiches journalieres. {@code montantTotal} a {@code 0},
     * {@code transmisComptabilite} a {@code false}, {@code idProcessusOrigine} et
     * {@code motifOuverture} nuls — un etat normal ne regularise rien.
     *
     * <p>Le Sprint 4.1 ne construit que des processus normaux : la creation d'un
     * {@link TypeProcessusEnum#COMPLEMENTAIRE} viendra au Sprint 6bis, avec ses
     * propres controles (drapeau {@code RATTRAPAGE_ACTIF}, delai de
     * regularisation, RG-15).
     *
     * @param moisPaiement mois du cycle, 1 a 12
     * @param anneePaiement annee du cycle
     * @param codeUnite unite qui supporte la charge, cinq chiffres
     */
    ProcessusMensuel(Integer moisPaiement, Integer anneePaiement, String codeUnite) {
        this.moisPaiement = moisPaiement;
        this.anneePaiement = anneePaiement;
        this.codeUnite = codeUnite;
        this.typeProcessus = TypeProcessusEnum.NORMAL;
        this.idProcessusOrigine = null;
        this.motifOuverture = null;
        this.montantTotal = 0;
        this.transmisComptabilite = false;
        this.statut = StatutEnum.EN_COURS_SAISIE;
    }

    @PrePersist
    void avantInsertion() {
        if (dateCreation == null) {
            dateCreation = LocalDateTime.now();
        }
    }

    /**
     * Applique un nouveau statut. <b>Visibilite paquet</b> : reserve a
     * {@link TransitionProcessus}, qui a d'abord verifie que la transition est
     * prevue par ET01. Aucun service applicatif n'appelle cette methode
     * directement.
     */
    void appliquerStatut(StatutEnum nouveauStatut) {
        this.statut = nouveauStatut;
    }

    // --- Lecture ---------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public Integer getMoisPaiement() {
        return moisPaiement;
    }

    public Integer getAnneePaiement() {
        return anneePaiement;
    }

    public String getCodeUnite() {
        return codeUnite;
    }

    public TypeProcessusEnum getTypeProcessus() {
        return typeProcessus;
    }

    public Long getIdProcessusOrigine() {
        return idProcessusOrigine;
    }

    public String getMotifOuverture() {
        return motifOuverture;
    }

    public int getMontantTotal() {
        return montantTotal;
    }

    public StatutEnum getStatut() {
        return statut;
    }

    public boolean isTransmisComptabilite() {
        return transmisComptabilite;
    }

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

}
