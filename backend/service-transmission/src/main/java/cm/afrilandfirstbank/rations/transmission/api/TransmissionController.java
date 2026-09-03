package cm.afrilandfirstbank.rations.transmission.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cm.afrilandfirstbank.rations.transmission.api.dto.StatutTransmissionResponse;
import cm.afrilandfirstbank.rations.transmission.api.dto.TransmissionResponse;
import cm.afrilandfirstbank.rations.transmission.application.ConsultationIntegrationService;
import cm.afrilandfirstbank.rations.transmission.application.TransmissionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Les deux routes du service Transmission : la consultation du statut d'integration, seul
 * endpoint au contrat de la passerelle, et le declenchement interne de la transmission.
 *
 * <pre>
 *   GET  /transmission/processus/{id}   statut d'integration   ARH + circuit   (5.3, CONTRAT)
 *   POST /transmission/processus/{id}   declenchement          DA, DR          (5.1, INTERNE)
 * </pre>
 *
 * <h2>Le POST est INTERNE, hors contrat passerelle</h2>
 *
 * <p>Le contrat d'API section 7 le dit sans ambiguite : « ce service n'expose pas
 * d'endpoint de declenchement au client ; la transmission est declenchee par le service
 * Workflow a la cloture ». {@code POST /transmission/processus/{id}} n'est donc <b>pas</b>
 * destine au frontend et n'a pas a etre route par la passerelle. Il rejoint les autres
 * endpoints internes du module :
 *
 * <table>
 *   <tr><td>{@code GET /identite/habilitation}</td><td>Sprint 1.3</td></tr>
 *   <tr><td>{@code GET /saisie/processus/{id}/etat}</td><td>Sprint 3.4</td></tr>
 *   <tr><td>{@code POST /transmission/processus/{id}}</td><td>Sprint 5.1</td></tr>
 *   <tr><td>{@code PUT /processus/{id}/integration}</td><td>Sprint 5.2</td></tr>
 *   <tr><td>{@code PUT /processus/{id}/transmission}</td><td>Sprint 5.3, verrou RG-13</td></tr>
 *   <tr><td>{@code GET /processus/{id}/integration}</td><td>Sprint 5.3, lu par ce service</td></tr>
 * </table>
 *
 * <p><b>Le compte du contrat reste donc de un endpoint</b> pour ce service : le
 * {@code GET} ci-dessous. Le {@code POST} ne s'y ajoute pas, comme les autres endpoints
 * internes ne se sont ajoutes ni aux trois de l'Identite, ni aux cinq de la Saisie, ni aux
 * six du Workflow.
 *
 * <h2>Pourquoi un POST et non un GET</h2>
 *
 * <p>Parce qu'il produit un effet irreversible hors du module : un evenement part vers la
 * comptabilite et ne se rattrape pas. Un {@code GET} laisserait croire a une lecture, et
 * n'importe quel outil qui prefetche des liens ou rejoue un journal d'acces declencherait
 * des paiements.
 *
 * <h2>Roles</h2>
 *
 * <p>Les deux valideurs du circuit, {@code CHEF_UNITE_DA} et {@code DIRECTEUR_RESEAU_DR},
 * parce que ce sont eux — et eux seuls — dont la validation prononce une cloture (RG-08).
 * Le service Workflow relaie leur jeton tel quel (doctrine Sprint 1.3) : aucune identite
 * machine n'existe au realm, et la portee d'acces verifiee par les deux services
 * interroges reste celle de la personne reellement a l'origine de la cloture.
 *
 * <p>Le role n'est ici que le premier filtre. Le controle qui compte est ailleurs : ce
 * service <b>relit l'etat a la source</b> et refuse tout ce qui n'est pas
 * {@code CLOTURE}. Un valideur ne peut donc pas se servir de cet endpoint pour envoyer en
 * paiement un etat encore en saisie.
 */
@RestController
@RequestMapping("/transmission/processus")
@Tag(name = "Transmission comptable",
        description = "Mise a disposition de l'etat valide (endpoint interne, hors passerelle)")
public class TransmissionController {

    private final TransmissionService transmissionService;
    private final ConsultationIntegrationService consultationIntegrationService;

    public TransmissionController(TransmissionService transmissionService,
            ConsultationIntegrationService consultationIntegrationService) {
        this.transmissionService = transmissionService;
        this.consultationIntegrationService = consultationIntegrationService;
    }

    /**
     * Publie l'etat cloture sur {@code rations.etat.valide} (US-12, CT-21).
     *
     * <p><b>Une reponse {@code 200} signifie que le broker a accuse reception</b>, et rien
     * de moins : c'est elle, et elle seule, qui autorise l'appelant a poser le drapeau
     * {@code transmis_comptabilite} de RG-13. Toute autre reponse veut dire « rien n'est
     * parti » — le drapeau doit alors rester a faux, sans quoi l'etat serait
     * definitivement impaye et la vraie transmission refusee ensuite comme un doublon.
     *
     * <p>Refus possibles : {@code 403 ACCES_REFUSE} role hors circuit ;
     * {@code 404 PROCESSUS_INTROUVABLE} ; {@code 422 ETAT_NON_CLOTURE} ;
     * {@code 500 CHARGE_INCOMPLETE} charge incomplete ou incoherente ;
     * {@code 503 SERVICE_WORKFLOW_INDISPONIBLE}, {@code 503 SERVICE_SAISIE_INDISPONIBLE},
     * {@code 503 PUBLICATION_ECHOUEE}.
     */
    @PostMapping("/{id}")
    @PreAuthorize("hasAnyRole('CHEF_UNITE_DA', 'DIRECTEUR_RESEAU_DR')")
    @Operation(summary = "Publie l'etat cloture sur le topic de l'etat valide",
            description = "Endpoint interne appele par le service Workflow a la cloture. "
                    + "Une reponse 200 atteste que le broker a accuse reception.")
    public ResponseEntity<TransmissionResponse> transmettre(
            @PathVariable Long id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation,
            HttpServletRequest requeteHttp) {

        return ResponseEntity.ok(TransmissionResponse.de(
                transmissionService.transmettre(id, enteteAutorisation,
                        requeteHttp.getRemoteAddr())));
    }

    /**
     * Statut d'integration comptable d'un etat (contrat d'API section 7, US-15,
     * Sprint 5.3). <b>C'est le seul endpoint de ce service au contrat de la passerelle</b>,
     * et il n'y en a pas un de plus.
     *
     * <h2>Roles ARH et circuit, comme le contrat le dit</h2>
     *
     * <p>L'analyste RH suit les paiements sans etre dans le circuit de validation ; les
     * trois acteurs du circuit suivent les dossiers qu'ils ont ouverts ou vises. Le role
     * n'est ici que le premier filtre : la <b>portee d'acces</b> est verifiee unite par
     * unite par le service Workflow, sur le jeton relaye tel quel (doctrine Sprint 1.3). Un
     * chef d'unite de {@code 00002} n'apprend rien d'un dossier de {@code 00007}.
     *
     * <h2>Cinq situations, jamais un champ vide sans explication</h2>
     *
     * <p>Le champ {@code situation} nomme ce qu'un statut d'integration nul ne sait pas
     * dire : jamais transmis, ou publication non confirmee. Le champ {@code message} le
     * redit en une phrase, y compris pour indiquer quoi faire quand quelque chose cloche.
     *
     * <p>Refus possibles : {@code 403 ACCES_REFUSE} role hors perimetre ;
     * {@code 403 UTILISATEUR_NON_HABILITE} hors portee ;
     * {@code 404 PROCESSUS_INTROUVABLE} ; {@code 503 SERVICE_WORKFLOW_INDISPONIBLE} — on
     * ne devine jamais un statut de paiement.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ARH', 'AGENT_UNITE', 'CHEF_UNITE_DA', 'DIRECTEUR_RESEAU_DR')")
    @Operation(summary = "Statut d'integration comptable d'un processus",
            description = "Rend le statut d'integration, la reference comptable, la date de "
                    + "traitement et le motif en cas de rejet, accompagnes d'une situation "
                    + "nommee et d'un message en clair.")
    public ResponseEntity<StatutTransmissionResponse> consulterStatut(
            @PathVariable Long id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation) {

        return ResponseEntity.ok(StatutTransmissionResponse.de(
                consultationIntegrationService.consulter(id, enteteAutorisation)));
    }

}
