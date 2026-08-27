package cm.afrilandfirstbank.rations.identite.domaine;

/**
 * Reponse a la question : cet utilisateur a-t-il le droit d'agir sur un dossier
 * rattache a ce code unite ?
 *
 * <p>Resultat de l'application de {@link PorteeAcces} a un code unite precis.
 * Objet-valeur immuable, sans dependance technique : le service Identite le
 * produit, le controleur le traduit en DTO de sortie.
 *
 * @param login login annuaire de l'utilisateur resolu depuis le jeton
 * @param role role applicatif local
 * @param codeUniteDemande le code unite interroge, repris tel quel
 * @param autorise vrai si la portee de l'utilisateur couvre ce code unite
 * @param porteeNationale vrai pour un role a portee nationale ; dans ce cas
 *        {@code autorise} vaut toujours vrai
 */
public record ResultatHabilitation(
        String login,
        RoleEnum role,
        String codeUniteDemande,
        boolean autorise,
        boolean porteeNationale) {

}
