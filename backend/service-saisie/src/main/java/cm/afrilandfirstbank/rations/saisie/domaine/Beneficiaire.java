package cm.afrilandfirstbank.rations.saisie.domaine;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Bénéficiaire : agent de la garde armée servi en ration ou en transport
 * (CLAUDE.md section 4).
 *
 * <p><b>Aucun enrôlement.</b> Il n'existe pas de référentiel de bénéficiaires en
 * amont, contrairement à d'autres modules du programme. Un bénéficiaire est créé
 * au moment de la <b>première saisie qui le concerne</b> et jamais autrement :
 * pas d'import, pas de liste préalable, pas d'écran de gestion. C'est
 * {@code ResolutionBeneficiaireService} qui le retrouve ou le crée au fil de la
 * saisie (étape 4 du Sprint 3.1).
 *
 * <p>La table {@code beneficiaires} existe en base depuis le Sprint 0.5 ; cette
 * entité s'y conforme, elle ne la pilote pas (Hibernate en
 * {@code ddl-auto: validate}).
 *
 * <p><b>{@code codeAgence} n'est pas {@code codeUnite}.</b> Même format
 * (VARCHAR(5), référentiel des codes guichets Afriland), rôles opposés :
 * {@code codeAgence} est l'agence de domiciliation du compte du bénéficiaire —
 * la ligne de <i>crédit</i>. Le {@code codeUnite}, qui désigne l'unité supportant
 * la charge — la ligne de <i>débit</i> —, appartient au processus mensuel et n'a
 * rien à faire dans cette table (CLAUDE.md section 4).
 */
@Entity
@Table(name = "beneficiaires")
public class Beneficiaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nom", nullable = false, length = 100)
    private String nom;

    @Column(name = "prenom", nullable = false, length = 100)
    private String prenom;

    /** Numéro du compte courant du bénéficiaire (ligne de crédit). */
    @Column(name = "num_compte_courant", nullable = false, length = 20)
    private String numCompteCourant;

    /**
     * Agence de domiciliation du compte du bénéficiaire (référentiel des codes
     * guichets Afriland). Distinct de {@code code_unite}, qui porte la charge.
     */
    @Column(name = "code_agence", nullable = false, length = 5)
    private String codeAgence;

    /** Horodatage technique d'audit (convention transverse, Sprint 0.7). */
    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation;

    protected Beneficiaire() {
        // requis par JPA
    }

    /**
     * Crée un bénéficiaire lors de la première saisie qui le concerne. Tous les
     * champs sont fournis par l'agent au moment de la saisie ; aucun ne provient
     * d'un référentiel.
     */
    public Beneficiaire(String nom, String prenom, String numCompteCourant, String codeAgence) {
        this.nom = nom;
        this.prenom = prenom;
        this.numCompteCourant = numCompteCourant;
        this.codeAgence = codeAgence;
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

    public String getNom() {
        return nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public String getNumCompteCourant() {
        return numCompteCourant;
    }

    public String getCodeAgence() {
        return codeAgence;
    }

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

}
