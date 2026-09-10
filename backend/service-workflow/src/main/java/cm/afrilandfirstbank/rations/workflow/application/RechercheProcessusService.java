package cm.afrilandfirstbank.rations.workflow.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.AgentNonHabiliteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.EtapeWorkflowRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ProcessusMensuelRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ProcessusSpecifications;
import cm.afrilandfirstbank.rations.workflow.domaine.EtapeWorkflow;

/**
 * Lecture en volume pour le service Reporting (Sprint 6.1) : la recherche d'etats
 * mensuels et l'historique complet d'un dossier.
 *
 * <p>Les deux servent des <b>endpoints internes</b>, hors contrat passerelle. Le
 * service Reporting n'a pas de base : ce qu'il presente vient d'ici et de la
 * Saisie, par leurs API et jamais par leurs bases (AR04).
 *
 * <h2>Le cloisonnement est pose au meme endroit que la requete</h2>
 *
 * <p>La portee est resolue depuis le jeton par {@link PorteeService}, puis passee a
 * {@code ProcessusSpecifications} comme un filtre que rien ne permet d'omettre.
 * Aucun parametre de portee n'existe dans l'URL : un appel direct forge sur le
 * port 8084 ne peut donc pas s'attribuer d'unites.
 */
@Service
public class RechercheProcessusService {

    /**
     * Du plus recent au plus ancien, puis par unite. L'ordre est fixe ici et non
     * laisse a l'appelant : la pagination du Reporting decoupe ce que ce service
     * rend, et deux ordres differents entre le comptage et la lecture produiraient
     * des pages qui se recouvrent.
     */
    private static final Sort ORDRE = Sort.by(
            Sort.Order.desc("dateDebut"),
            Sort.Order.asc("codeUnite"),
            Sort.Order.asc("id"));

    private final ProcessusMensuelRepository processusRepository;
    private final EtapeWorkflowRepository etapeRepository;
    private final PorteeService porteeService;
    private final HabilitationService habilitationService;

    public RechercheProcessusService(ProcessusMensuelRepository processusRepository,
            EtapeWorkflowRepository etapeRepository,
            PorteeService porteeService,
            HabilitationService habilitationService) {
        this.processusRepository = processusRepository;
        this.etapeRepository = etapeRepository;
        this.porteeService = porteeService;
        this.habilitationService = habilitationService;
    }

    /**
     * Les en-tetes des etats correspondant aux criteres, dans la limite du volume
     * demande par l'appelant.
     *
     * <h2>Une unite demandee hors portee est un refus, pas un resultat vide</h2>
     *
     * <p>Un chef d'unite qui interroge explicitement l'unite {@code 00007} recoit
     * {@code 403}, jamais une page vide. La page vide lui ferait croire que cette
     * unite n'a ouvert aucun etat, ce qui est une information qu'il n'a pas le droit
     * d'avoir et qui se trouve etre fausse.
     *
     * <p>En l'absence de filtre d'unite, en revanche, la portee restreint
     * silencieusement : demander « tous les etats » et n'obtenir que les siens est le
     * comportement attendu, pas un refus.
     *
     * @param limite nombre d'en-tetes au-dela duquel le contenu n'est pas renvoye ;
     *        le compte, lui, l'est toujours
     */
    @Transactional(readOnly = true)
    public ResultatRechercheProcessus rechercher(LocalDate dateDebut, LocalDate dateFin, String codeUnite,
            StatutEnum statut, int limite, String enteteAutorisation) {

        PorteeAccesUtilisateur portee = porteeService.exigerPortee(enteteAutorisation);

        if (codeUnite != null && !portee.couvre(codeUnite)) {
            throw new AgentNonHabiliteException(
                    "Vous n'avez pas de droit de lecture sur l'unite " + codeUnite
                            + ". Rapprochez-vous de l'administrateur du module.");
        }

        // null = portee nationale, donc aucun filtre d'unite ; un ensemble vide
        // signifie « aucune unite » et ne rend rien. Voir ProcessusSpecifications.
        Set<String> codesVisibles = portee.nationale() ? null : portee.codesUnite();

        Specification<ProcessusMensuel> criteres =
                ProcessusSpecifications.avecFiltres(dateDebut, dateFin, codeUnite, statut, codesVisibles);

        long nombreTotal = processusRepository.count(criteres);
        if (nombreTotal > limite) {
            return ResultatRechercheProcessus.tronquee(nombreTotal);
        }

        return ResultatRechercheProcessus.complete(processusRepository.findAll(criteres, ORDRE));
    }

    /**
     * Toutes les etapes d'un dossier, dans l'ordre de leur rang (CT-31).
     *
     * <h2>Tous les passages, pas le dernier</h2>
     *
     * <p>Le rang est calcule {@code dernier + 1} depuis le Sprint 4.4, y compris pour
     * les resoumissions : un dossier retourne puis resoumis porte donc deux etapes
     * {@code SOUMISSION_AGENT} et, le cas echeant, deux {@code VALIDATION_DA}. Cette
     * methode les rend <b>toutes</b>. Ne montrer que le dernier passage a chaque
     * niveau priverait le controle interne de ce qu'il vient precisement chercher :
     * qui a refuse quoi, quand, et ce qui a change ensuite.
     *
     * <p>La portee d'acces est verifiee sur l'unite du dossier, aupres du service
     * Identite, comme pour toute lecture de processus.
     */
    @Transactional(readOnly = true)
    public HistoriqueProcessus consulterHistorique(Long idProcessus, String enteteAutorisation) {
        ProcessusMensuel processus = processusRepository.findById(idProcessus)
                .orElseThrow(() -> new ProcessusIntrouvableException(idProcessus));

        habilitationService.exigerHabilitationSurUnite(processus.getCodeUnite(), enteteAutorisation);

        List<EtapeWorkflow> etapes = etapeRepository.findByIdProcessusOrderByOrdreEtape(idProcessus);
        return new HistoriqueProcessus(processus, etapes);
    }

}
