package cm.afrilandfirstbank.rations.transmission.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cm.afrilandfirstbank.rations.transmission.api.dto.TransmissionResponse;
import cm.afrilandfirstbank.rations.transmission.application.TransmissionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Declenchement de la transmission d'un etat cloture vers la comptabilite.
 *
 * <h2>Endpoint INTERNE, hors contrat passerelle</h2>
 *
 * <p>Le contrat d'API section 7 le dit sans ambiguite : « ce service n'expose pas
 * d'endpoint de declenchement au client ; la transmission est declenchee par le service
 * Workflow a la cloture ». {@code POST /transmission/processus/{id}} n'est donc <b>pas</b>
 * destine au frontend et n'a pas a etre route par la passerelle. Il rejoint les deux
 * autres endpoints internes du module :
 *
 * <table>
 *   <tr><td>{@code GET /identite/habilitation}</td><td>Sprint 1.3</td></tr>
 *   <tr><td>{@code GET /saisie/processus/{id}/etat}</td><td>Sprint 3.4</td></tr>
 *   <tr><td>{@code POST /transmission/processus/{id}}</td><td>Sprint 5.1</td></tr>
 * </table>
 *
 * <p><b>Le compte du contrat reste donc de un endpoint</b> pour ce service :
 * {@code GET /transmission/processus/{id}}, la consultation du statut d'integration, qui
 * releve du sous-sprint 5.3. Ce POST ne s'y ajoute pas, comme les deux precedents ne se
 * sont ajoutes ni aux trois de l'Identite ni aux cinq de la Saisie.
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

    public TransmissionController(TransmissionService transmissionService) {
        this.transmissionService = transmissionService;
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

}
