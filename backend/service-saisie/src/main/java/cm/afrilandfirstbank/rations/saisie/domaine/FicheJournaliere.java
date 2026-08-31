package cm.afrilandfirstbank.rations.saisie.domaine;

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
 * Fiche journalière : le relevé des prestations servies un jour donné, au sein
 * d'un processus mensuel (CLAUDE.md section 4).
 *
 * <p><b>RG-05 — fiche vierge par jour.</b> Chaque nouveau jour ouvre une fiche
 * réinitialisée. L'unicité {@code (id_processus, date_jour)} en base garantit
 * qu'il n'y a jamais deux fiches pour la même journée d'un même processus ;
 * {@code FicheJournaliereRepository.findByIdProcessusAndDateJour} sert à savoir
 * s'il faut ouvrir une fiche ou reprendre celle du jour.
 *
 * <p><b>{@code idProcessus} n'est pas une association JPA.</b> Le processus
 * mensuel vit dans la base {@code rations_workflow}, celle du service Workflow.
 * Aucune clé étrangère n'est possible entre deux bases : c'est une <i>référence
 * logique inter-services</i>, portée par un identifiant simple. Créer ici une
 * entité {@code ProcessusMensuel} dupliquerait le domaine d'un autre service —
 * faute d'architecture (guide §10, CLAUDE.md points de vigilance). La convention
 * de rattachement à ce processus est arrêtée à l'étape 5 du Sprint 3.1
 * ({@code docs/rattachement-processus.md}).
 *
 * <p>La table existe en base depuis le Sprint 0.5 ; l'entité s'y conforme
 * (Hibernate en {@code ddl-auto: validate}).
 */
@Entity
@Table(name = "fiche_journaliere")
public class FicheJournaliere {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Référence logique vers {@code processus_mensuel} (base
     * {@code rations_workflow}). Identifiant simple, sans clé étrangère
     * inter-base et sans association JPA.
     */
    @Column(name = "id_processus", nullable = false)
    private Long idProcessus;

    /**
     * Jour calendaire de la fiche. {@code LocalDate} et non {@code LocalDateTime} :
     * la colonne est {@code DATE}, par exception à la convention générale
     * (CLAUDE.md section 4, migration V1).
     */
    @Column(name = "date_jour", nullable = false)
    private LocalDate dateJour;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutFicheEnum statut;

    /**
     * Unité supportant la charge, <b>recopiée du processus mensuel à l'ouverture
     * et figée</b> (migration V3, décision Sprint 3.1). Ligne de <i>débit</i> — à
     * ne pas confondre avec {@code beneficiaires.code_agence}, la ligne de
     * crédit.
     *
     * <p>Elle sert deux besoins qu'un {@code id_processus} opaque ne peut pas
     * servir localement : la vérification de portée d'accès (RG-12) et le
     * regroupement des fiches par unité qu'exige RG-15 (Sprint 6bis), dont le
     * contrôle porterait sinon un appel réseau vers Workflow par processus
     * candidat, sur le chemin d'écriture d'une ligne.
     */
    @Column(name = "code_unite", length = 5)
    private String codeUnite;

    /** Mois du processus mensuel, recopié et figé (migration V3). */
    @Column(name = "mois_paiement")
    private Integer moisPaiement;

    /** Année du processus mensuel, recopiée et figée (migration V3). */
    @Column(name = "annee_paiement")
    private Integer anneePaiement;

    /** Horodatage technique d'audit (convention transverse, Sprint 0.7). */
    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation;

    protected FicheJournaliere() {
        // requis par JPA
    }

    /**
     * Ouvre la fiche d'un jour pour un processus mensuel. Statut initial
     * {@code EN_SAISIE} : les lignes de prestation restent modifiables tant que
     * la fiche n'est pas enregistrée.
     *
     * <p><b>Le triplet unité / mois / année est exigé à la construction</b>,
     * plutôt que renseigné après coup par un second constructeur. Il vient de la
     * réponse de {@code GET /processus/{id}}, obtenue avant toute écriture : une
     * fiche ne peut pas naître sans que le processus ait été vérifié. Un
     * constructeur qui s'en passerait laisserait une fiche muette sur son unité,
     * donc hors de portée du contrôle d'accès et de RG-15 — sans qu'aucune
     * contrainte de base ne le signale, les colonnes étant nullables pour
     * l'historique.
     *
     * <p>Le {@code statut} du processus, lui, n'est <b>jamais</b> copié : il est
     * mutable, et c'est pourquoi il est redemandé à chaque écriture
     * ({@code docs/rattachement-processus.md} §4 et §5).
     */
    public FicheJournaliere(Long idProcessus, LocalDate dateJour, String codeUnite,
                            Integer moisPaiement, Integer anneePaiement) {
        this.idProcessus = idProcessus;
        this.dateJour = dateJour;
        this.codeUnite = codeUnite;
        this.moisPaiement = moisPaiement;
        this.anneePaiement = anneePaiement;
        this.statut = StatutFicheEnum.EN_SAISIE;
    }

    @PrePersist
    void avantInsertion() {
        if (dateCreation == null) {
            dateCreation = LocalDateTime.now();
        }
    }

    // --- Lecture ----------------------------------------------------------

    public Long getId() {
        return id;
    }

    public Long getIdProcessus() {
        return idProcessus;
    }

    public LocalDate getDateJour() {
        return dateJour;
    }

    public StatutFicheEnum getStatut() {
        return statut;
    }

    public String getCodeUnite() {
        return codeUnite;
    }

    public Integer getMoisPaiement() {
        return moisPaiement;
    }

    public Integer getAnneePaiement() {
        return anneePaiement;
    }

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

}
