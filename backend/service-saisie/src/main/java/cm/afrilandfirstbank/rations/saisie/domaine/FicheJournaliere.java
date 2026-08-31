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
     */
    public FicheJournaliere(Long idProcessus, LocalDate dateJour) {
        this.idProcessus = idProcessus;
        this.dateJour = dateJour;
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

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

}
