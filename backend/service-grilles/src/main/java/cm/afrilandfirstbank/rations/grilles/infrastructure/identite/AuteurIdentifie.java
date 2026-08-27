package cm.afrilandfirstbank.rations.grilles.infrastructure.identite;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Auteur d'une action, resolu depuis {@code GET /identite/moi}.
 *
 * <p>Vue volontairement partielle de la reponse du service Identite : ce service
 * a besoin de savoir QUI agit, pas de connaitre son role applicatif ni sa portee
 * d'acces. Le role est deja verifie par la securite (jeton Keycloak,
 * {@code @PreAuthorize}), et une grille tarifaire n'est rattachee a aucun code
 * unite — elle vaut nationalement.
 *
 * <p>{@code ignoreUnknown = true} n'est pas une facilite : c'est ce qui permet au
 * service Identite d'enrichir sa reponse sans casser ce service au deploiement
 * suivant. Meme principe de <i>tolerant reader</i> que le service Audit vis-a-vis
 * du topic Kafka (CLAUDE.md section 9.2).
 *
 * @param id identifiant local, inscrit dans {@code id_createur}
 * @param login login annuaire, conserve pour la trace d'audit
 * @param nom nom de famille, tel que projete dans {@code utilisateurs}
 * @param prenom prenom
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuteurIdentifie(Long id, String login, String nom, String prenom) {

    /**
     * Libelle lisible, recopie dans la grille : « NKOLO Claire ».
     *
     * <p>Nom d'abord, comme dans les listes nominatives de la banque. Si l'un des
     * deux manque, on rend ce qu'on a plutot que de fabriquer un libelle a trous :
     * un nom seul reste identifiant, « NKOLO null » ne l'est pas.
     */
    public String libelle() {
        if (nom == null || nom.isBlank()) {
            return prenom == null || prenom.isBlank() ? login : prenom.strip();
        }
        if (prenom == null || prenom.isBlank()) {
            return nom.strip();
        }
        return nom.strip() + " " + prenom.strip();
    }

}
