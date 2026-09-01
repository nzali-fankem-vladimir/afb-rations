package cm.afrilandfirstbank.rations.workflow.api;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cm.afrilandfirstbank.rations.workflow.api.dto.DeclenchementProcessusRequest;
import cm.afrilandfirstbank.rations.workflow.api.dto.EtatProcessusResponse;
import cm.afrilandfirstbank.rations.workflow.api.dto.ProcessusResponse;
import cm.afrilandfirstbank.rations.workflow.application.ProcessusService;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Les trois endpoints du processus mensuel livres au Sprint 4.1 (contrat d'API
 * section 5).
 *
 * <pre>
 *   POST /processus              declenchement          AGENT_UNITE
 *   GET  /processus/{id}         detail et statut       roles du circuit
 *   GET  /processus/{id}/etat    etat consolide         roles du circuit
 * </pre>
 *
 * <p><b>Les trois autres endpoints de la section 5 n'existent pas encore</b> —
 * {@code /soumission}, {@code /validation}, {@code /retour} sont les sous-sprints
 * 4.2 a 4.4. Aucun n'est cree par anticipation : un endpoint declare mais
 * inoperant est pire qu'un endpoint absent, il se decouvre a l'usage.
 *
 * <h2>Les roles ne sont pas les memes selon l'endpoint</h2>
 *
 * <p>D'ou {@code @PreAuthorize} pose par methode et non sur la classe. Seul
 * l'agent d'unite ouvre un etat ; le chef d'unite et le directeur reseau doivent
 * en revanche pouvoir <b>lire l'etat qu'ils vont valider</b>, sans quoi le
 * circuit se bloquerait des son premier essai. C'est le meme decoupage qu'au
 * Sprint 3.4 cote Saisie, ou il avait impose un controleur separe.
 *
 * <p><b>Le role n'est que le premier filtre.</b> La portee d'acces — cet acteur
 * a-t-il droit sur <i>cette unite</i> — est verifiee en plus, unite par unite,
 * aupres du service Identite (RG-12). Un agent d'unite habilite sur {@code 00002}
 * ne declenche ni ne lit un dossier de {@code 00007}, et son role ne le distingue
 * pas de l'agent legitime.
 *
 * <h2>Le jeton est relaye, pas reemis</h2>
 *
 * <p>Chaque methode recoit l'en-tete {@code Authorization} et le transmet tel
 * quel aux appels sortants (Identite, Saisie). Le realm ne comporte aucun compte
 * de service, et la question posee en aval porte sur l'utilisateur final
 * (doctrine Sprint 1.3).
 */
@RestController
@RequestMapping("/processus")
public class ProcessusController {

    private final ProcessusService processusService;

    public ProcessusController(ProcessusService processusService) {
        this.processusService = processusService;
    }

    /**
     * Declenche l'etat mensuel d'une unite. {@code 201} avec le processus cree, au
     * statut {@code EN_COURS_SAISIE}.
     *
     * <p>Refus possibles : {@code 409 PROCESSUS_EXISTANT} si un etat NORMAL est
     * deja ouvert pour cette unite et cette periode ;
     * {@code 422 FONCTIONNALITE_NON_OUVERTE} si un type {@code COMPLEMENTAIRE} est
     * demande ; {@code 403} hors portee ; {@code 503} si le service Identite ne
     * repond pas.
     *
     * <p>L'en-tete {@code Location} pointe vers la ressource creee, comme le veut
     * la convention REST pour un {@code 201}.
     */
    @PostMapping
    @PreAuthorize("hasRole('AGENT_UNITE')")
    public ResponseEntity<ProcessusResponse> declencher(
            @Valid @RequestBody DeclenchementProcessusRequest requete,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation,
            HttpServletRequest requeteHttp) {

        ProcessusMensuel processus = processusService.declencher(
                requete, enteteAutorisation, requeteHttp.getRemoteAddr());

        return ResponseEntity
                .created(URI.create("/processus/" + processus.getId()))
                .body(ProcessusResponse.depuis(processus));
    }

    /**
     * Detail et statut d'un processus.
     *
     * <p>Sert aussi le service Saisie, qui interroge cet endpoint a chaque
     * ecriture pour savoir si l'etat est encore modifiable
     * ({@code docs/rattachement-processus.md} section 4). Les cinq premiers champs
     * de {@link ProcessusResponse} sont donc un contrat inter-services : voir
     * l'action B-04 de {@code docs/dispositifs_provisoires.md}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT_UNITE', 'CHEF_UNITE_DA', 'DIRECTEUR_RESEAU_DR')")
    public ResponseEntity<ProcessusResponse> consulter(
            @PathVariable Long id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation) {

        ProcessusMensuel processus = processusService.consulter(id, enteteAutorisation);

        return ResponseEntity.ok(ProcessusResponse.depuis(processus));
    }

    /**
     * Etat mensuel consolide : le detail journee par journee, obtenu du service
     * Saisie par appel d'API, joint a ce que Workflow detient seul.
     *
     * <p>Aucun acces a la base {@code rations_saisie} : l'echange passe par
     * l'endpoint interne {@code GET /saisie/processus/{id}/etat}, et le code unite
     * transmis est celui du processus, pas un parametre de la requete.
     *
     * <p>Un processus sans aucune journee saisie rend {@code 200} avec zero
     * journee et un total de zero — c'est un etat normal, pas une erreur. Service
     * Saisie injoignable : {@code 503 SERVICE_SAISIE_INDISPONIBLE}, jamais un
     * total suppose.
     */
    @GetMapping("/{id}/etat")
    @PreAuthorize("hasAnyRole('AGENT_UNITE', 'CHEF_UNITE_DA', 'DIRECTEUR_RESEAU_DR')")
    public ResponseEntity<EtatProcessusResponse> consulterEtat(
            @PathVariable Long id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation) {

        return ResponseEntity.ok(EtatProcessusResponse.depuis(
                processusService.consulterEtat(id, enteteAutorisation)));
    }

}
