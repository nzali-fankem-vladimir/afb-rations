package cm.afrilandfirstbank.rations.transmission.api;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import cm.afrilandfirstbank.rations.commun.audit.DeltaAudit;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.transmission.domaine.exception.ChargeIncompleteException;
import cm.afrilandfirstbank.rations.transmission.domaine.exception.EtatNonClotureException;
import cm.afrilandfirstbank.rations.transmission.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.transmission.domaine.exception.PublicationEchoueeException;
import cm.afrilandfirstbank.rations.transmission.domaine.exception.ServiceSaisieIndisponibleException;
import cm.afrilandfirstbank.rations.transmission.domaine.exception.ServiceWorkflowIndisponibleException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Traduit chaque refus du service Transmission en reponse HTTP au format uniforme du
 * module (CLAUDE.md section 11), sur le modele des gestionnaires des Sprints 2.2, 3.3
 * et 4.1.
 *
 * <h2>Point unique de convergence des refus d'acces (CT-04)</h2>
 *
 * <p>Obligation transverse du Sprint 1.3, rappelee a la section 0.2 du guide 5.1 : le
 * service Identite ne trace pas les verdicts negatifs — il repond a une question, il ne
 * refuse pas l'action —, c'est au consommateur de publier {@code ACCES_REFUSE}. <b>Si ce
 * fichier ne publiait pas, le refus ne serait trace nulle part</b> et l'exigence CT-04 ne
 * serait pas tenue, en silence.
 *
 * <p><b>Nuance propre a ce service</b> : il ne consomme pas
 * {@code GET /identite/habilitation}. La portee d'acces sur l'unite est deja verifiee par
 * le service Workflow, avant la validation qui a produit la cloture, puis une seconde
 * fois par les deux endpoints que ce service interroge — {@code GET /processus/{id}} et
 * {@code GET /saisie/processus/{id}/etat} —, sur le jeton relaye de l'utilisateur final.
 * La rejouer ici serait un troisieme appel pour la meme question, sans rien apprendre.
 * Reste donc le seul refus qui naisse <b>ici</b> : celui du role, oppose par
 * {@code @PreAuthorize}. Il est publie.
 *
 * <p><b>Non traces</b> : les {@code 401} sans jeton, refuses par le filtre de securite en
 * amont des controleurs — une requete anonyme est une absence d'authentification, pas une
 * action hors perimetre —, et les erreurs d'usage ({@code 400}, {@code 404}), qui sont
 * des maladresses et non des tentatives ({@code docs/publication-audit.md} section 4).
 *
 * <h2>Les refus de transmission, eux, sont traces par le service applicatif</h2>
 *
 * <p>{@code CHARGE_INCOMPLETE} et {@code PUBLICATION_ECHOUEE} sont publies en audit par
 * {@code TransmissionService}, la ou ils naissent : il connait le dossier, son unite et
 * sa periode, que ce gestionnaire n'a pas. Ils ne sont donc pas publies une seconde fois
 * ici — une trace en double se compte deux fois dans un rapport de controle interne.
 */
@RestControllerAdvice
public class GestionnaireErreursApi {

    private static final Logger journal = LoggerFactory.getLogger(GestionnaireErreursApi.class);

    private static final String ACTION_ACCES_REFUSE = "ACCES_REFUSE";
    private static final String ENTITE_ACCES = "acces";

    private final PublicateurAudit publicateurAudit;

    public GestionnaireErreursApi(PublicateurAudit publicateurAudit) {
        this.publicateurAudit = publicateurAudit;
    }

    // --- Acces (trace en audit, CT-04) -------------------------------------------

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErreurApiDto> accesRefuse(AccessDeniedException exception,
            HttpServletRequest requete) {
        publierRefus(requete, "ROLE_INSUFFISANT",
                "Le role de l'utilisateur ne permet pas cette action.");
        return reponse(requete, HttpStatus.FORBIDDEN, "ACCES_REFUSE",
                "Le role de l'utilisateur ne permet pas cette action.");
    }

    // --- Ressource introuvable (404) ---------------------------------------------

    @ExceptionHandler(ProcessusIntrouvableException.class)
    public ResponseEntity<ErreurApiDto> processusIntrouvable(
            ProcessusIntrouvableException exception, HttpServletRequest requete) {
        return reponse(requete, HttpStatus.NOT_FOUND, "PROCESSUS_INTROUVABLE",
                exception.getMessage());
    }

    // --- Regles de gestion (422) --------------------------------------------------

    /**
     * L'etat n'est pas cloture : rien ne part vers la comptabilite.
     *
     * <p>{@code 422} et non {@code 409} : rien n'est duplique, c'est une regle de gestion
     * qui refuse — meme distinction qu'au Sprint 2.3 pour {@code TRANSITION_INTERDITE}.
     */
    @ExceptionHandler(EtatNonClotureException.class)
    public ResponseEntity<ErreurApiDto> etatNonCloture(EtatNonClotureException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.UNPROCESSABLE_ENTITY, "ETAT_NON_CLOTURE",
                exception.getMessage());
    }

    // --- Incoherences du module (500) ---------------------------------------------

    /**
     * La charge ne tient pas debout : elle ne part pas.
     *
     * <p>{@code 500} et non {@code 422} : l'etat est cloture, donc fige, et l'appelant n'a
     * rien a corriger. Une charge incomplete a ce stade signale un defaut du module.
     * Meme parti qu'au Sprint 2.4 pour {@code INCOHERENCE_GRILLE} et au Sprint 4.3 pour
     * {@code SEUIL_INDISPONIBLE} : le service refuse et signale, il n'arbitre jamais.
     *
     * <p>Deja journalise au prefixe {@code CHARGE INCOMPLETE} et trace en audit par le
     * service applicatif.
     */
    @ExceptionHandler(ChargeIncompleteException.class)
    public ResponseEntity<ErreurApiDto> chargeIncomplete(ChargeIncompleteException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.INTERNAL_SERVER_ERROR, "CHARGE_INCOMPLETE",
                exception.getMessage());
    }

    // --- Dependances indisponibles (503) ------------------------------------------

    @ExceptionHandler(ServiceWorkflowIndisponibleException.class)
    public ResponseEntity<ErreurApiDto> workflowIndisponible(
            ServiceWorkflowIndisponibleException exception, HttpServletRequest requete) {
        return reponse(requete, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_WORKFLOW_INDISPONIBLE",
                exception.getMessage());
    }

    @ExceptionHandler(ServiceSaisieIndisponibleException.class)
    public ResponseEntity<ErreurApiDto> saisieIndisponible(
            ServiceSaisieIndisponibleException exception, HttpServletRequest requete) {
        return reponse(requete, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_SAISIE_INDISPONIBLE",
                exception.getMessage());
    }

    /**
     * Le broker n'a pas accuse reception.
     *
     * <p>{@code 503} : l'appelant doit comprendre que <b>rien n'est parti</b>, et donc ne
     * pas poser le drapeau {@code transmis_comptabilite}. Le poser rendrait l'etat
     * definitivement impaye, le controle d'unicite de RG-13 refusant ensuite la vraie
     * transmission comme un doublon.
     */
    @ExceptionHandler(PublicationEchoueeException.class)
    public ResponseEntity<ErreurApiDto> publicationEchouee(PublicationEchoueeException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.SERVICE_UNAVAILABLE, "PUBLICATION_ECHOUEE",
                exception.getMessage());
    }

    // --- Requete mal formee (400) --------------------------------------------------

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErreurApiDto> parametreInvalide(
            MethodArgumentTypeMismatchException exception, HttpServletRequest requete) {
        return reponse(requete, HttpStatus.BAD_REQUEST, "REQUETE_INVALIDE",
                "Parametre '" + exception.getName() + "' invalide.");
    }

    // --- Filet (500) ---------------------------------------------------------------

    /**
     * Tout le reste. Le message technique n'est pas rendu au client : il est journalise
     * avec sa pile, et la reponse reste generique.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErreurApiDto> erreurInattendue(Exception exception,
            HttpServletRequest requete) {
        journal.error("Erreur inattendue sur {} {}", requete.getMethod(), requete.getRequestURI(),
                exception);
        return reponse(requete, HttpStatus.INTERNAL_SERVER_ERROR, "ERREUR_INTERNE",
                "Une erreur interne est survenue. Signalez-le a l'administrateur du module.");
    }

    // --- Outillage -----------------------------------------------------------------

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
