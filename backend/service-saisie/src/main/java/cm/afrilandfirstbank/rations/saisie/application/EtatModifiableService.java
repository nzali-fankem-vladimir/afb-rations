package cm.afrilandfirstbank.rations.saisie.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.saisie.application.ResultatHabilitationUnite.AgentHabilite;
import cm.afrilandfirstbank.rations.saisie.application.ResultatHabilitationUnite.AgentNonHabilite;
import cm.afrilandfirstbank.rations.saisie.application.ResultatHabilitationUnite.ServiceIdentiteIndisponible;
import cm.afrilandfirstbank.rations.saisie.application.ResultatVerificationProcessus.ProcessusIntrouvable;
import cm.afrilandfirstbank.rations.saisie.application.ResultatVerificationProcessus.ProcessusVerifie;
import cm.afrilandfirstbank.rations.saisie.application.ResultatVerificationProcessus.ServiceWorkflowIndisponible;
import cm.afrilandfirstbank.rations.saisie.domaine.FicheJournaliere;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.AgentNonHabiliteException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.EtatNonModifiableException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.ServiceIdentiteIndisponibleException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.ServiceWorkflowIndisponibleException;

/**
 * Le portier du service Saisie : <b>un seul point de passage</b> avant toute
 * écriture, et avant toute lecture d'une fiche.
 *
 * <h2>Pourquoi les deux contrôles vivent ensemble</h2>
 *
 * <p>Ils répondent à deux questions distinctes — « l'état est-il encore
 * ouvert ? » (statut du processus) et « cet agent a-t-il le droit d'y toucher ? »
 * (RG-12, portée d'accès). Les séparer en deux services aurait été plus pur, et
 * plus dangereux : <b>ils se déduisent du même appel réseau</b>.
 * {@code GET /processus/{id}} rend le statut <i>et</i> le code unité, et c'est
 * ce code unité que le service Identité doit ensuite arbitrer
 * ({@code docs/rattachement-processus.md} §3). Deux services, ce serait deux
 * appels à Workflow par écriture, ou un code unité transporté à la main entre
 * eux — donc un jour oublié.
 *
 * <p>Surtout, la portée d'accès doit s'appliquer aux <b>cinq</b> endpoints
 * (guide 3.3 §10). Une porte unique se vérifie ; cinq contrôles recopiés se
 * vérifient cinq fois, et l'un d'eux finit par manquer.
 *
 * <h2>Trois refus, trois causes distinctes</h2>
 *
 * <ul>
 *   <li>{@code 404 PROCESSUS_INTROUVABLE} — Workflow a répondu : ce processus
 *       n'existe pas. C'est ce refus qui remplace la clé étrangère impossible
 *       entre deux bases.</li>
 *   <li>{@code 422 ETAT_NON_MODIFIABLE} — l'état est soumis, en validation ou
 *       clôturé.</li>
 *   <li>{@code 403 UTILISATEUR_NON_HABILITE} — l'agent n'a pas de droit sur
 *       cette unité.</li>
 * </ul>
 *
 * <p>Auxquels s'ajoutent les deux refus techniques, {@code 503}, distincts des
 * précédents : un serveur muet n'est pas une saisie fautive.
 *
 * <h2>L'ordre des deux appels n'est pas indifférent</h2>
 *
 * <p>Workflow d'abord, Identité ensuite — imposé, pas choisi : le code unité que
 * le second doit arbitrer est produit par le premier. Le statut est donc vérifié
 * avant l'habilitation, ce qui laisse un agent non habilité apprendre qu'un état
 * est clôturé. L'information est sans valeur (ni montant, ni identité, ni
 * bénéficiaire) et le refus est tracé dans les deux cas.
 *
 * <h2>Aucune mise en cache, jamais</h2>
 *
 * <p>Ni du statut, ni du verdict d'habilitation. Les deux peuvent changer entre
 * deux saisies, et autoriser sur une valeur potentiellement obsolète
 * contredirait RG-12 et le refus par défaut (doctrine Sprint 1.3). Le coût est
 * connu et assumé : écrire une ligne mobilise désormais <b>trois dépendances
 * synchrones</b> — Grilles, Workflow, Identité. Si la latence devient sensible,
 * la réponse est de fiabiliser ces services, pas d'assouplir la règle.
 */
@Service
public class EtatModifiableService {

    private static final Logger journal = LoggerFactory.getLogger(EtatModifiableService.class);

    private final VerificationProcessusClient verificationProcessusClient;
    private final HabilitationClient habilitationClient;

    public EtatModifiableService(VerificationProcessusClient verificationProcessusClient,
                                 HabilitationClient habilitationClient) {
        this.verificationProcessusClient = verificationProcessusClient;
        this.habilitationClient = habilitationClient;
    }

    /**
     * Vérifie qu'une écriture est possible sur ce processus, par cet agent, et
     * rend le contexte du processus — statut, code unité, période — dont
     * l'ouverture de fiche a besoin pour la recopie figée.
     *
     * @throws ProcessusIntrouvableException le processus n'existe pas ({@code 404})
     * @throws EtatNonModifiableException l'état n'est plus ouvert ({@code 422})
     * @throws AgentNonHabiliteException hors de la portée d'accès ({@code 403})
     * @throws ServiceWorkflowIndisponibleException Workflow muet ({@code 503})
     * @throws ServiceIdentiteIndisponibleException Identité muet ({@code 503})
     */
    public ProcessusVerifie exigerEcriturePossible(Long idProcessus, String enteteAutorisation) {
        ProcessusVerifie processus = verifierProcessus(idProcessus, enteteAutorisation);

        if (!processus.estModifiable()) {
            throw new EtatNonModifiableException(String.format(
                    "L'etat de la periode %02d/%d pour l'unite %s est %s : il n'est plus modifiable. "
                            + "Demandez son retour au chef d'unite pour reprendre la saisie.",
                    processus.moisPaiement(), processus.anneePaiement(),
                    processus.codeUnite(), processus.statut()));
        }

        exigerHabilitation(processus.codeUnite(), enteteAutorisation);
        return processus;
    }

    /**
     * Vérifie qu'un agent peut consulter cette fiche. Le statut du processus
     * n'est pas interrogé : <b>consulter un état clôturé est légitime</b>, seule
     * l'écriture est fermée.
     *
     * <p>Le code unité est lu sur la fiche, où il a été recopié à l'ouverture
     * (migration V3) : la consultation ne coûte donc <b>aucun appel à
     * Workflow</b>. C'est le bénéfice direct de cette recopie.
     *
     * <p>Repli explicite pour les fiches antérieures à la migration, dont le code
     * unité est nul : il est alors redemandé à Workflow. Traiter le nul comme une
     * autorisation ouvrirait toutes les fiches historiques à tout le monde ;
     * le traiter comme un refus les rendrait toutes illisibles.
     */
    public void exigerLecturePossible(FicheJournaliere fiche, String enteteAutorisation) {
        String codeUnite = fiche.getCodeUnite();

        if (codeUnite == null || codeUnite.isBlank()) {
            journal.debug("Fiche {} sans code unite recopie (anterieure a la migration V3) : "
                    + "code unite redemande au service Workflow.", fiche.getId());
            codeUnite = verifierProcessus(fiche.getIdProcessus(), enteteAutorisation).codeUnite();
        }

        exigerHabilitation(codeUnite, enteteAutorisation);
    }

    /**
     * Vérifie qu'un utilisateur a le droit d'agir sur une unité <b>désignée
     * explicitement</b>, sans passer par une fiche ni par le service Workflow.
     *
     * <p>Sert la consolidation mensuelle (Sprint 3.4), où le code unité est reçu
     * <b>en paramètre</b> de l'appelant plutôt que lu sur une fiche. Motif : un
     * processus dont aucune journée n'a encore été saisie n'a aucune fiche, donc
     * aucun code unité à lire — la portée d'accès disparaîtrait silencieusement
     * au moment précis où il n'y a rien à protéger. Le paramètre la rend
     * applicable dans les deux cas.
     *
     * <p><b>Ce contrôle ne dit rien de la véracité du code unité déclaré.</b> Il
     * répond à « cet utilisateur peut-il agir sur l'unité X ? », pas à « le
     * processus demandé relève-t-il bien de X ? ». Le recoupement de la
     * déclaration contre le code unité figé sur les fiches appartient à
     * {@code ConsolidationService}, et il est indispensable : sans lui, déclarer
     * une unité sur laquelle on est habilité suffirait à lire les lignes d'une
     * autre.
     *
     * @throws AgentNonHabiliteException hors de la portée d'accès ({@code 403})
     * @throws ServiceIdentiteIndisponibleException Identité muet ({@code 503})
     */
    public void exigerHabilitationSurUnite(String codeUnite, String enteteAutorisation) {
        exigerHabilitation(codeUnite, enteteAutorisation);
    }

    /**
     * Traduit les trois issues de la vérification de processus en un contexte ou
     * en un refus.
     *
     * <p>Le {@code switch} est exhaustif sans {@code default} : un quatrième cas
     * ferait échouer la compilation plutôt que de tomber dans une branche
     * fourre-tout, sur le contrôle qui protège un état déjà validé.
     */
    private ProcessusVerifie verifierProcessus(Long idProcessus, String enteteAutorisation) {
        ResultatVerificationProcessus resultat =
                verificationProcessusClient.verifier(idProcessus, enteteAutorisation);

        return switch (resultat) {

            case ProcessusVerifie processus -> processus;

            case ProcessusIntrouvable introuvable -> throw new ProcessusIntrouvableException(
                    "Aucun processus mensuel ne porte l'identifiant " + introuvable.idProcessus()
                            + ". Ouvrez d'abord l'etat du mois pour votre unite.");

            case ServiceWorkflowIndisponible panne -> {
                journal.warn("Ecriture refusee : statut du processus {} indeterminable ({}).",
                        idProcessus, panne.motifTechnique());
                throw new ServiceWorkflowIndisponibleException(
                        "Le service de gestion des etats est momentanement indisponible : "
                                + "il n'a pas pu confirmer que cet etat est encore modifiable. "
                                + "Rien n'a ete enregistre, reessayez dans un instant.");
            }
        };
    }

    /**
     * Traduit les trois issues de la vérification d'habilitation en un feu vert
     * ou en un refus. Aucune trace d'audit n'est publiée ici : elle l'est dans
     * {@code GestionnaireErreursApi}, seul point où tous les refus convergent
     * (doctrine Sprint 1.3) — y compris ceux venus d'ailleurs.
     */
    private void exigerHabilitation(String codeUnite, String enteteAutorisation) {
        ResultatHabilitationUnite resultat =
                habilitationClient.verifier(codeUnite, enteteAutorisation);

        switch (resultat) {

            case AgentHabilite habilite -> journal.debug(
                    "Acces accorde a {} sur l'unite {}.", habilite.login(), habilite.codeUnite());

            case AgentNonHabilite refus -> throw new AgentNonHabiliteException(
                    "Vous n'avez pas de droit sur l'unite " + codeUnite
                            + " : cette saisie ne releve pas de votre perimetre. (" + refus.motif() + ")");

            case ServiceIdentiteIndisponible panne -> {
                journal.warn("Operation refusee : habilitation sur l'unite {} indeterminable ({}).",
                        codeUnite, panne.motifTechnique());
                throw new ServiceIdentiteIndisponibleException(
                        "Le service Identite est momentanement indisponible : vos habilitations "
                                + "n'ont pas pu etre verifiees. Rien n'a ete enregistre, "
                                + "reessayez dans un instant.");
            }
        }
    }

}
