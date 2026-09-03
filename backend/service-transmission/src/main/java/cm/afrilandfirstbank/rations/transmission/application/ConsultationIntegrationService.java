package cm.afrilandfirstbank.rations.transmission.application;

import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.transmission.domaine.StatutIntegrationEnum;
import cm.afrilandfirstbank.rations.transmission.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.transmission.domaine.exception.ServiceWorkflowIndisponibleException;

/**
 * Sert {@code GET /transmission/processus/{id}} : ou en est cet etat vis-a-vis de la
 * comptabilite (contrat d'API section 7, US-15, Sprint 5.3).
 *
 * <h2>Le seul endpoint de ce service au contrat de la passerelle</h2>
 *
 * <p>Le contrat est explicite : « ce service n'expose pas d'endpoint de declenchement au
 * client ». Il en expose <b>un</b>, et c'est celui-ci — une lecture, ouverte aux roles ARH
 * et circuit. Les trois autres routes du service ({@code POST} de declenchement, plus les
 * appels vers Workflow) sont internes.
 *
 * <h2>Aucun champ vide sans explication</h2>
 *
 * <p>Exigence du guide, et elle n'est pas cosmetique : quatre des cinq situations rendent
 * une reference comptable nulle, pour quatre raisons differentes. Chacune est donc nommee
 * par une {@link SituationIntegration} et accompagnee d'une phrase qui dit ce qu'il faut
 * en penser — y compris {@code PUBLICATION_NON_CONFIRMEE}, la situation nee du verrou du
 * Sprint 5.3, qu'aucun statut du contrat ne sait exprimer.
 *
 * <h2>Ce service ne detient rien</h2>
 *
 * <p>Il lit le bloc d'integration aupres du service Workflow, par son API (diagramme
 * AR04), sans cache : un statut de paiement memorise ferait dire au suivi qu'un etat
 * attend encore alors qu'il est paye. La portee d'acces est verifiee par l'appele, sur le
 * jeton relaye.
 */
@Service
public class ConsultationIntegrationService {

    private final IntegrationProcessusClient integrationProcessusClient;

    public ConsultationIntegrationService(IntegrationProcessusClient integrationProcessusClient) {
        this.integrationProcessusClient = integrationProcessusClient;
    }

    /**
     * @param enteteAutorisation en-tete {@code Authorization} de l'utilisateur final,
     *        relaye tel quel (doctrine Sprint 1.3)
     * @throws ProcessusIntrouvableException identifiant inconnu ({@code 404})
     * @throws ServiceWorkflowIndisponibleException le service Workflow est muet
     *         ({@code 503}) : on ne devine pas un statut de paiement
     */
    public StatutIntegrationConsulte consulter(Long idProcessus, String enteteAutorisation) {
        return switch (integrationProcessusClient.obtenir(idProcessus, enteteAutorisation)) {

            case ResultatIntegrationProcessus.IntegrationObtenue obtenue ->
                    interpreter(obtenue.integration());

            case ResultatIntegrationProcessus.ProcessusInconnu inconnu ->
                    throw new ProcessusIntrouvableException(inconnu.idProcessus());

            case ResultatIntegrationProcessus.ServiceWorkflowIndisponible panne ->
                    throw new ServiceWorkflowIndisponibleException(
                            idProcessus, panne.motifTechnique());
        };
    }

    /**
     * Traduit l'etat des deux colonnes en une situation nommee et une phrase.
     *
     * <p>L'ordre des tests suit la chronologie de l'echange : d'abord « a-t-on envoye ? »,
     * puis « qu'en sait-on ? ». C'est aussi l'ordre dans lequel une personne se pose les
     * questions en lisant la reponse.
     */
    private StatutIntegrationConsulte interpreter(IntegrationProcessus integration) {
        if (!integration.transmis()) {
            return new StatutIntegrationConsulte(
                    integration.idProcessus(),
                    SituationIntegration.NON_TRANSMIS,
                    null, null, null, null, null,
                    "Cet etat n'a pas ete transmis a la comptabilite. Tant qu'il n'est pas "
                            + "cloture, c'est normal : la transmission suit la validation "
                            + "finale. S'il est cloture, c'est une transmission manquee, a "
                            + "signaler a l'administrateur du module.");
        }

        StatutIntegrationEnum statut = integration.statutIntegration();

        if (statut == null) {
            return new StatutIntegrationConsulte(
                    integration.idProcessus(),
                    SituationIntegration.PUBLICATION_NON_CONFIRMEE,
                    null, null, null, null,
                    integration.dateReservationTransmission(),
                    "La transmission de cet etat a ete engagee le "
                            + integration.dateReservationTransmission()
                            + " et sa publication n'a pas ete confirmee. Si cet instant est tres "
                            + "recent, l'envoi est en cours. Sinon, l'issue de la publication est "
                            + "incertaine : le module ne la rejoue pas de lui-meme, une seconde "
                            + "publication risquerait un double paiement (RG-13). A lever avec "
                            + "l'administrateur du module.");
        }

        return switch (statut) {

            case EN_ATTENTE -> new StatutIntegrationConsulte(
                    integration.idProcessus(),
                    SituationIntegration.EN_ATTENTE_ACCUSE,
                    statut, null, null, null,
                    integration.dateReservationTransmission(),
                    "Cet etat a bien ete transmis a la comptabilite le "
                            + integration.dateReservationTransmission()
                            + ". Elle n'a pas encore accuse reception : ni reference comptable "
                            + "ni date de traitement ne sont disponibles a ce stade.");

            case INTEGRE -> new StatutIntegrationConsulte(
                    integration.idProcessus(),
                    SituationIntegration.INTEGRE,
                    statut,
                    integration.referenceComptable(),
                    integration.dateTraitement(),
                    null,
                    integration.dateReservationTransmission(),
                    "La comptabilite a pris cet etat en charge sous la reference "
                            + integration.referenceComptable() + ", le "
                            + integration.dateTraitement() + ".");

            case REJETE -> new StatutIntegrationConsulte(
                    integration.idProcessus(),
                    SituationIntegration.REJETE,
                    statut,
                    integration.referenceComptable(),
                    integration.dateTraitement(),
                    integration.motifIntegration(),
                    integration.dateReservationTransmission(),
                    "La comptabilite a refuse cet etat le " + integration.dateTraitement()
                            + ". Motif : " + motifOuDefaut(integration.motifIntegration())
                            + ". Le traitement du refus se fait avec la Direction Financiere et "
                            + "Tresorerie : ce module ne produit aucune ecriture comptable.");
        };
    }

    /**
     * Un rejet sans motif ne devrait pas exister — le contrat en prevoit un — mais il
     * arriverait d'un systeme que l'equipe ne controle pas. Le dire vaut mieux que de
     * laisser un « Motif : null » dans une phrase.
     */
    private String motifOuDefaut(String motif) {
        return motif == null || motif.isBlank()
                ? "aucun motif n'accompagnait l'accuse de rejet"
                : motif;
    }

}
