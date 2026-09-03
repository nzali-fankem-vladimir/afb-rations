package cm.afrilandfirstbank.rations.transmission.api.dto;

import cm.afrilandfirstbank.rations.transmission.application.ResultatTransmission;

/**
 * Corps de {@code POST /transmission/processus/{id}} en cas de succes.
 *
 * <h2>Deux succes, distingues par un champ et non par un code HTTP</h2>
 *
 * <p>{@code TRANSMIS} : l'evenement vient de partir. {@code DEJA_TRANSMIS} : le verrou de
 * RG-13 a refuse une seconde publication, et <b>rien n'est reparti</b>. Les deux valent
 * {@code 200}, parce que les deux disent la meme chose a l'appelant — l'etat est a la
 * comptabilite, une fois et une seule. Un code d'erreur sur le second ferait croire a une
 * panne et pousserait a reessayer, ce qui est exactement le geste a decourager.
 *
 * <p>C'est l'idiome pose au Sprint 5.2 pour {@code APPLIQUE} / {@code DEJA_APPLIQUE}.
 *
 * <p>Il ne rend pas la charge : le service Workflow n'a rien a en faire, et la renvoyer
 * ferait traverser le reseau une seconde fois a un detail qui peut compter des centaines
 * de lignes.
 *
 * @param idProcessus l'etat concerne
 * @param resultat {@code TRANSMIS} ou {@code DEJA_TRANSMIS}. <b>C'est le champ que
 *        l'appelant lit pour savoir s'il vient de se passer quelque chose</b>
 * @param message ce qui s'est passe, en clair. Nul sur une transmission ordinaire ;
 *        renseigne sur un refus, ou il porte la date de la premiere transmission
 * @param topic topic effectivement servi, nul sur un refus. Le nom de developpement du
 *        Sprint 0.5 n'est pas forcement celui d'un environnement partage (point DSI D-07)
 * @param partition partition retenue par la cle de partition, nulle sur un refus
 * @param offset position du message, nulle sur un refus. Avec la partition, elle designe
 *        l'evenement de maniere unique et permet de le relire tant que la retention le
 *        garde
 * @param nombreLignes lignes effectivement parties, zero sur un refus
 * @param montantTotal montant effectivement parti, zero sur un refus
 */
public record TransmissionResponse(
        Long idProcessus,
        String resultat,
        String message,
        String topic,
        Integer partition,
        Long offset,
        int nombreLignes,
        long montantTotal) {

    /** L'evenement vient d'etre publie et le broker l'a accuse. */
    public static final String TRANSMIS = "TRANSMIS";

    /** Le verrou de RG-13 a refuse : l'etat etait deja parti, rien n'est reparti. */
    public static final String DEJA_TRANSMIS = "DEJA_TRANSMIS";

    /**
     * Le {@code switch} est exhaustif sur {@link ResultatTransmission} : une troisieme
     * issue ajoutee plus tard ferait echouer la compilation ici, plutot que de produire
     * une reponse muette sur ce qui s'est reellement passe.
     */
    public static TransmissionResponse de(ResultatTransmission resultat) {
        return switch (resultat) {

            case ResultatTransmission.Transmise transmise -> new TransmissionResponse(
                    transmise.charge().idProcessus(),
                    TRANSMIS,
                    null,
                    transmise.accuse().topic(),
                    transmise.accuse().partition(),
                    transmise.accuse().offset(),
                    transmise.charge().lignes().size(),
                    transmise.charge().montantTotal());

            case ResultatTransmission.DejaTransmise deja -> new TransmissionResponse(
                    deja.idProcessus(),
                    DEJA_TRANSMIS,
                    deja.message(),
                    null,
                    null,
                    null,
                    0,
                    0L);
        };
    }

}
