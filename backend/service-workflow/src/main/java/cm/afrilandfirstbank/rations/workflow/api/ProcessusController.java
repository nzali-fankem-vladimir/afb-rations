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
import cm.afrilandfirstbank.rations.workflow.api.dto.SoumissionResponse;
import cm.afrilandfirstbank.rations.workflow.api.dto.ValidationResponse;
import cm.afrilandfirstbank.rations.workflow.application.ProcessusService;
import cm.afrilandfirstbank.rations.workflow.application.SoumissionService;
import cm.afrilandfirstbank.rations.workflow.application.ValidationService;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Les endpoints du processus mensuel (contrat d'API section 5).
 *
 * <pre>
 *   POST /processus                        declenchement       AGENT_UNITE        (4.1)
 *   GET  /processus/{id}                   detail et statut    roles du circuit   (4.1)
 *   GET  /processus/{id}/etat              etat consolide      roles du circuit   (4.1)
 *   POST /processus/{id}/soumission        soumission          AGENT_UNITE        (4.2)
 *   POST /processus/{id}/validation        validation DA       CHEF_UNITE_DA      (4.3)
 * </pre>
 *
 * <p><b>{@code POST /processus/{id}/retour} n'existe pas encore</b>, et la
 * validation n'est pas encore ouverte au directeur reseau : ce sont le sous-sprint
 * 4.4. Rien n'est cree par anticipation — un endpoint declare mais inoperant est
 * pire qu'un endpoint absent, il se decouvre a l'usage.
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
    private final SoumissionService soumissionService;
    private final ValidationService validationService;

    public ProcessusController(ProcessusService processusService,
            SoumissionService soumissionService,
            ValidationService validationService) {
        this.processusService = processusService;
        this.soumissionService = soumissionService;
        this.validationService = validationService;
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

    /**
     * Soumission de l'etat mensuel par l'agent d'unite (US-07, CT-12, CT-13).
     *
     * <p><b>Reserve a {@code AGENT_UNITE}.</b> Le role n'est ici que le premier
     * filtre : la portee d'acces est verifiee en plus, sur l'unite <i>du
     * processus</i>, aupres du service Identite. Un chef d'unite ne soumet pas a la
     * place de son agent : ce serait un cumul saisie / validation que RG-12
     * interdit.
     *
     * <p><b>Aucun corps de requete.</b> Tout ce qui est necessaire se deduit du
     * processus et du jeton : rien n'est laisse au choix de l'appelant, et surtout
     * pas le montant, qui vient de l'etat consolide et de nulle part ailleurs.
     *
     * <p>{@code 200} et non {@code 201} : la soumission ne cree pas la ressource
     * adressee, elle en change l'etat. La piece jointe, elle, est bien creee, et
     * la reponse en rend compte.
     */
    @PostMapping("/{id}/soumission")
    @PreAuthorize("hasRole('AGENT_UNITE')")
    public ResponseEntity<SoumissionResponse> soumettre(
            @PathVariable Long id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation,
            HttpServletRequest requeteHttp) {

        return ResponseEntity.ok(SoumissionResponse.depuis(
                soumissionService.soumettre(id, enteteAutorisation, requeteHttp.getRemoteAddr())));
    }

    /**
     * Validation de premier niveau par le Chef d'Unite, avec aiguillage au seuil
     * (US-08, US-09, CT-14, CT-15).
     *
     * <p><b>Reserve a {@code CHEF_UNITE_DA} au sous-sprint 4.3.</b> Le contrat
     * d'API destine cet endpoint aux deux valideurs, « DA ou DR selon le niveau » ;
     * le second niveau est le sous-sprint 4.4, et le role du directeur reseau y sera
     * ajoute avec le code qui le sert. Ouvrir le role avant d'avoir la transition
     * {@code EN_ATTENTE_DR -> CLOTURE} laisserait le directeur reseau devant un
     * refus de statut incomprehensible.
     *
     * <p>Le role n'est que le premier filtre : la portee d'acces est verifiee en
     * plus, sur l'unite <i>du processus</i>, aupres du service Identite.
     *
     * <p><b>Aucun corps de requete.</b> Le montant vient du processus, le seuil de
     * {@code parametre_systeme} : rien n'est laisse au choix de l'appelant, et
     * surtout pas la valeur qui decide du niveau d'approbation requis.
     *
     * <p>{@code 200} et non {@code 201} : la validation ne cree pas la ressource
     * adressee, elle en change l'etat. Refus possibles : {@code 422
     * TRANSITION_INTERDITE} hors statut {@code EN_ATTENTE_DA} ; {@code 403} hors
     * role ou hors portee ; {@code 500 SEUIL_INDISPONIBLE} si le seuil RG-08 n'est
     * pas lisible ; {@code 503} si le service Identite ne repond pas.
     */
    @PostMapping("/{id}/validation")
    @PreAuthorize("hasRole('CHEF_UNITE_DA')")
    public ResponseEntity<ValidationResponse> valider(
            @PathVariable Long id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation,
            HttpServletRequest requeteHttp) {

        return ResponseEntity.ok(ValidationResponse.depuis(
                validationService.valider(id, enteteAutorisation, requeteHttp.getRemoteAddr())));
    }

}
