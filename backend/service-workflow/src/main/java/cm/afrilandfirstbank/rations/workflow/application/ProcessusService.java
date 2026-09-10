package cm.afrilandfirstbank.rations.workflow.application;

import java.time.LocalDate;

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
     *   <li><b>Type NORMAL ?</b> Invariant de programmation depuis le Sprint 6bis.1 :
     *       l'ouverture d'un etat complementaire appartient a
     *       {@link OuvertureComplementaireService}, et le controleur y aiguille sur le
     *       type demande. Voir {@link #exigerTypeNormal(TypeProcessusEnum)}.</li>
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

        exigerTypeNormal(requete.typeDemande());

        AgentHabilite agent =
                habilitationService.exigerHabilitationSurUnite(requete.codeUnite(), enteteAutorisation);

        exigerAucunChevauchementAvecUnEtatNormal(
                requete.codeUnite(), requete.dateDebut(), requete.dateFin());

        ProcessusMensuel processus = TransitionProcessus.declencher(
                requete.dateDebut(), requete.dateFin(), requete.codeUnite());
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
     * Ce service ne declenche que des etats <b>NORMAL</b>.
     *
     * <h2>Ce que ce garde-fou est devenu au Sprint 6bis.1</h2>
     *
     * <p>Jusqu'au Sprint 6.3, il portait le refus <i>metier</i> de l'etat
     * complementaire : {@code 422 FONCTIONNALITE_NON_OUVERTE}, inconditionnel,
     * puisque le code de la regularisation n'existait pas. Ce refus a desormais un
     * proprietaire — {@link OuvertureComplementaireService}, qui lit le drapeau
     * {@code RATTRAPAGE_ACTIF} en premiere position et applique ensuite les
     * controles d'ouverture. Le controleur aiguille sur le type demande.
     *
     * <p>Ce qui reste ici n'est donc plus une regle de gestion mais un <b>invariant
     * de programmation</b> : si une demande complementaire parvenait jusqu'a cette
     * methode, c'est que l'aiguillage du controleur serait casse. D'ou une
     * {@link IllegalArgumentException} — un {@code 500}, comme il se doit pour un
     * defaut du module — et non un refus metier qui ferait croire a l'agent que sa
     * demande est en cause.
     *
     * <p><b>Le garde-fou ne disparait pas pour autant</b>, et c'est le point :
     * {@link TransitionProcessus#declencher} cree <i>toujours</i> un
     * {@link TypeProcessusEnum#NORMAL}. Sans ce controle, une demande complementaire
     * arrivee ici produirait silencieusement un second etat mensuel ordinaire sur une
     * periode close — le pire des comportements possibles, celui que la javadoc de
     * {@code DeclenchementProcessusRequest} met en garde depuis le Sprint 4.1.
     */
    private void exigerTypeNormal(TypeProcessusEnum typeDemande) {
        if (typeDemande != TypeProcessusEnum.NORMAL) {
            throw new IllegalArgumentException(
                    "ProcessusService.declencher ne cree que des etats NORMAL, et le type demande "
                            + "est " + typeDemande + ". L'ouverture d'un etat complementaire passe "
                            + "par OuvertureComplementaireService ; l'aiguillage se fait dans "
                            + "ProcessusController. Une demande complementaire arrivee ici "
                            + "produirait un etat NORMAL sans aucun de ses controles.");
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
    private void exigerAucunChevauchementAvecUnEtatNormal(String codeUnite,
            LocalDate dateDebut, LocalDate dateFin) {

        processusMensuelRepository
                .chevauchant(codeUnite, dateDebut, dateFin, TypeProcessusEnum.NORMAL)
                .stream().findFirst()
                .ifPresent(existant -> {
                    throw new ProcessusExistantException(
                            "Un etat normal couvre deja tout ou partie de la periode demandee pour "
                                    + "l'unite " + codeUnite + " : processus n° " + existant.getId()
                                    + ", " + existant.libellePeriode() + ", statut "
                                    + existant.getStatut() + ". Deux etats normaux ne peuvent pas se "
                                    + "partager une meme journee, sans quoi elle serait payable deux "
                                    + "fois." + suiteSelonEtatEnConflit(existant));
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
                        .champ("dateDebut", null, processus.getDateDebut())
                        .champ("dateFin", null, processus.getDateFin())
                        .champ("codeUnite", null, processus.getCodeUnite())
                        .contexte("auteur", agent.login())
                        .contexte("role", agent.role())
                        .enJson()));
    }


    /**
     * Ce que l'agent doit faire, selon l'etat qui bloque.
     *
     * <h2>Pourquoi le conseil ne peut pas etre unique</h2>
     *
     * <p>Le message d'origine disait « rejoignez ce dossier ». C'est le bon conseil
     * quand l'etat en conflit est encore ouvert — le cas de loin le plus frequent,
     * une periode saisie deux fois par erreur. C'est un <b>mauvais</b> conseil quand
     * il est cloture : on ne rejoint pas un dossier clos, et l'agent se retrouve
     * devant un mur sans issue nommee.
     *
     * <p>Le second cas n'est pas theorique. Il se produira une fois par unite <b>au
     * moment de la bascule du mensuel vers l'hebdomadaire</b> : l'etat du mois de
     * septembre, borne du 01 au 30, bloque toute semaine qui deborde sur septembre.
     * La regle d'exploitation est de faire la bascule sur une frontiere de mois — le
     * dernier etat mensuel s'arrete le 30, la premiere semaine commence le 1er — mais
     * un agent qui l'ignore doit lire quoi faire, pas deviner.
     */
    private String suiteSelonEtatEnConflit(ProcessusMensuel existant) {
        if (existant.getStatut() != StatutEnum.CLOTURE) {
            return " Ce dossier est encore ouvert : rejoignez-le plutot que d'en creer un second.";
        }
        return " Ce dossier est CLOTURE, donc definitif : il ne se rejoint pas. Si des "
                + "beneficiaires ont ete oublies sur ces journees, la regularisation passe par un "
                + "etat complementaire qui le reference. Si vous ouvrez la premiere periode d'un "
                + "nouveau rythme de paiement, faites-la commencer le lendemain de "
                + existant.getDateFin() + ", pour qu'aucune journee ne soit couverte deux fois.";
    }

}
