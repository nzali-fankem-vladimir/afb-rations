package cm.afrilandfirstbank.rations.commun.audit;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Charge publiee sur le topic {@code rations.audit.evenement} (CLAUDE.md
 * section 9.2). Les huit champs correspondent aux colonnes de {@code audit_log}
 * (CLAUDE.md section 4), que le service Audit alimente en consommant ce topic.
 *
 * <p><b>Contrat de fil.</b> C'est le seul type de ce module qui traverse le
 * reseau. Le service Audit ne depend pas de cette classe : il lit le topic en
 * <i>tolerant reader</i>, avec son propre type. Toute evolution doit donc rester
 * additive (ajouter un champ, jamais en renommer ni en retirer), sans quoi les
 * messages deja presents dans le topic deviendraient illisibles.
 *
 * <p><b>Champs nullables, et pourquoi.</b>
 * <ul>
 *   <li>{@code idUtilisateur} : nul quand l'auteur n'a pas de profil local. Cas
 *       reel et important : un refus d'acces (CT-04) oppose a un jeton valide
 *       sans profil ouvert dans le module. La trace doit exister malgre
 *       l'absence d'identifiant local.</li>
 *   <li>{@code idEntite} : nul quand l'action ne vise pas une entite precise
 *       (une recherche refusee, par exemple).</li>
 *   <li>{@code adresseIp} : nul si la requete n'en expose pas.</li>
 *   <li>{@code detailJson} : nul quand l'action n'a pas de delta avant/apres.</li>
 * </ul>
 *
 * <p>{@code serviceEmetteur} est volontairement <b>renseigne par le producteur</b>,
 * depuis {@code spring.application.name}, et non par l'appelant : sans lui une
 * trace centralisee ne dit plus d'ou elle vient, et le faire remplir a la main
 * par six services garantit qu'un l'oubliera. Voir
 * {@link EvenementAudit#avecServiceEmetteur(String)}.
 *
 * @param idUtilisateur auteur de l'action, nul si sans profil local
 * @param serviceEmetteur service qui publie, estampille par le producteur
 * @param action verbe metier, en majuscules (ex. {@code ATTRIBUTION_ROLE})
 * @param entiteCible table ou agregat vise (ex. {@code utilisateurs})
 * @param idEntite identifiant de l'entite visee, nul si sans objet
 * @param dateAction horodatage de l'action elle-meme, pas de sa publication
 * @param adresseIp adresse d'origine de la requete
 * @param detailJson delta avant/apres, en JSON valide
 */
public record EvenementAudit(
        Long idUtilisateur,
        String serviceEmetteur,
        String action,
        String entiteCible,
        Long idEntite,
        LocalDateTime dateAction,
        String adresseIp,
        String detailJson) {

    public EvenementAudit {
        Objects.requireNonNull(action, "action requise : une trace sans verbe est illisible");
        Objects.requireNonNull(entiteCible, "entiteCible requise : une trace sans objet est illisible");
        Objects.requireNonNull(dateAction, "dateAction requise");
    }

    /**
     * Cree un evenement date de l'instant de l'action, sans service emetteur :
     * le producteur l'estampille a la publication.
     *
     * <p>L'horodatage est pris ici, au moment de l'action, et non a la
     * publication : celle-ci intervient apres le commit et sur un autre thread,
     * elle serait donc systematiquement en retard sur le fait qu'elle decrit.
     */
    public static EvenementAudit de(Long idUtilisateur, String action, String entiteCible, Long idEntite,
            String adresseIp, String detailJson) {
        return new EvenementAudit(idUtilisateur, null, action, entiteCible, idEntite,
                LocalDateTime.now(), adresseIp, detailJson);
    }

    /**
     * Copie estampillee du service emetteur. Appele par le producteur, pour
     * qu'aucun service ne puisse publier une trace anonyme.
     */
    public EvenementAudit avecServiceEmetteur(String serviceEmetteur) {
        return new EvenementAudit(idUtilisateur, serviceEmetteur, action, entiteCible, idEntite,
                dateAction, adresseIp, detailJson);
    }

    /**
     * Cle de partition : tous les evenements d'une meme entite empruntent la
     * meme partition, donc restent ordonnes entre eux cote consommateur.
     */
    public String clePartition() {
        return entiteCible + ":" + (idEntite == null ? "-" : idEntite);
    }

}
