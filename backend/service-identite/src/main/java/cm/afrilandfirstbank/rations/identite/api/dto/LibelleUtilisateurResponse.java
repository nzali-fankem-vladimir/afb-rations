package cm.afrilandfirstbank.rations.identite.api.dto;

import cm.afrilandfirstbank.rations.identite.domaine.Utilisateur;

/**
 * Un identifiant local traduit en libelle lisible, tel que le rend l'endpoint
 * <b>interne</b> {@code GET /identite/utilisateurs/libelles} (Sprint 6.1).
 *
 * <h2>Ce que ce DTO porte, et ce qu'il ne porte pas</h2>
 *
 * <p>Le {@code login} d'abord : c'est la cle de rapprochement avec le journal
 * d'audit, et c'est deja lui que porte le cadre de visa des documents signes
 * (RG-09, Sprint 4.2). Le nom et le prenom ensuite, pour un affichage humain.
 *
 * <p><b>Pas le role.</b> Le role est celui d'<i>aujourd'hui</i>, pas celui que
 * l'acteur portait au moment ou il a valide : un administrateur peut l'avoir
 * change depuis (Sprint 1.2). L'afficher a cote d'une etape de 2026 laisserait
 * croire a une information historique qu'il n'est pas. Le niveau auquel l'acteur
 * est intervenu est deja porte par {@code etape_workflow.nom_etape}, qui lui ne
 * bouge plus.
 *
 * <p><b>Pas le code unite</b> non plus, pour la meme raison, et parce qu'un
 * historique n'a pas a reveler le rattachement courant d'une personne a qui le
 * consulte.
 */
public record LibelleUtilisateurResponse(
        Long id,
        String login,
        String nom,
        String prenom) {

    public static LibelleUtilisateurResponse depuis(Utilisateur utilisateur) {
        return new LibelleUtilisateurResponse(
                utilisateur.getId(),
                utilisateur.getLogin(),
                utilisateur.getNom(),
                utilisateur.getPrenom());
    }

}
