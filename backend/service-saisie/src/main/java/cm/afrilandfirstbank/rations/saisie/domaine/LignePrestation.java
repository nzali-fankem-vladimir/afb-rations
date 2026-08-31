package cm.afrilandfirstbank.rations.saisie.domaine;

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
 * Ligne de prestation : un bénéficiaire servi une fois, pour une nature et une
 * session données, dans une fiche journalière (CLAUDE.md section 4).
 *
 * <p><b>RG-01 / RG-02</b> : {@code nature} vaut RATION ou TRANSPORT, {@code session}
 * vaut JOUR ou SOIR — typées par leurs énumérations, jamais des chaînes libres.
 *
 * <p><b>RG-03 — montant figé.</b> {@code montantApplique} est repris de la grille
 * ACTIVE à la saisie et n'est jamais recalculé. Sa résolution (appel au service
 * Grilles) relève du Sprint 3.2 ; ici l'entité porte seulement le champ.
 *
 * <p><b>{@code idFicheJournaliere} et {@code idBeneficiaire}</b> sont des
 * identifiants simples, pas des associations JPA — <b>bien que les clés
 * étrangères réelles existent en base</b> (intra-{@code rations_saisie},
 * migration V1). Motif : {@code ligne_prestation} est la table de contrôle à
 * fort volume du module (RG-04 au Sprint 3.2, RG-15 au Sprint 8), et ces
 * contrôles comparent des identifiants sans jamais naviguer vers la fiche ou le
 * bénéficiaire. Une association, même {@code LAZY}, ouvrirait un chargement
 * transitif non voulu sur ce chemin ; l'identifiant {@code Long} rend tout accès
 * au bénéficiaire explicite, via le repository. Ce n'est pas une règle « toujours
 * plat » : voir {@code docs/decisions/2026-08-28-identifiants-plats-dans-service-saisie.md}.
 *
 * <p><b>{@code idGrille}</b> (migration V2, additive) : grille d'où provient
 * {@code montantApplique}, pour justifier a posteriori un montant contesté.
 * Nullable et sans clé étrangère inter-base — {@code grille_tarifaire} vit dans
 * {@code rations_grilles} —, même convention que {@code id_createur} /
 * {@code id_validateur} côté Grilles. Restera {@code null} jusqu'à ce que la
 * valorisation du Sprint 3.2 le renseigne. Dette identifiée au Sprint 2.4
 * ({@code docs/decisions/2026-08-27-resolution-du-montant-applicable.md} §5,
 * CLAUDE.md section 17).
 *
 * <p>La table existe en base depuis le Sprint 0.5 ; l'entité s'y conforme
 * (Hibernate en {@code ddl-auto: validate}).
 */
@Entity
@Table(name = "ligne_prestation")
public class LignePrestation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Fiche journalière porteuse. Identifiant simple, sans association JPA. */
    @Column(name = "id_fiche_journaliere", nullable = false)
    private Long idFicheJournaliere;

    /** Bénéficiaire servi. Identifiant simple, sans association JPA. */
    @Column(name = "id_beneficiaire", nullable = false)
    private Long idBeneficiaire;

    @Enumerated(EnumType.STRING)
    @Column(name = "nature", nullable = false, length = 20)
    private NatureEnum nature;

    @Enumerated(EnumType.STRING)
    @Column(name = "session", nullable = false, length = 10)
    private SessionEnum session;

    /** Montant en FCFA, entier sans décimale. Repris de la grille ACTIVE (RG-03), figé. */
    @Column(name = "montant_applique", nullable = false)
    private Integer montantApplique;

    /**
     * Grille d'où provient {@code montantApplique}. Référence logique vers
     * {@code grille_tarifaire} (base {@code rations_grilles}), sans clé étrangère
     * inter-base. Nul tant que la valorisation du Sprint 3.2 ne l'a pas renseigné.
     */
    @Column(name = "id_grille")
    private Long idGrille;

    /** Horodatage technique d'audit (convention transverse, Sprint 0.7). */
    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation;

    protected LignePrestation() {
        // requis par JPA
    }

    /**
     * Crée une ligne de prestation avec un montant déjà résolu depuis la grille
     * active (RG-03). Le montant est figé à la construction ; l'identité de la
     * grille l'accompagne pour la traçabilité.
     */
    public LignePrestation(Long idFicheJournaliere, Long idBeneficiaire, NatureEnum nature,
                           SessionEnum session, Integer montantApplique, Long idGrille) {
        this.idFicheJournaliere = idFicheJournaliere;
        this.idBeneficiaire = idBeneficiaire;
        this.nature = nature;
        this.session = session;
        this.montantApplique = montantApplique;
        this.idGrille = idGrille;
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

    public Long getIdFicheJournaliere() {
        return idFicheJournaliere;
    }

    public Long getIdBeneficiaire() {
        return idBeneficiaire;
    }

    public NatureEnum getNature() {
        return nature;
    }

    public SessionEnum getSession() {
        return session;
    }

    public Integer getMontantApplique() {
        return montantApplique;
    }

    public Long getIdGrille() {
        return idGrille;
    }

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

}
