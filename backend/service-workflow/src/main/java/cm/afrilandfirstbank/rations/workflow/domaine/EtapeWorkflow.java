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
 * Etape du circuit de validation d'un processus mensuel : une ligne par passage
 * chez un acteur (soumission agent, validation DA, validation DR). CLAUDE.md
 * section 4.
 *
 * <h2>Squelette au Sprint 4.1</h2>
 *
 * <p>Ce sous-sprint cree l'entite, son repository et la machine a etats du
 * processus. Il ne cree <b>aucune</b> ligne d'etape : la soumission, la
 * validation et le retour — donc l'ecriture des etapes et la pose des signatures
 * (RG-09) — sont les sous-sprints 4.2 a 4.4.
 *
 * <h2>id_acteur et id_processus</h2>
 *
 * <p>{@code id_acteur} designe un utilisateur qui vit dans {@code rations_identite}
 * (service Identite) : identifiant simple, jamais une association JPA (CLAUDE.md
 * section 4, point de vigilance du guide 4.1).
 *
 * <p>{@code id_processus} pointe vers {@code processus_mensuel}, dans la meme
 * base : une vraie cle etrangere existe en base, mais on la modelise aussi en
 * identifiant simple. Le repository interroge par {@code idProcessus} directement
 * ({@code findByIdProcessusOrderByOrdreEtape}), et rien dans le circuit n'a
 * besoin de remonter du pas au processus complet.
 *
 * <p>Table {@code etape_workflow} presente depuis la migration V1 ; l'entite s'y
 * conforme exactement ({@code ddl-auto: validate}) : {@code date_creation}
 * (convention transverse Sprint 0.7), pas de {@code date_action}.
 */
@Entity
@Table(name = "etape_workflow")
public class EtapeWorkflow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_processus", nullable = false)
    private Long idProcessus;

    /** Reference {@code utilisateurs} (base {@code rations_identite}). Identifiant simple. */
    @Column(name = "id_acteur", nullable = false)
    private Long idActeur;

    /** Rang du pas dans le parcours de l'etat (1 pour la soumission agent, etc.). */
    @Column(name = "ordre_etape", nullable = false)
    private int ordreEtape;

    @Enumerated(EnumType.STRING)
    @Column(name = "nom_etape", nullable = false, length = 30)
    private NomEtapeEnum nomEtape;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_etape", nullable = false, length = 20)
    private StatutEtapeEnum statutEtape;

    /** Motif du retour a l'agent, obligatoire quand {@code statutEtape = RETOURNEE} (RG-10). */
    @Column(name = "motif_retour", length = 255)
    private String motifRetour;

    /** Signature numerique horodatee apposee a la validation (RG-09). */
    @Column(name = "signature_numerique", length = 255)
    private String signatureNumerique;

    /** Horodatage technique d'audit (convention transverse, Sprint 0.7). */
    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation;

    protected EtapeWorkflow() {
        // requis par JPA
    }

    /**
     * Ouvre une etape en attente d'action d'un acteur. Le motif de retour et la
     * signature sont poses plus tard, par les transitions des sous-sprints 4.2 a
     * 4.4.
     */
    public EtapeWorkflow(Long idProcessus, Long idActeur, int ordreEtape, NomEtapeEnum nomEtape) {
        this.idProcessus = idProcessus;
        this.idActeur = idActeur;
        this.ordreEtape = ordreEtape;
        this.nomEtape = nomEtape;
        this.statutEtape = StatutEtapeEnum.EN_ATTENTE;
    }

    @PrePersist
    void avantInsertion() {
        if (dateCreation == null) {
            dateCreation = LocalDateTime.now();
        }
    }

    // --- Lecture ---------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public Long getIdProcessus() {
        return idProcessus;
    }

    public Long getIdActeur() {
        return idActeur;
    }

    public int getOrdreEtape() {
        return ordreEtape;
    }

    public NomEtapeEnum getNomEtape() {
        return nomEtape;
    }

    public StatutEtapeEnum getStatutEtape() {
        return statutEtape;
    }

    public String getMotifRetour() {
        return motifRetour;
    }

    public String getSignatureNumerique() {
        return signatureNumerique;
    }

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

}
