package cm.afrilandfirstbank.rations.workflow.application;

/**
 * Port vers {@code GET /identite/moi}, pour connaitre l'auteur d'une ecriture.
 *
 * <h2>Cet appel est impose par le schema, pas choisi</h2>
 *
 * <p>{@code etape_workflow.id_acteur} est {@code BIGINT NOT NULL}, et le jeton
 * Keycloak ne porte pas l'identifiant local (CLAUDE.md section 10). Or
 * {@code GET /identite/habilitation}, deja appele pour la portee d'acces, ne rend
 * qu'un {@code login} : il ne peut donc pas servir ici. <b>Sans cet appel, aucune
 * etape de workflow ne peut etre ecrite.</b> C'est exactement le motif du
 * Sprint 2.2 pour {@code id_createur} sur les grilles.
 *
 * <h2>Ce que cela coute, et pourquoi c'est accepte</h2>
 *
 * <p>La soumission enchaine ainsi <b>trois</b> appels sortants : habilitation,
 * profil, consolidation. Avec les delais du Sprint 3.2 (2 s de connexion, 3 s de
 * lecture, aucun reessai), le pire cas atteint quinze secondes. C'est assume : la
 * soumission est un geste <b>mensuel</b>, pas un geste par ligne de prestation.
 * Le budget de trois secondes du Sprint 3.2 visait la saisie, ou chaque ligne
 * paie le prix.
 *
 * <p>Le jeton de l'utilisateur final est relaye tel quel : ce service ne
 * s'authentifie pas avec un compte de service, le realm n'en ayant aucun
 * (doctrine Sprint 1.3).
 */
public interface ProfilClient {

    ResultatProfil obtenir(String enteteAutorisation);

}
