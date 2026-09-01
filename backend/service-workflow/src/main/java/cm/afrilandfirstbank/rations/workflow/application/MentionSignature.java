package cm.afrilandfirstbank.rations.workflow.application;

import java.time.LocalDateTime;

import cm.afrilandfirstbank.rations.workflow.domaine.NomEtapeEnum;

/**
 * Une signature apposee sur le document de l'etat mensuel (RG-09).
 *
 * <p>RG-09 exige une signature <b>horodatee a chaque validation</b> ; l'etape 5
 * du guide precise ce qu'elle enregistre : « le nom de l'acteur, son role, la
 * date et l'heure ». Ce sont les trois donnees portees ici.
 *
 * <p><b>L'identite retenue est le login, pas le nom d'usage</b> (arbitrage
 * Sprint 4.2). Deux raisons : le login est la cle de rapprochement avec le
 * journal d'audit, qui n'enregistre lui aussi qu'un login (decision Sprint 3.3) ;
 * et il est stable, la ou un nom d'usage se corrige, s'accentue ou se reordonne.
 * Sur un document justificatif, pouvoir recouper une signature avec la trace
 * d'audit correspondante vaut mieux que la lire agreablement.
 *
 * @param etape le pas du circuit auquel cette signature se rattache. Il commande
 *        <b>l'emplacement</b> de la mention sur la page des visas : chaque etape a
 *        son cadre reserve, de sorte qu'une signature ajoutee plus tard vienne
 *        s'inscrire dans un espace laisse vide, sans jamais toucher aux
 *        precedentes
 * @param login identifiant de la personne, tel que le service Identite le connait
 * @param role son role applicatif au moment de l'acte, <b>recopie et fige</b> : un
 *        changement de role ulterieur ne doit pas reecrire l'histoire d'un
 *        document deja signe (motif du libelle fige, Sprint 2.2)
 * @param horodatage date et heure de l'apposition
 */
public record MentionSignature(
        NomEtapeEnum etape,
        String login,
        String role,
        LocalDateTime horodatage) {
}
