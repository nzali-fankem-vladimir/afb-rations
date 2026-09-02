package cm.afrilandfirstbank.rations.transmission.application;

import cm.afrilandfirstbank.rations.transmission.application.ResultatPublication.Publiee;
import cm.afrilandfirstbank.rations.transmission.domaine.EtatValideEvent;

/**
 * Ce qui est parti, et ou.
 *
 * <p>Rendu au service Workflow, qui en tire deux choses : la certitude que l'evenement
 * est bien sur le broker — c'est elle qui autorise l'ecriture du drapeau
 * {@code transmis_comptabilite} de RG-13 — et de quoi le retrouver plus tard.
 *
 * <p><b>Une seule forme, pas de variante d'echec.</b> Les refus sont des exceptions,
 * traduites en codes HTTP par {@code GestionnaireErreursApi} : l'appelant distingue alors
 * « transmis » de « non transmis » sur le statut de la reponse, sans avoir a inspecter un
 * champ. Un objet de resultat portant un booleen {@code succes} laisserait le drapeau de
 * RG-13 dependre d'une lecture que l'on peut oublier de faire.
 *
 * @param charge la charge effectivement publiee, telle quelle
 * @param accuse la position du message sur le broker
 */
public record ResultatTransmission(EtatValideEvent charge, Publiee accuse) {
}
