package cm.afrilandfirstbank.rations.reporting.api;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cm.afrilandfirstbank.rations.reporting.api.dto.DemandeResponse;
import cm.afrilandfirstbank.rations.reporting.api.dto.HistoriqueResponse;
import cm.afrilandfirstbank.rations.reporting.api.dto.PageResponse;
import cm.afrilandfirstbank.rations.reporting.api.dto.RapportResponse;
import cm.afrilandfirstbank.rations.reporting.application.CriteresRecherche;
import cm.afrilandfirstbank.rations.reporting.application.ExportExcelService;
import cm.afrilandfirstbank.rations.reporting.application.ExportPdfService;
import cm.afrilandfirstbank.rations.reporting.application.RapportService;
import cm.afrilandfirstbank.rations.reporting.application.SuiviService;
import cm.afrilandfirstbank.rations.reporting.domaine.EnTeteDemande;
import cm.afrilandfirstbank.rations.reporting.application.TracabiliteRapportService;
import jakarta.servlet.http.HttpServletRequest;
import cm.afrilandfirstbank.rations.reporting.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.reporting.domaine.Rapport;
import cm.afrilandfirstbank.rations.reporting.domaine.SessionEnum;
import cm.afrilandfirstbank.rations.reporting.domaine.exception.FormatExportInvalideException;
import cm.afrilandfirstbank.rations.reporting.domaine.exception.PeriodeInvalideException;

/**
 * Les quatre endpoints du service Reporting au contrat d'API section 6, et
 * aucun de plus : les deux endpoints de suivi (Sprint 6.1, US-15, CT-30, CT-31)
 * et les deux endpoints de rapport (Sprint 6.2, US-16, CT-32, CT-33).
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
    private final RapportService rapportService;
    private final ExportPdfService exportPdfService;
    private final ExportExcelService exportExcelService;
    private final TracabiliteRapportService tracabiliteRapportService;

    public ReportingController(SuiviService suiviService, RapportService rapportService,
            ExportPdfService exportPdfService, ExportExcelService exportExcelService,
            TracabiliteRapportService tracabiliteRapportService) {
        this.suiviService = suiviService;
        this.rapportService = rapportService;
        this.exportPdfService = exportPdfService;
        this.exportExcelService = exportExcelService;
        this.tracabiliteRapportService = tracabiliteRapportService;
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
     * Rapport d'activité d'une période, pour une agence ou pour toute la portée
     * (Sprint 6.2, US-16).
     *
     * <p>{@code periode} suit le format {@code AAAA-MM} et est <b>obligatoire</b> :
     * un rapport est toujours daté d'une période. {@code codeUnite} est optionnel —
     * absent, le rapport couvre toutes les unités visibles par l'utilisateur.
     *
     * <p>Réservé au rôle <b>ARH</b> (contrat d'API section 6), à la différence des
     * deux endpoints de suivi ouverts aussi au circuit. La portée d'accès reste
     * résolue par le service Workflow depuis le jeton relayé, jamais ici.
     *
     * <p>Une période sans aucun état rend {@code 200} avec {@code vide = true} et
     * une synthèse à zéro (CT-33) — jamais une erreur.
     *
     * <p>Refus possibles : {@code 400 PERIODE_INVALIDE} période absente ou mal
     * formée ; {@code 403 ACCES_REFUSE} rôle non ARH ; {@code 403
     * UTILISATEUR_NON_HABILITE} unité demandée hors portée ; {@code 422
     * RECHERCHE_TROP_LARGE} ; {@code 503 SERVICE_WORKFLOW_INDISPONIBLE}.
     */
    @GetMapping("/rapports")
    @PreAuthorize("hasRole('ARH')")
    public ResponseEntity<RapportResponse> produireRapport(
            @RequestParam(required = false) String periode,
            @RequestParam(required = false) String codeUnite,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation,
            @AuthenticationPrincipal Jwt jeton,
            HttpServletRequest requeteHttp) {

        YearMonth moisAnnee = exigerPeriode(periode);

        Rapport rapport = rapportService.produire(
                moisAnnee.getMonthValue(),
                moisAnnee.getYear(),
                codeUnite,
                login(jeton),
                enteteAutorisation);

        // Après production, jamais avant : une trace posée en amont affirmerait
        // un rapport qu'un refus de portée peut encore empêcher (document
        // maître §7.3, la journalisation suit le fait qu'elle décrit).
        tracabiliteRapportService.tracerGeneration(rapport, requeteHttp.getRemoteAddr());

        return ResponseEntity.ok(RapportResponse.depuis(rapport));
    }

    /**
     * Export du rapport d'activité en PDF ou en Excel (Sprint 6.2, US-16, CT-32).
     *
     * <p>Mêmes paramètres que {@code GET /reporting/rapports}, plus {@code format} :
     * {@code pdf} ou {@code excel}, insensible à la casse. Toute autre valeur est
     * refusée en {@code 422 FORMAT_EXPORT_INVALIDE}, avec un message nommant les
     * deux valeurs acceptées.
     *
     * <p>Le rapport exporté est produit par le <b>même</b> {@link RapportService}
     * que {@code GET /reporting/rapports} : mêmes chiffres, seule la mise en forme
     * change (CT-32). Le fichier est rendu en téléchargement, jamais écrit sur
     * disque — ce service n'a pas de base où le conserver.
     *
     * <p>Convention de nommage : {@code rapport-rations-<agence>-<AAAAMM>.<pdf|xlsx>},
     * {@code <agence>} valant {@code toutes-unites} quand aucune n'est demandée.
     *
     * <p>Refus possibles : ceux de {@code GET /reporting/rapports}, plus
     * {@code 422 FORMAT_EXPORT_INVALIDE} ; {@code 500 EXPORT_IMPOSSIBLE} si la
     * composition du fichier échoue.
     */
    @GetMapping("/rapports/export")
    @PreAuthorize("hasRole('ARH')")
    public ResponseEntity<byte[]> exporterRapport(
            @RequestParam(required = false) String periode,
            @RequestParam(required = false) String codeUnite,
            @RequestParam(required = false) String format,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation,
            @AuthenticationPrincipal Jwt jeton,
            HttpServletRequest requeteHttp) {

        YearMonth moisAnnee = exigerPeriode(periode);
        FormatExport formatExport = exigerFormat(format);

        Rapport rapport = rapportService.produire(moisAnnee.getMonthValue(), moisAnnee.getYear(),
                codeUnite, login(jeton), enteteAutorisation);

        byte[] contenu = formatExport == FormatExport.PDF
                ? exportPdfService.exporter(rapport)
                : exportExcelService.exporter(rapport);

        String nomFichier = nomFichier(rapport, formatExport);

        // Le fichier est composé : c'est maintenant, et seulement maintenant,
        // qu'un document quitte le périmètre applicatif. Une composition qui
        // échoue (500 EXPORT_IMPOSSIBLE) ne produit donc aucune trace d'export,
        // et c'est voulu — rien n'est sorti.
        tracabiliteRapportService.tracerExport(rapport, formatExport.name(), nomFichier,
                contenu.length, requeteHttp.getRemoteAddr());

        return ResponseEntity.ok()
                .contentType(formatExport.typeContenu)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + nomFichier + "\"")
                .body(contenu);
    }

    /** Le format d'export demandé, avec son type de contenu et son extension de fichier. */
    private enum FormatExport {
        PDF(MediaType.APPLICATION_PDF, "pdf"),
        EXCEL(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), "xlsx");

        private final MediaType typeContenu;
        private final String extension;

        FormatExport(MediaType typeContenu, String extension) {
            this.typeContenu = typeContenu;
            this.extension = extension;
        }
    }

    /**
     * @throws FormatExportInvalideException le paramètre est absent ou ne vaut ni
     *         {@code pdf} ni {@code excel}
     */
    private FormatExport exigerFormat(String format) {
        if (format == null || format.isBlank()) {
            throw new FormatExportInvalideException(
                    "Le parametre format est obligatoire : \"pdf\" ou \"excel\".");
        }
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "pdf" -> FormatExport.PDF;
            case "excel" -> FormatExport.EXCEL;
            default -> throw new FormatExportInvalideException(
                    "Le parametre format \"" + format + "\" est invalide : valeurs acceptees "
                            + "\"pdf\" ou \"excel\".");
        };
    }

    /** {@code rapport-rations-<agence>-<AAAAMM>.<pdf|xlsx>} (guide 6.2, étape 6). */
    private String nomFichier(Rapport rapport, FormatExport formatExport) {
        String agence = rapport.codeUnite() == null ? "toutes-unites" : rapport.codeUnite();
        return String.format(Locale.ROOT, "rapport-rations-%s-%04d%02d.%s",
                agence, rapport.periodeAnnee(), rapport.periodeMois(), formatExport.extension);
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

    /**
     * Comme {@link #analyserPeriode}, mais la période est obligatoire : un rapport
     * est toujours daté d'une période.
     *
     * @throws PeriodeInvalideException le paramètre est absent, vide ou mal formé
     */
    private YearMonth exigerPeriode(String periode) {
        if (periode == null || periode.isBlank()) {
            throw new PeriodeInvalideException(
                    "Le parametre periode est obligatoire et suit le format AAAA-MM "
                            + "(exemple : 2026-08).");
        }
        return analyserPeriode(periode);
    }

    /**
     * Le login de l'utilisateur qui produit le rapport, pour l'en-tête du document.
     *
     * <p>Lu sur la revendication {@code preferred_username} du jeton Keycloak, comme
     * partout ailleurs dans le module (convention {@code prenom_nom}). Un jeton sans
     * cette revendication ne devrait pas exister sur ce realm ; le cas échéant, le
     * rapport porte {@code "inconnu"} plutot que d'echouer pour un libelle.
     */
    private String login(Jwt jeton) {
        String preferedUsername = jeton == null ? null : jeton.getClaimAsString("preferred_username");
        return preferedUsername == null || preferedUsername.isBlank() ? "inconnu" : preferedUsername;
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
