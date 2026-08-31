package cm.afrilandfirstbank.rations.saisie.api;

import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import cm.afrilandfirstbank.rations.commun.audit.DeltaAudit;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.AgentNonHabiliteException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.DoublonLigneException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.EtatNonModifiableException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.FicheIntrouvableException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.GrilleIndisponibleException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.LigneIntrouvableException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.ServiceGrillesIndisponibleException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.ServiceIdentiteIndisponibleException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.ServiceWorkflowIndisponibleException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Traduit chaque refus du service Saisie en réponse HTTP au format uniforme du
 * module (CLAUDE.md §11), sur le modèle du gestionnaire de service-grilles
 * (Sprint 2.2).
 *
 * <h2>Point unique de convergence des refus d'accès (CT-04)</h2>
 *
 * <p>Comme côté Grilles, c'est ici — pas dans {@link cm.afrilandfirstbank.rations.saisie.application.EtatModifiableService}
 * — que les refus {@code ACCES_REFUSE} sont publiés : le service Identité ne
 * trace pas ses propres verdicts négatifs, c'est au consommateur de le faire
 * (CLAUDE.md §9.2). Deux cas distincts convergent ici : le rôle applicatif
 * insuffisant ({@link AccessDeniedException}, `@PreAuthorize`), et l'absence
 * d'habilitation sur le code unité ({@link AgentNonHabiliteException}). Un
 * troisième — l'indisponibilité du service Identité — est tracé de la même
 * façon, avec un motif distinct, en application de la même doctrine.
 *
 * <p>Neuf refus métier ou technique, plus les erreurs de requête. Voir
 * {@code docs/decisions/2026-08-31-refus-de-ligne-et-codes-erreur-saisie.md} et
 * {@code docs/decisions/2026-08-31-code-http-du-refus-sur-etat-non-modifiable.md}
 * pour la justification de chaque code.
 */
@RestControllerAdvice
public class GestionnaireErreursApi {

    private static final String ACTION_ACCES_REFUSE = "ACCES_REFUSE";
    private static final String ENTITE_ACCES = "acces";

    private final PublicateurAudit publicateurAudit;

    public GestionnaireErreursApi(PublicateurAudit publicateurAudit) {
        this.publicateurAudit = publicateurAudit;
    }

    // --- Accès (tracés en audit, CT-04) ------------------------------------

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErreurApiDto> accesRefuse(AccessDeniedException exception,
            HttpServletRequest requete) {
        publierRefus(requete, "ROLE_INSUFFISANT", "Le role de l'utilisateur ne permet pas cette action.");
        return reponse(requete, HttpStatus.FORBIDDEN, "ACCES_REFUSE",
                "Le role de l'utilisateur ne permet pas cette action.");
    }

    @ExceptionHandler(AgentNonHabiliteException.class)
    public ResponseEntity<ErreurApiDto> agentNonHabilite(AgentNonHabiliteException exception,
            HttpServletRequest requete) {
        publierRefus(requete, "HABILITATION_ABSENTE", exception.getMessage());
        return reponse(requete, HttpStatus.FORBIDDEN, "UTILISATEUR_NON_HABILITE", exception.getMessage());
    }

    @ExceptionHandler(ServiceIdentiteIndisponibleException.class)
    public ResponseEntity<ErreurApiDto> identiteIndisponible(ServiceIdentiteIndisponibleException exception,
            HttpServletRequest requete) {
        publierRefus(requete, "IDENTITE_INDISPONIBLE", exception.getMessage());
        return reponse(requete, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_IDENTITE_INDISPONIBLE",
                exception.getMessage());
    }

    // --- Ressources introuvables (404) -------------------------------------

    @ExceptionHandler(FicheIntrouvableException.class)
    public ResponseEntity<ErreurApiDto> ficheIntrouvable(FicheIntrouvableException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.NOT_FOUND, "FICHE_INTROUVABLE", exception.getMessage());
    }

    @ExceptionHandler(LigneIntrouvableException.class)
    public ResponseEntity<ErreurApiDto> ligneIntrouvable(LigneIntrouvableException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.NOT_FOUND, "LIGNE_INTROUVABLE", exception.getMessage());
    }

    @ExceptionHandler(ProcessusIntrouvableException.class)
    public ResponseEntity<ErreurApiDto> processusIntrouvable(ProcessusIntrouvableException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.NOT_FOUND, "PROCESSUS_INTROUVABLE", exception.getMessage());
    }

    // --- Règles de gestion (422) et conflit (409) --------------------------

    @ExceptionHandler(EtatNonModifiableException.class)
    public ResponseEntity<ErreurApiDto> etatNonModifiable(EtatNonModifiableException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.UNPROCESSABLE_ENTITY, "ETAT_NON_MODIFIABLE", exception.getMessage());
    }

    @ExceptionHandler(GrilleIndisponibleException.class)
    public ResponseEntity<ErreurApiDto> grilleIndisponible(GrilleIndisponibleException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.UNPROCESSABLE_ENTITY, "GRILLE_INDISPONIBLE", exception.getMessage());
    }

    @ExceptionHandler(DoublonLigneException.class)
    public ResponseEntity<ErreurApiDto> doublonLigne(DoublonLigneException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.CONFLICT, "DOUBLON_LIGNE", exception.getMessage());
    }

    /**
     * Filet de l'index unique {@code ux_ligne_par_fiche_beneficiaire_nature_session}
     * (migration V4) : deux écritures concurrentes ayant toutes deux franchi le
     * contrôle applicatif. Même traduction que
     * {@code ux_grille_active_par_couple} côté service Grilles (Sprint 2.2).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErreurApiDto> violationIntegrite(DataIntegrityViolationException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.CONFLICT, "DOUBLON_LIGNE",
                "Cette combinaison beneficiaire / journee / nature / session existe deja. "
                        + "Une autre operation a peut-etre abouti au meme instant ; "
                        + "reaffichez la fiche avant de recommencer.");
    }

    // --- Pannes techniques (503) -------------------------------------------

    @ExceptionHandler(ServiceGrillesIndisponibleException.class)
    public ResponseEntity<ErreurApiDto> grillesIndisponible(ServiceGrillesIndisponibleException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_GRILLES_INDISPONIBLE",
                exception.getMessage());
    }

    @ExceptionHandler(ServiceWorkflowIndisponibleException.class)
    public ResponseEntity<ErreurApiDto> workflowIndisponible(ServiceWorkflowIndisponibleException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_WORKFLOW_INDISPONIBLE",
                exception.getMessage());
    }

    // --- Requêtes invalides (400) -------------------------------------------

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErreurApiDto> parametreManquant(MissingServletRequestParameterException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.BAD_REQUEST, "REQUETE_INVALIDE",
                "Le parametre obligatoire %s est absent.".formatted(exception.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErreurApiDto> validationEchouee(MethodArgumentNotValidException exception,
            HttpServletRequest requete) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(erreur -> erreur.getField() + " : " + erreur.getDefaultMessage())
                .orElse("Requete invalide.");
        return reponse(requete, HttpStatus.BAD_REQUEST, "REQUETE_INVALIDE", message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErreurApiDto> corpsIllisible(HttpMessageNotReadableException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.BAD_REQUEST, "REQUETE_INVALIDE",
                "Le corps de la requete est illisible : verifiez que nature vaut RATION ou "
                        + "TRANSPORT, que session vaut JOUR ou SOIR, et que les dates sont au "
                        + "format AAAA-MM-JJ.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErreurApiDto> parametreInvalide(MethodArgumentTypeMismatchException exception,
            HttpServletRequest requete) {
        String valeursAdmises = exception.getRequiredType() != null && exception.getRequiredType().isEnum()
                ? java.util.Arrays.toString(exception.getRequiredType().getEnumConstants())
                : "";
        return reponse(requete, HttpStatus.BAD_REQUEST, "REQUETE_INVALIDE",
                ("Valeur invalide pour le parametre %s. %s")
                        .formatted(exception.getName(),
                                valeursAdmises.isEmpty() ? "" : "Valeurs admises : " + valeursAdmises + ".")
                        .strip());
    }

    // --- Utilitaires ---------------------------------------------------------

    private ResponseEntity<ErreurApiDto> reponse(HttpServletRequest requete, HttpStatus statut,
            String code, String message) {
        return ResponseEntity.status(statut).body(new ErreurApiDto(
                LocalDateTime.now(), statut.value(), code, message, requete.getRequestURI()));
    }

    private void publierRefus(HttpServletRequest requete, String motif, String detail) {
        publicateurAudit.publier(EvenementAudit.de(
                null,
                ACTION_ACCES_REFUSE,
                ENTITE_ACCES,
                null,
                requete.getRemoteAddr(),
                DeltaAudit.nouveau()
                        .contexte("motif", motif)
                        .contexte("chemin", requete.getRequestURI())
                        .contexte("methode", requete.getMethod())
                        .contexte("login", loginDuJeton())
                        .contexte("detail", detail)
                        .enJson()));
    }

    private String loginDuJeton() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification != null && authentification.getPrincipal() instanceof Jwt jeton) {
            return jeton.getClaimAsString("preferred_username");
        }
        return null;
    }

}
