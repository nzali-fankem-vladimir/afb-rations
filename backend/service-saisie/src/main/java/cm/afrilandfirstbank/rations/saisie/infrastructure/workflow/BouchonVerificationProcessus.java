package cm.afrilandfirstbank.rations.saisie.infrastructure.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import cm.afrilandfirstbank.rations.saisie.application.ResultatVerificationProcessus;
import cm.afrilandfirstbank.rations.saisie.application.ResultatVerificationProcessus.ProcessusVerifie;
import cm.afrilandfirstbank.rations.saisie.application.VerificationProcessusClient;
import cm.afrilandfirstbank.rations.saisie.domaine.StatutProcessusEnum;

import jakarta.annotation.PostConstruct;

/**
 * <b>DISPOSITIF PROVISOIRE — À SUPPRIMER AU SPRINT 4.</b>
 *
 * <p>Répond « ce processus existe et il est modifiable » sans appeler personne,
 * pour que le service Saisie soit utilisable à la main tant que le service
 * Workflow n'est pas écrit.
 *
 * <h2>Pourquoi ce fichier existe</h2>
 *
 * <p>La convention du Sprint 3.1 impose un refus conservateur : toute réponse
 * autre qu'un {@code 200} exploitable de Workflow fait refuser l'écriture. Or
 * Workflow n'existe qu'au Sprint 4. Sans dispositif, <b>toute</b> écriture serait
 * refusée en {@code 503} — pas seulement celles portant sur un état verrouillé,
 * mais aussi la saisie normale. Le sprint deviendrait invérifiable à la main.
 *
 * <p>Les deux autres voies ont été écartées avec l'utilisateur : ne rien câbler
 * laisserait le trou de sécurité du Sprint 3.2 ouvert (rien n'empêcherait
 * d'écrire sur un état déjà soumis) ; ne rien bouchonner rendrait le module
 * inutilisable jusqu'au Sprint 4.
 *
 * <h2>Trois garde-fous, parce qu'un dispositif provisoire s'oublie</h2>
 *
 * <ol>
 *   <li><b>Jamais actif par défaut.</b> Il faut nommer explicitement le profil
 *       {@value #PROFIL} au démarrage. Le profil {@code dev} seul ne suffit pas.</li>
 *   <li><b>Refus de démarrer hors développement.</b> Activé sans le profil
 *       {@code dev} — en recette, en production —, le service <b>ne monte pas</b>.
 *       Un simple avertissement se noierait dans les journaux ; un service qui ne
 *       démarre pas se remarque tout de suite. C'est la différence entre un
 *       dispositif qu'on retire et un dispositif qui reste.</li>
 *   <li><b>Bruyant.</b> Bannière {@code WARN} au démarrage, et un {@code WARN}
 *       à chaque appel : personne ne peut croire que le contrôle a lieu.</li>
 * </ol>
 *
 * <p>Le jour du Sprint 4, la suppression de ce fichier et du {@code @Profile}
 * porté par {@link VerificationProcessusHttpClient} suffit. Rien d'autre ne le
 * référence : le reste du code ne connaît que le port
 * {@link VerificationProcessusClient}.
 *
 * <p>Même esprit que le drapeau {@code RATTRAPAGE_ACTIF} de
 * {@code docs/dispositifs_provisoires.md} : centraliser en un seul endroit,
 * rendre le provisoire visible, faire du remplacement une opération d'une ligne.
 */
@Component
@Profile(BouchonVerificationProcessus.PROFIL)
public class BouchonVerificationProcessus implements VerificationProcessusClient {

    /** Profil Spring qui active ce bouchon. Doit être nommé explicitement. */
    public static final String PROFIL = "bouchon-workflow";

    private static final Logger journal =
            LoggerFactory.getLogger(BouchonVerificationProcessus.class);

    private final Environment environnement;
    private final String codeUnite;
    private final int moisPaiement;
    private final int anneePaiement;
    private final StatutProcessusEnum statut;

    public BouchonVerificationProcessus(
            Environment environnement,
            @Value("${app.workflow.bouchon.code-unite:00002}") String codeUnite,
            @Value("${app.workflow.bouchon.mois-paiement:8}") int moisPaiement,
            @Value("${app.workflow.bouchon.annee-paiement:2026}") int anneePaiement,
            @Value("${app.workflow.bouchon.statut:EN_COURS_SAISIE}") String statut) {

        this.environnement = environnement;
        this.codeUnite = codeUnite;
        this.moisPaiement = moisPaiement;
        this.anneePaiement = anneePaiement;
        this.statut = StatutProcessusEnum.depuisLibelle(statut).orElseThrow(
                () -> new IllegalStateException("Statut de bouchon inconnu : " + statut));
    }

    /**
     * Garde-fou : le bouchon n'est tolérable qu'en développement.
     *
     * <p>Activé ailleurs, il ferait tourner un service bancaire <i>sans contrôle
     * du statut de l'état</i>, en silence — des lignes entreraient dans des états
     * déjà validés par le Chef d'Unité. Le service refuse donc de démarrer,
     * plutôt que de fonctionner à moitié.
     */
    @PostConstruct
    void verifierQueLeProfilDeveloppementEstActif() {
        if (!environnement.acceptsProfiles(Profiles.of("dev"))) {
            throw new IllegalStateException(
                    "Le profil " + PROFIL + " est actif alors que le profil dev ne l'est pas. "
                            + "Ce bouchon remplace la verification du statut du processus mensuel "
                            + "et n'a aucune place hors developpement : demarrage refuse. "
                            + "Profils actifs : "
                            + String.join(", ", environnement.getActiveProfiles()) + ".");
        }

        journal.warn("""

                =====================================================================
                 BOUCHON WORKFLOW ACTIF (profil {})
                 Le statut du processus mensuel n'est PAS verifie : toute ecriture
                 est acceptee comme si l'etat etait {}, unite {}, periode {}/{}.
                 Dispositif provisoire du Sprint 3.3, a supprimer au Sprint 4.
                 Ne doit jamais etre actif ailleurs qu'en developpement local.
                =====================================================================
                """, PROFIL, statut, codeUnite, moisPaiement, anneePaiement);
    }

    @Override
    public ResultatVerificationProcessus verifier(Long idProcessus, String enteteAutorisation) {
        journal.warn("BOUCHON WORKFLOW : processus {} tenu pour {} sans aucune verification.",
                idProcessus, statut);

        return new ProcessusVerifie(idProcessus, statut, codeUnite, moisPaiement, anneePaiement);
    }

}
