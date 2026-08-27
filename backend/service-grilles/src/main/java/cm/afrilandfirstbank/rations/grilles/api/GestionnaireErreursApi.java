package cm.afrilandfirstbank.rations.grilles.api;

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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import cm.afrilandfirstbank.rations.commun.audit.DeltaAudit;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.grilles.domaine.exception.AuteurNonHabiliteException;
import cm.afrilandfirstbank.rations.grilles.domaine.exception.ConflitGrilleException;
import cm.afrilandfirstbank.rations.grilles.domaine.exception.GrilleIntrouvableException;
import cm.afrilandfirstbank.rations.grilles.domaine.exception.MotifRejetRequisException;
import cm.afrilandfirstbank.rations.grilles.domaine.exception.TransitionGrilleInterditeException;
import cm.afrilandfirstbank.rations.grilles.infrastructure.identite.IdentiteIndisponibleException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Traduit les exceptions en reponses au format d'erreur uniforme, et publie un
 * evenement d'audit sur les refus d'acces.
 *
 * <p>Meme dispositif que dans service-identite (decision Sprint 1.3) : les refus
 * sont traces <b>ici</b>, seul point ou ils convergent tous. Les disperser dans
 * chaque controleur garantirait qu'un jour l'un d'eux soit oublie, et un refus
 * non trace n'existe pour personne (CT-04).
 *
 * <p>Les {@code 401} ne passent pas par ici : un jeton absent ou invalide est
 * refuse par le filtre de securite, en amont des controleurs.
 *
 * <p>Trois refus sont traces :
 * <ul>
 *   <li><b>role insuffisant</b> — un DRH tente de creer une grille ;</li>
 *   <li><b>habilitation absente</b> — jeton valide, aucun profil ouvert dans le
 *       module ;</li>
 *   <li><b>service Identite injoignable</b> — l'action est refusee sans qu'on
 *       sache si l'auteur y avait droit. C'est le cas le plus important a
 *       tracer : sans trace, une panne prolongee ne laisserait aucune marque
 *       qu'un travail a ete empeche.</li>
 * </ul>
 *
 * <p>Les conflits d'unicite (409) et les erreurs de validation (400) ne sont
 * <b>pas</b> traces comme des refus d'acces : ce sont des erreurs d'usage d'un
 * utilisateur legitime, pas des tentatives hors perimetre. Les y meler noierait
 * les secondes dans les premieres.
 */
@RestControllerAdvice
public class GestionnaireErreursApi {

    private static final String ACTION_ACCES_REFUSE = "ACCES_REFUSE";

    /** L'objet vise par un refus n'est pas une table, mais la tentative elle-meme. */
    private static final String ENTITE_ACCES = "acces";

    private final PublicateurAudit publicateurAudit;

    public GestionnaireErreursApi(PublicateurAudit publicateurAudit) {
        this.publicateurAudit = publicateurAudit;
    }

    /**
     * Refus prononce par {@code @PreAuthorize}. Sans ce gestionnaire, Spring
     * Security produirait un 403 sans corps, hors du format uniforme du projet,
     * et surtout sans trace.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErreurApiDto> accesRefuse(AccessDeniedException exception,
            HttpServletRequest requete) {
        publierRefus(requete, "ROLE_INSUFFISANT",
                "Le role de l'utilisateur ne permet pas cette action.");

        return reponse(requete, HttpStatus.FORBIDDEN, "ACCES_REFUSE",
                "Le role de l'utilisateur ne permet pas cette action.");
    }

    @ExceptionHandler(AuteurNonHabiliteException.class)
    public ResponseEntity<ErreurApiDto> auteurNonHabilite(AuteurNonHabiliteException exception,
            HttpServletRequest requete) {
        publierRefus(requete, "HABILITATION_ABSENTE", exception.getMessage());

        return reponse(requete, HttpStatus.FORBIDDEN, "UTILISATEUR_NON_HABILITE", exception.getMessage());
    }

    /**
     * Refus conservateur sur panne du service Identite (decision Sprint 1.3).
     *
     * <p>503 et non 403 (decision Sprint 2.2) : l'ARH possede le droit qu'il
     * exerce. Lui repondre « acces refuse » l'enverrait reclamer a
     * l'administrateur une habilitation qu'il a deja, pendant que la panne
     * resterait invisible. Le refus est identique, le diagnostic rendu est juste.
     */
    @ExceptionHandler(IdentiteIndisponibleException.class)
    public ResponseEntity<ErreurApiDto> identiteIndisponible(IdentiteIndisponibleException exception,
            HttpServletRequest requete) {
        publierRefus(requete, "IDENTITE_INDISPONIBLE", exception.getMessage());

        return reponse(requete, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_IDENTITE_INDISPONIBLE",
                "Le service Identite n'a pas repondu : l'auteur de l'action n'a pas pu etre "
                        + "identifie. La grille n'a pas ete enregistree ; reessayez dans "
                        + "quelques instants.");
    }

    /** Conflit d'unicite RG-14. Porte son propre code : les deux causes different. */
    @ExceptionHandler(ConflitGrilleException.class)
    public ResponseEntity<ErreurApiDto> conflitGrille(ConflitGrilleException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.CONFLICT, exception.getCode(), exception.getMessage());
    }

    /**
     * Grille demandee inexistante (Sprint 2.3). 404, sans trace de refus : ce
     * n'est pas une tentative hors perimetre, c'est un lien perime.
     */
    @ExceptionHandler(GrilleIntrouvableException.class)
    public ResponseEntity<ErreurApiDto> grilleIntrouvable(GrilleIntrouvableException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.NOT_FOUND, "GRILLE_INTROUVABLE", exception.getMessage());
    }

    /**
     * Transition de statut non prevue par ET02 : valider une grille deja ACTIVE,
     * rejeter une grille REJETEE, etc.
     *
     * <p>422 et non 409 : la ressource existe et rien ne la duplique — c'est une
     * regle de gestion qui refuse l'operation (contrat d'API, tableau des codes).
     * La DRH consulte typiquement une liste chargee il y a quelques minutes ; le
     * message doit donc nommer le statut courant, seul moyen pour elle de
     * comprendre que quelqu'un a tranche entre-temps.
     */
    @ExceptionHandler(TransitionGrilleInterditeException.class)
    public ResponseEntity<ErreurApiDto> transitionInterdite(TransitionGrilleInterditeException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.UNPROCESSABLE_ENTITY, "TRANSITION_INTERDITE",
                exception.getMessage());
    }

    /**
     * Rejet sans motif (RG-10). 422 {@code MOTIF_OBLIGATOIRE}, code deja retenu
     * par le contrat d'API pour le retour d'un processus sans motif : meme regle,
     * meme code, pour que le frontend n'ait pas deux traitements a ecrire.
     *
     * <p>Distinct d'un 400 de validation de DTO : le champ peut etre present et
     * syntaxiquement valide tout en ne constituant pas un motif — une chaine
     * d'espaces, par exemple.
     */
    @ExceptionHandler(MotifRejetRequisException.class)
    public ResponseEntity<ErreurApiDto> motifRequis(MotifRejetRequisException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.UNPROCESSABLE_ENTITY, "MOTIF_OBLIGATOIRE",
                exception.getMessage());
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

    /**
     * Corps illisible : nature ou session hors enumeration, date mal formee, JSON
     * invalide.
     *
     * <p>Sans ce gestionnaire, {@code "nature": "CARBURANT"} produirait un 400 au
     * format Spring par defaut, hors du format uniforme du module — et l'ecart
     * passerait inapercu, puisque le code HTTP, lui, serait le bon.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErreurApiDto> corpsIllisible(HttpMessageNotReadableException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.BAD_REQUEST, "REQUETE_INVALIDE",
                "Le corps de la requete est illisible : verifiez que nature vaut RATION ou "
                        + "TRANSPORT, que session vaut JOUR ou SOIR, et que dateDebut est au "
                        + "format AAAA-MM-JJ.");
    }

    /**
     * Garde-fou de dernier recours : l'index partiel
     * {@code ux_grille_active_par_couple} s'est declenche.
     *
     * <p>Le controle applicatif de {@code UniciteGrilleService} est cense passer
     * avant, et il passe avant dans tous les cas sequentiels. Reste la course :
     * deux ecritures simultanees que deux verifications simultanees ne peuvent
     * pas voir l'une de l'autre. Sans ce gestionnaire, l'utilisateur recevrait un
     * 500 citant un nom d'index PostgreSQL.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErreurApiDto> violationIntegrite(DataIntegrityViolationException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.CONFLICT, ConflitGrilleException.CODE_GRILLE_ACTIVE,
                "Une grille active existe deja pour cette nature et cette session. "
                        + "Une autre operation a peut-etre abouti au meme instant ; "
                        + "reaffichez la liste avant de recommencer.");
    }

    /**
     * Parametre de requete du mauvais type : {@code ?statut=EN_COURS} sur une
     * enumeration qui ne connait pas cette valeur.
     *
     * <p>Meme raison que {@link #corpsIllisible} : sans ce gestionnaire, la
     * reponse serait un 400 au format Spring, hors du format uniforme du module.
     * Le message enumere les valeurs admises plutot que de renvoyer un nom de
     * classe Java.
     */
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

    private ResponseEntity<ErreurApiDto> reponse(HttpServletRequest requete, HttpStatus statut,
            String code, String message) {
        return ResponseEntity.status(statut).body(new ErreurApiDto(
                LocalDateTime.now(), statut.value(), code, message, requete.getRequestURI()));
    }

    /**
     * Publie la trace d'un refus.
     *
     * <p>{@code idUtilisateur} est nul : c'est precisement l'identifiant que ce
     * service ne connait pas — et, dans le cas d'une panne d'identite, qu'il n'a
     * pas pu obtenir. Le login porte par le jeton est alors la seule identite
     * disponible, sans quoi le refus le plus interessant serait aussi le seul
     * anonyme.
     *
     * <p>Aucune protection contre l'echec : le contrat de {@link PublicateurAudit}
     * garantit que {@code publier} ne leve jamais. Un incident d'audit ne doit pas
     * transformer un 403 en 500.
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
