package cm.afrilandfirstbank.rations.transmission.api.dto;

import cm.afrilandfirstbank.rations.transmission.application.ResultatTransmission;

/**
 * Corps de {@code POST /transmission/processus/{id}} en cas de succes.
 *
 * <p>Il ne rend pas la charge : le service Workflow n'a rien a en faire, et la renvoyer
 * ferait traverser le reseau une seconde fois a un detail qui peut compter des centaines
 * de lignes. Il rend ce dont l'appelant a besoin — <b>la certitude que l'evenement est
 * parti</b>, qui autorise l'ecriture du drapeau {@code transmis_comptabilite} (RG-13) —
 * et de quoi le retrouver sur le broker.
 *
 * @param idProcessus l'etat transmis
 * @param topic topic effectivement servi. Le nom de developpement du Sprint 0.5 n'est pas
 *        forcement celui d'un environnement partage (point DSI D-07) : le rendre evite
 *        d'avoir a le deviner en lisant deux configurations
 * @param partition partition retenue par la cle de partition, qui est l'identifiant du
 *        processus
 * @param offset position du message. Avec la partition, elle designe l'evenement de
 *        maniere unique et permet de le relire tant que la retention le garde
 * @param nombreLignes lignes effectivement parties : un temoin immediat, lisible dans le
 *        journal du service Workflow sans avoir a consulter le topic
 * @param montantTotal montant effectivement parti, meme usage
 */
public record TransmissionResponse(
        Long idProcessus,
        String topic,
        int partition,
        long offset,
        int nombreLignes,
        long montantTotal) {

    public static TransmissionResponse de(ResultatTransmission resultat) {
        return new TransmissionResponse(
                resultat.charge().idProcessus(),
                resultat.accuse().topic(),
                resultat.accuse().partition(),
                resultat.accuse().offset(),
                resultat.charge().lignes().size(),
                resultat.charge().montantTotal());
    }

}
