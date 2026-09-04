package cm.afrilandfirstbank.rations.reporting.application;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.reporting.domaine.EnTeteDemande;
import cm.afrilandfirstbank.rations.reporting.domaine.exception.RechercheTropLargeException;
import cm.afrilandfirstbank.rations.reporting.domaine.exception.ServiceSaisieIndisponibleException;
import cm.afrilandfirstbank.rations.reporting.domaine.exception.ServiceWorkflowIndisponibleException;
import cm.afrilandfirstbank.rations.reporting.domaine.exception.UtilisateurNonHabiliteException;

/**
 * Croise deux bases qu'aucune jointure SQL ne peut joindre (Sprint 6.1).
 *
 * <h2>Le probleme, en une phrase</h2>
 *
 * <p>La recherche multicritere de CT-30 porte sur cinq criteres : la periode et
 * l'unite vivent dans {@code rations_workflow}, la nature, la session et le
 * beneficiaire dans {@code rations_saisie}. Ce service n'a <b>pas de base</b> et
 * n'accede a aucune des deux (AR04). Le croisement se fait donc ici, en memoire,
 * a partir de deux appels d'API.
 *
 * <h2>La strategie : deux appels a somme fixe</h2>
 *
 * <pre>
 *   1. Workflow : les en-tetes de la portee, filtres sur periode et unite
 *   2. Saisie   : les identifiants d'etats portant une ligne retenue
 *                 -- SEULEMENT si un critere de ligne est demande
 *   3. ici      : intersection, dans l'ordre rendu par le Workflow
 * </pre>
 *
 * <p><b>Un ou deux appels, quel que soit le nombre de resultats.</b> C'est ce qui
 * tient la cible de trois secondes : une strategie qui interrogerait la Saisie une
 * fois par etat candidat ferait exploser le temps de reponse des la premiere
 * recherche large, et le guide 6.1 la proscrit explicitement. Les alternatives
 * examinees et ecartees sont dans
 * {@code docs/decisions/2026-09-03-agregation-multi-services-du-reporting.md}.
 *
 * <h2>Intersection stricte quand les criteres portent sur les deux sources</h2>
 *
 * <p>Un etat ressort s'il satisfait <i>a la fois</i> les criteres d'en-tete et
 * contient au moins une ligne satisfaisant les criteres de ligne. Demander « aout
 * 2026 + RATION » ne doit pas rendre les etats d'aout sans ration.
 *
 * <p>Le grain du resultat reste <b>l'etat mensuel</b> : un critere de ligne agit
 * comme un filtre d'existence sur l'etat, jamais comme un decoupage de son contenu.
 *
 * <h2>Ce que ce service ne fait pas</h2>
 *
 * <p>Il ne pagine pas — c'est {@link SuiviService} —, et il ne resout aucune portee
 * d'acces. Celle-ci est appliquee par les services qui detiennent les donnees,
 * depuis le jeton relaye : aucun parametre de portee ne voyage sur le reseau, donc
 * aucun ne peut etre forge.
 */
@Service
public class AgregationService {

    private final WorkflowLectureClient workflowClient;
    private final SaisieLectureClient saisieClient;
    private final int limiteResultats;

    public AgregationService(WorkflowLectureClient workflowClient,
            SaisieLectureClient saisieClient,
            @Value("${app.reporting.limite-resultats:5000}") int limiteResultats) {
        this.workflowClient = workflowClient;
        this.saisieClient = saisieClient;
        this.limiteResultats = limiteResultats;
    }

    /**
     * Les demandes retenues, triees du plus recent au plus ancien.
     *
     * <p>Une liste <b>vide</b> est une reponse normale : rien ne correspond. Elle ne
     * se confond avec aucun refus, chacun ayant son exception et son code.
     *
     * @throws RechercheTropLargeException {@code 422}, le volume depasse la borne
     * @throws UtilisateurNonHabiliteException {@code 403}, refus relaye d'un service amont
     * @throws ServiceWorkflowIndisponibleException {@code 503}
     * @throws ServiceSaisieIndisponibleException {@code 503}, seulement si un critere
     *         de ligne etait demande
     */
    public List<EnTeteDemande> rechercher(CriteresRecherche criteres, String enteteAutorisation) {
        List<EnTeteDemande> enTetes = lireEnTetes(criteres, enteteAutorisation);

        if (!criteres.porteSurLesLignes() || enTetes.isEmpty()) {
            // Sans critere de ligne, il n'y a rien a croiser. Et si le Workflow n'a
            // rien rendu, interroger la Saisie ne changerait pas le resultat : autant
            // ne pas la deranger, ni dependre de sa disponibilite pour rien.
            return enTetes;
        }

        Set<Long> retenusParLaSaisie = lireIdentifiantsDeLignes(criteres, enteteAutorisation);

        return enTetes.stream()
                .filter(enTete -> retenusParLaSaisie.contains(enTete.id()))
                .toList();
    }

    /**
     * Les en-tetes du service Workflow, ou un refus nomme.
     *
     * <p>La borne de volume est transmise a l'appele : il compte d'abord, et ne
     * transporte le contenu que s'il tient. Un chargement suivi d'un comptage ici
     * aurait ramene ce qu'on cherchait justement a ne pas ramener.
     */
    private List<EnTeteDemande> lireEnTetes(CriteresRecherche criteres, String enteteAutorisation) {
        ResultatRechercheDemandes resultat = workflowClient.rechercher(
                criteres.mois(), criteres.annee(), criteres.codeUnite(), null,
                limiteResultats, enteteAutorisation);

        return switch (resultat) {
            case ResultatRechercheDemandes.Obtenue obtenue -> obtenue.contenu();

            case ResultatRechercheDemandes.TropDeResultats trop -> throw new RechercheTropLargeException(
                    trop.nombreTotal() + " etats correspondent a votre recherche, au-dela de la "
                            + "limite de " + limiteResultats + " resultats. Ajoutez un filtre de "
                            + "periode ou d'unite pour restreindre la recherche.");

            case ResultatRechercheDemandes.AccesRefuse refus ->
                    throw new UtilisateurNonHabiliteException(refus.motif());

            case ResultatRechercheDemandes.ServiceIndisponible panne ->
                    throw new ServiceWorkflowIndisponibleException(
                            "Le service Workflow est momentanement indisponible ; la recherche "
                                    + "est refusee (" + panne.motifTechnique()
                                    + "). Reessayez dans un instant.");
        };
    }

    /**
     * Les identifiants d'etats portant une ligne retenue, ou un refus nomme.
     *
     * <p><b>Echec net, jamais un resultat partiel</b> (decision du Sprint 6.1). Rendre
     * les en-tetes non filtres afficherait, sous une etiquette « RATION », des dossiers
     * dont on ignore s'ils en contiennent : une liste plus large que ce qui a ete
     * demande est aussi trompeuse qu'une liste plus etroite, et plus difficile a
     * reperer. Le module refuse et signale, il n'arbitre jamais.
     */
    private Set<Long> lireIdentifiantsDeLignes(CriteresRecherche criteres,
            String enteteAutorisation) {

        ResultatIdentifiantsAvecLigne resultat = saisieClient.identifiantsAvecLigne(
                criteres.mois(), criteres.annee(), criteres.nature(), criteres.session(),
                criteres.beneficiaire(), enteteAutorisation);

        return switch (resultat) {
            case ResultatIdentifiantsAvecLigne.Obtenus obtenus -> obtenus.identifiants();

            case ResultatIdentifiantsAvecLigne.AccesRefuse refus ->
                    throw new UtilisateurNonHabiliteException(refus.motif());

            case ResultatIdentifiantsAvecLigne.ServiceIndisponible panne ->
                    throw new ServiceSaisieIndisponibleException(
                            "Le service Saisie est momentanement indisponible ; les criteres de "
                                    + "nature, de session et de beneficiaire ne peuvent pas etre "
                                    + "appliques (" + panne.motifTechnique() + "). Reessayez dans "
                                    + "un instant, ou relancez la recherche sans ces criteres.");
        };
    }

}
