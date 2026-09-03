package cm.afrilandfirstbank.rations.transmission.application;

import cm.afrilandfirstbank.rations.transmission.domaine.CodeAnomalieAccuseEnum;

/**
 * Une raison, et une seule, pour laquelle un accuse n'est pas applique.
 *
 * <p>Le couple {@code (code, message)} reprend la forme d'{@code AnomalieCharge} (Sprint
 * 5.1) et du champ {@code manques} (Sprint 4.2) : le <b>code</b> est ce qu'un programme
 * lit, le <b>message</b> ce qu'un humain lit dans le journal.
 *
 * <p><b>Le message ne reproduit jamais le contenu brut du message Kafka.</b> C'est le
 * journal du consommateur qui en garde une trace tronquee, a un seul endroit et sous un
 * prefixe repérable ; le recopier dans chaque anomalie le multiplierait sans rien
 * apprendre.
 */
public record AnomalieAccuse(CodeAnomalieAccuseEnum code, String message) {

    public AnomalieAccuse {
        if (code == null || message == null || message.isBlank()) {
            throw new IllegalArgumentException(
                    "Une anomalie d'accuse porte toujours un code et un message : sans eux, le "
                            + "refus serait inexplicable a la relecture.");
        }
    }

    public static AnomalieAccuse de(CodeAnomalieAccuseEnum code, String message) {
        return new AnomalieAccuse(code, message);
    }

}
