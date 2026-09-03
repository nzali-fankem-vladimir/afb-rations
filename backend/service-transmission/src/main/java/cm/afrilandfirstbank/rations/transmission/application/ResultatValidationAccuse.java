package cm.afrilandfirstbank.rations.transmission.application;

import java.time.OffsetDateTime;
import java.util.List;

import cm.afrilandfirstbank.rations.transmission.domaine.CodeAnomalieAccuseEnum;
import cm.afrilandfirstbank.rations.transmission.domaine.StatutIntegrationEnum;

/**
 * Les deux issues du controle d'un accuse recu : il est exploitable, ou il ne l'est pas.
 *
 * <h2>La conversion a lieu ici, et nulle part ailleurs</h2>
 *
 * <p>{@link AccuseRecevable} porte le statut en {@link StatutIntegrationEnum} et la date
 * en {@link OffsetDateTime}, la ou l'accuse recu les portait en chaine. C'est le seul
 * endroit du service ou cette conversion se fait, et elle n'aboutit que si le controle
 * est passe : <b>aucun code en aval ne peut voir un statut non verifie</b>, le
 * compilateur s'en charge.
 *
 * <p>Type scelle, sans champ booleen : un refus n'a pas de statut a lire a moitie, et le
 * {@code switch} qui traite les deux issues est exhaustif. Meme discipline qu'au Sprint
 * 5.1 pour {@code ResultatConstruction} et {@code ResultatDemandeTransmission}.
 */
public sealed interface ResultatValidationAccuse {

    /**
     * L'accuse tient debout : identifiant present, statut connu, motif present s'il
     * s'agit d'un rejet, date lisible si elle est la.
     *
     * <p>Cela ne dit <b>rien</b> de son applicabilite : le processus peut etre inconnu,
     * jamais transmis, ou deja porteur d'un statut que celui-ci contredirait. Ces trois
     * verdicts demandent l'etat courant, que ce service n'a pas — il n'a pas de base — et
     * ils sont rendus plus loin, par le service Workflow.
     *
     * @param dateTraitement nulle si l'accuse n'en portait pas ; la colonne reste alors
     *        nulle, un manque n'etant pas une contradiction
     */
    record AccuseRecevable(
            Long idProcessus,
            StatutIntegrationEnum statutIntegration,
            String referenceComptable,
            OffsetDateTime dateTraitement,
            String motif) implements ResultatValidationAccuse {
    }

    /**
     * L'accuse ne peut pas etre exploite. Refus <b>definitif</b> : aucune de ces
     * anomalies ne s'arrange en reessayant, le message est donc trace puis avance.
     *
     * <p>Les anomalies sont <b>toutes</b> rapportees, pas seulement la premiere : un
     * accuse a la fois sans identifiant et de statut inconnu est plus vraisemblablement
     * un message destine a un autre systeme qu'une faute de frappe, et le journal doit
     * permettre de le voir d'un coup d'oeil.
     */
    record AccuseInvalide(List<AnomalieAccuse> anomalies) implements ResultatValidationAccuse {

        public AccuseInvalide {
            if (anomalies == null || anomalies.isEmpty()) {
                throw new IllegalArgumentException(
                        "Un accuse refuse porte toujours au moins une anomalie : sans elle, le "
                                + "refus serait inexplicable a la relecture.");
            }
            anomalies = List.copyOf(anomalies);
        }

        /** Les codes seuls, pour les rapprochements et les tests. */
        public List<CodeAnomalieAccuseEnum> codes() {
            return anomalies.stream().map(AnomalieAccuse::code).toList();
        }

    }

}
