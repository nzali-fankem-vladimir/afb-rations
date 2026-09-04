package cm.afrilandfirstbank.rations.reporting.api;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import cm.afrilandfirstbank.rations.reporting.domaine.exception.ExportImpossibleException;
import cm.afrilandfirstbank.rations.reporting.domaine.exception.FormatExportInvalideException;
import cm.afrilandfirstbank.rations.reporting.domaine.exception.PeriodeInvalideException;
import cm.afrilandfirstbank.rations.reporting.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.reporting.domaine.exception.RechercheTropLargeException;
import cm.afrilandfirstbank.rations.reporting.domaine.exception.ServiceSaisieIndisponibleException;
import cm.afrilandfirstbank.rations.reporting.domaine.exception.ServiceWorkflowIndisponibleException;
import cm.afrilandfirstbank.rations.reporting.domaine.exception.UtilisateurNonHabiliteException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Traduit chaque refus du service Reporting en réponse HTTP au format uniforme
 * du module (CLAUDE.md §11), sur le modèle des gestionnaires des cinq autres
 * services.
 *
 * <h2>Ce service ne trace pas ses propres refus d'accès</h2>
 *
 * <p>{@code ACCES_REFUSE} (CT-04) est publié par les services qui <b>consomment
 * eux-mêmes</b> {@code GET /identite/habilitation} — Workflow et Saisie, sur
 * leurs propres endpoints internes de recherche et d'historique. Ce service ne
 * consomme jamais cet endpoint directement : il relaie les refus qu'ils
 * prononcent ({@link UtilisateurNonHabiliteException}), déjà tracés à leur
 * source. Republier une seconde trace ici pour un refus déjà tracé en amont
 * ferait croire à deux incidents distincts.
 *
 * <p>Un rôle applicatif insuffisant ({@link AccessDeniedException},
 * {@code @PreAuthorize}) n'implique pas non plus de consultation
 * d'habilitation : c'est un filtre de rôle, distinct de la portée d'accès.
 */
@RestControllerAdvice
public class GestionnaireErreursApi {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErreurApiDto> accesRefuse(AccessDeniedException exception,
            HttpServletRequest requete) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErreurApiDto(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                "ACCES_REFUSE",
                "Votre role ne permet pas d'acceder a cette ressource.",
                requete.getRequestURI()));
    }

    /** Refus de portée relayé tel quel depuis un service en amont (Workflow ou Saisie). */
    @ExceptionHandler(UtilisateurNonHabiliteException.class)
    public ResponseEntity<ErreurApiDto> utilisateurNonHabilite(UtilisateurNonHabiliteException exception,
            HttpServletRequest requete) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErreurApiDto(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                "UTILISATEUR_NON_HABILITE",
                exception.getMessage(),
                requete.getRequestURI()));
    }

    @ExceptionHandler(ProcessusIntrouvableException.class)
    public ResponseEntity<ErreurApiDto> processusIntrouvable(ProcessusIntrouvableException exception,
            HttpServletRequest requete) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErreurApiDto(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                "PROCESSUS_INTROUVABLE",
                exception.getMessage(),
                requete.getRequestURI()));
    }

    /**
     * Volume de résultats au-delà de la borne configurée (décision du Sprint 6.1,
     * §4). {@code 422} et non {@code 500} : ce n'est pas une panne, l'appelant
     * peut corriger en affinant sa recherche.
     */
    @ExceptionHandler(RechercheTropLargeException.class)
    public ResponseEntity<ErreurApiDto> rechercheTropLarge(RechercheTropLargeException exception,
            HttpServletRequest requete) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErreurApiDto(
                LocalDateTime.now(),
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                "RECHERCHE_TROP_LARGE",
                exception.getMessage(),
                requete.getRequestURI()));
    }

    @ExceptionHandler(PeriodeInvalideException.class)
    public ResponseEntity<ErreurApiDto> periodeInvalide(PeriodeInvalideException exception,
            HttpServletRequest requete) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErreurApiDto(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "PERIODE_INVALIDE",
                exception.getMessage(),
                requete.getRequestURI()));
    }

    @ExceptionHandler(ServiceWorkflowIndisponibleException.class)
    public ResponseEntity<ErreurApiDto> workflowIndisponible(ServiceWorkflowIndisponibleException exception,
            HttpServletRequest requete) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErreurApiDto(
                LocalDateTime.now(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "SERVICE_WORKFLOW_INDISPONIBLE",
                exception.getMessage(),
                requete.getRequestURI()));
    }

    /**
     * Le service Saisie n'a pas répondu alors qu'un critère de ligne (nature,
     * session, bénéficiaire) était demandé. Voir la décision du Sprint 6.1 §5 :
     * échec net, jamais un résultat plus large que celui demandé.
     */
    @ExceptionHandler(ServiceSaisieIndisponibleException.class)
    public ResponseEntity<ErreurApiDto> saisieIndisponible(ServiceSaisieIndisponibleException exception,
            HttpServletRequest requete) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErreurApiDto(
                LocalDateTime.now(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "SERVICE_SAISIE_INDISPONIBLE",
                exception.getMessage(),
                requete.getRequestURI()));
    }

    /**
     * La composition d'un export (PDF ou Excel) a échoué (Sprint 6.2). {@code 500} :
     * ce n'est pas une règle de gestion qui refuse, c'est une panne de production
     * documentaire — même parti que {@code DOCUMENT_NON_PRODUIT} au Sprint 4.2.
     */
    @ExceptionHandler(ExportImpossibleException.class)
    public ResponseEntity<ErreurApiDto> exportImpossible(ExportImpossibleException exception,
            HttpServletRequest requete) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErreurApiDto(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "EXPORT_IMPOSSIBLE",
                exception.getMessage(),
                requete.getRequestURI()));
    }

    /**
     * Le paramètre {@code format} de {@code GET /reporting/rapports/export} ne vaut
     * ni {@code pdf} ni {@code excel} (Sprint 6.2, guide §6). {@code 422} : ce n'est
     * pas une panne, l'appelant peut corriger.
     */
    @ExceptionHandler(FormatExportInvalideException.class)
    public ResponseEntity<ErreurApiDto> formatExportInvalide(FormatExportInvalideException exception,
            HttpServletRequest requete) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErreurApiDto(
                LocalDateTime.now(),
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                "FORMAT_EXPORT_INVALIDE",
                exception.getMessage(),
                requete.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErreurApiDto> parametreInvalide(MethodArgumentTypeMismatchException exception,
            HttpServletRequest requete) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErreurApiDto(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "REQUETE_INVALIDE",
                "Le parametre \"" + exception.getName() + "\" est invalide : " + exception.getValue(),
                requete.getRequestURI()));
    }

}
