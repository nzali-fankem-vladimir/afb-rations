package cm.afrilandfirstbank.rations.workflow.application;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.commun.audit.DeltaAudit;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.workflow.application.ResultatDemandeTransmission.DejaTransmise;
import cm.afrilandfirstbank.rations.workflow.application.ResultatDemandeTransmission.EchecApresTentative;
import cm.afrilandfirstbank.rations.workflow.application.ResultatDemandeTransmission.EchecAvantPublication;
import cm.afrilandfirstbank.rations.workflow.application.ResultatDemandeTransmission.Transmise;
import cm.afrilandfirstbank.rations.workflow.infrastructure.config.ConfigurationTransmission;

/**
 * Declenche la mise a disposition d'un etat cloture, et fait en sorte qu'un echec ne passe
 * jamais inapercu (US-12, RG-13).
 *
 * <h2>Le scenario que cette classe existe pour eviter</h2>
 *
 * <p>Un etat cloture est <b>fige</b> : plus personne ne peut le corriger, ni le rouvrir. S'il
 * n'est pas transmis, ses beneficiaires ne sont pas payes — et, sans dispositif, rien ne le
 * dirait. C'est le risque le plus grave du Sprint 5.1. Trois mesures y repondent, dans cet
 * ordre :
 *
 * <ol>
 *   <li><b>Un reessai</b>, mais seulement quand on sait qu'aucun message n'est parti ;</li>
 *   <li><b>Le drapeau reste a faux</b> en cas d'echec : l'etat reste retrouvable par une
 *       requete sur {@code statut = CLOTURE AND transmis_comptabilite = false} ;</li>
 *   <li><b>Trois traces</b> : un journal au prefixe {@value #PREFIXE_MANQUEE}, un evenement
 *       d'audit dans une base qu'aucun service metier ne peut reecrire, et le resultat rendu
 *       au valideur dans la reponse de sa validation.</li>
 * </ol>
 *
 * <h2>La cloture n'est jamais annulee</h2>
 *
 * <p>Elle est deja commitee quand cette classe est appelee, et c'est voulu. L'annuler
 * laisserait un document PDF portant un visa — ecrit sur disque hors transaction au
 * Sprint 4.2 — pour une validation qui n'existerait plus ; elle ferait dependre toute
 * validation de la banque de la disponibilite de Kafka ; et Kafka et PostgreSQL ne partagent
 * de toute facon aucune transaction. La cloture est la decision metier, la transmission en
 * est la consequence.
 *
 * <h2>Un reessai, et seulement sur ce qui n'a pas pu partir</h2>
 *
 * <p>La regle est portee par le type {@link ResultatDemandeTransmission}, pas par ce code :
 * {@link EchecAvantPublication} atteste qu'aucun {@code send()} n'a eu lieu et se reessaie ;
 * {@link EchecApresTentative} est ambigu ou deterministe et ne se reessaie jamais.
 *
 * <p>Le motif est le double paiement. L'idempotence du producteur Kafka ne couvre que les
 * reessais <b>internes</b> d'un meme {@code send()} ; un second {@code send()} applicatif est
 * un message neuf. Reessayer apres un accuse perdu ecrirait deux fois le meme etat sur le
 * topic, donc deux jeux d'ecritures pour les memes beneficiaires — ce que RG-13 interdit.
 *
 * <p>C'est un ecart assume a la doctrine « aucun reessai » du Sprint 3.2, et le motif de
 * cette doctrine explique pourquoi : elle visait la latence d'un appel synchrone dans la
 * boucle de saisie, ligne par ligne. Ici, l'enjeu n'est pas une seconde d'attente, c'est un
 * salaire verse ou non.
 *
 * <h2>Le budget de temps, chiffre</h2>
 *
 * <table>
 *   <tr><th>Panne</th><th>1re tentative</th><th>Reessai</th><th>Total</th></tr>
 *   <tr><td>Transmission injoignable</td><td>~2 s</td><td>oui</td><td>~6 s</td></tr>
 *   <tr><td>Workflow ou Saisie qui pend</td><td>5 s</td><td>oui</td><td><b>12 s</b></td></tr>
 *   <tr><td>Kafka muet</td><td>7 s</td><td>non</td><td>7 s</td></tr>
 *   <tr><td>Les cinq a leur limite (verrou compris, 5.3)</td><td>27 s</td><td>non</td><td><b>27 s</b></td></tr>
 *   <tr><td>Charge incomplete</td><td>~0,2 s</td><td>non</td><td>0,2 s</td></tr>
 * </table>
 *
 * <p>Cas nominal : environ 200 ms. Pire cas d'un fil HTTP retenu : 27 s, et au plus quatre a
 * la fois grace au pool dedie ({@link ConfigurationTransmission}).
 */
@Service
public class DeclenchementTransmission {

    private static final Logger journal = LoggerFactory.getLogger(DeclenchementTransmission.class);

    /**
     * Prefixe reperable en supervision : un etat cloture n'est pas parti. Distinct de
     * {@code TRANSMISSION REJETEE POOL SATURE}, qui designe un pic de charge et non une
     * panne, et de {@code AUDIT PERDU} du Sprint 1.3.
     */
    public static final String PREFIXE_MANQUEE = "TRANSMISSION MANQUEE";

    /** Tentatives au total : la premiere, plus un reessai. */
    static final int TENTATIVES_MAXIMUM = 2;

    /** Pause entre deux tentatives : de quoi laisser un service redemarrer, pas davantage. */
    static final long PAUSE_ENTRE_TENTATIVES_MS = 2_000;

    /**
     * Borne defensive de l'attente du fil appelant. Superieure au pire cas construit (27 s
     * pour une tentative depuis le Sprint 5.3, 12 s pour deux), elle ne devrait jamais se
     * declencher : elle existe pour qu'un blocage imprevu ne retienne pas un fil HTTP
     * indefiniment.
     *
     * <p>Relevee de 40 a 55 s au Sprint 5.3, en consequence des deux appels du verrou de
     * RG-13 ajoutes au budget de l'appele : elle doit rester au-dessus du delai de lecture
     * HTTP (35 s), faute de quoi elle trancherait a sa place — par un delai depasse sans
     * motif, la ou l'appele aurait rendu une reponse nommee.
     */
    static final long ATTENTE_MAXIMALE_SECONDES = 55;

    private static final String ENTITE_CIBLE = "processus_mensuel";
    private static final String ACTION_MANQUEE = "TRANSMISSION_MANQUEE";

    private final TransmissionClient transmissionClient;
    private final PublicateurAudit publicateurAudit;
    /**
     * Type {@link Executor} et non {@code ThreadPoolTaskExecutor} : cette classe se sert de
     * l'executeur, elle n'a pas a connaitre son dimensionnement. Le pool reel, sa file nulle
     * et son handler de rejet vivent dans {@link ConfigurationTransmission}, ou ils se
     * justifient. Un test peut alors passer un executeur synchrone et eprouver la regle de
     * reessai sans concurrence — la propriete qu'il faut prouver n'est pas le parallelisme,
     * c'est de ne jamais republier ce qui a pu partir.
     */
    private final Executor executeurTransmission;

    public DeclenchementTransmission(TransmissionClient transmissionClient,
            PublicateurAudit publicateurAudit,
            @Qualifier("executeurTransmission") Executor executeurTransmission) {
        this.transmissionClient = transmissionClient;
        this.publicateurAudit = publicateurAudit;
        this.executeurTransmission = executeurTransmission;
    }

    /**
     * Transmet l'etat cloture, sur un fil du pool dedie, et rend ce qui s'est passe.
     *
     * <p><b>Ne leve jamais.</b> La cloture est acquise et commitee : aucun incident de
     * transmission ne doit la transformer en erreur pour le valideur, qui a bien valide. Le
     * probleme est reel, mais il se dit — il ne se rejoue pas en exception.
     *
     * @param enteteAutorisation jeton du valideur, relaye tel quel. Il est encore valide :
     *        la transmission suit immediatement sa validation
     */
    public ResultatTransmissionCloture transmettre(Long idProcessus, String codeUnite,
            String enteteAutorisation, String adresseIp) {

        try {
            CompletableFuture<ResultatTransmissionCloture> tache = CompletableFuture.supplyAsync(
                    () -> transmettreAvecReessai(idProcessus, codeUnite, enteteAutorisation,
                            adresseIp),
                    executeurTransmission);

            return tache.get(ATTENTE_MAXIMALE_SECONDES, TimeUnit.SECONDS);

        } catch (RejectedExecutionException poolSature) {
            // Les quatre fils sont occupes : le handler du pool l'a deja journalise au
            // prefixe TRANSMISSION REJETEE POOL SATURE, puis a leve pour que nous
            // l'apprenions tout de suite au lieu d'attendre un resultat qui ne viendrait
            // jamais. On le trace en audit et on le dit au valideur, seule personne a
            // pouvoir le constater aujourd'hui. Couvre aussi TaskRejectedException, que
            // Spring derive de RejectedExecutionException.
            return echecTrace(idProcessus, codeUnite,
                    "les " + ConfigurationTransmission.FILS_DE_TRANSMISSION + " fils de "
                            + "transmission etaient occupes : l'etat n'a pas ete transmis",
                    0, adresseIp);

        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            return echecTrace(idProcessus, codeUnite,
                    "attente de la transmission interrompue", 0, adresseIp);

        } catch (TimeoutException delaiDepasse) {
            return echecTrace(idProcessus, codeUnite,
                    "aucun resultat de transmission en " + ATTENTE_MAXIMALE_SECONDES + " s",
                    0, adresseIp);

        } catch (ExecutionException incident) {
            Throwable cause = incident.getCause() == null ? incident : incident.getCause();
            journal.error("{} : incident imprevu pendant la transmission de l'etat {}.",
                    PREFIXE_MANQUEE, idProcessus, cause);
            return echecTrace(idProcessus, codeUnite,
                    "incident imprevu : " + cause.getMessage(), 0, adresseIp);
        }
    }

    // --- Boucle de tentatives -------------------------------------------------------

    /**
     * Au plus {@value #TENTATIVES_MAXIMUM} tentatives, et l'on ne recommence que sur un
     * echec dont le type atteste qu'aucun message n'est parti.
     *
     * <p>Le {@code switch} est exhaustif sur {@link ResultatDemandeTransmission} : une
     * quatrieme issue ajoutee plus tard ferait echouer la compilation ici, au lieu de tomber
     * dans une branche par defaut qui reessaierait — c'est-a-dire au lieu de choisir le
     * risque de double paiement par inadvertance.
     */
    private ResultatTransmissionCloture transmettreAvecReessai(Long idProcessus, String codeUnite,
            String enteteAutorisation, String adresseIp) {

        String dernierMotif = null;

        for (int tentative = 1; tentative <= TENTATIVES_MAXIMUM; tentative++) {
            ResultatDemandeTransmission resultat =
                    transmissionClient.demanderTransmission(idProcessus, enteteAutorisation);

            switch (resultat) {
                case Transmise accuse -> {
                    // Aucune ecriture ici depuis le Sprint 5.3 : le drapeau de RG-13 a ete
                    // pose a la RESERVATION et confirme par le service Transmission, seul a
                    // savoir ce qui est reellement parti. L'ecrire une seconde fois de ce
                    // cote rouvrirait deux verites sur le meme fait.
                    journal.info("Etat {} (unite {}) transmis a la comptabilite : {} ligne(s), "
                                    + "{} FCFA, topic {} partition {} offset {}, en {} tentative(s).",
                            idProcessus, codeUnite, accuse.nombreLignes(), accuse.montantTotal(),
                            accuse.topic(), accuse.partition(), accuse.offset(), tentative);
                    return ResultatTransmissionCloture.reussie(tentative);
                }
                case DejaTransmise deja -> {
                    // Le verrou a fait son office : rien n'est reparti vers la comptabilite.
                    // Ce n'est pas un echec, et cela ne se journalise pas comme tel — le
                    // refus est deja trace en audit par le service qui l'a oppose.
                    journal.info("Etat {} (unite {}) : deja transmis, aucune seconde "
                                    + "publication (RG-13). {}",
                            idProcessus, codeUnite, deja.message());
                    return ResultatTransmissionCloture.dejaTransmise(deja.message(), tentative);
                }
                case EchecApresTentative definitif -> {
                    // Un message a pu partir, ou l'echec se reproduirait a l'identique :
                    // on s'arrete la, sans reessayer.
                    return echecTrace(idProcessus, codeUnite, definitif.motif(), tentative,
                            adresseIp);
                }
                case EchecAvantPublication reessayable -> {
                    dernierMotif = reessayable.motif();
                    if (tentative < TENTATIVES_MAXIMUM) {
                        journal.warn("Transmission de l'etat {} en echec avant toute publication "
                                        + "({}). Aucun evenement n'est parti : nouvelle tentative "
                                        + "dans {} ms.",
                                idProcessus, dernierMotif, PAUSE_ENTRE_TENTATIVES_MS);
                        if (!patienter()) {
                            return echecTrace(idProcessus, codeUnite,
                                    "attente entre deux tentatives interrompue apres : "
                                            + dernierMotif,
                                    tentative, adresseIp);
                        }
                    }
                }
            }
        }

        return echecTrace(idProcessus, codeUnite, dernierMotif, TENTATIVES_MAXIMUM, adresseIp);
    }

    /** @return {@code false} si l'attente a ete interrompue, auquel cas on renonce. */
    private boolean patienter() {
        try {
            Thread.sleep(PAUSE_ENTRE_TENTATIVES_MS);
            return true;
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // --- Signalement ----------------------------------------------------------------

    /**
     * Journalise, trace en audit, et rend l'echec pour qu'il remonte au valideur.
     *
     * <p>Les trois vont ensemble et aucun ne remplace les autres. Le journal sert la
     * supervision, l'audit sert le controle interne six mois plus tard — dans une base
     * qu'aucun service metier ne peut reecrire —, et la reponse sert la seule personne qui,
     * aujourd'hui, peut agir : celle qui vient de cloturer.
     */
    private ResultatTransmissionCloture echecTrace(Long idProcessus, String codeUnite,
            String motif, int tentatives, String adresseIp) {

        journal.error("{} : l'etat {} de l'unite {} est CLOTURE mais n'a pas ete transmis a la "
                        + "comptabilite apres {} tentative(s). Motif : {}. Ses beneficiaires ne "
                        + "seront pas payes tant qu'il n'aura pas ete retransmis.",
                PREFIXE_MANQUEE, idProcessus, codeUnite, tentatives, motif);

        publicateurAudit.publier(EvenementAudit.de(
                null,
                ACTION_MANQUEE,
                ENTITE_CIBLE,
                idProcessus,
                adresseIp,
                DeltaAudit.nouveau()
                        .contexte("codeUnite", codeUnite)
                        .contexte("motif", motif)
                        .contexte("tentatives", tentatives)
                        .enJson()));

        return ResultatTransmissionCloture.echouee(motif, tentatives);
    }

}
