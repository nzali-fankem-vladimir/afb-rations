package cm.afrilandfirstbank.rations.reporting.infrastructure.workflow;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Les charges rendues par les deux endpoints internes du service Workflow
 * (Sprint 6.1).
 *
 * <p><b>Tolerant reader</b> partout : le service Workflow peut enrichir ses
 * reponses sans casser celui-ci. Tolerance a la <i>lecture</i> seulement — les
 * types sont boites, un champ absent se lit {@code null}, et c'est le client qui
 * decide si l'absence est acceptable. Meme discipline qu'aux Sprints 4.1 et 5.3.
 *
 * <p>Les statuts sont lus en <b>chaine</b>, jamais convertis en enumeration locale :
 * le reporting les presente, il ne decide rien sur leur base. Une valeur ajoutee
 * plus tard doit s'afficher, pas faire echouer une page entiere.
 */
public final class ReponseWorkflow {

    private ReponseWorkflow() {
    }

    /** Reponse de {@code GET /processus/recherche}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Recherche(Long nombreTotal, Boolean tronque, List<EnTete> contenu) {
    }

    /** Un en-tete d'etat mensuel. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EnTete(
            Long id,
            Integer moisPaiement,
            Integer anneePaiement,
            String codeUnite,
            String typeProcessus,
            Integer montantTotal,
            String statut,
            Boolean transmisComptabilite,
            String statutIntegration,
            LocalDateTime dateCreation) {
    }

    /** Reponse de {@code GET /processus/{id}/historique}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Historique(
            Long idProcessus,
            Integer moisPaiement,
            Integer anneePaiement,
            String codeUnite,
            String statut,
            List<Etape> etapes) {
    }

    /** Une etape du circuit. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Etape(
            Integer ordreEtape,
            String nomEtape,
            String statutEtape,
            Long idActeur,
            String motifRetour,
            Boolean signee,
            LocalDateTime dateAction) {
    }

    /**
     * Le format d'erreur uniforme du module ({@code timestamp, status, code, message,
     * path}). Lu pour <b>reprendre le message tel quel</b> : un refus formule par le
     * service qui detient la regle est toujours plus juste qu'une phrase reecrite en
     * chemin.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Erreur(String code, String message) {
    }

}
