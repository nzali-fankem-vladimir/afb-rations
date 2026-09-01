package cm.afrilandfirstbank.rations.workflow.application;

import cm.afrilandfirstbank.rations.workflow.domaine.RoleEnum;

/**
 * La personne qui signe, telle que le service Identite la connait.
 *
 * <p>Les trois donnees viennent d'un <b>appel unique</b> a {@code GET /identite/moi},
 * qui rend {@code id}, {@code login}, {@code nom}, {@code prenom}, {@code role} et
 * {@code codeUnite}.
 *
 * <p><b>Cet appel est impose par le schema, pas choisi</b> — meme motif qu'au
 * Sprint 2.2 pour {@code id_createur} sur les grilles :
 * {@code etape_workflow.id_acteur} est {@code BIGINT NOT NULL}, et
 * {@code GET /identite/habilitation} ne rend qu'un {@code login}, jamais
 * l'identifiant local. Sans cet appel, aucune etape de workflow ne peut etre
 * ecrite.
 *
 * @param id identifiant local, destine a {@code etape_workflow.id_acteur}
 * @param login identite portee par la mention imprimee sur le document
 * @param role role applicatif au moment de l'acte. <b>Recopie et fige</b> dans la
 *        mention : un changement de role ulterieur ne doit pas reecrire l'histoire
 *        d'un document deja signe
 */
public record ActeurSignataire(Long id, String login, RoleEnum role) {
}
