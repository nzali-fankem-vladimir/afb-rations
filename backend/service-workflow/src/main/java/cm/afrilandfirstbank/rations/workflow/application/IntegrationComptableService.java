package cm.afrilandfirstbank.rations.workflow.application;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cm.afrilandfirstbank.rations.commun.audit.DeltaAudit;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutIntegrationEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.TransitionIntegration;
import cm.afrilandfirstbank.rations.workflow.domaine.TransitionIntegration.Decision;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.AccuseContradictoireException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusNonTransmisException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ProcessusMensuelRepository;

/**
 * Inscrit sur un etat la suite que la comptabilite lui a donnee (Sprint 5.2, contrat
 * d'API section 7.2, US-12, US-15).
 *
 * <h2>Pourquoi c'est ce service qui decide, et pas celui qui recoit l'accuse</h2>
 *
 * <p>Le service Transmission consomme le topic, mais il <b>n'a pas de base</b> et n'accede
 * jamais a celle-ci (diagramme AR04). Trois des cinq verdicts possibles — processus
 * inconnu, jamais transmis, accuse contradictoire — demandent l'etat courant du processus,
 * qui vit ici. Il pose donc la question, et ce service repond <b>en meme temps qu'il
 * ecrit</b>.
 *
 * <p>C'est ce qui rend l'idempotence sure plutot qu'apparente. Si le service Transmission
 * lisait l'etat, decidait, puis ecrivait en un second appel, un accuse concurrent pourrait
 * s'intercaler entre les deux : deux receptions du meme accuse produiraient deux ecritures
 * et deux traces. Ici la lecture, la decision et l'ecriture tiennent dans <b>une seule
 * transaction</b>, et {@link Decision.DejaApplique} en est le compte rendu.
 *
 * <h2>Une seule trace, et seulement quand quelque chose change</h2>
 *
 * <p>{@link Decision.DejaApplique} ne publie <b>aucun</b> evenement d'audit. Une seconde
 * ligne pour un rejeu ferait croire a un second traitement comptable du meme etat, et le
 * journal d'audit — la piece que le controle interne relira — deviendrait faux. Recevoir
 * deux fois le meme accuse laisse donc exactement une trace.
 *
 * <h2>Ce que ce service ne fait pas</h2>
 *
 * <p><b>Il ne pose jamais {@code transmis_comptabilite}.</b> Ce drapeau se pose a la
 * publication effective et nulle part ailleurs (RG-13, Sprint 5.1). Le poser ici, sur la
 * foi d'un accuse, ferait croire a un envoi qui n'a pas eu lieu.
 *
 * <p><b>Il ne produit aucune ecriture comptable</b> et ne fabrique aucune reference : il
 * recopie celle que la comptabilite lui donne (CLAUDE.md section 8).
 */
@Service
public class IntegrationComptableService {

    private static final Logger journal =
            LoggerFactory.getLogger(IntegrationComptableService.class);

    private static final String ENTITE_CIBLE = "processus_mensuel";
    private static final String ACTION_INTEGRATION = "INTEGRATION_COMPTABLE";

    private final ProcessusMensuelRepository processusMensuelRepository;
    private final PublicateurAudit publicateurAudit;

    public IntegrationComptableService(ProcessusMensuelRepository processusMensuelRepository,
            PublicateurAudit publicateurAudit) {
        this.processusMensuelRepository = processusMensuelRepository;
        this.publicateurAudit = publicateurAudit;
    }

    /**
     * Applique l'accuse, ou dit pourquoi il ne l'est pas.
     *
     * <p>Transactionnelle : elle ecrit, et surtout la lecture et la decision doivent tenir
     * dans le meme geste que l'ecriture. Aucun appel reseau ne s'y trouve, contrairement
     * aux services de validation ou de soumission — c'est ce qui permet de la garder
     * transactionnelle de bout en bout sans immobiliser une connexion (doctrines 2.3 et
     * 4.1).
     *
     * @param dateTraitementIso date portee par l'accuse, ISO 8601 avec decalage, ou
     *        {@code null}
     * @param adresseIp origine de la trace d'audit
     * @return la decision prise et l'etat d'integration qui en resulte
     * @throws ProcessusIntrouvableException identifiant inconnu ({@code 404})
     * @throws ProcessusNonTransmisException etat jamais transmis ({@code 422})
     * @throws AccuseContradictoireException contredit un statut definitif ({@code 409})
     */
    @Transactional
    public ResultatIntegrationComptable appliquerAccuse(Long idProcessus,
            StatutIntegrationEnum statutRecu,
            String referenceComptable, String dateTraitementIso, String motif,
            String adresseIp) {

        ProcessusMensuel processus = processusMensuelRepository.findById(idProcessus)
                .orElseThrow(() -> new ProcessusIntrouvableException(idProcessus));

        StatutIntegrationEnum statutAvant = processus.getStatutIntegration();
        String referenceAvant = processus.getReferenceComptable();
        LocalDateTime dateRecue = convertir(dateTraitementIso);

        Decision decision = TransitionIntegration.appliquerAccuse(
                processus, statutRecu, referenceComptable, dateRecue, motif);

        return switch (decision) {

            case Decision.Appliquer applique -> {
                ProcessusMensuel enregistre = processusMensuelRepository.save(processus);
                journal.info("Statut d'integration de l'etat {} porte a {} (reference {}, "
                                + "date {}).",
                        idProcessus, statutRecu, referenceComptable, dateRecue);
                publierTrace(enregistre, statutAvant, referenceAvant, adresseIp);
                yield ResultatIntegrationComptable.de(enregistre, applique);
            }

            // Rien n'a change : ni ecriture, ni trace. C'est l'idempotence qui se voit.
            case Decision.DejaApplique deja -> {
                journal.info("Accuse deja applique a l'etat {} (statut {}) : rejeu sans effet.",
                        idProcessus, statutAvant);
                yield ResultatIntegrationComptable.de(processus, deja);
            }

            case Decision.NonTransmis nonTransmis ->
                    throw new ProcessusNonTransmisException(nonTransmis.message());

            case Decision.Contradiction contradiction ->
                    throw new AccuseContradictoireException(contradiction.message());
        };
    }

    /**
     * Convertit la date de l'accuse pour la colonne {@code date_traitement}, un
     * {@code TIMESTAMP} sans fuseau.
     *
     * <p>L'instant recu est ramene au fuseau du systeme, comme l'est tout horodatage de ce
     * module ({@code LocalDateTime.now()} partout ailleurs). Retenir l'UTC ici ferait
     * cohabiter dans la meme table deux references de temps que rien ne distinguerait a la
     * lecture.
     *
     * <p><b>La conversion est deterministe</b>, ce qui compte pour l'idempotence : le meme
     * accuse recu deux fois produit la meme valeur, donc la comparaison le reconnait.
     *
     * <p>Une date illisible ne devrait jamais arriver ici — le service Transmission la
     * refuse en amont, avec un diagnostic nomme. Le cas est neanmoins traite : une date
     * absente vaut mieux qu'un accuse perdu, et la colonne est nullable.
     */
    private LocalDateTime convertir(String dateTraitementIso) {
        if (dateTraitementIso == null || dateTraitementIso.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(dateTraitementIso.trim())
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (DateTimeParseException illisible) {
            journal.warn("Date de traitement illisible dans l'accuse ({}) : la colonne reste "
                    + "nulle, le statut d'integration est applique.", dateTraitementIso);
            return null;
        }
    }

    /**
     * Trace l'inscription du statut d'integration (CLAUDE.md section 12).
     *
     * <p><b>{@code idUtilisateur} est nul</b>, comme pour la transmission au Sprint 5.1 :
     * aucun utilisateur n'est derriere un accuse comptable. L'auteur du fait trace est le
     * module de comptabilisation, qui n'a pas de compte dans ce module.
     *
     * <p>Le delta porte l'avant et l'apres du statut et de la reference : un controle
     * interne doit pouvoir refaire l'histoire d'un paiement sans redemander les donnees a
     * un systeme auquel l'equipe n'a pas acces.
     */
    private void publierTrace(ProcessusMensuel processus, StatutIntegrationEnum statutAvant,
            String referenceAvant, String adresseIp) {

        publicateurAudit.publier(EvenementAudit.de(
                null,
                ACTION_INTEGRATION,
                ENTITE_CIBLE,
                processus.getId(),
                adresseIp,
                DeltaAudit.nouveau()
                        .champ("statutIntegration", statutAvant, processus.getStatutIntegration())
                        .champ("referenceComptable", referenceAvant,
                                processus.getReferenceComptable())
                        .contexte("dateTraitement", processus.getDateTraitement())
                        .contexte("motifIntegration", processus.getMotifIntegration())
                        .contexte("codeUnite", processus.getCodeUnite())
                        .contexte("moisPaiement", processus.getMoisPaiement())
                        .contexte("anneePaiement", processus.getAnneePaiement())
                        .contexte("montantTotal", processus.getMontantTotal())
                        .enJson()));
    }

}
