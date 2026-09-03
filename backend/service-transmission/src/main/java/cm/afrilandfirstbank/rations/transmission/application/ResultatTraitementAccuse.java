package cm.afrilandfirstbank.rations.transmission.application;

import java.util.List;

import cm.afrilandfirstbank.rations.transmission.domaine.StatutIntegrationEnum;

/**
 * Ce qu'il advient d'un accuse recu, du point de vue du consommateur.
 *
 * <h2>La distinction qui commande tout : definitif ou temporaire</h2>
 *
 * <p>Le consommateur ne prend qu'une seule decision a la lecture de ce type : <b>faut-il
 * rejouer ce message, ou le laisser avancer ?</b>
 *
 * <ul>
 *   <li>{@link Applique}, {@link DejaApplique}, {@link RefusDefinitif} : le message
 *       avance. Aucun rejeu ne changerait quoi que ce soit, et s'obstiner bloquerait la
 *       partition — donc tous les accuses suivants, y compris les valides.</li>
 *   <li>{@link EchecTemporaire} : le consommateur <b>leve</b>, et le gestionnaire
 *       d'erreurs rejoue avec une pause bornee.</li>
 * </ul>
 *
 * <p>Type scelle plutot qu'un booleen {@code rejouable} : un booleen se lit juste une fois
 * sur deux, et il n'aurait rien dit du reste — quel statut a ete pose, quelles anomalies
 * relevees. Meme discipline qu'au Sprint 4.3 pour la decision d'aiguillage, nommee d'apres
 * sa consequence.
 */
public sealed interface ResultatTraitementAccuse {

    /** L'accuse a ete applique au processus : le statut d'integration a change. */
    record Applique(Long idProcessus, StatutIntegrationEnum statutApplique)
            implements ResultatTraitementAccuse {
    }

    /**
     * Le processus portait deja exactement cet accuse. Rien n'a ete ecrit, <b>ni en base
     * ni en audit</b> : c'est l'idempotence, et une seconde trace ferait croire a un
     * second traitement comptable.
     */
    record DejaApplique(Long idProcessus, StatutIntegrationEnum statutCourant)
            implements ResultatTraitementAccuse {
    }

    /**
     * L'accuse ne sera jamais applique : illisible, sans identifiant, de statut inconnu,
     * rejet sans motif, processus inconnu, jamais transmis, ou contradictoire avec un
     * statut deja recu.
     *
     * <p>Trace en journal sous un prefixe repérable et en audit, puis le message avance.
     *
     * @param idProcessus nul quand l'accuse n'en portait pas — c'est precisement l'une
     *        des anomalies possibles
     */
    record RefusDefinitif(Long idProcessus, List<AnomalieAccuse> anomalies)
            implements ResultatTraitementAccuse {

        public RefusDefinitif {
            if (anomalies == null || anomalies.isEmpty()) {
                throw new IllegalArgumentException(
                        "Un refus definitif porte toujours au moins une anomalie : sans elle, le "
                                + "refus serait inexplicable a la relecture.");
            }
            anomalies = List.copyOf(anomalies);
        }

    }

    /**
     * Le service Workflow n'a pas repondu. L'accuse est peut-etre parfaitement valide :
     * on ne le saura qu'en reessayant.
     */
    record EchecTemporaire(Long idProcessus, String motifTechnique)
            implements ResultatTraitementAccuse {
    }

}
