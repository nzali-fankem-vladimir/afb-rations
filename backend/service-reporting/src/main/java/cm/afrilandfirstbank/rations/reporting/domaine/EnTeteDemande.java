package cm.afrilandfirstbank.rations.reporting.domaine;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Un etat mensuel vu par le suivi (Sprint 6.1, US-15).
 *
 * <p>Recopie de ce que rend {@code GET /processus/recherche} cote Workflow. Ce
 * service ne detient rien : cet objet ne vit que le temps d'une requete.
 *
 * <h2>Les statuts voyagent en chaine, deliberement</h2>
 *
 * <p>{@code statut} et {@code statutIntegration} ne sont pas convertis en
 * enumerations locales. Le service Reporting ne <b>decide</b> rien sur leur base :
 * il les presente. Une valeur ajoutee plus tard cote Workflow ou cote comptabilite
 * doit s'afficher, pas faire echouer toute une page de suivi. C'est la meme
 * discipline qu'au Sprint 5.2, ou le statut d'un accuse voyage en chaine pour que
 * le refus soit nomme au lieu d'etre subi dans le conteneur Kafka.
 *
 * <p>Les enumerations recopiees de ce service — {@link NatureEnum},
 * {@link SessionEnum} — sont celles des <b>filtres d'entree</b>, ou l'inverse vaut :
 * une valeur inconnue doit etre refusee en {@code 400}, jamais transmise a un autre
 * service.
 *
 * @param transmisComptabilite necessaire a cote de {@code statutIntegration}, qui
 *        est nul dans deux situations sans rapport (Sprint 5.3)
 */
public record EnTeteDemande(
        Long id,
        LocalDate dateDebut,
        LocalDate dateFin,
        String codeUnite,
        String typeProcessus,
        int montantTotal,
        String statut,
        boolean transmisComptabilite,
        String statutIntegration,
        LocalDateTime dateCreation) {

    /** La situation d'integration nommee, deduite du couple de colonnes. */
    public SituationIntegration situationIntegration() {
        return SituationIntegration.deduire(transmisComptabilite, statutIntegration);
    }

}
