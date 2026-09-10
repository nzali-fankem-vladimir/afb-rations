package cm.afrilandfirstbank.rations.workflow.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cm.afrilandfirstbank.rations.commun.audit.DeltaAudit;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.workflow.domaine.EtapeWorkflow;
import cm.afrilandfirstbank.rations.workflow.domaine.NiveauValidation;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.TransitionProcessus;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.TransitionProcessusInterditeException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.EtapeWorkflowRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ProcessusMensuelRepository;

/**
 * Le seul bloc transactionnel du retour a l'agent.
 *
 * <p>Classe a part pour les deux raisons des Sprints 4.2 et 4.3, dans le meme ordre.
 * <b>Technique</b> : Spring pose ses transactions par mandataire, et
 * {@code @Transactional} sur une methode appelee depuis la meme classe n'a aucun
 * effet — la transaction serait absente sans que rien ne le signale.
 * <b>Lisible</b> : la frontiere entre les appels reseau et les ecritures en base
 * devient visible dans la structure du code.
 *
 * <p><b>Aucun appel reseau ici</b>, ni ecriture de fichier : un retour n'estampe pas
 * le document. La piece jointe n'est donc pas touchee — ni son chemin, ni son
 * compteur de signatures. Elle sera regeneree a la resoumission, quand les montants
 * corriges seront connus.
 *
 * <h2>RG-11 rendue structurelle</h2>
 *
 * <p>{@link #appliquerRetour} appelle l'une des deux transitions de retour d'ET01
 * selon le niveau. Elles menent au <b>meme</b> statut {@link StatutEnum#RETOURNE} :
 * il n'existe aucune cible intermediaire a choisir, donc aucune occasion de renvoyer
 * un dossier au chef d'unite plutot qu'a l'agent. Le {@code switch} est exhaustif —
 * un troisieme niveau ferait echouer la compilation ici.
 */
@Component
public class EnregistrementRetour {

    private static final String ENTITE_CIBLE = "processus_mensuel";
    private static final String ACTION_RETOUR = "RETOUR_PROCESSUS";

    private final ProcessusMensuelRepository processusMensuelRepository;
    private final EtapeWorkflowRepository etapeWorkflowRepository;
    private final PublicateurAudit publicateurAudit;

    public EnregistrementRetour(ProcessusMensuelRepository processusMensuelRepository,
            EtapeWorkflowRepository etapeWorkflowRepository,
            PublicateurAudit publicateurAudit) {
        this.processusMensuelRepository = processusMensuelRepository;
        this.etapeWorkflowRepository = etapeWorkflowRepository;
        this.publicateurAudit = publicateurAudit;
    }

    /**
     * Enregistre le retour : etape {@code RETOURNEE} portant le motif, transition vers
     * {@code RETOURNE}, audit.
     *
     * @param niveau le niveau depuis lequel le retour est fait, designe par le statut
     * @param motif ce que l'agent doit corriger (RG-10), deja verifie non vide
     */
    @Transactional
    public ResultatRetour enregistrer(Long idProcessus, NiveauValidation niveau,
            ActeurSignataire acteur, String motif, String adresseIp) {

        // Relecture dans la transaction. Entre le controle de RetourService et cet
        // instant, deux appels reseau se sont ecoules : une seconde requete a eu le
        // temps de valider ou de retourner le meme etat.
        ProcessusMensuel processus = processusMensuelRepository.findById(idProcessus)
                .orElseThrow(() -> new ProcessusIntrouvableException(idProcessus));

        exigerStatutEncoreRetournable(processus, niveau);

        StatutEnum statutAvant = processus.getStatut();

        EtapeWorkflow etape = new EtapeWorkflow(idProcessus, acteur.id(),
                ordreEtapeSuivant(idProcessus), niveau.nomEtape());
        etape.retournerAvecMotif(motif);

        appliquerRetour(processus, niveau, motif);

        ProcessusMensuel enregistre = processusMensuelRepository.save(processus);
        EtapeWorkflow etapeEnregistree = etapeWorkflowRepository.save(etape);

        publierRetour(enregistre, niveau, acteur, motif, statutAvant, adresseIp);

        return new ResultatRetour(enregistre, etapeEnregistree, niveau);
    }

    // --- Application de la decision ---------------------------------------------

    /**
     * <b>RG-11 : le retour ramene toujours a l'agent.</b>
     *
     * <p>Les deux transitions menent au meme statut {@link StatutEnum#RETOURNE}, y
     * compris celle du directeur reseau — dont le dossier est pourtant passe par le
     * chef d'unite. Le renvoyer a ce dernier semblerait naturel et serait faux : son
     * visa portait sur une version que le directeur reseau vient de refuser, et il n'a
     * pas la main sur les lignes de saisie. Le circuit sera integralement refait apres
     * correction (RG-07).
     */
    private void appliquerRetour(ProcessusMensuel processus, NiveauValidation niveau,
            String motif) {
        switch (niveau) {
            case CHEF_UNITE -> TransitionProcessus.retournerParChefUnite(processus, motif);
            case DIRECTEUR_RESEAU ->
                    TransitionProcessus.retournerParDirecteurReseau(processus, motif);
        }
    }

    /**
     * Rang du pas dans le parcours : celui du dernier pas connu, plus un.
     *
     * <p>Un etat retourne puis resoumis repassera par les memes niveaux : un rang fixe
     * ferait apparaitre deux etapes de meme ordre, et l'historique du dossier
     * deviendrait ambigu a la lecture — alors que c'est precisement lui qui distingue
     * les cycles de validation (RG-12, {@code CycleValidation}).
     */
    private int ordreEtapeSuivant(Long idProcessus) {
        return etapeWorkflowRepository.findFirstByIdProcessusOrderByOrdreEtapeDesc(idProcessus)
                .map(derniere -> derniere.getOrdreEtape() + 1)
                .orElse(1);
    }

    // --- Garde de concurrence ------------------------------------------------------

    private void exigerStatutEncoreRetournable(ProcessusMensuel processus,
            NiveauValidation niveau) {
        if (processus.getStatut() != niveau.statutRequis()) {
            throw new TransitionProcessusInterditeException(
                    "L'etat " + processus.getId() + " a change de statut pendant la preparation "
                            + "du retour : il est passe a " + processus.getStatut()
                            + ", alors qu'un retour depuis le niveau " + niveau.libelle()
                            + " exige " + niveau.statutRequis() + ". Il a vraisemblablement ete "
                            + "traite par ailleurs. Rechargez le dossier avant toute nouvelle "
                            + "action.");
        }
    }

    // --- Audit -----------------------------------------------------------------------

    /**
     * Trace le retour et son motif (CLAUDE.md section 12).
     *
     * <p><b>Le motif est enregistre dans l'audit</b>, en plus de
     * {@code etape_workflow.motif_retour}. Ce n'est pas une redite : l'etape vit dans
     * une base que le module peut ecrire, le journal d'audit dans une base ou aucun
     * service metier n'a de droit de modification (CLAUDE.md section 3). Un controle
     * interne qui veut savoir pourquoi un etat a ete refuse le lit dans une trace que
     * personne ne peut reecrire.
     *
     * <p>Publication dans la transaction ; envoi sur le topic differe apres le commit
     * par {@code rations-audit-commun} (doctrine Sprint 1.3). Un rollback ne laisse
     * donc jamais la trace d'un retour annule.
     */
    private void publierRetour(ProcessusMensuel processus, NiveauValidation niveau,
            ActeurSignataire acteur, String motif, StatutEnum statutAvant, String adresseIp) {

        publicateurAudit.publier(EvenementAudit.de(
                acteur.id(),
                ACTION_RETOUR,
                ENTITE_CIBLE,
                processus.getId(),
                adresseIp,
                DeltaAudit.nouveau()
                        .champ("statut", statutAvant, processus.getStatut())
                        .contexte("niveau", niveau)
                        .contexte("etape", niveau.nomEtape())
                        .contexte("motifRetour", motif)
                        .contexte("auteur", acteur.login())
                        .contexte("role", acteur.role())
                        .contexte("codeUnite", processus.getCodeUnite())
                        .contexte("dateDebut", processus.getDateDebut())
                        .contexte("dateFin", processus.getDateFin())
                        .contexte("montantTotal", processus.getMontantTotal())
                        .enJson()));
    }

}
