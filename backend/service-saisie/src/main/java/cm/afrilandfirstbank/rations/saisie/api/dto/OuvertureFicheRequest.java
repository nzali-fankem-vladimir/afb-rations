package cm.afrilandfirstbank.rations.saisie.api.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Entrée de {@code POST /saisie/fiches} : le processus mensuel auquel la fiche
 * se rattache, et la journée qu'elle couvre.
 *
 * <p><b>Aucun {@code codeUnite} en entrée.</b> Il serait pourtant commode : la
 * vérification de portée d'accès en a besoin. Mais le laisser au client
 * reviendrait à demander à l'agent sur quelle unité il a le droit d'écrire — la
 * vérification ne vérifierait plus rien. Le code unité est lu dans la réponse de
 * {@code GET /processus/{id}} (service Workflow), avec le statut et la période,
 * en un seul appel ({@code docs/rattachement-processus.md} §3 et §5).
 *
 * <p><b>Aucune contrainte de date future ni de cohérence avec la période.</b> La
 * saisie rétroactive est explicitement supportée (Sprint 2.4 : le montant est
 * résolu à la date de la <i>prestation</i>). Quant à savoir si la journée tombe
 * dans le mois du processus, c'est une question qui appartient au processus, donc
 * au service Workflow : une annotation de validation ici en ferait une règle du
 * service Saisie, dupliquée et divergente le jour où elle changerait.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OuvertureFicheRequest(

        @NotNull(message = "l'identifiant du processus mensuel est obligatoire")
        @Positive(message = "l'identifiant du processus mensuel doit être un entier positif")
        Long idProcessus,

        @NotNull(message = "la date du jour est obligatoire, au format AAAA-MM-JJ")
        LocalDate dateJour) {
}
