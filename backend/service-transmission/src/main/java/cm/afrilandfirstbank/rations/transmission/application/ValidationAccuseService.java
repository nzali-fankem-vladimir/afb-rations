package cm.afrilandfirstbank.rations.transmission.application;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.transmission.application.ResultatValidationAccuse.AccuseInvalide;
import cm.afrilandfirstbank.rations.transmission.application.ResultatValidationAccuse.AccuseRecevable;
import cm.afrilandfirstbank.rations.transmission.domaine.AccuseComptableEvent;
import cm.afrilandfirstbank.rations.transmission.domaine.CodeAnomalieAccuseEnum;
import cm.afrilandfirstbank.rations.transmission.domaine.StatutIntegrationEnum;

/**
 * Controle un accuse recu sur {@code rations.etat.accuse} avant tout traitement
 * (guide 5.2 etape 2).
 *
 * <h2>Ce qu'il verifie, et ce qu'il ne peut pas verifier</h2>
 *
 * <p>Il verifie ce qui se lit <b>dans le message seul</b> : identifiant present, statut
 * appartenant a l'enumeration du contrat, motif present quand le statut vaut
 * {@code REJETE}, date lisible si elle est presente.
 *
 * <p>Il ne verifie <b>pas</b> que le processus existe, qu'il a ete transmis, ni que
 * l'accuse ne contredit pas un statut deja recu : ces trois verdicts demandent l'etat
 * courant du processus, qui vit dans la base du service Workflow. Ce service n'a pas de
 * base, et il n'y accede pas (AR04). Ils sont donc rendus plus loin, par l'API du
 * Workflow.
 *
 * <h2>Un message qui echoue ici ne fait jamais tomber le consommateur</h2>
 *
 * <p>Aucune exception ne sort de cette classe : elle <b>rend</b> un refus au lieu de le
 * lever. Un accuse malforme qui remonterait en exception jusqu'au conteneur Kafka serait
 * rejoue indefiniment et bloquerait la consommation de tous les suivants, accuses
 * valides compris (guide 5.2 section 10).
 *
 * <h2>Toutes les anomalies, pas seulement la premiere</h2>
 *
 * <p>Le controle ne s'arrete pas au premier defaut. Un message a la fois sans identifiant
 * et de statut inconnu est vraisemblablement destine a un autre systeme, et non le fruit
 * d'une faute de frappe : le journal doit permettre de le voir d'un coup d'oeil, pas au
 * fil de trois rejeux successifs.
 */
@Service
public class ValidationAccuseService {

    /**
     * Controle un accuse deja deserialise.
     *
     * @param accuse jamais {@code null} — un message illisible n'arrive pas jusqu'ici, il
     *        est traite comme tel par le consommateur, qui seul detient le contenu brut
     * @return {@link AccuseRecevable} avec les valeurs converties, ou
     *         {@link AccuseInvalide} avec toutes les anomalies relevees
     */
    public ResultatValidationAccuse valider(AccuseComptableEvent accuse) {
        List<AnomalieAccuse> anomalies = new ArrayList<>();

        controlerIdentifiant(accuse, anomalies);
        StatutIntegrationEnum statut = statutOuAnomalie(accuse, anomalies);
        controlerMotif(accuse, statut, anomalies);
        OffsetDateTime dateTraitement = dateOuAnomalie(accuse, anomalies);

        if (!anomalies.isEmpty()) {
            return new AccuseInvalide(anomalies);
        }

        return new AccuseRecevable(
                accuse.idProcessus(),
                statut,
                ebarbe(accuse.referenceComptable()),
                dateTraitement,
                ebarbe(accuse.motif()));
    }

    // --- Controles ------------------------------------------------------------------

    /**
     * L'identifiant du processus est la <b>seule</b> cle de rapprochement avec
     * {@code processus_mensuel}. Sans lui, l'accuse ne designe rien et rien ne
     * permettrait de le rattacher a un etat.
     */
    private void controlerIdentifiant(AccuseComptableEvent accuse,
            List<AnomalieAccuse> anomalies) {

        if (accuse.idProcessus() == null) {
            anomalies.add(AnomalieAccuse.de(CodeAnomalieAccuseEnum.IDENTIFIANT_ABSENT,
                    "L'accuse ne porte aucun idProcessus : il ne designe aucun etat, et rien "
                            + "ne permet de le rattacher a un processus (contrat section 7.2)."));
        }
    }

    /**
     * Le statut est converti ici, et seulement si sa valeur appartient au contrat.
     *
     * <p><b>Un statut inconnu n'est pas un message illisible.</b> Le distinguer coute une
     * ligne et change tout pour qui lit le journal : {@code STATUT_INCONNU} nomme le champ
     * fautif et sa valeur, la ou {@code ACCUSE ILLISIBLE} laisserait chercher. C'est la
     * lecon du defaut 2 du Sprint 5.1, ou le comportement etait juste mais le diagnostic
     * perdu.
     */
    private StatutIntegrationEnum statutOuAnomalie(AccuseComptableEvent accuse,
            List<AnomalieAccuse> anomalies) {

        String valeur = ebarbe(accuse.statutIntegration());
        if (valeur == null) {
            anomalies.add(AnomalieAccuse.de(CodeAnomalieAccuseEnum.STATUT_ABSENT,
                    "L'accuse ne porte aucun statutIntegration : il ne dit pas ce qu'il "
                            + "accuse (contrat section 7.2)."));
            return null;
        }

        for (StatutIntegrationEnum connu : StatutIntegrationEnum.values()) {
            if (connu.name().equals(valeur)) {
                return connu;
            }
        }

        anomalies.add(AnomalieAccuse.de(CodeAnomalieAccuseEnum.STATUT_INCONNU,
                "statutIntegration vaut " + entreGuillemets(valeur) + ", hors des valeurs du "
                        + "contrat section 7.2 : "
                        + Arrays.toString(StatutIntegrationEnum.values()) + "."));
        return null;
    }

    /**
     * Contrat section 7.2 : « en cas de rejet, un motif accompagne l'accuse ».
     *
     * <p>Une suite d'espaces n'est pas un motif — meme discipline qu'au Sprint 4.4 pour le
     * motif de retour, ou {@code "   "} est un champ present et un motif absent. Sans ce
     * controle, le suivi (US-15) afficherait un etat rejete sans dire pourquoi, et l'unite
     * resterait devant un refus muet, sans savoir quoi corriger.
     */
    private void controlerMotif(AccuseComptableEvent accuse, StatutIntegrationEnum statut,
            List<AnomalieAccuse> anomalies) {

        if (statut != StatutIntegrationEnum.REJETE) {
            return;
        }
        if (ebarbe(accuse.motif()) == null) {
            anomalies.add(AnomalieAccuse.de(CodeAnomalieAccuseEnum.MOTIF_REJET_ABSENT,
                    "L'accuse de l'etat " + accuse.idProcessus() + " porte le statut REJETE sans "
                            + "motif : le contrat section 7.2 l'exige, et le suivi doit pouvoir "
                            + "dire pourquoi l'etat a ete refuse (US-15)."));
        }
    }

    /**
     * La date est facultative, mais une date presente doit etre lisible.
     *
     * <p><b>Absente</b> : acceptee, la colonne {@code date_traitement} reste nulle. Un
     * manque n'est pas une contradiction, et refuser l'accuse entier pour cela perdrait le
     * statut d'integration, qui est l'information utile.
     *
     * <p><b>Presente mais sans decalage horaire</b> : refusee. Le contrat section 1.1 exige
     * de l'ISO 8601, et {@code "2026-08-18T02:15:00"} est <b>ambigu</b> — lui supposer un
     * fuseau reviendrait a inventer une information sur un traitement de paiement. Le
     * module refuse et signale, il n'arbitre jamais (doctrine Sprint 2.4).
     */
    private OffsetDateTime dateOuAnomalie(AccuseComptableEvent accuse,
            List<AnomalieAccuse> anomalies) {

        String valeur = ebarbe(accuse.dateTraitement());
        if (valeur == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(valeur);
        } catch (DateTimeParseException illisible) {
            anomalies.add(AnomalieAccuse.de(CodeAnomalieAccuseEnum.DATE_TRAITEMENT_ILLISIBLE,
                    "dateTraitement vaut " + entreGuillemets(valeur) + ", qui n'est pas une date "
                            + "ISO 8601 avec decalage horaire (contrat section 1.1). Exemple "
                            + "attendu : 2026-08-18T02:15:00Z."));
            return null;
        }
    }

    // --- Outils ---------------------------------------------------------------------

    /** Ebarbe et ramene une chaine vide a {@code null} : un champ present et vide est absent. */
    private static String ebarbe(String valeur) {
        if (valeur == null) {
            return null;
        }
        String ebarbee = valeur.trim();
        return ebarbee.isEmpty() ? null : ebarbee;
    }

    private static String entreGuillemets(String valeur) {
        return "\"" + valeur + "\"";
    }

}
