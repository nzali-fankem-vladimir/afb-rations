package cm.afrilandfirstbank.rations.workflow.domaine;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Arbitre ce qu'il faut faire d'un accuse comptable recu pour un etat donne (Sprint 5.2).
 *
 * <h2>Deux etats ouverts, deux etats definitifs — toute la regle tient la</h2>
 *
 * <table>
 *   <caption>Table des transitions du statut d'integration</caption>
 *   <tr><th>Statut courant</th><th>Accuse identique en tout point</th><th>Accuse different</th></tr>
 *   <tr><td>{@code NULL}, etat <b>non transmis</b></td><td colspan="2">{@link Decision.NonTransmis}</td></tr>
 *   <tr><td>{@code NULL}, etat transmis</td><td>{@link Decision.DejaApplique}</td><td>{@link Decision.Appliquer}</td></tr>
 *   <tr><td>{@link StatutIntegrationEnum#EN_ATTENTE}</td><td>{@link Decision.DejaApplique}</td><td>{@link Decision.Appliquer}</td></tr>
 *   <tr><td>{@link StatutIntegrationEnum#INTEGRE}</td><td>{@link Decision.DejaApplique}</td><td>{@link Decision.Contradiction}</td></tr>
 *   <tr><td>{@link StatutIntegrationEnum#REJETE}</td><td>{@link Decision.DejaApplique}</td><td>{@link Decision.Contradiction}</td></tr>
 * </table>
 *
 * <h2>Les quatre principes que cette table applique</h2>
 *
 * <ol>
 *   <li><b>{@code INTEGRE} et {@code REJETE} sont definitifs.</b> Seuls {@code NULL} et
 *       {@code EN_ATTENTE} sont ouverts a l'ecriture. {@code EN_ATTENTE} l'est parce que
 *       c'est le module lui-meme qui l'a pose a la publication (Sprint 5.1), sans
 *       reference ni date : le premier accuse venu de la comptabilite l'enrichit, il ne le
 *       contredit pas.</li>
 *   <li><b>Aucune regression.</b> Depuis {@code INTEGRE} ou {@code REJETE}, un accuse
 *       {@code EN_ATTENTE} differe donc est refuse. C'est ce qui rend un rejeu de topic
 *       inoffensif <b>meme dans le desordre</b> — et l'ordre est ici une supposition, la
 *       cle de partition des accuses etant posee par un producteur que l'equipe ne
 *       controle pas (docs/points-en-attente.md).</li>
 *   <li><b>Le rejeu a l'identique n'est pas une contradiction.</b> Meme statut, meme
 *       reference, meme date, meme motif : rien n'est ecrit, rien n'est trace, et le
 *       resultat final est exactement celui d'une reception unique. C'est l'idempotence,
 *       et elle vit ici parce que c'est ici que se trouve la seule source de verite.</li>
 *   <li><b>Meme statut, contenu different, sur un etat definitif = contradiction.</b> Deux
 *       {@code INTEGRE} portant deux references comptables differentes ne sont pas le meme
 *       accuse. Le module n'a aucun moyen de savoir lequel dit vrai : il <b>refuse et
 *       signale, il n'arbitre jamais</b> — meme doctrine qu'{@code INCOHERENCE_GRILLE} au
 *       Sprint 2.4. Ecraser en silence ferait passer un etat paye pour rejete sans que
 *       personne ne le voie.</li>
 * </ol>
 *
 * <h2>Un etat jamais transmis n'accuse rien</h2>
 *
 * <p>{@link Decision.NonTransmis} precede toute autre consideration : la comptabilite ne
 * peut pas avoir traite un etat qu'on ne lui a jamais envoye. Le drapeau
 * {@code transmis_comptabilite} n'est <b>jamais</b> pose a cette occasion — il ferait
 * croire a un envoi qui n'a pas eu lieu, et RG-13 refuserait ensuite le vrai comme un
 * doublon, laissant l'etat impaye a jamais. La contrainte
 * {@code ck_processus_integration_apres_transmission} refuserait de toute facon
 * l'ecriture.
 *
 * <h2>Classe d'arbitrage, sans effet de bord</h2>
 *
 * <p>Elle <b>decide</b>, elle n'ecrit pas — meme partage qu'entre
 * {@link TransitionProcessus} et {@link ProcessusMensuel}. L'ecriture appartient a
 * l'entite, sous la garde de cette decision, et la table ci-dessus se relit sans avoir a
 * demeler du code de persistance.
 */
public final class TransitionIntegration {

    private TransitionIntegration() {
        // classe d'arbitrage, non instanciable
    }

    /**
     * Ce qu'il faut faire de l'accuse. Type scelle : le {@code switch} qui l'applique est
     * exhaustif, et une cinquieme issue ajoutee plus tard ferait echouer la compilation au
     * lieu de tomber dans une branche par defaut.
     */
    public sealed interface Decision {

        /** Le statut d'integration doit etre ecrit sur le processus. */
        record Appliquer() implements Decision {
        }

        /** Le processus porte deja exactement cet accuse : rien a ecrire, rien a tracer. */
        record DejaApplique() implements Decision {
        }

        /** L'accuse contredit un statut deja recu. Rien n'est ecrit. */
        record Contradiction(String message) implements Decision {
        }

        /** L'etat n'a jamais ete transmis : la comptabilite ne peut pas l'avoir traite. */
        record NonTransmis(String message) implements Decision {
        }

    }

    /**
     * Arbitre l'accuse <b>et l'inscrit</b> sur le processus quand la table l'autorise.
     *
     * <p><b>Un seul point d'entree, un seul geste.</b> Arbitrer d'un cote et ecrire de
     * l'autre laisserait entre les deux une fenetre ou l'etat du processus pourrait
     * changer : la decision porterait sur un etat, l'ecriture sur un autre, et
     * l'idempotence ne serait qu'une apparence. Le mutateur de {@link ProcessusMensuel}
     * etant en visibilite paquet, aucun service applicatif ne peut d'ailleurs ecrire sans
     * passer par ici — le compilateur le garantit.
     *
     * @return la decision prise, que l'appelant traduit en reponse. Une ecriture n'a eu
     *         lieu que dans le cas {@link Decision.Appliquer}
     */
    public static Decision appliquerAccuse(ProcessusMensuel processus,
            StatutIntegrationEnum statutRecu,
            String referenceRecue,
            LocalDateTime dateRecue,
            String motifRecu) {

        Decision decision =
                arbitrer(processus, statutRecu, referenceRecue, dateRecue, motifRecu);

        if (decision instanceof Decision.Appliquer) {
            processus.appliquerAccuseComptable(statutRecu, referenceRecue, dateRecue, motifRecu);
        }
        return decision;
    }

    /**
     * Arbitre l'accuse au vu de l'etat courant du processus, <b>sans rien ecrire</b>.
     *
     * @param processus l'etat concerne, tel qu'il est en base
     * @param statutRecu statut porte par l'accuse, jamais {@code null} — il a ete controle
     *        en amont par le service Transmission
     * @param referenceRecue reference comptable portee par l'accuse, eventuellement nulle
     * @param dateRecue date de traitement portee par l'accuse, eventuellement nulle
     * @param motifRecu motif portee par l'accuse, non nul quand le statut vaut
     *        {@link StatutIntegrationEnum#REJETE}
     */
    public static Decision arbitrer(ProcessusMensuel processus,
            StatutIntegrationEnum statutRecu,
            String referenceRecue,
            LocalDateTime dateRecue,
            String motifRecu) {

        if (!processus.isTransmisComptabilite()) {
            return new Decision.NonTransmis(
                    "L'etat " + processus.getId() + " de l'unite " + processus.getCodeUnite()
                            + " (" + processus.libellePeriode()
                            + ") n'a jamais ete transmis a la comptabilite : elle ne peut pas en "
                            + "accuser reception. Incoherence a lever d'un cote ou de l'autre ; le "
                            + "drapeau de transmission n'est pas pose a cette occasion, faute de "
                            + "quoi RG-13 refuserait ensuite la vraie transmission.");
        }

        boolean identique = identique(processus, statutRecu, referenceRecue, dateRecue, motifRecu);
        if (identique) {
            return new Decision.DejaApplique();
        }

        StatutIntegrationEnum courant = processus.getStatutIntegration();
        if (estDefinitif(courant)) {
            return new Decision.Contradiction(
                    "L'etat " + processus.getId() + " porte deja le statut d'integration "
                            + courant + " (reference " + processus.getReferenceComptable()
                            + ", date " + processus.getDateTraitement() + "), et l'accuse recu "
                            + "porte " + statutRecu + " (reference " + referenceRecue + ", date "
                            + dateRecue + "). Un statut d'integration definitif ne se remplace "
                            + "pas : le module ne peut pas savoir lequel des deux accuses dit "
                            + "vrai, il refuse et signale. A lever avec la comptabilite.");
        }

        return new Decision.Appliquer();
    }

    /**
     * {@code INTEGRE} et {@code REJETE} sont les deux verdicts definitifs de la
     * comptabilite. {@code EN_ATTENTE} ne l'est pas : c'est le module qui l'a pose a la
     * publication, comme un constat provisoire. {@code null} ne l'est pas davantage.
     */
    private static boolean estDefinitif(StatutIntegrationEnum statut) {
        return statut == StatutIntegrationEnum.INTEGRE || statut == StatutIntegrationEnum.REJETE;
    }

    /**
     * Deux accuses sont le meme s'ils s'accordent sur <b>les quatre</b> valeurs.
     *
     * <p>Comparer le seul statut suffirait a rendre le traitement idempotent au sens
     * etroit, mais laisserait passer en silence un second accuse portant une reference
     * comptable differente : le suivi garderait la premiere, personne ne verrait la
     * seconde, et le rapprochement comptable serait faux sans qu'aucune trace ne l'indique.
     */
    private static boolean identique(ProcessusMensuel processus,
            StatutIntegrationEnum statutRecu,
            String referenceRecue,
            LocalDateTime dateRecue,
            String motifRecu) {

        return processus.getStatutIntegration() == statutRecu
                && Objects.equals(processus.getReferenceComptable(), referenceRecue)
                && Objects.equals(processus.getDateTraitement(), dateRecue)
                && Objects.equals(processus.getMotifIntegration(), motifRecu);
    }

}
