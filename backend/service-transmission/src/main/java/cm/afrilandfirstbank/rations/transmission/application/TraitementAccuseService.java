package cm.afrilandfirstbank.rations.transmission.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.commun.audit.DeltaAudit;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.transmission.application.ResultatTraitementAccuse.Applique;
import cm.afrilandfirstbank.rations.transmission.application.ResultatTraitementAccuse.DejaApplique;
import cm.afrilandfirstbank.rations.transmission.application.ResultatTraitementAccuse.EchecTemporaire;
import cm.afrilandfirstbank.rations.transmission.application.ResultatTraitementAccuse.RefusDefinitif;
import cm.afrilandfirstbank.rations.transmission.application.ResultatValidationAccuse.AccuseInvalide;
import cm.afrilandfirstbank.rations.transmission.application.ResultatValidationAccuse.AccuseRecevable;
import cm.afrilandfirstbank.rations.transmission.domaine.AccuseComptableEvent;
import cm.afrilandfirstbank.rations.transmission.domaine.CodeAnomalieAccuseEnum;

/**
 * Applique au processus l'accuse renvoye par la comptabilite (guide 5.2 etapes 4 et 5,
 * US-12, US-15, contrat d'API section 7.2).
 *
 * <h2>L'ordre des operations</h2>
 *
 * <pre>
 *   1. controler la charge recue                 -&gt; refus definitif
 *   2. demander au Workflow d'appliquer l'accuse -&gt; il decide ET ecrit, en une transaction
 *   3. traduire son verdict en issue de traitement
 *   4. tracer -- sauf si rien n'a change
 * </pre>
 *
 * <h2>Comment l'idempotence est garantie</h2>
 *
 * <p>Elle ne repose <b>pas</b> sur une memoire de ce service : il n'a pas de base, et un
 * cache en memoire disparaitrait au premier redemarrage — c'est-a-dire exactement au
 * moment ou un rejeu est le plus probable.
 *
 * <p>Elle repose sur la <b>seule source de verite</b>, {@code processus_mensuel}, et sur
 * une propriete de la donnee elle-meme : le statut d'integration porte deja le resultat de
 * tout accuse anterieur. Le service Workflow compare donc l'accuse a ce qu'il detient, et
 * decide, <b>dans la meme transaction que l'ecriture</b> :
 *
 * <ul>
 *   <li>identique en tout point — statut, reference, date, motif — : rien n'est ecrit,
 *       {@code DEJA_APPLIQUE} est rendu, et <b>aucune trace d'audit n'est publiee</b> ;</li>
 *   <li>applicable : le statut est ecrit, {@code APPLIQUE} est rendu, une trace est
 *       publiee ;</li>
 *   <li>contradictoire : rien n'est ecrit, refus definitif trace.</li>
 * </ul>
 *
 * <p>Recevoir deux fois le meme accuse produit donc exactement le meme etat final qu'une
 * seule reception, et une seule ligne d'audit. Lire puis ecrire en <b>deux</b> appels
 * aurait laisse entre les deux une fenetre ou un second accuse pouvait s'intercaler :
 * l'idempotence n'aurait ete qu'une apparence.
 *
 * <p>Et parce qu'aucun accuse ne fait regresser un statut — {@code INTEGRE} et
 * {@code REJETE} sont definitifs —, la garantie tient meme si les messages arrivent
 * <b>dans le desordre</b>, ce qui compte : la cle de partition des accuses est posee par
 * un producteur que l'equipe ne controle pas.
 *
 * <h2>Ce que ce service ne fait pas</h2>
 *
 * <p><b>Il ne pose jamais {@code transmis_comptabilite}.</b> Un accuse portant sur un etat
 * jamais transmis est une incoherence, pas une transmission oubliee : poser le drapeau a
 * cette occasion ferait croire a un envoi qui n'a pas eu lieu, et RG-13 refuserait ensuite
 * le vrai comme un doublon — l'etat resterait impaye a jamais.
 *
 * <p><b>Il ne produit aucune ecriture comptable</b> et ne fabrique aucune reference : il
 * recopie celle que la comptabilite lui donne (CLAUDE.md section 8).
 */
@Service
public class TraitementAccuseService {

    private static final Logger journal = LoggerFactory.getLogger(TraitementAccuseService.class);

    private static final String ENTITE_CIBLE = "processus_mensuel";
    private static final String ACTION_ACCUSE_APPLIQUE = "ACCUSE_COMPTABLE_APPLIQUE";
    private static final String ACTION_ACCUSE_REFUSE = "ACCUSE_COMPTABLE_REFUSE";

    /** Prefixes de supervision, convention {@code INCOHERENCE GRILLE} (Sprint 2.4). */
    static final String PREFIXE_ORPHELIN = "ACCUSE ORPHELIN";
    static final String PREFIXE_INCOHERENT = "ACCUSE INCOHERENT";
    static final String PREFIXE_CONTRADICTOIRE = "ACCUSE CONTRADICTOIRE";
    static final String PREFIXE_INVALIDE = "ACCUSE INVALIDE";

    private final ValidationAccuseService validationAccuseService;
    private final StatutIntegrationClient statutIntegrationClient;
    private final PublicateurAudit publicateurAudit;

    public TraitementAccuseService(ValidationAccuseService validationAccuseService,
            StatutIntegrationClient statutIntegrationClient,
            PublicateurAudit publicateurAudit) {
        this.validationAccuseService = validationAccuseService;
        this.statutIntegrationClient = statutIntegrationClient;
        this.publicateurAudit = publicateurAudit;
    }

    /**
     * Traite un accuse deja deserialise.
     *
     * <p><b>Aucune exception ne sort de cette methode</b> : elle rend une issue. C'est le
     * consommateur qui decide, au vu de cette issue, s'il faut rejouer le message. Lever
     * ici confondrait « cet accuse est fautif » et « je n'ai pas pu joindre le Workflow »,
     * deux situations dont l'une doit avancer et l'autre etre rejouee.
     *
     * @param adresseIp origine de la trace d'audit. Un message Kafka n'a pas d'adresse IP
     *        d'appelant : le consommateur y met le nom du topic, seule origine reelle
     */
    public ResultatTraitementAccuse traiter(AccuseComptableEvent accuse, String adresseIp) {

        // 1. Ce qui se lit dans le message seul. Controle une seule fois : le rejouer
        //    ferait deux fois le meme travail et, surtout, deux verdicts a tenir d'accord.
        ResultatValidationAccuse validation = validationAccuseService.valider(accuse);
        if (validation instanceof AccuseInvalide invalide) {
            return refuser(accuse.idProcessus(), PREFIXE_INVALIDE, invalide.anomalies(), adresseIp);
        }
        AccuseRecevable recevable = (AccuseRecevable) validation;

        // 2. Le reste demande l'etat courant du processus : c'est le Workflow qui decide
        //    et ecrit, dans une seule transaction (voir StatutIntegrationClient).
        return switch (statutIntegrationClient.mettreAJour(recevable)) {

            case ResultatMiseAJourIntegration.Applique applique -> {
                journal.info("Accuse comptable applique a l'etat {} : statut d'integration {}, "
                                + "reference {}.",
                        applique.idProcessus(), applique.statutApplique(),
                        recevable.referenceComptable());
                publierTraceApplication(recevable, adresseIp);
                yield new Applique(applique.idProcessus(), applique.statutApplique());
            }

            // Rien n'a change : ni ecriture, ni trace d'audit. Une seconde ligne
            // laisserait croire a un second traitement comptable du meme etat.
            case ResultatMiseAJourIntegration.DejaApplique deja -> {
                journal.info("Accuse comptable deja applique a l'etat {} (statut {}) : rejeu "
                                + "sans effet, aucune ecriture ni trace produite.",
                        deja.idProcessus(), deja.statutCourant());
                yield new DejaApplique(deja.idProcessus(), deja.statutCourant());
            }

            case ResultatMiseAJourIntegration.ProcessusInconnu inconnu -> refuser(
                    inconnu.idProcessus(), PREFIXE_ORPHELIN,
                    List.of(AnomalieAccuse.de(CodeAnomalieAccuseEnum.PROCESSUS_INCONNU,
                            "Le service Workflow ne connait pas l'etat " + inconnu.idProcessus()
                                    + " : soit la comptabilite s'est trompee d'identifiant, soit "
                                    + "cet accuse s'adresse a un autre module.")),
                    adresseIp);

            case ResultatMiseAJourIntegration.ProcessusNonTransmis nonTransmis -> refuser(
                    nonTransmis.idProcessus(), PREFIXE_INCOHERENT,
                    List.of(AnomalieAccuse.de(CodeAnomalieAccuseEnum.PROCESSUS_NON_TRANSMIS,
                            nonTransmis.message())),
                    adresseIp);

            case ResultatMiseAJourIntegration.AccuseContradictoire contradiction -> refuser(
                    contradiction.idProcessus(), PREFIXE_CONTRADICTOIRE,
                    List.of(AnomalieAccuse.de(CodeAnomalieAccuseEnum.ACCUSE_CONTRADICTOIRE,
                            contradiction.message())),
                    adresseIp);

            // La seule issue temporaire. Le consommateur levera pour faire rejouer.
            case ResultatMiseAJourIntegration.ServiceWorkflowIndisponible panne ->
                    new EchecTemporaire(panne.idProcessus(), panne.motifTechnique());
        };
    }

    // --- Refus et traces ---------------------------------------------------------

    /**
     * Journalise sous le prefixe de supervision, trace en audit, et rend un refus
     * definitif.
     *
     * <p>La trace d'audit compte plus ici qu'ailleurs : un accuse refuse est une
     * information venue de la comptabilite que le module n'a pas appliquee. Elle doit
     * survivre dans une base qu'aucun service metier ne peut reecrire, avec son motif
     * exact — c'est ce qui permettra, plus tard, de rapprocher les deux versions des faits.
     */
    private RefusDefinitif refuser(Long idProcessus, String prefixe,
            List<AnomalieAccuse> anomalies, String adresseIp) {

        String detail = anomalies.stream()
                .map(anomalie -> anomalie.code() + " : " + anomalie.message())
                .reduce((premiere, suivante) -> premiere + " | " + suivante)
                .orElseThrow();

        journal.error("{} : l'accuse recu pour l'etat {} n'est pas applique. {}",
                prefixe, idProcessus, detail);

        publicateurAudit.publier(EvenementAudit.de(
                null,
                ACTION_ACCUSE_REFUSE,
                ENTITE_CIBLE,
                idProcessus,
                adresseIp,
                DeltaAudit.nouveau()
                        .contexte("motif", anomalies.getFirst().code().name())
                        .contexte("detail", detail)
                        .enJson()));

        return new RefusDefinitif(idProcessus, anomalies);
    }

    /**
     * Trace l'application de l'accuse (CLAUDE.md section 12).
     *
     * <p><b>{@code idUtilisateur} est nul</b>, comme pour la transmission au Sprint 5.1 :
     * aucun utilisateur n'est derriere un message Kafka. L'auteur du fait trace est le
     * module de comptabilisation, qui n'a pas de compte dans ce module.
     *
     * <p>La reference comptable et la date de traitement y figurent : un controle interne
     * doit pouvoir rapprocher un paiement de son accuse longtemps apres que la retention
     * du topic a expire, sans redemander les donnees a un systeme auquel l'equipe n'a pas
     * acces.
     */
    private void publierTraceApplication(AccuseRecevable accuse, String adresseIp) {
        publicateurAudit.publier(EvenementAudit.de(
                null,
                ACTION_ACCUSE_APPLIQUE,
                ENTITE_CIBLE,
                accuse.idProcessus(),
                adresseIp,
                DeltaAudit.nouveau()
                        .contexte("statutIntegration", accuse.statutIntegration().name())
                        .contexte("referenceComptable", accuse.referenceComptable())
                        .contexte("dateTraitement",
                                accuse.dateTraitement() == null
                                        ? null
                                        : accuse.dateTraitement().toString())
                        .contexte("motif", accuse.motif())
                        .enJson()));
    }

}
