package cm.afrilandfirstbank.rations.saisie.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cm.afrilandfirstbank.rations.saisie.api.dto.EtatConsolideResponse;
import cm.afrilandfirstbank.rations.saisie.application.ConsolidationService;
import cm.afrilandfirstbank.rations.saisie.application.EtatConsolide;

/**
 * L'<b>unique endpoint interne</b> du service Saisie : l'état mensuel consolidé
 * d'un processus, consommé par le service Workflow (RG-06, Sprint 3.4).
 *
 * <h2>Interne, et hors du contrat exposé par la passerelle</h2>
 *
 * <p>Le contrat d'API décrit {@code GET /processus/{id}/etat} — mais côté
 * <b>service Workflow</b>, port 8084, routé par la passerelle et compté dans les
 * 26 endpoints du module (CLAUDE.md §11). Celui-ci, {@code GET
 * /saisie/processus/{id}/etat}, vit côté <b>service Saisie</b>, port 8082, n'est
 * pas routé vers le frontend et ne change pas ce compte. Workflow reprendra cette
 * réponse, y ajoutera ce qu'il détient seul — statut, type de processus, montant
 * total porté sur {@code processus_mensuel} — et servira l'endpoint public.
 *
 * <p>C'est exactement le statut de {@code GET /identite/habilitation?codeUnite=…}
 * (décision Sprint 1.3) : un endpoint interne qui répond à une question posée par
 * un autre service. Voir
 * {@code docs/decisions/2026-08-31-endpoint-interne-de-consolidation.md} et
 * {@code docs/appel-consolidation.md}.
 *
 * <h2>Pourquoi un contrôleur séparé de {@link SaisieController}</h2>
 *
 * <p>Les rôles diffèrent. {@link SaisieController} est réservé à
 * {@code AGENT_UNITE} : lui seul saisit. Ici s'ajoutent {@code CHEF_UNITE_DA} et
 * {@code DIRECTEUR_RESEAU_DR}, qui doivent <b>lire l'état qu'ils sont en train de
 * valider</b> — c'est ce que dit le contrat d'API §5 pour
 * {@code /processus/{id}/etat} (« rôles du circuit »). Les enfermer dans le
 * contrôleur de saisie aurait imposé d'assouplir le rôle des cinq endpoints
 * d'écriture, ou bloqué le workflow du Sprint 4 dès son premier essai.
 *
 * <p>Le rôle n'est que le premier filtre : la <b>portée d'accès</b> est vérifiée
 * en plus, unité par unité, dans {@code ConsolidationService}.
 */
@RestController
@RequestMapping("/saisie")
@PreAuthorize("hasAnyRole('AGENT_UNITE', 'CHEF_UNITE_DA', 'DIRECTEUR_RESEAU_DR')")
public class ConsolidationController {

    private final ConsolidationService consolidationService;

    public ConsolidationController(ConsolidationService consolidationService) {
        this.consolidationService = consolidationService;
    }

    /**
     * État mensuel consolidé d'un processus : chaque journée avec ses lignes et
     * son sous-total, puis le total du mois.
     *
     * <p><b>{@code codeUnite} est obligatoire</b>, sans valeur par défaut. Il est
     * fourni par le service Workflow, qui détient {@code processus_mensuel} et
     * connaît donc l'unité du processus. Deux raisons, dans cet ordre :
     *
     * <ol>
     *   <li><b>La portée d'accès reste vérifiable sur un état vide.</b> Un
     *       processus sans aucune fiche n'a aucun code unité à lire ; sans ce
     *       paramètre, le contrôle disparaîtrait silencieusement au moment précis
     *       où il n'y a rien à protéger, et un chef d'unité se verrait refuser un
     *       dossier vide de sa propre unité.</li>
     *   <li><b>Aucun aller-retour circulaire.</b> Redemander l'unité au service
     *       Workflow produirait Workflow → Saisie → Workflow sur le chemin le plus
     *       emprunté du Sprint 4 : latence doublée et dépendance mutuelle à
     *       l'exécution entre deux services.</li>
     * </ol>
     *
     * <p>La valeur déclarée est ensuite <b>recoupée</b> contre le code unité figé
     * sur les fiches, qui fait autorité ({@code 403 UNITE_NON_CONCORDANTE} en cas
     * de désaccord) : un paramètre fourni par l'appelant ne se croit pas sur
     * parole.
     *
     * <p>Un paramètre absent est un {@code 400 REQUETE_INVALIDE}, rendu par
     * {@link GestionnaireErreursApi}. Un repli silencieux sur les fiches
     * rouvrirait le trou du point 1, et sans jamais déclencher d'erreur — même
     * raison qu'au Sprint 2.4 pour le paramètre {@code date} de
     * {@code GET /grilles/active}.
     *
     * <p>Toujours {@code 200}, y compris pour un processus sans aucune journée
     * saisie : la réponse porte alors zéro journée et un total de zéro.
     */
    @GetMapping("/processus/{id}/etat")
    public ResponseEntity<EtatConsolideResponse> consulterEtatConsolide(
            @PathVariable Long id,
            @RequestParam String codeUnite,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation) {

        EtatConsolide etat = consolidationService.consolider(id, codeUnite, enteteAutorisation);

        return ResponseEntity.ok(EtatConsolideResponse.depuis(etat));
    }

}
