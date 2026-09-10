package cm.afrilandfirstbank.rations.saisie.api;

import java.time.LocalDate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cm.afrilandfirstbank.rations.saisie.api.dto.RechercheLignesResponse;
import cm.afrilandfirstbank.rations.saisie.application.RechercheLignesService;
import cm.afrilandfirstbank.rations.saisie.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.SessionEnum;

/**
 * Recherche d'etats par criteres de prestation.
 * <b>Endpoint interne, hors contrat passerelle</b> (Sprint 6.1).
 *
 * <pre>
 *   GET /saisie/processus/recherche?dateDebut=2026-09-07&amp;dateFin=2026-09-13&amp;nature=RATION&amp;session=JOUR&amp;beneficiaire=10001234567
 * </pre>
 *
 * <h2>Pourquoi il existe</h2>
 *
 * <p>La recherche multicritere du cahier des charges (CT-30) porte sur cinq
 * criteres repartis sur <b>deux bases</b> : la periode et l'unite vivent sur
 * {@code processus_mensuel} cote Workflow, la nature, la session et le beneficiaire
 * sur {@code ligne_prestation} et {@code beneficiaires} ici. Aucune jointure SQL
 * n'est possible entre elles.
 *
 * <p>Cet endpoint repond a la moitie qui lui revient, en un seul appel : quels
 * etats contiennent au moins une ligne correspondante. Le service Reporting croise
 * ensuite avec les en-tetes du Workflow. La strategie et ses alternatives sont dans
 * {@code docs/decisions/2026-09-03-agregation-multi-services-du-reporting.md}.
 *
 * <h2>Un controleur a part, et non une methode de plus sur SaisieController</h2>
 *
 * <p>{@code SaisieController} est reserve a {@code AGENT_UNITE} : y loger cette
 * lecture aurait impose d'assouplir le role de ses cinq endpoints d'ecriture. Meme
 * raison qu'au Sprint 3.4, ou la consolidation avait deja impose un controleur
 * separe. L'ARH s'ajoute ici aux trois roles du circuit, le suivi lui etant ouvert
 * par le contrat d'API section 6.
 *
 * <h2>La portee n'est pas un parametre</h2>
 *
 * <p>Elle est resolue depuis le jeton, dans le service. Un appel direct sur le
 * port 8082 ne peut donc pas s'attribuer d'unites, ni sonder l'existence d'un
 * beneficiaire hors de la portee de son auteur.
 */
@RestController
@RequestMapping("/saisie/processus/recherche")
@PreAuthorize("hasAnyRole('ARH', 'AGENT_UNITE', 'CHEF_UNITE_DA', 'DIRECTEUR_RESEAU_DR')")
public class RechercheLignesController {

    private final RechercheLignesService rechercheLignesService;

    public RechercheLignesController(RechercheLignesService rechercheLignesService) {
        this.rechercheLignesService = rechercheLignesService;
    }

    /**
     * Les identifiants d'etats retenus.
     *
     * <p>Tous les criteres sont optionnels et se combinent. Sans aucun critere,
     * l'endpoint rend tous les etats de la portee ayant au moins une ligne — ce qui
     * n'a pas d'usage pour le Reporting, qui ne l'appelle que lorsqu'au moins un
     * critere de ligne est demande, mais reste une reponse coherente.
     *
     * <p>{@code beneficiaire} accepte un <b>numero de compte courant exact</b> — la
     * cle d'identification d'un beneficiaire depuis le Sprint 3.1 — ou un fragment
     * de nom ou de prenom, insensible a la casse. Le compte est stable, le nom est
     * recopie a la main et varie : les comparer de la meme facon ferait echouer
     * l'une des deux recherches.
     *
     * <p>Refus possibles : {@code 403 ACCES_REFUSE} role hors circuit ;
     * {@code 403 UTILISATEUR_NON_HABILITE} aucun profil local ;
     * {@code 503 SERVICE_IDENTITE_INDISPONIBLE} si la portee n'a pas pu etre
     * resolue — jamais une portee devinee. Une recherche sans resultat rend
     * {@code 200} avec une liste vide.
     */
    @GetMapping
    public ResponseEntity<RechercheLignesResponse> rechercher(
            @RequestParam(required = false) LocalDate dateDebut,
            @RequestParam(required = false) LocalDate dateFin,
            @RequestParam(required = false) NatureEnum nature,
            @RequestParam(required = false) SessionEnum session,
            @RequestParam(required = false) String beneficiaire,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation) {

        return ResponseEntity.ok(RechercheLignesResponse.de(
                rechercheLignesService.rechercher(
                        dateDebut, dateFin, nature, session, beneficiaire, enteteAutorisation)));
    }

}
