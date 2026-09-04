package cm.afrilandfirstbank.rations.identite.api;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import cm.afrilandfirstbank.rations.commun.audit.DeltaAudit;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.identite.domaine.exception.AutoModificationInterditeException;
import cm.afrilandfirstbank.rations.identite.domaine.exception.CodeUniteIncoherentException;
import cm.afrilandfirstbank.rations.identite.domaine.exception.LotTropGrandException;
import cm.afrilandfirstbank.rations.identite.domaine.exception.DernierAdministrateurException;
import cm.afrilandfirstbank.rations.identite.domaine.exception.UtilisateurIntrouvableException;
import cm.afrilandfirstbank.rations.identite.domaine.exception.UtilisateurNonHabiliteException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Traduit les exceptions du domaine en reponses au format d'erreur uniforme, et
 * publie un evenement d'audit sur les refus d'acces.
 *
 * <p>Les 401 ne passent pas ici : un jeton absent ou invalide est refuse par le
 * filtre de securite, en amont des controleurs.
 *
 * <p><b>Pourquoi les refus sont traces ici</b> (CT-04, US-02). Une tentative
 * d'action hors perimetre est une information de securite, pas un simple rejet a
 * ignorer. Ce point est le seul endroit du service ou tous les refus convergent :
 * y placer la publication garantit qu'aucun n'est oublie, et evite de disperser
 * l'appel dans chaque controleur.
 *
 * <p>Deux refus sont traces, tous deux en 403 :
 * <ul>
 *   <li><b>habilitation absente</b> : jeton valide, mais aucun profil ouvert
 *       dans le module, ou profil desactive ;</li>
 *   <li><b>role insuffisant</b> : profil valide, mais role sans droit sur
 *       l'endpoint vise ({@code @PreAuthorize}).</li>
 * </ul>
 *
 * <p>Les 4xx metier (404, 409, 400) ne sont pas traces comme des refus d'acces :
 * ce sont des erreurs d'usage, pas des tentatives hors perimetre. Le verdict
 * {@code autorise = false} de {@code GET /identite/habilitation} ne l'est pas non
 * plus : ce service repond a une question, il ne refuse pas une action. C'est le
 * service consommateur qui refuse, et qui publie
 * ({@code docs/appel-habilitation.md} section 4) ; tracer des deux cotes
 * compterait deux fois le meme evenement.
 */
@RestControllerAdvice
public class GestionnaireErreursApi {

    private static final String ACTION_ACCES_REFUSE = "ACCES_REFUSE";

    /**
     * L'objet vise par un refus n'est pas une table mais la tentative d'acces
     * elle-meme ; le chemin appele est porte par le contexte.
     */
    private static final String ENTITE_ACCES = "acces";

    private final PublicateurAudit publicateurAudit;

    public GestionnaireErreursApi(PublicateurAudit publicateurAudit) {
        this.publicateurAudit = publicateurAudit;
    }

    @ExceptionHandler(UtilisateurNonHabiliteException.class)
    public ResponseEntity<ErreurApiDto> nonHabilite(UtilisateurNonHabiliteException exception,
            HttpServletRequest requete) {
        // Authentification reussie mais habilitation absente : 403, pas 401
        // (document maitre, section 7.1).
        publierRefus(requete, "HABILITATION_ABSENTE", exception.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErreurApiDto(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                "UTILISATEUR_NON_HABILITE",
                exception.getMessage(),
                requete.getRequestURI()));
    }

    /**
     * Refus prononce par {@code @PreAuthorize}. Sans ce gestionnaire, Spring
     * Security produirait un 403 sans corps, hors du format d'erreur uniforme du
     * projet, et surtout sans trace d'audit.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErreurApiDto> accesRefuse(AccessDeniedException exception,
            HttpServletRequest requete) {
        publierRefus(requete, "ROLE_INSUFFISANT",
                "Le role de l'utilisateur ne permet pas cette action.");

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErreurApiDto(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                "ACCES_REFUSE",
                "Le role de l'utilisateur ne permet pas cette action.",
                requete.getRequestURI()));
    }

    @ExceptionHandler(UtilisateurIntrouvableException.class)
    public ResponseEntity<ErreurApiDto> introuvable(UtilisateurIntrouvableException exception,
            HttpServletRequest requete) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErreurApiDto(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                "UTILISATEUR_INTROUVABLE",
                exception.getMessage(),
                requete.getRequestURI()));
    }

    @ExceptionHandler(CodeUniteIncoherentException.class)
    public ResponseEntity<ErreurApiDto> codeUniteIncoherent(CodeUniteIncoherentException exception,
            HttpServletRequest requete) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErreurApiDto(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "CODE_UNITE_INCOHERENT",
                exception.getMessage(),
                requete.getRequestURI()));
    }

    /** Lot d'identifiants trop grand sur {@code GET /identite/utilisateurs/libelles} (Sprint 6.1). */
    @ExceptionHandler(LotTropGrandException.class)
    public ResponseEntity<ErreurApiDto> lotTropGrand(LotTropGrandException exception,
            HttpServletRequest requete) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErreurApiDto(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "LOT_TROP_GRAND",
                exception.getMessage(),
                requete.getRequestURI()));
    }

    @ExceptionHandler(AutoModificationInterditeException.class)
    public ResponseEntity<ErreurApiDto> autoModificationInterdite(AutoModificationInterditeException exception,
            HttpServletRequest requete) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErreurApiDto(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "AUTO_MODIFICATION_INTERDITE",
                exception.getMessage(),
                requete.getRequestURI()));
    }

    @ExceptionHandler(DernierAdministrateurException.class)
    public ResponseEntity<ErreurApiDto> dernierAdministrateur(DernierAdministrateurException exception,
            HttpServletRequest requete) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErreurApiDto(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "DERNIER_ADMINISTRATEUR",
                exception.getMessage(),
                requete.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErreurApiDto> validationEchouee(MethodArgumentNotValidException exception,
            HttpServletRequest requete) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(erreur -> erreur.getField() + " : " + erreur.getDefaultMessage())
                .orElse("Requete invalide.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErreurApiDto(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "REQUETE_INVALIDE",
                message,
                requete.getRequestURI()));
    }

    /**
     * Publie la trace d'un refus.
     *
     * <p>{@code idUtilisateur} est nul quand l'auteur n'a pas de profil local :
     * c'est precisement le cas d'un refus pour habilitation absente. Le login du
     * jeton est alors la seule identite disponible, et il est porte par le
     * contexte — sans quoi le refus le plus interessant serait aussi le seul
     * anonyme.
     *
     * <p>Aucune protection contre l'echec n'est necessaire ici : le contrat de
     * {@link PublicateurAudit} garantit que {@code publier} ne leve jamais. Un
     * incident d'audit ne doit pas transformer un 403 en 500.
     */
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

    /** Login porte par le jeton, ou {@code null} si le contexte n'en porte pas. */
    private String loginDuJeton() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification != null && authentification.getPrincipal() instanceof Jwt jeton) {
            return jeton.getClaimAsString("preferred_username");
        }
        return null;
    }

}
