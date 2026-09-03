package cm.afrilandfirstbank.rations.transmission.application;

import cm.afrilandfirstbank.rations.transmission.application.ResultatPublication.Publiee;
import cm.afrilandfirstbank.rations.transmission.domaine.EtatValideEvent;

/**
 * Les deux issues acceptables d'une demande de transmission.
 *
 * <h2>Deux succes, pas un succes et une erreur</h2>
 *
 * <p>Au Sprint 5.1, il n'y avait qu'une forme : soit l'evenement partait, soit une
 * exception disait pourquoi. Le Sprint 5.3 en ajoute une seconde, et c'est une exigence du
 * guide : <b>une seconde demande n'est pas forcement une anomalie</b>. Un rejeu legitime
 * existe — reprise apres incident, double declenchement, appel repete — et lui opposer une
 * erreur technique ferait croire a une panne, ou pousserait a reessayer.
 *
 * <p>Du point de vue de l'appelant comme de celui de la comptabilite, les deux issues
 * disent la meme chose : <b>l'etat est a la comptabilite, une fois et une seule</b>. Elles
 * partagent donc le meme {@code 200}, et un champ les distingue — l'idiome du Sprint 5.2,
 * ou {@code APPLIQUE} et {@code DEJA_APPLIQUE} cohabitent de la meme facon.
 *
 * <p>Les vrais refus restent des exceptions, traduites en codes HTTP par
 * {@code GestionnaireErreursApi}.
 *
 * <p>Type scelle : le {@code switch} qui construit la reponse est exhaustif.
 */
public sealed interface ResultatTransmission {

    /**
     * L'evenement vient d'etre publie et le broker l'a accuse.
     *
     * @param charge la charge effectivement publiee, telle quelle
     * @param accuse la position du message sur le broker
     */
    record Transmise(EtatValideEvent charge, Publiee accuse) implements ResultatTransmission {
    }

    /**
     * L'etat avait deja ete transmis : le verrou de RG-13 a refuse, <b>rien n'est
     * reparti</b>.
     *
     * <p>Aucune position sur le broker n'accompagne cette issue : le message d'origine
     * existe, mais ce service ne le connait pas — il ne l'a pas publie. Le journal d'audit
     * de la premiere transmission, lui, en porte le topic, la partition et l'offset.
     *
     * @param idProcessus l'etat concerne
     * @param message ce que le verrou a repondu : date de la premiere transmission et
     *        suite comptable, de quoi juger s'il s'agit d'un rejeu banal ou d'une anomalie
     */
    record DejaTransmise(Long idProcessus, String message) implements ResultatTransmission {
    }

}
