package cm.afrilandfirstbank.rations.workflow.api;

import java.net.URI;
import java.time.LocalDate;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cm.afrilandfirstbank.rations.workflow.api.dto.DeclenchementProcessusRequest;
import cm.afrilandfirstbank.rations.workflow.api.dto.EtatProcessusResponse;
import cm.afrilandfirstbank.rations.workflow.api.dto.HistoriqueProcessusResponse;
import cm.afrilandfirstbank.rations.workflow.api.dto.IntegrationComptableRequest;
import cm.afrilandfirstbank.rations.workflow.api.dto.IntegrationComptableResponse;
import cm.afrilandfirstbank.rations.workflow.api.dto.IntegrationProcessusResponse;
import cm.afrilandfirstbank.rations.workflow.api.dto.ProcessusResponse;
import cm.afrilandfirstbank.rations.workflow.api.dto.RechercheProcessusResponse;
import cm.afrilandfirstbank.rations.workflow.api.dto.RetourRequest;
import cm.afrilandfirstbank.rations.workflow.api.dto.RetourResponse;
import cm.afrilandfirstbank.rations.workflow.api.dto.SoumissionResponse;
import cm.afrilandfirstbank.rations.workflow.api.dto.ValidationResponse;
import cm.afrilandfirstbank.rations.workflow.api.dto.VerrouTransmissionRequest;
import cm.afrilandfirstbank.rations.workflow.api.dto.VerrouTransmissionResponse;
import cm.afrilandfirstbank.rations.workflow.application.FonctionnaliteService;
import cm.afrilandfirstbank.rations.workflow.application.IntegrationComptableService;
import cm.afrilandfirstbank.rations.workflow.application.OuvertureComplementaireService;
import cm.afrilandfirstbank.rations.workflow.application.ProcessusService;
import cm.afrilandfirstbank.rations.workflow.application.RechercheProcessusService;
import cm.afrilandfirstbank.rations.workflow.application.ResultatVerrouTransmission;
import cm.afrilandfirstbank.rations.workflow.application.RetourService;
import cm.afrilandfirstbank.rations.workflow.application.SoumissionService;
import cm.afrilandfirstbank.rations.workflow.application.ValidationService;
import cm.afrilandfirstbank.rations.workflow.application.VerrouTransmissionService;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEnum;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Les endpoints du processus mensuel (contrat d'API section 5).
 *
 * <pre>
 *   POST /processus                        declenchement       AGENT_UNITE        (4.1)
 *                                          + ouverture d'un etat complementaire   (6bis.1)
 *   GET  /processus/{id}                   detail et statut    roles du circuit   (4.1)
 *   GET  /processus/{id}/etat              etat consolide      roles du circuit   (4.1)
 *   POST /processus/{id}/soumission        soumission          AGENT_UNITE        (4.2)
 *   POST /processus/{id}/validation        validation DA et DR DA, DR            (4.3, 4.4)
 *   POST /processus/{id}/retour            retour motive       DA, DR             (4.4)
 * </pre>
 *
 * <p>Les six endpoints du contrat d'API sont desormais tous servis, et il n'y en a
 * pas un de plus. La reprise d'un etat retourne n'en ajoute aucun : elle est portee
 * par la resoumission (voir {@code SoumissionService}).
 *
 * <h2>Plus un endpoint interne, hors contrat passerelle</h2>
 *
 * <pre>
 *   PUT  /processus/{id}/integration       accuse comptable    secret partage     (5.2)
 *   PUT  /processus/{id}/transmission      verrou RG-13        DA, DR             (5.3)
 *   GET  /processus/{id}/integration       statut d'integration ARH + circuit     (5.3)
 * </pre>
 *
 * <p>Aucun des trois n'est destine au frontend, et <b>aucun n'est route par la
 * passerelle</b> : le premier est appele par le service Transmission a la reception d'un
 * accuse sur {@code rations.etat.accuse} ; les deux autres le sont pendant le traitement
 * de {@code POST /transmission/processus/{id}} et de
 * {@code GET /transmission/processus/{id}}. Meme statut que
 * {@code GET /identite/habilitation} (Sprint 1.3),
 * {@code GET /saisie/processus/{id}/etat} (Sprint 3.4) et
 * {@code POST /transmission/processus/{id}} (Sprint 5.1) : <b>le compte de six endpoints
 * reste celui du contrat expose par la passerelle</b>.
 *
 * <p>Le verrou du Sprint 5.3 ne contredit pas la section 7 du contrat, qui dit que le
 * service Transmission n'expose <b>aucun endpoint de declenchement au client</b> : il ne
 * declenche rien, il tient un drapeau, et il vit ici — dans le service qui detient la
 * table — et non dans le service Transmission.
 *
 * <h2>Les roles ne sont pas les memes selon l'endpoint</h2>
 *
 * <p>D'ou {@code @PreAuthorize} pose par methode et non sur la classe. Seul
 * l'agent d'unite ouvre un etat ; le chef d'unite et le directeur reseau doivent
 * en revanche pouvoir <b>lire l'etat qu'ils vont valider</b>, sans quoi le
 * circuit se bloquerait des son premier essai. C'est le meme decoupage qu'au
 * Sprint 3.4 cote Saisie, ou il avait impose un controleur separe.
 *
 * <p><b>Le role n'est que le premier filtre.</b> La portee d'acces — cet acteur
 * a-t-il droit sur <i>cette unite</i> — est verifiee en plus, unite par unite,
 * aupres du service Identite (RG-12). Un agent d'unite habilite sur {@code 00002}
 * ne declenche ni ne lit un dossier de {@code 00007}, et son role ne le distingue
 * pas de l'agent legitime.
 *
 * <h2>Le jeton est relaye, pas reemis</h2>
 *
 * <p>Chaque methode recoit l'en-tete {@code Authorization} et le transmet tel
 * quel aux appels sortants (Identite, Saisie). Le realm ne comporte aucun compte
 * de service, et la question posee en aval porte sur l'utilisateur final
 * (doctrine Sprint 1.3).
 */
@RestController
@RequestMapping("/processus")
public class ProcessusController {

    private final ProcessusService processusService;
    private final FonctionnaliteService fonctionnaliteService;
    private final OuvertureComplementaireService ouvertureComplementaireService;
    private final SoumissionService soumissionService;
    private final ValidationService validationService;
    private final RetourService retourService;
    private final IntegrationComptableService integrationComptableService;
    private final VerrouTransmissionService verrouTransmissionService;
    private final RechercheProcessusService rechercheProcessusService;

    public ProcessusController(ProcessusService processusService,
            FonctionnaliteService fonctionnaliteService,
            OuvertureComplementaireService ouvertureComplementaireService,
            SoumissionService soumissionService,
            ValidationService validationService,
            RetourService retourService,
            IntegrationComptableService integrationComptableService,
            VerrouTransmissionService verrouTransmissionService,
            RechercheProcessusService rechercheProcessusService) {
        this.processusService = processusService;
        this.fonctionnaliteService = fonctionnaliteService;
        this.ouvertureComplementaireService = ouvertureComplementaireService;
        this.soumissionService = soumissionService;
        this.validationService = validationService;
        this.retourService = retourService;
        this.integrationComptableService = integrationComptableService;
        this.verrouTransmissionService = verrouTransmissionService;
        this.rechercheProcessusService = rechercheProcessusService;
    }

    /**
     * Ouvre un etat mensuel. {@code 201} avec le processus cree, au statut
     * {@code EN_COURS_SAISIE}.
     *
     * <h2>Un endpoint, deux gestes, aiguilles sur le type demande</h2>
     *
     * <pre>
     *   NORMAL         le cycle du mois courant                    (Sprint 4.1)
     *   COMPLEMENTAIRE une regularisation sur une periode close    (Sprint 6bis.1)
     * </pre>
     *
     * <p>Le contrat d'API section 5 ne prevoit qu'un endpoint pour les deux, et son
     * exemple d'etat complementaire porte exactement le meme corps de requete, enrichi
     * de {@code typeProcessus}, {@code idProcessusOrigine} et {@code motifOuverture}.
     * L'aiguillage se lit donc ici, au seul endroit ou le type arrive.
     *
     * <p><b>Deux services et non un</b> : les deux gestes ne partagent presque rien.
     * L'un verifie qu'aucun etat normal n'existe pour la periode ; l'autre verifie un
     * drapeau de fonctionnalite, l'existence et la cloture d'une origine, un delai de
     * regularisation et la concordance de ce qui est declare. Les fondre en une methode
     * a branches aurait produit un service dont la moitie du corps ne s'applique jamais
     * au cas courant.
     *
     * <p><b>Le refus systematique du Sprint 4.1 est leve</b> : il est desormais porte par
     * le drapeau {@code RATTRAPAGE_ACTIF}, en premiere position de
     * {@code OuvertureComplementaireService}. Tant que le metier n'a pas confirme le
     * besoin (point M-01), le comportement observable est le meme —
     * {@code 422 FONCTIONNALITE_NON_OUVERTE} — mais il s'ouvre desormais par une mise a
     * jour de parametre, sans reprise de code ni redeploiement.
     *
     * <p>Refus possibles, etat NORMAL : {@code 409 PROCESSUS_EXISTANT} si un etat normal
     * est deja ouvert pour cette unite et cette periode ; {@code 403} hors portee ;
     * {@code 503} si le service Identite ne repond pas.
     *
     * <p>Refus possibles, etat COMPLEMENTAIRE : {@code 422 FONCTIONNALITE_NON_OUVERTE}
     * drapeau ferme ; {@code 422 ORIGINE_REQUISE} sans identifiant d'origine ;
     * {@code 422 MOTIF_OBLIGATOIRE} sans motif ; {@code 404 PROCESSUS_INTROUVABLE} ;
     * {@code 403 UTILISATEUR_NON_HABILITE} hors portee sur l'unite de l'origine ;
     * {@code 422 ETAT_NON_CLOTURE} origine encore dans le circuit ;
     * {@code 422 DELAI_REGULARISATION_DEPASSE} origine trop ancienne ;
     * {@code 403 UNITE_NON_CONCORDANTE} et {@code 422 PERIODE_NON_CONCORDANTE} si la
     * demande ne decrit pas le dossier qu'elle vise ;
     * {@code 500 DELAI_REGULARISATION_INDISPONIBLE} si le delai n'est pas exploitable.
     *
     * <p>L'en-tete {@code Location} pointe vers la ressource creee, comme le veut
     * la convention REST pour un {@code 201}.
     */
    @PostMapping
    @PreAuthorize("hasRole('AGENT_UNITE')")
    public ResponseEntity<ProcessusResponse> declencher(
            @Valid @RequestBody DeclenchementProcessusRequest requete,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation,
            HttpServletRequest requeteHttp) {

        String adresseIp = requeteHttp.getRemoteAddr();

        ProcessusMensuel processus = switch (requete.typeDemande()) {
            case NORMAL -> processusService.declencher(requete, enteteAutorisation, adresseIp);
            case COMPLEMENTAIRE ->
                    ouvertureComplementaireService.ouvrir(requete, enteteAutorisation, adresseIp);
        };

        return ResponseEntity
                .created(URI.create("/processus/" + processus.getId()))
                .body(ProcessusResponse.depuis(processus));
    }

    /**
     * Detail et statut d'un processus.
     *
     * <p>Sert aussi le service Saisie, qui interroge cet endpoint a chaque
     * ecriture pour savoir si l'etat est encore modifiable
     * ({@code docs/rattachement-processus.md} section 4). Les cinq premiers champs
     * de {@link ProcessusResponse} sont donc un contrat inter-services : voir
     * l'action B-04 de {@code docs/dispositifs_provisoires.md}.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT_UNITE', 'CHEF_UNITE_DA', 'DIRECTEUR_RESEAU_DR')")
    public ResponseEntity<ProcessusResponse> consulter(
            @PathVariable Long id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation) {

        // Le compte de charge accompagne le dossier parce que le service Transmission
        // lit cette meme reponse pour construire la charge comptable, et n'a pas de base
        // ou lire parametre_systeme (Maille 2). Relu a chaque appel, jamais mis en cache.
        return ResponseEntity.ok(ProcessusResponse.depuis(
                processusService.consulter(id, enteteAutorisation),
                fonctionnaliteService.compteChargeRations()));
    }

    /**
     * Etat consolide de la periode : le detail journee par journee, obtenu du service
     * Saisie par appel d'API, joint a ce que Workflow detient seul.
     *
     * <p>Aucun acces a la base {@code rations_saisie} : l'echange passe par
     * l'endpoint interne {@code GET /saisie/processus/{id}/etat}, et le code unite
     * transmis est celui du processus, pas un parametre de la requete.
     *
     * <p>Un processus sans aucune journee saisie rend {@code 200} avec zero
     * journee et un total de zero — c'est un etat normal, pas une erreur. Service
     * Saisie injoignable : {@code 503 SERVICE_SAISIE_INDISPONIBLE}, jamais un
     * total suppose.
     */
    @GetMapping("/{id}/etat")
    @PreAuthorize("hasAnyRole('AGENT_UNITE', 'CHEF_UNITE_DA', 'DIRECTEUR_RESEAU_DR')")
    public ResponseEntity<EtatProcessusResponse> consulterEtat(
            @PathVariable Long id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation) {

        return ResponseEntity.ok(EtatProcessusResponse.depuis(
                processusService.consulterEtat(id, enteteAutorisation)));
    }

    /**
     * Soumission de l'etat mensuel par l'agent d'unite (US-07, CT-12, CT-13).
     *
     * <p><b>Reserve a {@code AGENT_UNITE}.</b> Le role n'est ici que le premier
     * filtre : la portee d'acces est verifiee en plus, sur l'unite <i>du
     * processus</i>, aupres du service Identite. Un chef d'unite ne soumet pas a la
     * place de son agent : ce serait un cumul saisie / validation que RG-12
     * interdit.
     *
     * <p><b>Aucun corps de requete.</b> Tout ce qui est necessaire se deduit du
     * processus et du jeton : rien n'est laisse au choix de l'appelant, et surtout
     * pas le montant, qui vient de l'etat consolide et de nulle part ailleurs.
     *
     * <p>{@code 200} et non {@code 201} : la soumission ne cree pas la ressource
     * adressee, elle en change l'etat. La piece jointe, elle, est bien creee, et
     * la reponse en rend compte.
     */
    @PostMapping("/{id}/soumission")
    @PreAuthorize("hasRole('AGENT_UNITE')")
    public ResponseEntity<SoumissionResponse> soumettre(
            @PathVariable Long id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation,
            HttpServletRequest requeteHttp) {

        return ResponseEntity.ok(SoumissionResponse.depuis(
                soumissionService.soumettre(id, enteteAutorisation, requeteHttp.getRemoteAddr())));
    }

    /**
     * Validation d'un etat, aux deux niveaux du circuit (US-08, US-09, US-10, CT-14,
     * CT-15, CT-19).
     *
     * <p><b>Ouvert aux deux valideurs</b>, comme le veut le contrat d'API — « DA ou
     * DR selon le niveau ». Le role du directeur reseau est ajoute au sous-sprint 4.4
     * en meme temps que la transition {@code EN_ATTENTE_DR -> CLOTURE} qui le sert :
     * l'ouvrir plus tot l'aurait place devant un refus de statut incomprehensible.
     *
     * <p><b>C'est le statut du dossier qui designe le niveau traite</b>, pas le role
     * de l'appelant ; le role est ensuite verifie contre ce niveau. Le raisonnement
     * est dans {@code NiveauValidation}. Consequence visible ici : le
     * {@code @PreAuthorize} ne peut pas trancher seul, il ne fait qu'ecarter les
     * roles etrangers au circuit de validation.
     *
     * <p>Le role n'est donc que le premier filtre. La portee d'acces est verifiee en
     * plus, sur l'unite <i>du processus</i>, aupres du service Identite, puis la
     * separation des taches (RG-12) sur les etapes deja realisees.
     *
     * <p><b>Aucun corps de requete.</b> Le montant vient du processus, le seuil de
     * {@code parametre_systeme} : rien n'est laisse au choix de l'appelant, et
     * surtout pas la valeur qui decide du niveau d'approbation requis.
     *
     * <p>{@code 200} et non {@code 201} : la validation ne cree pas la ressource
     * adressee, elle en change l'etat. Refus possibles : {@code 422
     * TRANSITION_INTERDITE} si le dossier n'attend aucune validation ; {@code 403
     * ACCES_REFUSE} si le role ne tient pas le niveau attendu ; {@code 403
     * SEPARATION_TACHES} si l'appelant a deja agi sur cette version du dossier ;
     * {@code 403 UTILISATEUR_NON_HABILITE} hors portee ; {@code 500
     * SEUIL_INDISPONIBLE} si le seuil RG-08 n'est pas lisible au premier niveau ;
     * {@code 503} si le service Identite ne repond pas.
     */
    @PostMapping("/{id}/validation")
    @PreAuthorize("hasAnyRole('CHEF_UNITE_DA', 'DIRECTEUR_RESEAU_DR')")
    public ResponseEntity<ValidationResponse> valider(
            @PathVariable Long id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation,
            HttpServletRequest requeteHttp) {

        return ResponseEntity.ok(ValidationResponse.depuis(
                validationService.valider(id, enteteAutorisation, requeteHttp.getRemoteAddr())));
    }

    /**
     * Retour motive de l'etat a l'agent d'unite (US-10, US-11, CT-16, CT-20, CT-23).
     *
     * <p><b>Ouvert aux deux valideurs</b>, comme le veut le contrat d'API. Comme pour
     * la validation, c'est le <b>statut du dossier</b> qui designe le niveau qui
     * retourne, et le role de l'appelant est verifie contre lui : retourner et valider
     * sont les deux issues d'un meme geste, offertes au meme acteur au meme moment.
     *
     * <p><b>RG-11 : le retour ramene toujours a l'agent</b>, jamais au niveau
     * precedent. Un retour du directeur reseau ne repasse pas par le chef d'unite : le
     * statut atteint est {@code RETOURNE} dans les deux cas, et la reponse le montre a
     * cote du niveau d'origine pour que ce soit lisible sans documentation.
     *
     * <p><b>RG-10 : le motif est obligatoire</b>, et une suite d'espaces n'en est pas
     * un — {@code @NotBlank} sur {@link RetourRequest} refuse les deux en {@code 400
     * REQUETE_INVALIDE}, et la regle est redite en {@code 422 MOTIF_OBLIGATOIRE} par le
     * service et par la machine a etats.
     *
     * <p>{@code 200} et non {@code 201} : le retour ne cree pas la ressource adressee,
     * il en change l'etat. Refus possibles : {@code 400 REQUETE_INVALIDE} motif vide ;
     * {@code 422 MOTIF_OBLIGATOIRE} ; {@code 422 TRANSITION_INTERDITE} si le dossier
     * n'est pas en attente de decision ; {@code 403 ACCES_REFUSE} si le role ne tient
     * pas le niveau attendu ; {@code 403 UTILISATEUR_NON_HABILITE} hors portee ;
     * {@code 503} si le service Identite ne repond pas.
     *
     * <p><b>Aucun controle de separation des taches ici</b> : RG-12 interdit de
     * <i>valider</i> un dossier qu'on a soutenu, pas de le refuser. Voir
     * {@code RetourService}.
     */
    @PostMapping("/{id}/retour")
    @PreAuthorize("hasAnyRole('CHEF_UNITE_DA', 'DIRECTEUR_RESEAU_DR')")
    public ResponseEntity<RetourResponse> retourner(
            @PathVariable Long id,
            @Valid @RequestBody RetourRequest requete,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation,
            HttpServletRequest requeteHttp) {

        return ResponseEntity.ok(RetourResponse.depuis(retourService.retourner(
                id, requete.motif(), enteteAutorisation, requeteHttp.getRemoteAddr())));
    }

    /**
     * Inscrit sur l'etat la suite que la comptabilite lui a donnee (Sprint 5.2, contrat
     * d'API section 7.2, US-12, US-15). <b>Endpoint interne, hors contrat passerelle.</b>
     *
     * <h2>Ni jeton, ni role — et pourquoi ce n'est pas un relachement</h2>
     *
     * <p>Aucun {@code @PreAuthorize} et aucun en-tete {@code Authorization} : cet appel nait
     * d'un <b>message Kafka</b>, ou aucun utilisateur n'existe et ou aucun jeton n'est donc
     * relayable — la doctrine du Sprint 1.3 suppose un utilisateur final. Le realm ne porte
     * par ailleurs aucun compte de service.
     *
     * <p>La route n'est pas ouverte pour autant : elle a sa propre chaine de securite,
     * gardee par un secret partage ({@code FiltreCleInterne}), dispositif provisoire a
     * remplacer par un compte de service le jour ou la DSI en ouvre un.
     *
     * <h2>{@code PUT} et non {@code POST}</h2>
     *
     * <p>L'operation est <b>idempotente</b> : recevoir deux fois le meme accuse produit
     * exactement le meme etat qu'une seule reception, sans erreur ni seconde ecriture
     * d'audit. C'est la definition de {@code PUT}, et le verbe le dit avant tout
     * commentaire.
     *
     * <p>{@code 200} dans les deux cas d'acceptation ; le champ {@code resultat} distingue
     * {@code APPLIQUE} de {@code DEJA_APPLIQUE}, ce que l'appelant doit savoir pour ne
     * publier une trace d'audit que dans le premier cas.
     *
     * <p>Refus possibles : {@code 401 CLE_INTERNE_INVALIDE} ;
     * {@code 404 PROCESSUS_INTROUVABLE} ; {@code 422 PROCESSUS_NON_TRANSMIS} si la
     * comptabilite accuse un etat qui ne lui a jamais ete envoye ;
     * {@code 409 ACCUSE_CONTRADICTOIRE} si l'accuse contredit un statut deja recu.
     */
    @PutMapping("/{id}/integration")
    public ResponseEntity<IntegrationComptableResponse> appliquerAccuseComptable(
            @PathVariable Long id,
            @Valid @RequestBody IntegrationComptableRequest requete,
            HttpServletRequest requeteHttp) {

        return ResponseEntity.ok(IntegrationComptableResponse.depuis(
                integrationComptableService.appliquerAccuse(
                        id,
                        requete.statutIntegration(),
                        requete.referenceComptable(),
                        requete.dateTraitement(),
                        requete.motif(),
                        requeteHttp.getRemoteAddr())));
    }

    /**
     * Pose, confirme ou leve le verrou d'unicite de transmission (RG-13, Sprint 5.3).
     * <b>Endpoint interne, hors contrat passerelle.</b>
     *
     * <h2>Pourquoi cet endpoint existe</h2>
     *
     * <p>RG-13 exige qu'un etat ne parte qu'une fois vers la comptabilite. Le service qui
     * publie — Transmission — <b>n'a pas de base</b> ; la seule source de verite est
     * {@code processus_mensuel}, ici. Il doit donc pouvoir revendiquer le droit de publier
     * <b>pendant</b> sa propre requete, et de facon atomique : un verrou pose en amont par
     * le service Workflow serait entierement contourne par un appel direct a
     * {@code POST /transmission/processus/{id}} — l'un des chemins de double transmission
     * que ce sous-sprint doit fermer (CT-22).
     *
     * <h2>Trois etapes, un seul endpoint</h2>
     *
     * <pre>
     *   RESERVATION   avant de publier : rend RESERVEE, ou DEJA_TRANSMISE et rien ne part
     *   CONFIRMATION  apres l'accuse du broker : ouvre l'attente de l'accuse comptable
     *   LIBERATION    echec PROUVE sans envoi : le verrou est leve, une reprise est possible
     * </pre>
     *
     * <p><b>{@code 200} y compris sur {@code DEJA_TRANSMISE}</b> : une seconde demande
     * n'est pas forcement une anomalie — un rejeu legitime existe —, et une erreur
     * technique ferait croire a une panne. Le champ {@code resultat} porte la distinction,
     * comme au Sprint 5.2.
     *
     * <h2>Le jeton est relaye, contrairement a l'endpoint d'integration</h2>
     *
     * <p>Cet appel-ci nait d'une <b>requete HTTP d'un valideur</b> qui vient de cloturer :
     * un utilisateur final existe, son jeton est encore valide, la doctrine du Sprint 1.3
     * s'applique donc pleinement. Le secret partage du Sprint 5.2 ne s'imposait que faute
     * d'utilisateur derriere un message Kafka.
     *
     * <p>La portee d'acces n'est pas revalidee ici : elle a deja ete etablie par la
     * validation qui a produit la cloture, puis par {@code GET /processus/{id}} que le
     * service Transmission interroge sur le meme jeton. Une troisieme interrogation du
     * service Identite ajouterait cinq secondes au pire cas d'un fil HTTP qui attend.
     *
     * <p>Refus possibles : {@code 403 ACCES_REFUSE} role hors circuit ;
     * {@code 404 PROCESSUS_INTROUVABLE} ; {@code 422 ETAT_NON_CLOTURE}.
     */
    @PutMapping("/{id}/transmission")
    @PreAuthorize("hasAnyRole('CHEF_UNITE_DA', 'DIRECTEUR_RESEAU_DR')")
    public ResponseEntity<VerrouTransmissionResponse> tenirVerrouTransmission(
            @PathVariable Long id,
            @Valid @RequestBody VerrouTransmissionRequest requete,
            HttpServletRequest requeteHttp) {

        String adresseIp = requeteHttp.getRemoteAddr();

        ResultatVerrouTransmission resultat = switch (requete.etape()) {
            case RESERVATION -> verrouTransmissionService.reserver(id, adresseIp);
            case CONFIRMATION -> verrouTransmissionService.confirmer(id, requete.topic(),
                    requete.partition(), requete.offset(), requete.nombreLignes(),
                    requete.montantTotal(), adresseIp);
            case LIBERATION -> verrouTransmissionService.liberer(id, requete.motif(), adresseIp);
        };

        return ResponseEntity.ok(VerrouTransmissionResponse.de(id, resultat));
    }

    /**
     * Le bloc d'integration comptable d'un etat (Sprint 5.3). <b>Endpoint interne, hors
     * contrat passerelle.</b>
     *
     * <p>Il sert la consultation {@code GET /transmission/processus/{id}} du contrat d'API
     * section 7, ouverte aux <b>roles ARH et circuit</b>. Le service Transmission n'ayant
     * pas de base, il vient lire ici la donnee qui vit sur {@code processus_mensuel}.
     *
     * <p><b>Un endpoint etroit plutot qu'un role de plus sur {@code GET /processus/{id}}.</b>
     * Ce dernier rend le dossier complet — montant, motif de retour, type, periode — et
     * l'ARH a une portee nationale (Sprint 1.1) : lui ouvrir cette porte lui donnerait la
     * lecture integrale de tous les dossiers de toutes les unites pour un besoin de quatre
     * champs. Le module a deja tranche ainsi au Sprint 1.3 avec
     * {@code GET /identite/habilitation}, et le suivi complet relevera du service Reporting
     * (Sprint 6), lui aussi ouvert a l'ARH — deux chemins vers le meme dossier finiraient
     * par diverger.
     *
     * <p>La portee d'acces reste verifiee unite par unite aupres du service Identite : le
     * role n'est que le premier filtre.
     */
    @GetMapping("/{id}/integration")
    @PreAuthorize("hasAnyRole('ARH', 'AGENT_UNITE', 'CHEF_UNITE_DA', 'DIRECTEUR_RESEAU_DR')")
    public ResponseEntity<IntegrationProcessusResponse> consulterIntegration(
            @PathVariable Long id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation) {

        return ResponseEntity.ok(IntegrationProcessusResponse.depuis(
                processusService.consulterIntegration(id, enteteAutorisation)));
    }

    /**
     * Recherche d'etats mensuels pour le suivi (Sprint 6.1, US-15, CT-30).
     * <b>Endpoint interne, hors contrat passerelle.</b>
     *
     * <pre>
     *   GET /processus/recherche?mois=8&amp;annee=2026&amp;codeUnite=00002&amp;statut=CLOTURE&amp;limite=5000
     * </pre>
     *
     * <h2>Pourquoi cet endpoint existe</h2>
     *
     * <p>Le service Reporting n'a <b>pas de base</b> : la periode, l'unite, le montant
     * et les deux statuts d'un etat vivent sur {@code processus_mensuel}, ici. Jusqu'a
     * ce sous-sprint, ce service n'exposait que le detail d'<i>un</i> processus ; une
     * recherche aurait donc exige un appel par dossier, ce que le guide 6.1 proscrit
     * explicitement.
     *
     * <h2>Aucun parametre de portee, et c'est le point</h2>
     *
     * <p>La portee d'acces est resolue <b>depuis le jeton</b> par {@code PorteeService},
     * jamais recue en parametre. Un appel direct forge sur le port 8084 ne peut donc pas
     * s'attribuer des unites : il n'existe aucun champ ou les declarer. C'est la doctrine
     * du Sprint 3.4 — « un parametre fourni par l'appelant ne se croit pas sur parole » —
     * poussee un cran plus loin.
     *
     * <p>Une unite <b>explicitement demandee</b> hors portee est refusee en
     * {@code 403 UTILISATEUR_NON_HABILITE}, jamais rendue comme une liste vide : la liste
     * vide affirmerait que cette unite n'a ouvert aucun etat, ce qui serait a la fois une
     * information non due et, le plus souvent, fausse. En l'absence de filtre d'unite, la
     * portee restreint sans refuser — c'est le comportement attendu.
     *
     * <h2>{@code limite} borne le transport, pas la verite</h2>
     *
     * <p>Le compte exact est <b>toujours</b> rendu. Au-dela de {@code limite}, seul le
     * contenu est omis, et {@code tronque} le dit. Le consommateur peut donc annoncer
     * « 6 214 etats, affinez votre recherche » au lieu d'une page vide indiscernable
     * d'une absence de dossiers.
     *
     * <p>Refus possibles : {@code 403 ACCES_REFUSE} role hors circuit ;
     * {@code 403 UTILISATEUR_NON_HABILITE} unite demandee hors portee ;
     * {@code 503 SERVICE_IDENTITE_INDISPONIBLE} si la portee n'a pas pu etre resolue —
     * jamais une portee devinee.
     */
    @GetMapping("/recherche")
    @PreAuthorize("hasAnyRole('ARH', 'AGENT_UNITE', 'CHEF_UNITE_DA', 'DIRECTEUR_RESEAU_DR')")
    public ResponseEntity<RechercheProcessusResponse> rechercher(
            @RequestParam(required = false) LocalDate dateDebut,
            @RequestParam(required = false) LocalDate dateFin,
            @RequestParam(required = false) String codeUnite,
            @RequestParam(required = false) StatutEnum statut,
            @RequestParam(defaultValue = "5000") int limite,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation) {

        return ResponseEntity.ok(RechercheProcessusResponse.depuis(
                rechercheProcessusService.rechercher(
                        dateDebut, dateFin, codeUnite, statut, limite, enteteAutorisation)));
    }

    /**
     * Historique complet des validations et retours d'un dossier (Sprint 6.1, US-15,
     * CT-31). <b>Endpoint interne, hors contrat passerelle.</b>
     *
     * <p>Il sert {@code GET /reporting/processus/{id}/historique} du contrat d'API
     * section 6. Le service Reporting n'ayant pas de base, il vient lire ici les etapes
     * qui vivent sur {@code etape_workflow}.
     *
     * <h2>Tous les passages, pas le dernier</h2>
     *
     * <p>Un dossier retourne puis resoumis repasse par les memes niveaux, et le rang
     * d'etape est calcule {@code dernier + 1} depuis le Sprint 4.4 precisement pour que
     * ces passages restent distincts. Les etapes sont rendues <b>toutes</b>, dans l'ordre
     * du rang. N'afficher que le dernier passage a chaque niveau cacherait le refus et sa
     * correction, c'est-a-dire ce que le controle interne vient chercher.
     *
     * <p>La portee d'acces est verifiee sur l'unite du dossier aupres du service Identite,
     * comme pour {@code GET /processus/{id}}. Refus possibles :
     * {@code 404 PROCESSUS_INTROUVABLE} ; {@code 403 UTILISATEUR_NON_HABILITE} hors
     * portee ; {@code 503} si le service Identite ne repond pas.
     */
    @GetMapping("/{id}/historique")
    @PreAuthorize("hasAnyRole('ARH', 'AGENT_UNITE', 'CHEF_UNITE_DA', 'DIRECTEUR_RESEAU_DR')")
    public ResponseEntity<HistoriqueProcessusResponse> consulterHistorique(
            @PathVariable Long id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String enteteAutorisation) {

        return ResponseEntity.ok(HistoriqueProcessusResponse.depuis(
                rechercheProcessusService.consulterHistorique(id, enteteAutorisation)));
    }

}
