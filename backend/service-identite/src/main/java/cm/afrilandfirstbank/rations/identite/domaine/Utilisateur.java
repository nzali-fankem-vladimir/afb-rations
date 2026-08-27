package cm.afrilandfirstbank.rations.identite.domaine;

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
 * Projection locale d'un compte de l'annuaire (CLAUDE.md section 10).
 *
 * <p>Keycloak porte l'identite et l'authentification ; ce module porte l'habilitation
 * metier. La ligne locale detient donc le role applicatif et le code unite, que
 * l'annuaire ne connait pas.
 *
 * <p>Cette entite ne comporte AUCUN champ de mot de passe, et il ne faut jamais lui
 * en ajouter : le module ne verifie aucun identifiant, il valide des jetons.
 *
 * <p>{@code subKeycloak} est nul tant que l'agent ne s'est pas connecte une premiere
 * fois : l'administrateur cree le profil a partir du login, la liaison au compte
 * Keycloak s'etablit a la premiere connexion.
 */
@Entity
@Table(name = "utilisateurs")
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identifiant annuaire au format prenom_nom. Cle de rapprochement a la premiere connexion. */
    @Column(name = "login", nullable = false, unique = true, length = 100)
    private String login;

    /** Identifiant technique du compte Keycloak. Nul tant que la liaison n'a pas eu lieu. */
    @Column(name = "sub_keycloak", unique = true, length = 100)
    private String subKeycloak;

    @Column(name = "matricule", length = 20)
    private String matricule;

    @Column(name = "nom", nullable = false, length = 100)
    private String nom;

    @Column(name = "prenom", nullable = false, length = 100)
    private String prenom;

    @Column(name = "email", length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private RoleEnum role;

    /**
     * Unite qui supporte la charge, au referentiel des codes guichets.
     * Nullable : les roles a portee nationale (ARH, DRH, ADMIN) ne sont rattaches
     * a aucune unite (CLAUDE.md section 4). A ne pas confondre avec le code agence
     * du beneficiaire.
     */
    @Column(name = "code_unite", length = 5)
    private String codeUnite;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_dernier_acces")
    private LocalDateTime dateDernierAcces;

    protected Utilisateur() {
        // requis par JPA
    }

    public Utilisateur(String login, String nom, String prenom, RoleEnum role, String codeUnite) {
        this.login = login;
        this.nom = nom;
        this.prenom = prenom;
        this.role = role;
        this.codeUnite = codeUnite;
    }

    @PrePersist
    void avantInsertion() {
        if (dateCreation == null) {
            dateCreation = LocalDateTime.now();
        }
    }

    /**
     * Rattache definitivement ce profil au compte Keycloak qui vient de se presenter.
     * La liaison n'a lieu qu'une fois : un profil deja lie n'est pas reattribue.
     */
    public void lierAuCompteKeycloak(String subKeycloak) {
        if (this.subKeycloak != null && !this.subKeycloak.equals(subKeycloak)) {
            throw new IllegalStateException(
                    "Le profil " + login + " est deja lie a un autre compte Keycloak.");
        }
        this.subKeycloak = subKeycloak;
    }

    /**
     * Attribue un nouveau role applicatif et code unite (sous-sprint 1.2). La
     * coherence entre les deux (code unite requis pour un role a portee locale)
     * est verifiee en amont, dans {@code UtilisateurAdminService} : l'entite ne
     * connait pas la notion de portee.
     */
    public void attribuerRoleEtCodeUnite(RoleEnum role, String codeUnite) {
        this.role = role;
        this.codeUnite = codeUnite;
    }

    /** Retire l'habilitation au module sans supprimer la trace du profil. */
    public void desactiver() {
        this.actif = false;
    }

    public void reactiver() {
        this.actif = true;
    }

    public void enregistrerAcces(LocalDateTime instant) {
        this.dateDernierAcces = instant;
    }

    public boolean estLie() {
        return subKeycloak != null;
    }

    public Long getId() {
        return id;
    }

    public String getLogin() {
        return login;
    }

    public String getSubKeycloak() {
        return subKeycloak;
    }

    public String getMatricule() {
        return matricule;
    }

    public void setMatricule(String matricule) {
        this.matricule = matricule;
    }

    public String getNom() {
        return nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public RoleEnum getRole() {
        return role;
    }

    public String getCodeUnite() {
        return codeUnite;
    }

    public boolean estActif() {
        return actif;
    }

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

    public LocalDateTime getDateDernierAcces() {
        return dateDernierAcces;
    }

}
