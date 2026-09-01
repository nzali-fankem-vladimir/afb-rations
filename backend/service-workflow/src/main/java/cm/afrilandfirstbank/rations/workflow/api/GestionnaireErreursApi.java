package cm.afrilandfirstbank.rations.workflow.api;

import java.time.LocalDateTime;
import java.util.Arrays;

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
import cm.afrilandfirstbank.rations.workflow.domaine.exception.AgentNonHabiliteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.FonctionnaliteNonOuverteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.MotifRetourRequisException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusExistantException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ServiceIdentiteIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ServiceSaisieIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.TransitionProcessusInterditeException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Traduit chaque refus du service Workflow en reponse HTTP au format uniforme du
 * module (CLAUDE.md section 11), sur le modele des gestionnaires de
 * service-grilles (Sprint 2.2) et service-saisie (Sprint 3.3).
 *
 * <h2>Point unique de convergence des refus d'acces (CT-04)</h2>
 *
 * <p>Le service Workflow est <b>le premier consommateur reel</b> de
 * {@code GET /identite/habilitation} (guide 4.1 section 0.2). L'obligation qui va
 * avec est tenue ici : le service Identite ne trace pas ses verdicts negatifs —
 * il repond a une question, il ne refuse pas l'action —, c'est au consommateur de
 * publier {@code ACCES_REFUSE}. <b>Si ce fichier ne publiait pas, le refus ne
 * serait trace nulle part</b> et l'exigence CT-04 ne serait pas tenue, en
 * silence (CLAUDE.md sections 9.2 et 15).
 *
 * <p>Trois cas convergent ici et sont publies, avec trois motifs distincts :
 *
 * <table>
 *   <tr><td>{@link AccessDeniedException}</td><td>{@code ROLE_INSUFFISANT}</td><td>role applicatif insuffisant ({@code @PreAuthorize})</td></tr>
 *   <tr><td>{@link AgentNonHabiliteException}</td><td>{@code HABILITATION_ABSENTE}</td><td>verdict negatif d'Identite sur l'unite</td></tr>
 *   <tr><td>{@link ServiceIdentiteIndisponibleException}</td><td>{@code IDENTITE_INDISPONIBLE}</td><td>refus conservateur sur panne</td></tr>
 * </table>
 *
 * <p><b>Non traces</b> : les {@code 401} sans jeton, refuses par le filtre de
 * securite en amont des controleurs — une requete anonyme est une absence
 * d'authentification, pas une action hors perimetre —, et les erreurs d'usage
 * ({@code 400}, {@code 404}, {@code 409}), qui sont des maladresses et non des
 * tentatives ({@code docs/publication-audit.md} section 4).
 */
@RestControllerAdvice
public class GestionnaireErreursApi {

    private static final String ACTION_ACCES_REFUSE = "ACCES_REFUSE";
    private static final String ENTITE_ACCES = "acces";

    private final PublicateurAudit publicateurAudit;

    public GestionnaireErreursApi(PublicateurAudit publicateurAudit) {
        this.publicateurAudit = publicateurAudit;
    }

    // --- Acces (traces en audit, CT-04) ---------------------------------------

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErreurApiDto> accesRefuse(AccessDeniedException exception,
            HttpServletRequest requete) {
        publierRefus(requete, "ROLE_INSUFFISANT",
                "Le role de l'utilisateur ne permet pas cette action.");
        return reponse(requete, HttpStatus.FORBIDDEN, "ACCES_REFUSE",
                "Le role de l'utilisateur ne permet pas cette action.");
    }

    @ExceptionHandler(AgentNonHabiliteException.class)
    public ResponseEntity<ErreurApiDto> agentNonHabilite(AgentNonHabiliteException exception,
            HttpServletRequest requete) {
        publierRefus(requete, "HABILITATION_ABSENTE", exception.getMessage());
        return reponse(requete, HttpStatus.FORBIDDEN, "UTILISATEUR_NON_HABILITE",
                exception.getMessage());
    }

    @ExceptionHandler(ServiceIdentiteIndisponibleException.class)
    public ResponseEntity<ErreurApiDto> identiteIndisponible(
            ServiceIdentiteIndisponibleException exception, HttpServletRequest requete) {
        publierRefus(requete, "IDENTITE_INDISPONIBLE", exception.getMessage());
        return reponse(requete, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_IDENTITE_INDISPONIBLE",
                exception.getMessage());
    }

    // --- Ressource introuvable (404) ------------------------------------------

    @ExceptionHandler(ProcessusIntrouvableException.class)
    public ResponseEntity<ErreurApiDto> processusIntrouvable(ProcessusIntrouvableException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.NOT_FOUND, "PROCESSUS_INTROUVABLE", exception.getMessage());
    }

    // --- Conflit d'unicite (409) ----------------------------------------------

    @ExceptionHandler(ProcessusExistantException.class)
    public ResponseEntity<ErreurApiDto> processusExistant(ProcessusExistantException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.CONFLICT, "PROCESSUS_EXISTANT", exception.getMessage());
    }

    /**
     * Filet de l'index partiel {@code ux_processus_normal_par_periode} (migration
     * V1) : deux demandes concurrentes ayant toutes deux franchi le controle
     * applicatif. Meme traduction que {@code ux_grille_active_par_couple} cote
     * Grilles et {@code ux_ligne_par_fiche_...} cote Saisie.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErreurApiDto> violationIntegrite(DataIntegrityViolationException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.CONFLICT, "PROCESSUS_EXISTANT",
                "Un etat mensuel existe deja pour cette unite et cette periode. "
                        + "Une autre demande a peut-etre abouti au meme instant ; "
                        + "reaffichez la liste avant de recommencer.");
    }

    // --- Regles de gestion (422) ----------------------------------------------

    /**
     * {@code 422} et non {@code 409} : rien n'est duplique, c'est une regle de
     * gestion qui refuse. Meme raisonnement qu'au Sprint 2.3 pour
     * {@code TRANSITION_INTERDITE} cote Grilles, et qu'au Sprint 3.3 pour
     * {@code ETAT_NON_MODIFIABLE} cote Saisie.
     */
    @ExceptionHandler(TransitionProcessusInterditeException.class)
    public ResponseEntity<ErreurApiDto> transitionInterdite(
            TransitionProcessusInterditeException exception, HttpServletRequest requete) {
        return reponse(requete, HttpStatus.UNPROCESSABLE_ENTITY, "TRANSITION_INTERDITE",
                exception.getMessage());
    }

    /** RG-10. Code deja retenu par le contrat pour le retour sans motif. */
    @ExceptionHandler(MotifRetourRequisException.class)
    public ResponseEntity<ErreurApiDto> motifRequis(MotifRetourRequisException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.UNPROCESSABLE_ENTITY, "MOTIF_OBLIGATOIRE",
                exception.getMessage());
    }

    @ExceptionHandler(FonctionnaliteNonOuverteException.class)
    public ResponseEntity<ErreurApiDto> fonctionnaliteNonOuverte(
            FonctionnaliteNonOuverteException exception, HttpServletRequest requete) {
        return reponse(requete, HttpStatus.UNPROCESSABLE_ENTITY, "FONCTIONNALITE_NON_OUVERTE",
                exception.getMessage());
    }

    // --- Panne technique (503) -------------------------------------------------

    @ExceptionHandler(ServiceSaisieIndisponibleException.class)
    public ResponseEntity<ErreurApiDto> saisieIndisponible(ServiceSaisieIndisponibleException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_SAISIE_INDISPONIBLE",
                exception.getMessage());
    }

    // --- Requetes invalides (400) ----------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErreurApiDto> validationEchouee(MethodArgumentNotValidException exception,
            HttpServletRequest requete) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(erreur -> erreur.getField() + " : " + erreur.getDefaultMessage())
                .orElse("Requete invalide.");
        return reponse(requete, HttpStatus.BAD_REQUEST, "REQUETE_INVALIDE", message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErreurApiDto> parametreManquant(
            MissingServletRequestParameterException exception, HttpServletRequest requete) {
        return reponse(requete, HttpStatus.BAD_REQUEST, "REQUETE_INVALIDE",
                "Le parametre obligatoire %s est absent.".formatted(exception.getParameterName()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErreurApiDto> corpsIllisible(HttpMessageNotReadableException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.BAD_REQUEST, "REQUETE_INVALIDE",
                "Le corps de la requete est illisible : verifiez que typeProcessus vaut NORMAL ou "
                        + "COMPLEMENTAIRE, et que le mois et l'annee sont des nombres entiers.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErreurApiDto> parametreInvalide(MethodArgumentTypeMismatchException exception,
            HttpServletRequest requete) {
        String valeursAdmises = exception.getRequiredType() != null && exception.getRequiredType().isEnum()
                ? Arrays.toString(exception.getRequiredType().getEnumConstants())
                : "";
        return reponse(requete, HttpStatus.BAD_REQUEST, "REQUETE_INVALIDE",
                ("Valeur invalide pour le parametre %s. %s")
                        .formatted(exception.getName(),
                                valeursAdmises.isEmpty() ? "" : "Valeurs admises : " + valeursAdmises + ".")
                        .strip());
    }

    // --- Utilitaires ------------------------------------------------------------

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
