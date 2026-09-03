package cm.afrilandfirstbank.rations.workflow.application;

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
import cm.afrilandfirstbank.rations.workflow.domaine.TypeProcessusEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.FonctionnaliteNonOuverteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusExistantException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ServiceSaisieIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.EtapeWorkflowRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ProcessusMensuelRepository;

/**
 * Declenchement et consultation d'un processus mensuel (Sprint 4.1, US-03).
 *
 * <p>Ce service porte les deux premiers gestes du circuit : ouvrir l'etat du mois,
 * et le consulter. <b>Ni la soumission, ni la validation, ni le retour</b> — ce
 * sont les sous-sprints 4.2 a 4.4, et les melanger ici brouillerait la separation
 * que RG-07 et RG-12 exigent entre celui qui saisit et ceux qui valident.
 */
@Service
public class ProcessusService {

    private static final String ENTITE_CIBLE = "processus_mensuel";
    private static final String ACTION_DECLENCHEMENT = "DECLENCHEMENT_PROCESSUS";

    private final ProcessusMensuelRepository processusMensuelRepository;
    private final EtapeWorkflowRepository etapeWorkflowRepository;
    private final HabilitationService habilitationService;
    private final ConsolidationClient consolidationClient;
    private final PublicateurAudit publicateurAudit;

    public ProcessusService(ProcessusMensuelRepository processusMensuelRepository,
            EtapeWorkflowRepository etapeWorkflowRepository,
            HabilitationService habilitationService,
            ConsolidationClient consolidationClient,
            PublicateurAudit publicateurAudit) {
        this.processusMensuelRepository = processusMensuelRepository;
        this.etapeWorkflowRepository = etapeWorkflowRepository;
        this.habilitationService = habilitationService;
        this.consolidationClient = consolidationClient;
        this.publicateurAudit = publicateurAudit;
    }

    /**
     * Ouvre le processus NORMAL du mois pour une unite.
     *
     * <p><b>Ordre des operations</b> (document maitre section 7.3) :
     *
     * <ol>
     *   <li><b>Fonctionnalite ouverte ?</b> Un type COMPLEMENTAIRE est refuse
     *       avant tout le reste — inutile d'interroger le service Identite pour
     *       une demande qui ne peut aboutir, et
     *       {@code docs/dispositifs_provisoires.md} section 1.3 place ce controle
     *       en tete.</li>
     *   <li><b>Habilitation.</b> Le role {@code AGENT_UNITE} est deja exige par la
     *       securite ; il reste a savoir si <i>cet</i> agent a portee sur
     *       <i>cette</i> unite (RG-12). Appel reseau place avant toute requete :
     *       avec l'acquisition differee de connexion de Spring Boot, aucune
     *       connexion a la base n'est detenue pendant qu'il dure (doctrine
     *       Sprint 2.2).</li>
     *   <li><b>Unicite.</b> Un seul etat NORMAL par unite et periode.</li>
     *   <li><b>Modification d'etat</b> par la machine a etats.</li>
     *   <li><b>Audit</b>, apres coup, jamais avant.</li>
     * </ol>
     *
     * @param requete demande deja validee syntaxiquement par la couche api
     * @param enteteAutorisation en-tete {@code Authorization} de l'agent, relaye tel quel
     * @param adresseIp origine de la requete, pour la trace d'audit
     * @return le processus enregistre, au statut {@code EN_COURS_SAISIE}
     */
    @Transactional
    public ProcessusMensuel declencher(DeclenchementProcessusRequest requete,
            String enteteAutorisation, String adresseIp) {

        exigerTypeOuvert(requete.typeDemande());

        AgentHabilite agent =
                habilitationService.exigerHabilitationSurUnite(requete.codeUnite(), enteteAutorisation);

        exigerAucunProcessusNormalExistant(
                requete.codeUnite(), requete.moisPaiement(), requete.anneePaiement());

        ProcessusMensuel processus = TransitionProcessus.declencher(
                requete.moisPaiement(), requete.anneePaiement(), requete.codeUnite());
        ProcessusMensuel enregistre = processusMensuelRepository.save(processus);

        publierDeclenchement(enregistre, agent, adresseIp);

        return enregistre;
    }

    /**
     * Detail et statut d'un processus, portee d'acces verifiee.
     *
     * <p><b>Charger avant de verifier</b>, contrairement au declenchement : la
     * portee porte sur le code unite <i>du processus</i>, qu'il faut donc lire
     * d'abord. Un identifiant inexistant produit un {@code 404} avant toute
     * question d'habilitation — l'inverse obligerait a demander « avez-vous droit
     * sur l'unite de rien ».
     *
     * <p>Aucune trace d'audit : une lecture autorisee n'est pas une action
     * sensible ({@code docs/publication-audit.md} section 4). Le refus, lui, est
     * trace par {@code GestionnaireErreursApi}.
     *
     * <p><b>Volontairement non transactionnelle</b>, contrairement a
     * {@link #declencher}. Une seule lecture par identifiant, suivie d'un appel
     * reseau : ouvrir une transaction autour immobiliserait une connexion de la
     * reserve pendant tout l'appel au service Identite — le defaut que la
     * doctrine du Sprint 2.3 ecarte deja a la bascule des grilles, et que le
     * Sprint 3.4 a redit pour la consolidation. {@link ProcessusMensuel} ne porte
     * aucune association paresseuse : l'entite est complete des le
     * {@code findById}.
     */
    public DetailProcessus consulter(Long idProcessus, String enteteAutorisation) {
        ProcessusMensuel processus = processusMensuelRepository.findById(idProcessus)
                .orElseThrow(() -> new ProcessusIntrouvableException(idProcessus));

        habilitationService.exigerHabilitationSurUnite(processus.getCodeUnite(), enteteAutorisation);

        return new DetailProcessus(processus, motifDuRetourEnCours(processus));
    }

    /**
     * Le bloc d'integration comptable d'un processus, portee d'acces verifiee
     * (Sprint 5.3, endpoint interne {@code GET /processus/{id}/integration}).
     *
     * <p>Il sert la consultation {@code GET /transmission/processus/{id}} du contrat
     * d'API section 7, ouverte aux <b>roles ARH et circuit</b>. Le service Transmission
     * n'ayant pas de base, il vient lire ici la donnee qui vit sur
     * {@code processus_mensuel}.
     *
     * <p><b>Pourquoi une methode a part plutot que {@link #consulter}.</b> Cette
     * derniere rend le dossier complet et n'est ouverte qu'aux roles du circuit. Y
     * ajouter l'ARH, dont la portee est nationale (Sprint 1.1), lui donnerait la lecture
     * integrale de tous les dossiers de toutes les unites pour un besoin qui n'en demande
     * que quatre champs. Meme parti qu'au Sprint 1.3 pour
     * {@code GET /identite/habilitation} : un endpoint interne repond a une question
     * precise, il ne rend pas un objet complet « au cas ou ».
     *
     * <p>La portee reste verifiee unite par unite : le role n'est que le premier filtre.
     * Non transactionnelle, comme {@link #consulter} — une lecture suivie d'un appel
     * reseau.
     */
    public ProcessusMensuel consulterIntegration(Long idProcessus, String enteteAutorisation) {
        ProcessusMensuel processus = processusMensuelRepository.findById(idProcessus)
                .orElseThrow(() -> new ProcessusIntrouvableException(idProcessus));

        habilitationService.exigerHabilitationSurUnite(processus.getCodeUnite(), enteteAutorisation);

        return processus;
    }

    /**
     * Le motif du dernier retour, <b>tant que l'etat est effectivement retourne</b>
     * (US-11 : « le motif de retour est visible par l'agent »).
     *
     * <p>Nul des que l'agent a resoumis : le motif serait alors une correction deja
     * faite, affichee sur un dossier reparti dans le circuit — un valideur pourrait le
     * lire comme un reproche en cours. L'historique complet des retours releve de
     * {@code GET /reporting/processus/{id}/historique} (Sprint 6), qui est fait pour
     * cela.
     *
     * <p>Une seule lecture, sur un index par processus, et seulement dans le cas
     * {@code RETOURNE} : les autres statuts ne paient rien.
     */
    private String motifDuRetourEnCours(ProcessusMensuel processus) {
        if (processus.getStatut() != StatutEnum.RETOURNE) {
            return null;
        }
        return etapeWorkflowRepository
                .findFirstByIdProcessusAndStatutEtapeOrderByOrdreEtapeDesc(
                        processus.getId(), StatutEtapeEnum.RETOURNEE)
                .map(EtapeWorkflow::getMotifRetour)
                .orElse(null);
    }

    /**
     * Le detail rendu par {@code GET /processus/{id}} : le processus, et le motif du
     * retour en cours s'il y en a un.
     *
     * <p>Le motif ne vit pas sur {@code processus_mensuel} mais sur
     * {@code etape_workflow} (CLAUDE.md section 4) : il faut donc les rapprocher
     * quelque part, et ce quelque part est ici plutot que dans le controleur — la
     * regle « visible tant que l'etat est retourne » est une regle, pas une question
     * de presentation.
     */
    public record DetailProcessus(ProcessusMensuel processus, String motifRetour) {
    }

    /**
     * Etat mensuel consolide d'un processus : le detail que detient le service
     * Saisie, joint a ce que Workflow detient seul (statut, type, montant porte).
     *
     * <p><b>C'est l'endpoint public du contrat</b> ({@code GET /processus/{id}/etat},
     * CLAUDE.md section 11) ; celui du service Saisie est interne et n'est pas
     * routé par la passerelle (decision Sprint 3.4).
     *
     * <p><b>Le code unite transmis vient de {@code processus_mensuel}</b>, jamais
     * de la requete. Workflow le detient : c'est precisement pourquoi le parametre
     * est obligatoire cote Saisie — un processus sans aucune fiche n'a pas de code
     * unite a lire, et le controle de portee y disparaitrait au moment exact ou il
     * n'y a rien a lire. Saisie recoupe ensuite cette declaration contre le code
     * fige sur les fiches, qui fait autorite.
     *
     * <p><b>Portee verifiee deux fois, et ce n'est pas une redondance inutile.</b>
     * Ici, sur l'unite reelle du processus ; puis cote Saisie, sur l'unite
     * declaree. Le premier controle protege ce service, le second protege Saisie
     * d'un appelant defaillant — y compris de celui-ci.
     *
     * <p>Non transactionnelle, pour la meme raison que {@link #consulter} : deux
     * appels reseau s'enchainent, aucune connexion a la base ne doit etre tenue
     * pendant ce temps.
     *
     * @throws cm.afrilandfirstbank.rations.workflow.domaine.exception.ServiceSaisieIndisponibleException
     *         si Saisie est muet — refus conservateur, jamais un total suppose
     */
    public EtatProcessus consulterEtat(Long idProcessus, String enteteAutorisation) {
        ProcessusMensuel processus = consulter(idProcessus, enteteAutorisation).processus();

        ResultatConsolidation resultat = consolidationClient.consolider(
                processus.getId(), processus.getCodeUnite(), enteteAutorisation);

        return switch (resultat) {
            case ResultatConsolidation.EtatObtenu obtenu ->
                    new EtatProcessus(processus, obtenu.etat());

            case ResultatConsolidation.ServiceSaisieIndisponible panne ->
                    throw new ServiceSaisieIndisponibleException(
                            "Le service Saisie est momentanement indisponible : l'etat consolide du "
                                    + "processus " + idProcessus + " ne peut pas etre etabli ("
                                    + panne.motifTechnique() + "). Aucun montant n'est suppose ni "
                                    + "repris d'une lecture anterieure. Reessayez dans un instant.");
        };
    }

    /**
     * Le processus tel que Workflow le connait, et l'etat consolide tel que Saisie
     * le rend. Deux moities de RG-06, assemblees pour la reponse et jamais
     * fusionnees en base.
     */
    public record EtatProcessus(ProcessusMensuel processus, EtatConsolide consolidation) {
    }

    // --- Regles ----------------------------------------------------------------

    /**
     * Refuse un type de processus non ouvert.
     *
     * <p><b>Le refus est inconditionnel, il ne lit pas {@code RATTRAPAGE_ACTIF}.</b>
     * Le drapeau de {@code parametre_systeme} gouverne le Sprint 6bis, ou le code
     * de l'etat complementaire existera — reference a l'etat d'origine, delai de
     * regularisation, controle d'unicite inter-etats RG-15. Le lire ici
     * signifierait « si le drapeau passe a vrai, ceci fonctionne », ce qui est
     * faux au Sprint 4.1 : un basculement du parametre produirait des etats
     * complementaires sans aucun de leurs controles. Le drapeau sera consulte le
     * jour ou il aura quelque chose a ouvrir.
     */
    private void exigerTypeOuvert(TypeProcessusEnum typeDemande) {
        if (typeDemande != TypeProcessusEnum.NORMAL) {
            throw new FonctionnaliteNonOuverteException(
                    "L'ouverture d'un etat complementaire n'est pas encore ouverte. "
                            + "Rapprochez-vous de la DRH.");
        }
    }

    /**
     * RG : un seul etat NORMAL par unite et periode.
     *
     * <p>L'index partiel {@code ux_processus_normal_par_periode} garantit la regle
     * en base ; ce controle applicatif existe pour le <b>message</b>, qui nomme le
     * processus deja ouvert et son statut. L'index reste le filet en cas de course
     * entre deux demandes simultanees — traduit lui aussi en {@code 409} par
     * {@code GestionnaireErreursApi}.
     */
    private void exigerAucunProcessusNormalExistant(String codeUnite, Integer mois, Integer annee) {
        processusMensuelRepository
                .findByCodeUniteAndMoisPaiementAndAnneePaiementAndTypeProcessus(
                        codeUnite, mois, annee, TypeProcessusEnum.NORMAL)
                .ifPresent(existant -> {
                    throw new ProcessusExistantException(
                            "Un etat mensuel est deja ouvert pour l'unite " + codeUnite
                                    + " en " + mois + "/" + annee + " (processus n° "
                                    + existant.getId() + ", statut " + existant.getStatut()
                                    + "). Rejoignez ce dossier plutot que d'en ouvrir un second.");
                });
    }

    // --- Audit -----------------------------------------------------------------

    /**
     * Trace le declenchement.
     *
     * <p>{@code idUtilisateur} reste nul : {@code GET /identite/habilitation} ne
     * rend qu'un {@code login}, et {@code processus_mensuel} ne porte aucune
     * colonne d'auteur qui rendrait un appel supplementaire a
     * {@code GET /identite/moi} necessaire par ailleurs (decision Sprint 3.3,
     * {@code docs/decisions/2026-08-31-idutilisateur-non-renseigne-en-saisie.md}).
     * Le login est en revanche repris en contexte du delta : ici, contrairement au
     * service Saisie, il est disponible sans plomberie — le service le tient de
     * l'habilitation qu'il vient d'obtenir.
     *
     * <p>La publication ne leve jamais et n'attend rien (contrat de
     * {@link PublicateurAudit}) : l'envoi reel a lieu apres le commit.
     */
    private void publierDeclenchement(ProcessusMensuel processus, AgentHabilite agent, String adresseIp) {
        publicateurAudit.publier(EvenementAudit.de(
                null,
                ACTION_DECLENCHEMENT,
                ENTITE_CIBLE,
                processus.getId(),
                adresseIp,
                DeltaAudit.nouveau()
                        .champ("statut", null, processus.getStatut())
                        .champ("typeProcessus", null, processus.getTypeProcessus())
                        .champ("moisPaiement", null, processus.getMoisPaiement())
                        .champ("anneePaiement", null, processus.getAnneePaiement())
                        .champ("codeUnite", null, processus.getCodeUnite())
                        .contexte("auteur", agent.login())
                        .contexte("role", agent.role())
                        .enJson()));
    }

}
