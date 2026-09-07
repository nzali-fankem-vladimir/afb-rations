package cm.afrilandfirstbank.rations.workflow.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cm.afrilandfirstbank.rations.commun.audit.DeltaAudit;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.workflow.api.dto.DeclenchementProcessusRequest;
import cm.afrilandfirstbank.rations.workflow.application.ResultatHabilitationUnite.AgentHabilite;
import cm.afrilandfirstbank.rations.workflow.domaine.EtapeWorkflow;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEtapeEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.TransitionProcessus;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.DelaiRegularisationDepasseException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.DelaiRegularisationIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.EtatNonClotureException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.FonctionnaliteNonOuverteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.MotifOuvertureRequisException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.OrigineRequiseException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.PeriodeNonConcordanteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.UniteNonConcordanteException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.EtapeWorkflowRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ProcessusMensuelRepository;

/**
 * Ouverture d'un etat complementaire sur une periode close (Sprint 6bis.1, US-17,
 * CT-34, CT-37).
 *
 * <h2>Ce que ce service ne fait pas — et ne doit jamais faire</h2>
 *
 * <p><b>Il ne touche a aucun champ de l'etat d'origine.</b> Ni son statut, ni ses
 * signatures, ni son drapeau de transmission, ni son montant. L'origine reste figee
 * avec ses visas, ce qui preserve l'integrite du controle interne : c'est tout
 * l'interet de la solution retenue (CLAUDE.md sections 7 et 15). Le seul lien est un
 * identifiant recopie sur le <i>nouvel</i> etat.
 *
 * <p>Il ne cree <b>aucune entite Reclamation</b> — le signalement du beneficiaire est
 * exterieur au systeme, l'agent l'enregistre dans le motif d'ouverture — et ne
 * construit <b>aucune liste de beneficiaires attendus</b> : ce processus n'a pas
 * d'enrolement, le module ne peut pas savoir qui aurait du etre paye. Les deux ont ete
 * explicitement ecartes en conception.
 *
 * <p>Il n'applique pas RG-15 : le controle d'unicite inter-etats est le sous-sprint
 * 6bis.2. C'est precisement pourquoi le drapeau ne doit pas etre ouvert en base reelle
 * avant lui.
 *
 * <h2>L'ordre des controles, et pourquoi le drapeau vient en premier</h2>
 *
 * <pre>
 *   1. RATTRAPAGE_ACTIF ouvert ?                     -&gt; 422 FONCTIONNALITE_NON_OUVERTE
 *   2. identifiant d'origine fourni ?                -&gt; 422 ORIGINE_REQUISE
 *   3. motif non vide ?                              -&gt; 422 MOTIF_OBLIGATOIRE
 *   4. l'origine existe ?                            -&gt; 404 PROCESSUS_INTROUVABLE
 *   5. portee d'acces sur l'unite DE L'ORIGINE       -&gt; 403 / 503
 *   6. l'origine est close ?                         -&gt; 422 ETAT_NON_CLOTURE
 *   7. close depuis moins de N jours ?               -&gt; 422 DELAI_REGULARISATION_DEPASSE
 *                                                       500 DELAI_REGULARISATION_INDISPONIBLE
 *   8. unite declaree = unite de l'origine ?         -&gt; 403 UNITE_NON_CONCORDANTE  (trace)
 *   9. periode declaree = periode de l'origine ?     -&gt; 422 PERIODE_NON_CONCORDANTE
 *  10. creation par la machine a etats, puis audit
 * </pre>
 *
 * <p><b>Le drapeau se verifie avant tout le reste</b>, y compris avant de savoir si
 * l'origine existe. Un refus d'habilitation ou un {@code 404} affiche avant le refus de
 * fonctionnalite laisserait croire que la regularisation est ouverte et que seul un
 * detail bloque la demande : l'agent chercherait le bon numero d'origine, puis
 * reclamerait une habilitation, pour finalement se heurter au meme mur. Le test 2 du
 * guide verrouille ce point sur une origine <i>inexistante</i>.
 *
 * <p>Les controles 2 et 3 ne coutent aucune entree-sortie et se placent donc avant la
 * lecture en base : ce sont des fautes de la <i>requete</i>, pas du dossier — meme
 * parti qu'au Sprint 4.4, ou {@code RetourService} refuse un motif vide avant meme
 * d'aller chercher le processus.
 *
 * <p>Le controle 4 precede le 5 pour la raison deja retenue a la consultation (Sprint
 * 4.1) : on ne demande pas au service Identite « avez-vous droit sur l'unite de rien ».
 * C'est aussi ce qui permet au controle 5 de porter sur l'unite <b>reelle</b> de
 * l'origine plutot que sur celle que l'appelant declare.
 */
@Service
public class OuvertureComplementaireService {

    private static final String ENTITE_CIBLE = "processus_mensuel";
    private static final String ACTION_OUVERTURE = "OUVERTURE_COMPLEMENTAIRE";

    private static final Logger journal =
            LoggerFactory.getLogger(OuvertureComplementaireService.class);

    private final ProcessusMensuelRepository processusMensuelRepository;
    private final EtapeWorkflowRepository etapeWorkflowRepository;
    private final HabilitationService habilitationService;
    private final FonctionnaliteService fonctionnaliteService;
    private final PublicateurAudit publicateurAudit;

    public OuvertureComplementaireService(ProcessusMensuelRepository processusMensuelRepository,
            EtapeWorkflowRepository etapeWorkflowRepository,
            HabilitationService habilitationService,
            FonctionnaliteService fonctionnaliteService,
            PublicateurAudit publicateurAudit) {
        this.processusMensuelRepository = processusMensuelRepository;
        this.etapeWorkflowRepository = etapeWorkflowRepository;
        this.habilitationService = habilitationService;
        this.fonctionnaliteService = fonctionnaliteService;
        this.publicateurAudit = publicateurAudit;
    }

    /**
     * Ouvre l'etat complementaire d'une regularisation.
     *
     * <p><b>Transactionnelle, appel reseau compris</b>, comme
     * {@link ProcessusService#declencher} : l'acquisition differee de connexion de
     * Spring Boot fait qu'aucune connexion a la base n'est detenue pendant l'appel au
     * service Identite (doctrine Sprint 2.2). La lecture de l'origine qui le precede est
     * une requete par identifiant, deja terminee.
     *
     * @param requete la demande, telle que {@code POST /processus} l'a recue
     * @param enteteAutorisation en-tete {@code Authorization} de l'agent, relaye tel quel
     * @param adresseIp origine de la requete, pour la trace d'audit
     * @return le processus complementaire cree, au statut {@code EN_COURS_SAISIE}
     */
    @Transactional
    public ProcessusMensuel ouvrir(DeclenchementProcessusRequest requete,
            String enteteAutorisation, String adresseIp) {

        // 1. Le drapeau, avant tout le reste.
        exigerFonctionnaliteOuverte();

        // 2 et 3. Fautes de la requete : aucune entree-sortie n'est necessaire.
        Long idOrigine = origineDeclareeOuRefus(requete);
        String motif = motifOuRefus(requete);

        // 4. L'origine existe ?
        ProcessusMensuel origine = processusMensuelRepository.findById(idOrigine)
                .orElseThrow(() -> new ProcessusIntrouvableException(idOrigine));

        // 5. Portee d'acces, sur l'unite REELLE de l'origine.
        AgentHabilite agent = habilitationService.exigerHabilitationSurUnite(
                origine.getCodeUnite(), enteteAutorisation);

        // 6. L'origine est close ? Redit par la machine a etats a la creation.
        exigerOrigineClose(origine);

        // 7. Close depuis moins longtemps que le delai de regularisation.
        LocalDateTime dateCloture = exigerDansLeDelai(origine);

        // 8 et 9. Ce que l'appelant declare est confronte a l'origine, qui fait autorite.
        exigerUniteConcordante(requete, origine);
        exigerPeriodeConcordante(requete, origine);

        // 10. Creation : la periode et l'unite sont recopiees de l'origine, jamais de la
        //     requete, qui n'a servi qu'a etre verifiee.
        ProcessusMensuel complementaire = processusMensuelRepository.save(
                TransitionProcessus.ouvrirComplementaire(origine, motif));

        publierOuverture(complementaire, origine, dateCloture, agent, adresseIp);

        return complementaire;
    }

    // --- Regles ----------------------------------------------------------------

    /**
     * Controle 1 : la fonctionnalite est-elle ouverte ?
     *
     * <p>Le refus porte un code <b>distinct</b> d'un refus d'habilitation. Confondre les
     * deux enverrait l'agent reclamer a sa hierarchie un droit que personne ne peut lui
     * accorder aujourd'hui : ce n'est pas une question de droits, c'est une
     * fonctionnalite que le metier n'a pas encore confirmee (point M-01).
     */
    private void exigerFonctionnaliteOuverte() {
        if (fonctionnaliteService.rattrapageActif()) {
            return;
        }
        throw new FonctionnaliteNonOuverteException(
                "L'ouverture d'un etat complementaire n'est pas encore ouverte. "
                        + "Rapprochez-vous de la DRH.");
    }

    private Long origineDeclareeOuRefus(DeclenchementProcessusRequest requete) {
        if (requete.idProcessusOrigine() == null) {
            throw new OrigineRequiseException(
                    "Un etat complementaire regularise un etat existant : l'identifiant de l'etat "
                            + "d'origine (idProcessusOrigine) est obligatoire. Sans lui, cet etat "
                            + "serait un second etat mensuel autonome sur une periode close, ce que "
                            + "le module n'autorise pas.");
        }
        return requete.idProcessusOrigine();
    }

    /**
     * Controle 3 : le motif, exige non vide.
     *
     * <p>{@code isBlank} et non {@code isEmpty} : {@code "   "} est un champ present et un
     * motif absent — la formulation exacte du Sprint 4.4 pour le motif de retour. La regle
     * est redite par la machine a etats a la creation, comme RG-10 l'est a trois etages.
     */
    private String motifOuRefus(DeclenchementProcessusRequest requete) {
        String motif = requete.motifOuverture();
        if (motif == null || motif.isBlank()) {
            throw new MotifOuvertureRequisException(
                    "L'ouverture d'un etat complementaire exige un motif (motifOuverture) : il est "
                            + "la seule trace de ce qui a declenche la regularisation, ce module "
                            + "n'enregistrant pas les signalements des beneficiaires. Une suite "
                            + "d'espaces n'est pas un motif.");
        }
        return motif.strip();
    }

    /**
     * Controle 6 : l'origine est close.
     *
     * <p>Le refus est prononce ici pour le <b>message</b>, qui nomme la periode, l'unite,
     * le statut reel et l'action attendue ; la machine a etats le redit a la creation, ou
     * il devient une garantie du compilateur plutot qu'une politesse.
     */
    private void exigerOrigineClose(ProcessusMensuel origine) {
        if (origine.getStatut() == StatutEnum.CLOTURE) {
            return;
        }
        throw new EtatNonClotureException(
                "L'etat " + libellePeriode(origine) + " de l'unite " + origine.getCodeUnite()
                        + " (processus n° " + origine.getId() + ") porte le statut "
                        + origine.getStatut() + " : il n'est pas cloture, il n'y a donc rien a "
                        + "regulariser. Tant qu'un dossier est dans le circuit, une correction "
                        + "passe par un retour a l'agent (RG-11), pas par un etat complementaire.");
    }

    /**
     * Controle 7 : l'origine est close depuis moins de {@code DELAI_REGULARISATION_JOURS}.
     *
     * <h2>Ou se lit la date de cloture</h2>
     *
     * <p>{@code processus_mensuel} ne porte <b>aucune colonne {@code date_cloture}</b> :
     * elle n'existe ni au dictionnaire ni en base, et le Sprint 4.1 a tranche de ne pas
     * l'ajouter. L'instant de la cloture est porte par {@code etape_workflow.date_creation}
     * de l'etape de validation qui a clos le dossier (decision Sprint 4.3).
     *
     * <p>On prend donc la <b>derniere</b> etape {@code VALIDEE}, par rang decroissant. Le
     * rang est calcule {@code dernier + 1} depuis le Sprint 4.4, y compris pour les
     * resoumissions : un dossier valide par le chef d'unite, monte au directeur reseau,
     * retourne, corrige, resoumis puis clos porte plusieurs etapes {@code VALIDEE}, et
     * c'est celle du <b>dernier</b> cycle qui l'a reellement close. Prendre la premiere
     * ferait courir le delai depuis un visa annule par un retour, et refuserait des
     * regularisations legitimes.
     *
     * <p>Le raisonnement tient parce que {@link StatutEnum#CLOTURE} est <b>terminal</b> :
     * aucune etape ne peut suivre celle qui a clos le dossier.
     *
     * <h2>Delai franc, en jours calendaires</h2>
     *
     * <p>La comparaison porte sur les <b>dates</b>, pas sur les instants : une origine
     * close le 1er et un delai de 90 jours restent regularisables jusqu'a la fin du 90e
     * jour, quelle que soit l'heure. Comparer des instants ferait dependre le verdict de
     * l'heure a laquelle un chef d'unite a signe, ce qu'aucun utilisateur ne pourrait
     * anticiper.
     *
     * @return la date de cloture retenue, reprise dans la trace d'audit
     */
    private LocalDateTime exigerDansLeDelai(ProcessusMensuel origine) {
        LocalDateTime dateCloture = dateClotureOuRefus(origine);
        long delaiJours = fonctionnaliteService.delaiRegularisationJours();

        long joursEcoules = ChronoUnit.DAYS.between(dateCloture.toLocalDate(), LocalDate.now());

        if (joursEcoules > delaiJours) {
            throw new DelaiRegularisationDepasseException(
                    "L'etat " + libellePeriode(origine) + " de l'unite " + origine.getCodeUnite()
                            + " (processus n° " + origine.getId() + ") a ete cloture le "
                            + dateCloture.toLocalDate() + ", il y a " + joursEcoules + " jours, "
                            + "alors que le delai de regularisation est de " + delaiJours
                            + " jours. Cette periode n'est plus regularisable par le module ; "
                            + "rapprochez-vous de la DRH.");
        }

        return dateCloture;
    }

    /**
     * La date de cloture de l'origine, ou le refus si elle est introuvable.
     *
     * <p>Un etat {@link StatutEnum#CLOTURE} porte necessairement une etape
     * {@code VALIDEE} : la cloture ne s'obtient que par une validation, qui ecrit
     * toujours l'etape et le processus dans la meme transaction (Sprint 4.3). Son absence
     * n'est donc pas un cas metier mais une incoherence de donnees.
     *
     * <p><b>Refus plutot que repli.</b> Sans date de cloture, le delai n'est pas
     * evaluable : le supposer respecte ouvrirait une regularisation sur une periode
     * peut-etre close depuis des annees. {@code 500}, comme un seuil illisible : l'agent
     * n'a rien a corriger dans sa demande.
     */
    private LocalDateTime dateClotureOuRefus(ProcessusMensuel origine) {
        return etapeWorkflowRepository
                .findFirstByIdProcessusAndStatutEtapeOrderByOrdreEtapeDesc(
                        origine.getId(), StatutEtapeEnum.VALIDEE)
                .map(EtapeWorkflow::getDateCreation)
                .orElseThrow(() -> {
                    journal.error("DELAI REGULARISATION INDISPONIBLE : le processus {} porte le "
                            + "statut {} mais aucune etape VALIDEE ; sa date de cloture est "
                            + "introuvable.", origine.getId(), origine.getStatut());
                    return new DelaiRegularisationIndisponibleException(
                            "La date de cloture de l'etat d'origine " + origine.getId()
                                    + " est introuvable : ce dossier est cloture mais ne porte "
                                    + "aucune etape de validation. Le delai de regularisation ne "
                                    + "peut pas etre verifie, et l'ouverture est refusee plutot "
                                    + "qu'accordee sur une anciennete supposee. Signalez-le a "
                                    + "l'administrateur du module.");
                });
    }

    /**
     * Controle 8 : l'unite declaree est celle de l'origine.
     *
     * <p>Le code unite de l'origine fait <b>autorite</b> ; celui de la requete n'est
     * qu'une declaration a verifier — doctrine du Sprint 3.4, ou le service Saisie oppose
     * deja le meme refus sous le meme code. La portee d'acces a certes deja ete verifiee
     * sur l'unite reelle de l'origine ; ce controle-ci ferme le cas inverse, celui d'une
     * demande qui vise un dossier tout en en declarant un autre.
     *
     * <p>{@code 403}, et <b>trace en audit</b> par {@code GestionnaireErreursApi} : un
     * desaccord d'unite peut venir d'un defaut de client comme d'une tentative de
     * debordement de perimetre, et rien ne permet de les distinguer au moment du refus
     * (CT-04, doctrine du refus conservateur du Sprint 1.3).
     */
    private void exigerUniteConcordante(DeclenchementProcessusRequest requete,
            ProcessusMensuel origine) {

        if (origine.getCodeUnite().equals(requete.codeUnite())) {
            return;
        }
        throw new UniteNonConcordanteException(
                "La demande declare l'unite " + requete.codeUnite() + ", alors que l'etat "
                        + "d'origine " + origine.getId() + " appartient a l'unite "
                        + origine.getCodeUnite() + ". Un etat complementaire regularise le dossier "
                        + "de l'unite qui a supporte la charge, et d'aucune autre.");
    }

    /**
     * Controle 9 : la periode declaree est celle de l'origine.
     *
     * <p>{@code 422} et non {@code 403} : contrairement a l'unite, la periode n'ouvre
     * aucun droit — se tromper de mois est une maladresse de saisie, pas un
     * franchissement de perimetre. La traiter en refus d'acces polluerait le journal des
     * refus avec des fautes de frappe, et enverrait l'agent reclamer une habilitation
     * dont l'absence n'est pas en cause.
     */
    private void exigerPeriodeConcordante(DeclenchementProcessusRequest requete,
            ProcessusMensuel origine) {

        if (origine.getMoisPaiement().equals(requete.moisPaiement())
                && origine.getAnneePaiement().equals(requete.anneePaiement())) {
            return;
        }
        throw new PeriodeNonConcordanteException(
                "La demande porte sur la periode " + String.format("%02d/%d",
                        requete.moisPaiement(), requete.anneePaiement())
                        + ", alors que l'etat d'origine " + origine.getId() + " couvre la periode "
                        + libellePeriode(origine) + ". Un etat complementaire regularise la "
                        + "periode de son origine, jamais une autre.");
    }

    // --- Audit -----------------------------------------------------------------

    /**
     * Trace l'ouverture, avec le motif.
     *
     * <p><b>Une action distincte de {@code DECLENCHEMENT_PROCESSUS}</b>, et non le meme
     * evenement dont le delta porterait {@code type = COMPLEMENTAIRE}. Ouvrir une
     * regularisation sur une periode deja payee et deja transmise a la comptabilite n'est
     * pas le meme geste que d'ouvrir le mois courant : un controle interne doit pouvoir
     * les compter separement, sans avoir a filtrer sur le contenu d'un champ JSON.
     *
     * <p>Le delta porte l'origine et sa date de cloture : c'est ce qui permet, six mois
     * plus tard, de refaire le raisonnement du delai avec les valeurs du jour meme —
     * meme principe qu'au Sprint 4.3, ou l'audit de validation inscrit le seuil applique.
     *
     * <p>{@code idUtilisateur} reste nul : {@code GET /identite/habilitation} ne rend
     * qu'un {@code login}, et {@code processus_mensuel} ne porte aucune colonne d'auteur
     * qui imposerait un appel de plus a {@code GET /identite/moi} (decision Sprint 3.3).
     * Le login est repris en contexte du delta.
     */
    private void publierOuverture(ProcessusMensuel complementaire, ProcessusMensuel origine,
            LocalDateTime dateCloture, AgentHabilite agent, String adresseIp) {

        publicateurAudit.publier(EvenementAudit.de(
                null,
                ACTION_OUVERTURE,
                ENTITE_CIBLE,
                complementaire.getId(),
                adresseIp,
                DeltaAudit.nouveau()
                        .champ("statut", null, complementaire.getStatut())
                        .champ("typeProcessus", null, complementaire.getTypeProcessus())
                        .champ("idProcessusOrigine", null, complementaire.getIdProcessusOrigine())
                        .champ("motifOuverture", null, complementaire.getMotifOuverture())
                        .champ("moisPaiement", null, complementaire.getMoisPaiement())
                        .champ("anneePaiement", null, complementaire.getAnneePaiement())
                        .champ("codeUnite", null, complementaire.getCodeUnite())
                        .contexte("auteur", agent.login())
                        .contexte("role", agent.role())
                        .contexte("statutOrigine", origine.getStatut())
                        .contexte("dateClotureOrigine", dateCloture.toLocalDate())
                        .enJson()));
    }

    private String libellePeriode(ProcessusMensuel processus) {
        return String.format("%02d/%d", processus.getMoisPaiement(), processus.getAnneePaiement());
    }

}
