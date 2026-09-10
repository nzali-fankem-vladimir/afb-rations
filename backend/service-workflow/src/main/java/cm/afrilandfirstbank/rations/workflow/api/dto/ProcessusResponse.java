package cm.afrilandfirstbank.rations.workflow.api.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import cm.afrilandfirstbank.rations.workflow.application.ProcessusService.DetailProcessus;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.TypeProcessusEnum;

/**
 * Detail et statut d'un processus mensuel — corps de {@code POST /processus}
 * ({@code 201}) et de {@code GET /processus/{id}} ({@code 200}).
 *
 * <h2>Les cinq premiers champs sont un contrat inter-services, pas un choix
 * d'affichage</h2>
 *
 * <p>{@code idProcessus}, {@code statut}, {@code codeUnite}, {@code moisPaiement}
 * et {@code anneePaiement} sont exactement ceux que lit
 * {@code ProcessusReponse} du <b>service Saisie</b>, ecrit au Sprint 3.3 contre
 * un contrat deduit alors que ce service n'existait pas encore. C'est l'action
 * B-04 de {@code docs/dispositifs_provisoires.md} : les renommer ferait refuser
 * <b>toute ecriture de ligne de prestation</b> en {@code 503}, avec le message
 * « reponse 200 sans unite ni periode exploitables » — une panne complete de la
 * saisie, sans erreur de compilation nulle part.
 *
 * <p>Les champs suivants sont libres : {@code ProcessusReponse} est un
 * <i>tolerant reader</i> ({@code @JsonIgnoreProperties(ignoreUnknown = true)}) et
 * ignore ce qu'il ne connait pas. On peut donc enrichir cette reponse sans risque,
 * jamais l'amputer ni la renommer.
 *
 * <p>{@code montantTotal} porte le nom de l'exemple du contrat d'API section 5.
 * Il vaut {@code 0} tant que l'etat n'a pas ete soumis : c'est la soumission
 * (Sprint 4.2) qui y reporte le total rendu par le service Saisie.
 */
public record ProcessusResponse(
        Long idProcessus,
        StatutEnum statut,
        String codeUnite,
        LocalDate dateDebut,
        LocalDate dateFin,
        TypeProcessusEnum typeProcessus,
        Long idProcessusOrigine,
        String motifOuverture,
        int montantTotal,
        boolean transmisComptabilite,
        LocalDateTime dateCreation,
        String motifRetour) {

    /**
     * Le detail complet : le processus, et le motif du retour en cours s'il y en a un
     * (US-11 — « le motif de retour est visible par l'agent »).
     *
     * <p><b>Champ ajoute en fin de record</b>, comme {@code manques} sur le format
     * d'erreur au Sprint 4.2 : les onze champs anterieurs gardent leur nom, leur type
     * et leur ordre. Les cinq premiers sont un contrat inter-services — le service
     * Saisie les lit a chaque ecriture de ligne pour savoir si l'etat est encore
     * modifiable — et les renommer ferait refuser toute saisie en {@code 503}, sans
     * aucune erreur de compilation (action B-04, Sprint 4.1).
     *
     * <p>{@code motifRetour} est nul partout ailleurs que sur un etat
     * {@code RETOURNE} : voir {@code ProcessusService.motifDuRetourEnCours}.
     */
    public static ProcessusResponse depuis(DetailProcessus detail) {
        return depuis(detail.processus(), detail.motifRetour());
    }

    /**
     * Sans motif de retour : la forme rendue au declenchement, ou l'etat vient de
     * naitre et n'a par construction jamais ete retourne.
     */
    public static ProcessusResponse depuis(ProcessusMensuel processus) {
        return depuis(processus, null);
    }

    private static ProcessusResponse depuis(ProcessusMensuel processus, String motifRetour) {
        return new ProcessusResponse(
                processus.getId(),
                processus.getStatut(),
                processus.getCodeUnite(),
                processus.getDateDebut(),
                processus.getDateFin(),
                processus.getTypeProcessus(),
                processus.getIdProcessusOrigine(),
                processus.getMotifOuverture(),
                processus.getMontantTotal(),
                processus.isTransmisComptabilite(),
                processus.getDateCreation(),
                motifRetour);
    }

}
