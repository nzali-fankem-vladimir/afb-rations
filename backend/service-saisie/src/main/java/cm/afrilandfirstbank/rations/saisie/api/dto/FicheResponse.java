package cm.afrilandfirstbank.rations.saisie.api.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import cm.afrilandfirstbank.rations.saisie.domaine.FicheJournaliere;
import cm.afrilandfirstbank.rations.saisie.domaine.StatutFicheEnum;

/**
 * Vue de sortie d'une fiche journalière, avec ses lignes et leur sous-total.
 *
 * <h2>La fiche est rendue avec ses lignes — c'est ce qui rend RG-05 démontrable</h2>
 *
 * <p>RG-05 dit qu'un nouveau jour ouvre une fiche neuve. Elle ne dit pas qu'une
 * réouverture efface la saisie en cours : l'interprétation destructrice ferait
 * perdre le travail de l'agent au moindre rafraîchissement d'écran (guide 3.3
 * §10). Rendre les lignes dans la réponse d'ouverture rend la différence
 * <i>visible</i> : la seconde ouverture du même jour ramène la même fiche, ses
 * lignes intactes ; l'ouverture d'un autre jour en ramène une vide.
 *
 * <h2>Le sous-total est calculé, jamais stocké</h2>
 *
 * <p>{@code sousTotalFcfa} est la somme des montants des lignes, produite à la
 * lecture. Le persister créerait un second montant de référence à tenir
 * cohérent avec les lignes à chaque création, modification et suppression — trois
 * occasions de divergence, sur une donnée financière. La consolidation mensuelle
 * (RG-06) relève du sous-sprint 3.4 et porte sur le processus, pas sur la fiche.
 *
 * <p>Somme en {@code long} alors que chaque montant est un {@code int} : la
 * somme d'entiers positifs ne peut pas déborder sur ce type, quel que soit le
 * nombre de lignes.
 *
 * <p><b>{@code codeUnite}, {@code moisPaiement} et {@code anneePaiement}</b> sont
 * les valeurs recopiées du processus mensuel à l'ouverture, figées
 * ({@code docs/rattachement-processus.md} §5). Elles sont rendues telles quelles :
 * elles renseignent l'interface sans qu'elle ait à interroger le service Workflow.
 */
public record FicheResponse(
        Long id,
        Long idProcessus,
        LocalDate dateJour,
        StatutFicheEnum statut,
        String codeUnite,
        Integer moisPaiement,
        Integer anneePaiement,
        List<LigneResponse> lignes,
        int nombreLignes,
        long sousTotalFcfa,
        LocalDateTime dateCreation) {

    public static FicheResponse depuis(FicheJournaliere fiche, List<LigneResponse> lignes) {
        long sousTotal = lignes.stream()
                .mapToLong(LigneResponse::montantApplique)
                .sum();

        return new FicheResponse(
                fiche.getId(),
                fiche.getIdProcessus(),
                fiche.getDateJour(),
                fiche.getStatut(),
                fiche.getCodeUnite(),
                fiche.getMoisPaiement(),
                fiche.getAnneePaiement(),
                lignes,
                lignes.size(),
                sousTotal,
                fiche.getDateCreation());
    }

}
