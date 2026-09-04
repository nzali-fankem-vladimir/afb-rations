package cm.afrilandfirstbank.rations.reporting.application;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.reporting.api.dto.PageResponse;

import cm.afrilandfirstbank.rations.reporting.domaine.EnTeteDemande;
import cm.afrilandfirstbank.rations.reporting.domaine.HistoriqueDemande;
import cm.afrilandfirstbank.rations.reporting.domaine.HistoriqueDemande.EtapeHistorique;
import cm.afrilandfirstbank.rations.reporting.domaine.LibelleActeur;
import cm.afrilandfirstbank.rations.reporting.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.reporting.domaine.exception.ServiceWorkflowIndisponibleException;
import cm.afrilandfirstbank.rations.reporting.domaine.exception.UtilisateurNonHabiliteException;

/**
 * Sert les deux endpoints de suivi (Sprint 6.1, US-15) : la recherche
 * multicritère et l'historique complet d'un dossier.
 *
 * <h2>Ce que ce service ajoute à {@link AgregationService}</h2>
 *
 * <p>{@link AgregationService} croise les deux sources et rend une liste. Ce
 * service la <b>pagine</b> ({@link PageResponse}, format de référence du
 * Sprint 1.2) et traduit l'historique reçu du Workflow en historique
 * <b>nommé</b>, chaque acteur portant son login au lieu de son seul
 * identifiant.
 *
 * <h2>Un lot, jamais un appel par étape</h2>
 *
 * <p>Les identifiants d'acteurs d'un historique sont dédoublonnés puis
 * traduits en <b>un seul appel</b> à {@code GET /identite/utilisateurs/libelles}
 * — le même principe qui interdit d'interroger la Saisie une fois par état
 * candidat dans {@link AgregationService}.
 */
@Service
public class SuiviService {

    private final AgregationService agregationService;
    private final WorkflowLectureClient workflowClient;
    private final IdentiteLectureClient identiteClient;

    public SuiviService(AgregationService agregationService,
            WorkflowLectureClient workflowClient,
            IdentiteLectureClient identiteClient) {
        this.agregationService = agregationService;
        this.workflowClient = workflowClient;
        this.identiteClient = identiteClient;
    }

    /**
     * Recherche paginée des demandes accessibles à l'utilisateur.
     *
     * <p>La pagination se fait <b>après</b> le croisement des deux sources, en
     * mémoire : {@link AgregationService} rend déjà la liste complète, triée du
     * plus récent au plus ancien. Une page demandée au-delà du contenu rend
     * simplement une liste vide — jamais une erreur.
     */
    public PageResponse<EnTeteDemande> rechercher(CriteresRecherche criteres, int page, int size,
            String enteteAutorisation) {

        List<EnTeteDemande> tout = agregationService.rechercher(criteres, enteteAutorisation);
        return PageResponse.depuis(tout, page, size);
    }

    /**
     * L'historique complet d'un dossier, acteurs nommés.
     *
     * @throws ProcessusIntrouvableException {@code 404}
     * @throws UtilisateurNonHabiliteException {@code 403}, refus relayé du Workflow
     * @throws ServiceWorkflowIndisponibleException {@code 503}
     */
    public HistoriqueDemande consulterHistorique(Long idProcessus, String enteteAutorisation) {
        HistoriqueDemande brut = lireHistorique(idProcessus, enteteAutorisation);

        List<Long> identifiants = brut.etapes().stream()
                .map(EtapeHistorique::idActeur)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, LibelleActeur> libelles = identiteClient.libelles(identifiants, enteteAutorisation);

        List<EtapeHistorique> etapesNommees = brut.etapes().stream()
                .map(etape -> nommer(etape, libelles))
                .toList();

        return new HistoriqueDemande(brut.idProcessus(), brut.moisPaiement(), brut.anneePaiement(),
                brut.codeUnite(), brut.statut(), etapesNommees);
    }

    private HistoriqueDemande lireHistorique(Long idProcessus, String enteteAutorisation) {
        ResultatHistorique resultat = workflowClient.consulterHistorique(idProcessus, enteteAutorisation);

        return switch (resultat) {
            case ResultatHistorique.Obtenu obtenu -> obtenu.historique();

            case ResultatHistorique.ProcessusIntrouvable introuvable ->
                    throw new ProcessusIntrouvableException(introuvable.motif());

            case ResultatHistorique.AccesRefuse refus ->
                    throw new UtilisateurNonHabiliteException(refus.motif());

            case ResultatHistorique.ServiceIndisponible panne ->
                    throw new ServiceWorkflowIndisponibleException(
                            "Le service Workflow est momentanement indisponible ; l'historique "
                                    + "est refuse (" + panne.motifTechnique()
                                    + "). Reessayez dans un instant.");
        };
    }

    /**
     * Un identifiant sans libellé disponible reste rendu tel quel, sans login ni
     * nom : voir {@link IdentiteLectureClient}, qui ne fait jamais échouer cet
     * appel.
     */
    private EtapeHistorique nommer(EtapeHistorique etape, Map<Long, LibelleActeur> libelles) {
        if (etape.idActeur() == null) {
            return etape;
        }
        LibelleActeur libelle = libelles.get(etape.idActeur());
        if (libelle == null) {
            return etape;
        }
        return etape.avecActeur(libelle.login(), libelle.nomComplet());
    }

}
