package cm.afrilandfirstbank.rations.reporting.application;

import java.util.Collection;
import java.util.Map;

import cm.afrilandfirstbank.rations.reporting.domaine.LibelleActeur;

/**
 * Port de lecture vers le service Identite (Sprint 6.1).
 *
 * <h2>Un seul besoin : nommer les acteurs d'un historique</h2>
 *
 * <p>{@code etape_workflow.id_acteur} ne stocke qu'un nombre. « Valide par l'acteur
 * 7 » n'a aucune valeur pour un controle interne.
 *
 * <p><b>La portee d'acces n'est pas resolue ici.</b> Elle l'est par les services qui
 * detiennent les donnees — Workflow et Saisie —, depuis le jeton relaye. Ce service
 * ne consomme donc jamais {@code GET /identite/habilitation}, et l'obligation CT-04
 * qui l'accompagne pese sur ceux qui le consomment. Ses propres refus, eux, sont
 * traces par {@code GestionnaireErreursApi}, comme dans les cinq autres services.
 *
 * <h2>Un lot, jamais un appel par etape</h2>
 *
 * <p>Un historique compte quelques etapes et deux ou trois acteurs distincts. Ils
 * sont dedoublonnes puis demandes en <b>un seul appel</b>.
 */
public interface IdentiteLectureClient {

    /**
     * Traduit un lot d'identifiants en libelles.
     *
     * <p><b>Ne rend jamais d'echec.</b> Une carte vide en cas de panne, et
     * l'historique s'affiche avec ses identifiants nus. C'est le seul endroit du
     * module ou l'indisponibilite du service Identite n'entraine pas un refus, et
     * c'est justifie : il ne s'agit pas d'une decision d'acces mais d'un libelle
     * d'affichage. Le cloisonnement, lui, a deja ete tranche en amont par le service
     * Workflow, qui a rendu — ou refuse — l'historique.
     *
     * @return une carte identifiant vers libelle ; un identifiant absent signifie
     *         « pas de libelle disponible », jamais « acteur inexistant »
     */
    Map<Long, LibelleActeur> libelles(Collection<Long> identifiants, String enteteAutorisation);

}
