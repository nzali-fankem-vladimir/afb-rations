package cm.afrilandfirstbank.rations.audit.domaine;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;

/**
 * Ligne du journal d'audit immuable (CLAUDE.md sections 3 et 4). Schema arrete
 * au sprint de rattrapage du service Audit, etape 3 : les huit colonnes
 * reprennent exactement celles deja fixees par les migrations {@code V1} et
 * {@code V2}, deja commitees et donc intouchables (CLAUDE.md section 15).
 *
 * <h2>Aucun champ `login_acteur`</h2>
 *
 * <p>Arbitrage du point A-01, tranche a cette etape avec l'utilisateur plutot
 * que reporte (docs/decisions/2026-09-04-arbitrage-point-a-01-login-acteur.md).
 * Un champ derive de {@code detail_json} n'aurait resolu ni le cas le plus
 * frequent (les cinq actions du service Saisie ne capturent aucun acteur, sous
 * quelque forme que ce soit) ni le cas le plus simple (la cle du login varie
 * selon le service emetteur : {@code login} contre {@code auteur}). Voir
 * {@code docs/points-en-attente.md}, point A-01, pour le detail complet et le
 * tableau des 15 actions concernees.
 *
 * <h2>Immuabilite : une propriete du code, pas seulement de l'architecture</h2>
 *
 * <p>Aucune methode de mutation au-dela du constructeur : pas de setter sur un
 * champ deja ecrit (guide de ce sprint, etape 3). Une ligne d'audit se cree une
 * fois et ne change plus jamais — c'est {@link
 * cm.afrilandfirstbank.rations.audit.infrastructure.AuditLogRepository} (etape
 * 4) qui interdit en plus toute suppression, y compris celle heritee de
 * {@code JpaRepository}.
 *
 * <h2>{@code idUtilisateur} et {@code idEntite} nullables</h2>
 *
 * <p>Depuis la migration V2 (Sprint 6.3) : un refus d'acces (CT-04) oppose a un
 * jeton valide sans profil local n'a pas d'{@code idUtilisateur} a inscrire, et
 * une action sans objet precis (recherche refusee) n'a pas d'{@code idEntite}.
 * Aucune valeur sentinelle : un {@code null} dit ce qu'il est, une jointure ne
 * le prendra jamais pour un utilisateur ou une entite reels.
 *
 * <h2>{@code detailJson} en JSONB</h2>
 *
 * <p>{@code @JdbcTypeCode(SqlTypes.JSON)} sur un simple {@code String} : la
 * colonne reste du JSON textuel cote Java (deja serialise par le producteur,
 * {@code EvenementAudit.detailJson()}), Hibernate se charge de la conversion
 * vers le type {@code jsonb} de PostgreSQL. Pas de bibliotheque tierce : cette
 * capacite est nativement portee par Hibernate ORM depuis la 6.2, deja la
 * version embarquee par Spring Boot 4.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Reference {@code utilisateurs} (base {@code rations_identite}). Identifiant simple, nullable. */
    @Column(name = "id_utilisateur")
    private Long idUtilisateur;

    @Column(name = "service_emetteur", nullable = false, length = 30)
    private String serviceEmetteur;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "entite_cible", nullable = false, length = 50)
    private String entiteCible;

    /** Identifiant de l'entite visee, nullable (action sans objet precis). */
    @Column(name = "id_entite")
    private Long idEntite;

    /**
     * Horodatage de l'action elle-meme, pas de sa publication ni de son
     * insertion en base (CLAUDE.md section 9.2) : c'est la seule colonne de tri
     * fiable, l'ordre d'arrivee sur le topic n'etant pas l'ordre des faits.
     */
    @Column(name = "date_action", nullable = false)
    private LocalDateTime dateAction;

    @Column(name = "adresse_ip", length = 45)
    private String adresseIp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail_json", columnDefinition = "jsonb")
    private String detailJson;

    protected AuditLog() {
        // requis par JPA
    }

    /**
     * Refuse toute suppression, quelle que soit la voie empruntee pour y
     * arriver. {@link AuditLogRepository} n'expose deja aucune methode de
     * suppression (etape 4), mais ce controle a lui seul ne couvre que
     * <b>ce</b> repository : un appel direct a
     * {@code EntityManager.remove(auditLog)} — un autre repository du
     * reacteur, un test, un futur code qui contournerait le repository —
     * resterait possible sans lui. Ce callback ferme cette voie a la racine :
     * l'immuabilite devient une propriete de l'entite elle-meme, pas
     * seulement de l'interface qui la sert (CLAUDE.md section 3).
     */
    @PreRemove
    private void suppressionInterdite() {
        throw new UnsupportedOperationException(
                "Le journal d'audit est immuable : la ligne " + id + " ne peut pas etre supprimee, "
                        + "quelle que soit la voie empruntee.");
    }

    public AuditLog(Long idUtilisateur, String serviceEmetteur, String action, String entiteCible,
            Long idEntite, LocalDateTime dateAction, String adresseIp, String detailJson) {
        this.idUtilisateur = idUtilisateur;
        this.serviceEmetteur = serviceEmetteur;
        this.action = action;
        this.entiteCible = entiteCible;
        this.idEntite = idEntite;
        this.dateAction = dateAction;
        this.adresseIp = adresseIp;
        this.detailJson = detailJson;
    }

    // --- Lecture ---------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public Long getIdUtilisateur() {
        return idUtilisateur;
    }

    public String getServiceEmetteur() {
        return serviceEmetteur;
    }

    public String getAction() {
        return action;
    }

    public String getEntiteCible() {
        return entiteCible;
    }

    public Long getIdEntite() {
        return idEntite;
    }

    public LocalDateTime getDateAction() {
        return dateAction;
    }

    public String getAdresseIp() {
        return adresseIp;
    }

    public String getDetailJson() {
        return detailJson;
    }

}
