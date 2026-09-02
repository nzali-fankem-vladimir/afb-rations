package cm.afrilandfirstbank.rations.transmission.application;

import cm.afrilandfirstbank.rations.transmission.domaine.CodeAnomalieEnum;

/**
 * Une raison, et une seule, pour laquelle une charge ne part pas.
 *
 * <p>Le couple {@code (code, message)} reprend la forme du champ {@code manques} du
 * Sprint 4.2 : le <b>code</b> est ce qu'un programme lit, le <b>message</b> ce qu'un
 * humain lit dans le journal. Les concatener en prose rendrait l'ensemble illisible a
 * l'un comme a l'autre.
 *
 * <p>Le message nomme jusqu'a cinq exemples et annonce le reste en nombre : sur une
 * charge de trois cents lignes toutes fautives, lister les trois cents ne dirait rien
 * de plus que « toutes », et noierait les autres anomalies.
 */
public record AnomalieCharge(CodeAnomalieEnum code, String message) {

    /** Nombre d'exemples nommes avant de passer au denombrement. */
    public static final int EXEMPLES_MAXIMUM = 5;

    public AnomalieCharge {
        if (code == null || message == null || message.isBlank()) {
            throw new IllegalArgumentException(
                    "Une anomalie de charge porte toujours un code et un message : sans eux, "
                            + "le refus de publication serait inexplicable a la relecture.");
        }
    }

}
