package cm.afrilandfirstbank.rations.reporting.api;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cm.afrilandfirstbank.rations.reporting.api.dto.DemandeResponse;
import cm.afrilandfirstbank.rations.reporting.api.dto.HistoriqueResponse;
import cm.afrilandfirstbank.rations.reporting.api.dto.PageResponse;
import cm.afrilandfirstbank.rations.reporting.application.CriteresRecherche;
import cm.afrilandfirstbank.rations.reporting.application.SuiviService;
import cm.afrilandfirstbank.rations.reporting.domaine.EnTeteDemande;
import cm.afrilandfirstbank.rations.reporting.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.reporting.domaine.SessionEnum;
import cm.afrilandfirstbank.rations.reporting.domaine.exception.PeriodeInvalideException;

/**
 * Les deux endpoints de suivi du contrat d'API section 6 (Sprint 6.1, US-15,
 * CT-30, CT-31). Les deux autres endpoints du service — {@code /reporting/rapports}
 * et {@code /reporting/rapports/export} — relèvent du sous-sprint 6.2 et ne sont
 * pas implémentés ici.
 *
 * <h2>Aucun accès direct à une base</h2>
 *
 * <p>Ce service n'a pas de source de données propre (document maître section 3).
 * Chaque méthode délègue à {@link SuiviService}, qui lit les services Workflow,
 * Saisie et Identité par leurs API.
 *
 * <h2>Le rôle n'est que le premier filtre</h2>
 *
 * <p>Les deux endpoints sont ouverts à l'ARH et aux trois rôles du circuit, comme
 * le veut le contrat (« Rôles ARH et circuit »). La portée d'accès, elle, n'est
 * jamais résolue ici : elle est résolue par les services qui détiennent les
 * données, depuis le jeton relayé. Ce contrôleur ne fait que le transmettre.
 */
@RestController
@RequestMapping("/reporting")
@PreAuthorize("hasAnyRole('ARH', 'AGENT_UNITE', 'CHEF_UNITE_DA', 'DIRECTEUR_RESEAU_DR')")
public class ReportingController {

    private final SuiviService suiviService;

    public ReportingController(SuiviService suiviService) {
        this.suiviService = suiviService;
    }

    /**
     * Recherche multicritère paginée (CT-30).
     *
     * <p>Tous les filtres sont optionnels et se combinent librement : période,
     * unité, session, nature, bénéficiaire. {@code periode} suit le format
     * {@code AAAA-MM}, comme les autres dates du contrat d'API (ISO 8601).
     *
     * <p>Une recherche sans résultat rend {@code 200} avec une page vide — jamais
     * une erreur. Un volume trop grand pour être montré est refusé en
     * {@code 422 RECHERCHE_TROP_LARGE}, avec un message nommant le nombre trouvé.
     *
     * <p>Refus possibles : {@code 400 PERIODE_INVALIDE} format de période
     * incorrect ; {@code 422 RECHERCHE_TROP_LARGE} ; {@code 403
     * UTILISATEUR_NON_HABILITE} unité demandée hors portée ;
     * {@code 503 SERVICE_WORKFLOW_INDISPONIBLE} ;
     * {@code 503 SERVICE_SAISIE_INDISPONIBLE} si un critère de ligne était demandé.
     */
    @GetMapping("/demandes")
    public ResponseEntity<PageResponse<DemandeResponse>> rechercherDemandes(
            @RequestParam(required = false) String periode,
            @RequestParam(required = false) String codeUnite,
            @RequestParam(required = false) SessionEnum session,
            @RequestParam(required = false) NatureEnum nature,
            @RequestParam(required = false) String beneficiaire,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation) {

        YearMonth moisAnnee = analyserPeriode(periode);
        Integer mois = moisAnnee == null ? null : moisAnnee.getMonthValue();
        Integer annee = moisAnnee == null ? null : moisAnnee.getYear();

        CriteresRecherche criteres = new CriteresRecherche(mois, annee, codeUnite, nature,
                session, beneficiaire);

        PageResponse<DemandeResponse> reponse = mapperVersReponse(
                suiviService.rechercher(criteres, page, size, enteteAutorisation));

        return ResponseEntity.ok(reponse);
    }

    /**
     * Historique complet des validations et retours d'un dossier (CT-31).
     *
     * <p>Toutes les étapes sont rendues, dans l'ordre de leur rang — y compris les
     * passages répétés au même niveau après un retour et une resoumission
     * (Sprint 4.4). Chaque acteur est nommé quand un libellé est disponible.
     *
     * <p>Refus possibles : {@code 404 PROCESSUS_INTROUVABLE} ;
     * {@code 403 UTILISATEUR_NON_HABILITE} hors portée ;
     * {@code 503 SERVICE_WORKFLOW_INDISPONIBLE}.
     */
    @GetMapping("/processus/{id}/historique")
    public ResponseEntity<HistoriqueResponse> consulterHistorique(
            @PathVariable Long id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation) {

        return ResponseEntity.ok(HistoriqueResponse.depuis(
                suiviService.consulterHistorique(id, enteteAutorisation)));
    }

    /**
     * @throws PeriodeInvalideException le paramètre est présent mais ne suit pas
     *         le format {@code AAAA-MM}
     */
    private YearMonth analyserPeriode(String periode) {
        if (periode == null || periode.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(periode);
        } catch (DateTimeParseException erreurFormat) {
            throw new PeriodeInvalideException(
                    "Le parametre periode doit suivre le format AAAA-MM (exemple : 2026-08), "
                            + "recu : \"" + periode + "\".");
        }
    }

    private PageResponse<DemandeResponse> mapperVersReponse(PageResponse<EnTeteDemande> page) {

        return new PageResponse<>(
                page.content().stream().map(DemandeResponse::depuis).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages(),
                page.dernierePage());
    }

}
