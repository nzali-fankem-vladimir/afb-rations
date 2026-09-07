package cm.afrilandfirstbank.rations.workflow.api;

import java.time.LocalDateTime;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import cm.afrilandfirstbank.rations.workflow.domaine.exception.AccuseContradictoireException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.AgentNonHabiliteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.DelaiRegularisationDepasseException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.DelaiRegularisationIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.DocumentNonProduitException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.EtatIncompletException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.EtatNonClotureException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.FonctionnaliteNonOuverteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.MotifOuvertureRequisException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.MotifRetourRequisException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.OrigineRequiseException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.PeriodeNonConcordanteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.PieceJointeExistanteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusExistantException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusNonTransmisException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.RoleNonAttenduException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.SeparationTachesException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ServiceIdentiteIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ServiceSaisieIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.SeuilIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.TransitionProcessusInterditeException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.UniteNonConcordanteException;

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

    private static final Logger journal = LoggerFactory.getLogger(GestionnaireErreursApi.class);

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

    /**
     * Le role de l'appelant n'est pas celui que le dossier attend a ce stade (RG-07).
     *
     * <p>{@code 403 ACCES_REFUSE}, comme le refus de role de Spring Security : c'est
     * la meme nature de refus. Seul le message differe, et c'est tout l'objet de ce
     * gestionnaire — dire quel niveau le dossier attend, plutot que « votre role ne
     * permet pas cette action », qui serait faux pour un valideur du circuit.
     */
    @ExceptionHandler(RoleNonAttenduException.class)
    public ResponseEntity<ErreurApiDto> roleNonAttendu(RoleNonAttenduException exception,
            HttpServletRequest requete) {
        publierRefus(requete, "ROLE_INSUFFISANT", exception.getMessage());
        return reponse(requete, HttpStatus.FORBIDDEN, "ACCES_REFUSE", exception.getMessage());
    }

    /**
     * Separation des taches (RG-12) : {@code 403 SEPARATION_TACHES}, code du contrat
     * d'API section 5.
     *
     * <p><b>Un troisieme code de refus en 403, et ce n'est pas une redondance.</b>
     * Celui-ci dit a l'utilisateur qu'il a le bon role et la bonne portee, mais qu'il
     * a deja agi sur ce dossier : rien ne lui manque, c'est le dossier qui doit
     * changer de mains. Le confondre avec {@code ACCES_REFUSE} ou
     * {@code UTILISATEUR_NON_HABILITE} enverrait un chef d'unite reclamer une
     * habilitation qu'il possede deja.
     *
     * <p>Trace en audit comme les deux autres refus (CT-04), avec son propre motif :
     * un controle interne doit pouvoir compter les tentatives de cumul separement des
     * tentatives d'acces hors perimetre.
     */
    @ExceptionHandler(SeparationTachesException.class)
    public ResponseEntity<ErreurApiDto> separationTaches(SeparationTachesException exception,
            HttpServletRequest requete) {
        publierRefus(requete, "SEPARATION_TACHES", exception.getMessage());
        return reponse(requete, HttpStatus.FORBIDDEN, "SEPARATION_TACHES", exception.getMessage());
    }

    /**
     * L'unite declaree dans une demande d'ouverture ne correspond pas a celle de l'etat
     * d'origine (Sprint 6bis.1) : {@code 403 UNITE_NON_CONCORDANTE}, meme code que celui
     * oppose par le service Saisie depuis le Sprint 3.4 pour le meme desaccord.
     *
     * <p><b>Trace en audit, et ce n'etait pas negociable.</b> Le scenario derriere ce
     * refus est une demande qui vise un dossier tout en en declarant un autre — un defaut
     * de client, ou une tentative de debordement de perimetre, et rien ne permet de les
     * distinguer au moment du refus. La doctrine du Sprint 6.3 est explicite : tout refus
     * d'acces se trace (CT-04), sans quoi il n'existe nulle part. Il passe donc par le
     * meme {@code publierRefus} que les quatre autres refus d'acces de ce fichier, avec
     * son propre motif — un controle interne doit pouvoir compter les desaccords d'unite
     * separement des absences d'habilitation.
     */
    @ExceptionHandler(UniteNonConcordanteException.class)
    public ResponseEntity<ErreurApiDto> uniteNonConcordante(UniteNonConcordanteException exception,
            HttpServletRequest requete) {
        publierRefus(requete, "UNITE_NON_CONCORDANTE", exception.getMessage());
        return reponse(requete, HttpStatus.FORBIDDEN, "UNITE_NON_CONCORDANTE",
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

    /**
     * Ouverture d'un etat complementaire sans identifiant d'origine (Sprint 6bis.1).
     *
     * <p>{@code 422} et non {@code 400} : la requete est syntaxiquement valide — c'est
     * exactement celle d'un etat NORMAL —, et la contrainte est <b>conditionnelle</b> au
     * type demande. Un {@code @NotNull} sur le DTO refuserait tous les declenchements
     * ordinaires du module.
     */
    @ExceptionHandler(OrigineRequiseException.class)
    public ResponseEntity<ErreurApiDto> origineRequise(OrigineRequiseException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.UNPROCESSABLE_ENTITY, "ORIGINE_REQUISE",
                exception.getMessage());
    }

    /**
     * Ouverture d'un etat complementaire sans motif (Sprint 6bis.1).
     *
     * <p><b>Meme code que le retour sans motif</b> : {@code MOTIF_OBLIGATOIRE} est celui
     * du contrat pour RG-10, repris tel quel au Sprint 2.3 pour le rejet d'une grille.
     * Meme regle — une decision qui engage doit etre justifiee —, donc meme code. Une
     * exception distincte pour que les deux messages restent ecrits pour leur lecteur.
     */
    @ExceptionHandler(MotifOuvertureRequisException.class)
    public ResponseEntity<ErreurApiDto> motifOuvertureRequis(MotifOuvertureRequisException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.UNPROCESSABLE_ENTITY, "MOTIF_OBLIGATOIRE",
                exception.getMessage());
    }

    /**
     * L'etat d'origine est clos depuis plus longtemps que le delai de regularisation
     * (Sprint 6bis.1).
     *
     * <p>{@code 422} et non {@code 409} : rien n'est duplique, une regle de gestion
     * refuse. Le delai est celui de {@code DELAI_REGULARISATION_JOURS}, dont la valeur
     * de 90 jours reste <b>provisoire</b> jusqu'a confirmation metier (point M-02) : le
     * message le nomme, pour qu'un refus contestable puisse etre conteste.
     */
    @ExceptionHandler(DelaiRegularisationDepasseException.class)
    public ResponseEntity<ErreurApiDto> delaiRegularisationDepasse(
            DelaiRegularisationDepasseException exception, HttpServletRequest requete) {
        return reponse(requete, HttpStatus.UNPROCESSABLE_ENTITY, "DELAI_REGULARISATION_DEPASSE",
                exception.getMessage());
    }

    /**
     * La periode declaree n'est pas celle de l'etat d'origine (Sprint 6bis.1).
     *
     * <p>{@code 422} la ou l'unite vaut {@code 403} : la periode n'ouvre aucun droit, se
     * tromper de mois est une maladresse et non un franchissement de perimetre. Non
     * tracee en audit, pour la meme raison — le journal des refus d'acces n'a pas a se
     * remplir de fautes de frappe.
     */
    @ExceptionHandler(PeriodeNonConcordanteException.class)
    public ResponseEntity<ErreurApiDto> periodeNonConcordante(
            PeriodeNonConcordanteException exception, HttpServletRequest requete) {
        return reponse(requete, HttpStatus.UNPROCESSABLE_ENTITY, "PERIODE_NON_CONCORDANTE",
                exception.getMessage());
    }

    /**
     * CT-13 : une soumission incomplete est refusee <b>en listant les manques</b>.
     *
     * <p>C'est le seul endroit du module ou le champ {@code manques} du format
     * d'erreur est renseigne. Un message generique du type « etat incomplet »
     * obligerait l'agent a chercher lui-meme ce qui cloche, ce que l'etape 2 du
     * guide 4.2 refuse explicitement.
     *
     * <p>{@code 422} et non {@code 409} : rien n'est duplique, c'est une regle de
     * gestion qui refuse (meme raisonnement qu'au Sprint 2.3 pour
     * {@code TRANSITION_INTERDITE}).
     */
    @ExceptionHandler(EtatIncompletException.class)
    public ResponseEntity<ErreurApiDto> etatIncomplet(EtatIncompletException exception,
            HttpServletRequest requete) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErreurApiDto(
                LocalDateTime.now(),
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                "ETAT_INCOMPLET",
                exception.getMessage(),
                requete.getRequestURI(),
                exception.getManques()));
    }

    /**
     * Un document a deja ete genere pour ce processus : il a donc deja ete soumis.
     *
     * <p>{@code 409} comme {@code PROCESSUS_EXISTANT} au Sprint 4.1 : quelque chose
     * est bien duplique. Le refus vient du service, dont le message est lisible ;
     * la contrainte {@code id_processus UNIQUE} reste le filet en cas de course,
     * traduite en {@code 409} par le gestionnaire de violation d'integrite.
     */
    @ExceptionHandler(PieceJointeExistanteException.class)
    public ResponseEntity<ErreurApiDto> pieceJointeExistante(PieceJointeExistanteException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.CONFLICT, "PIECE_JOINTE_EXISTANTE",
                exception.getMessage());
    }

    // --- Defaillance du serveur (500) ------------------------------------------

    /**
     * Le document n'a pas pu etre produit : composition ou ecriture en echec.
     *
     * <p>{@code 500} et non {@code 422} : ce n'est ni une maladresse de l'agent, ni
     * une regle de gestion, c'est une defaillance du serveur. Le presenter comme un
     * refus metier enverrait l'agent corriger une saisie qui n'a rien de faux.
     *
     * <p>Le message affirme que <b>rien n'a ete enregistre</b>, et c'est vrai par
     * construction : la generation et l'ecriture precedent l'ouverture de la
     * transaction (decision Sprint 4.2). L'agent peut donc simplement recommencer.
     *
     * <p>Journalise en {@code error} avec la pile : contrairement aux refus metier,
     * celui-ci appelle une intervention d'exploitation (disque plein, volume non
     * monte, droits d'ecriture).
     */
    @ExceptionHandler(DocumentNonProduitException.class)
    public ResponseEntity<ErreurApiDto> documentNonProduit(DocumentNonProduitException exception,
            HttpServletRequest requete) {
        journal.error("DOCUMENT NON PRODUIT sur {} : {}",
                requete.getRequestURI(), exception.getMessage(), exception);
        return reponse(requete, HttpStatus.INTERNAL_SERVER_ERROR, "DOCUMENT_NON_PRODUIT",
                exception.getMessage());
    }

    /**
     * Le seuil d'aiguillage (RG-08) n'a pas pu etre lu.
     *
     * <p>{@code 500} et non {@code 422} : le chef d'unite n'a commis aucune erreur
     * et n'a rien a corriger, c'est la configuration du module qui est en defaut.
     * Le presenter comme un refus metier l'enverrait chercher une faute dans un
     * dossier qui n'en a pas.
     *
     * <p>Aucune valeur de repli n'est appliquee : le service refuse plutot que
     * d'aiguiller sur un seuil qu'il aurait invente (CLAUDE.md section 15). Le
     * detail est deja journalise au prefixe {@code SEUIL INDISPONIBLE} par
     * {@code SeuilService} ; on ne le redit pas ici.
     */
    @ExceptionHandler(SeuilIndisponibleException.class)
    public ResponseEntity<ErreurApiDto> seuilIndisponible(SeuilIndisponibleException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.INTERNAL_SERVER_ERROR, "SEUIL_INDISPONIBLE",
                exception.getMessage());
    }

    /**
     * Le delai de regularisation n'a pas pu etre exploite (Sprint 6bis.1) : parametre
     * absent, desactive, valeur illisible ou negative — ou date de cloture de l'origine
     * introuvable.
     *
     * <p><b>Jumeau de {@code SEUIL_INDISPONIBLE}, et pour la meme raison</b> :
     * {@code 500} et non {@code 422}, parce que l'agent n'a rien a corriger dans sa
     * demande, c'est la configuration du module qui est en defaut. Aucune valeur de repli
     * n'est appliquee : un delai devine ouvrirait ou fermerait la regularisation au
     * hasard sur une periode ou un paiement a deja eu lieu.
     *
     * <p>Le detail est deja journalise au prefixe {@code DELAI REGULARISATION
     * INDISPONIBLE} par {@code FonctionnaliteService} ; on ne le redit pas ici.
     */
    @ExceptionHandler(DelaiRegularisationIndisponibleException.class)
    public ResponseEntity<ErreurApiDto> delaiRegularisationIndisponible(
            DelaiRegularisationIndisponibleException exception, HttpServletRequest requete) {
        return reponse(requete, HttpStatus.INTERNAL_SERVER_ERROR,
                "DELAI_REGULARISATION_INDISPONIBLE", exception.getMessage());
    }

    /**
     * Accuse comptable portant sur un etat jamais transmis (Sprint 5.2).
     *
     * <p><b>{@code 422} et non {@code 409}</b> : rien n'est duplique, c'est une regle de
     * gestion qui refuse — meme distinction qu'au Sprint 2.3 entre
     * {@code TRANSITION_INTERDITE} et un conflit d'unicite.
     *
     * <p>Non trace ici : c'est le service Transmission, qui a recu l'accuse, qui publie la
     * trace d'audit avec le prefixe {@code ACCUSE INCOHERENT}. La tracer aux deux endroits
     * ferait deux lignes pour un seul fait.
     */
    @ExceptionHandler(ProcessusNonTransmisException.class)
    public ResponseEntity<ErreurApiDto> processusNonTransmis(ProcessusNonTransmisException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.UNPROCESSABLE_ENTITY, "PROCESSUS_NON_TRANSMIS",
                exception.getMessage());
    }

    /**
     * Verrou de transmission demande sur un etat qui n'est pas cloture (Sprint 5.3).
     *
     * <p><b>{@code 422} et non {@code 409}</b>, comme le precedent : rien n'est duplique,
     * une regle de gestion refuse. Le code {@code ETAT_NON_CLOTURE} est le meme que celui
     * rendu par le service Transmission pour le meme fait vu de l'autre cote — deux codes
     * differents pour un seul refus enverraient chercher deux causes.
     */
    @ExceptionHandler(EtatNonClotureException.class)
    public ResponseEntity<ErreurApiDto> etatNonCloture(EtatNonClotureException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.UNPROCESSABLE_ENTITY, "ETAT_NON_CLOTURE",
                exception.getMessage());
    }

    /**
     * Accuse comptable contredisant un statut d'integration deja recu (Sprint 5.2).
     *
     * <p><b>{@code 409} et non {@code 422}</b>, contrairement au precedent : il y a bien
     * ici deux affirmations concurrentes sur la meme ressource, et c'est la definition d'un
     * conflit — meme distinction qu'au Sprint 4.1 entre {@code PROCESSUS_EXISTANT}
     * ({@code 409}) et {@code FONCTIONNALITE_NON_OUVERTE} ({@code 422}).
     *
     * <p>Le message nomme le statut deja porte <b>et</b> celui de l'accuse recu : sans les
     * deux, personne ne pourrait lever la contradiction avec la comptabilite.
     */
    @ExceptionHandler(AccuseContradictoireException.class)
    public ResponseEntity<ErreurApiDto> accuseContradictoire(AccuseContradictoireException exception,
            HttpServletRequest requete) {
        return reponse(requete, HttpStatus.CONFLICT, "ACCUSE_CONTRADICTOIRE",
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
