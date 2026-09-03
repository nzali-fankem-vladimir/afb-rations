package cm.afrilandfirstbank.rations.transmission.application;

import cm.afrilandfirstbank.rations.transmission.application.ResultatValidationAccuse.AccuseRecevable;

/**
 * Port sortant : « applique cet accuse au processus, et dis-moi ce que tu en as fait. »
 * Question posee au service Workflow, qui detient {@code processus_mensuel}.
 *
 * <h2>Par l'API, jamais par la base</h2>
 *
 * <p>Le statut d'integration, la reference comptable, la date de traitement et le motif
 * vivent sur {@code processus_mensuel} (migration V4, arbitrage Sprint 5.1), dans la base
 * du service Workflow. Ce service n'a pas de base et n'accede jamais a celle d'un autre
 * (diagramme AR04, guide 5.2 section 10) : une ecriture directe serait une faute
 * d'architecture, et le controle de cartographie du sous-sprint 5.3 la detecterait.
 *
 * <h2>Une seule question, pas deux</h2>
 *
 * <p>L'interface n'offre <b>aucune methode de lecture</b>. Ce n'est pas un oubli : lire
 * l'etat courant puis decider puis ecrire, en deux appels, laisserait entre les deux une
 * fenetre ou un second accuse pourrait s'intercaler — et l'idempotence ne serait qu'une
 * apparence. Le Workflow decide et ecrit dans la meme transaction, et rend le verdict.
 *
 * <h2>Aucun jeton utilisateur — il n'y en a pas</h2>
 *
 * <p>Toute la chaine du Sprint 5.1 relaie le jeton de l'utilisateur final (doctrine
 * Sprint 1.3). <b>Cet appel ne le peut pas</b> : il nait d'un message Kafka, arrive
 * plusieurs minutes ou plusieurs heures apres la cloture, et aucun utilisateur n'est
 * derriere lui. Le realm ne porte par ailleurs aucun compte de service
 * ({@code serviceAccountsEnabled: false}) : il n'existe aujourd'hui aucune identite
 * machine a presenter.
 *
 * <p>L'implementation presente donc un <b>secret partage</b> en en-tete, dispositif
 * provisoire arbitre au Sprint 5.2, a remplacer par un compte de service le jour ou la
 * DSI en ouvre un (voir {@code docs/points-en-attente.md} et
 * {@code docs/dispositifs_provisoires.md}). Aucun parametre de jeton ne figure donc dans
 * cette signature.
 */
public interface StatutIntegrationClient {

    /**
     * @param accuse un accuse deja controle : identifiant present, statut connu, motif
     *        present s'il s'agit d'un rejet
     * @return l'une des six issues, jamais {@code null}
     */
    ResultatMiseAJourIntegration mettreAJour(AccuseRecevable accuse);

}
