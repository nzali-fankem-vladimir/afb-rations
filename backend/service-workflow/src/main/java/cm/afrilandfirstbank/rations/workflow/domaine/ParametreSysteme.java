package cm.afrilandfirstbank.rations.workflow.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Parametre systeme : porte le seuil d'aiguillage (RG-08) et les drapeaux de
 * fonctionnalite du module (CLAUDE.md sections 4, 6 et 7).
 *
 * <p>La table {@code parametre_systeme} est alimentee par la migration V2 :
 * {@code SEUIL_AIGUILLAGE_DR} (100 000), {@code RATTRAPAGE_ACTIF} et
 * {@code DELAI_REGULARISATION_JOURS}. Le seuil est <b>toujours</b> lu ici, jamais
 * code en dur (CLAUDE.md section 15). Sa lecture effective et l'aiguillage
 * relevent du sous-sprint 4.3.
 *
 * <h2>Pas d'horodatage</h2>
 *
 * <p>Contrairement aux autres tables metier, {@code parametre_systeme} n'a
 * volontairement aucune colonne de date (CLAUDE.md section 4, decision Sprint
 * 0.7). L'entite s'y conforme : ni {@code date_creation}, ni
 * {@code date_modification}.
 *
 * <h2>Valeur en texte</h2>
 *
 * <p>{@code valeur} est stockee en {@code VARCHAR} : le type reel (entier pour un
 * seuil, booleen pour un drapeau) est interprete cote application par le service
 * qui lit le parametre, pas par cette entite.
 */
@Entity
@Table(name = "parametre_systeme")
public class ParametreSysteme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 50, unique = true)
    private String code;

    @Column(name = "libelle", nullable = false, length = 255)
    private String libelle;

    @Column(name = "valeur", nullable = false, length = 255)
    private String valeur;

    @Column(name = "actif", nullable = false)
    private boolean actif;

    protected ParametreSysteme() {
        // requis par JPA
    }

    // --- Lecture ---------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getLibelle() {
        return libelle;
    }

    public String getValeur() {
        return valeur;
    }

    public boolean isActif() {
        return actif;
    }

}
