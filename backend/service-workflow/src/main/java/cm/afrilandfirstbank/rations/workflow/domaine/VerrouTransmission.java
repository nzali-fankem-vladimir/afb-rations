package cm.afrilandfirstbank.rations.workflow.domaine;

import java.time.LocalDateTime;

/**
 * Arbitre les trois gestes du verrou d'unicite de transmission (RG-13, Sprint 5.3).
 *
 * <h2>Le probleme que ce verrou resout</h2>
 *
 * <p>RG-13 dit qu'un etat valide n'est transmis qu'une seule fois. La consequence d'une
 * violation n'est pas technique : la comptabilite recoit deux fois le meme etat, produit
 * deux jeux d'ecritures, et les memes beneficiaires sont <b>payes deux fois</b>. C'est
 * l'une des erreurs interdites de CLAUDE.md section 15.
 *
 * <p>Quatre chemins y menent, et aucun n'est theorique : le rejeu d'une cloture, un appel
 * manuel de l'endpoint interne de transmission, deux instances du service traitant la meme
 * cloture, une reprise apres l'incident ambigu du Sprint 5.1. Un controle en deux temps —
 * lire le drapeau, puis publier, puis l'ecrire — n'en bloque aucun serieusement : entre la
 * lecture et l'ecriture, une seconde demande passe.
 *
 * <h2>Trois gestes, dans cet ordre, et jamais deux</h2>
 *
 * <pre>
 *   RESERVER   transmis_comptabilite : false -&gt; true, horodatage pose
 *              statut_integration    : reste NUL
 *                  |
 *                  |  publication sur rations.etat.valide
 *                  v
 *   CONFIRMER  statut_integration    : NUL -&gt; EN_ATTENTE
 *
 *   LIBERER    transmis_comptabilite : true -&gt; false, horodatage efface
 *              (uniquement sur la PREUVE qu'aucun evenement n'est parti)
 * </pre>
 *
 * <h2>Les quatre etats lisibles en base, et pourquoi ils comptent</h2>
 *
 * <table>
 *   <caption>Ce que le couple de colonnes dit d'un etat</caption>
 *   <tr><th>transmis</th><th>statut d'integration</th><th>Signification</th></tr>
 *   <tr><td>false</td><td>nul</td><td>jamais transmis — une transmission est possible</td></tr>
 *   <tr><td><b>true</b></td><td><b>nul</b></td><td><b>reserve, publication non confirmee</b></td></tr>
 *   <tr><td>true</td><td>EN_ATTENTE</td><td>publie, la comptabilite n'a pas encore repondu</td></tr>
 *   <tr><td>true</td><td>INTEGRE / REJETE</td><td>la comptabilite a repondu</td></tr>
 * </table>
 *
 * <p>La deuxieme ligne est celle qui interesse la supervision. Elle dure normalement
 * quelques centaines de millisecondes ; elle <b>persiste</b> quand la publication s'est
 * terminee de facon ambigue. C'est {@code date_reservation_transmission} qui separe les
 * deux : une reservation de dix secondes est un etat en transit, une reservation de trois
 * jours est un incident a lever a la main. Sans cet horodatage, l'alerte se noierait dans
 * le trafic normal ou ne se declencherait jamais.
 *
 * <h2>Ou vit l'atomicite, puisqu'elle n'est pas ici</h2>
 *
 * <p>Cette classe <b>arbitre</b>, elle ne serialise rien : elle voit un processus deja
 * charge. La resistance a la concurrence est posee un cran plus bas, par
 * {@code ProcessusMensuelRepository.verrouillerPourTransmission}, qui charge la ligne en
 * {@code SELECT ... FOR UPDATE}. Deux instances qui reservent le meme etat sont alors
 * serialisees par PostgreSQL : la seconde attend le commit de la premiere, puis lit le
 * drapeau <b>deja pose</b> et se voit refuser. Meme partage qu'entre
 * {@link TransitionProcessus} et {@link ProcessusMensuel} : la regle se relit sans demeler
 * du code de persistance.
 */
public final class VerrouTransmission {

    private VerrouTransmission() {
        // classe d'arbitrage, non instanciable
    }

    /**
     * Ce qu'il faut faire d'une demande de reservation. Type scelle : le {@code switch}
     * qui l'applique est exhaustif, et une quatrieme issue ajoutee plus tard ferait
     * echouer la compilation au lieu de tomber dans une branche par defaut.
     */
    public sealed interface Reservation {

        /** Le verrou est pose : l'appelant peut publier, et lui seul. */
        record Reservee(LocalDateTime instant) implements Reservation {
        }

        /**
         * L'etat est deja transmis. <b>Ce n'est pas forcement une anomalie</b> : un rejeu
         * legitime doit recevoir une reponse explicite, pas une erreur qui ferait croire a
         * une panne.
         */
        record DejaTransmise(String message) implements Reservation {
        }

        /** L'etat n'est pas cloture : rien n'a a partir en comptabilite. */
        record EtatNonCloture(String message) implements Reservation {
        }

    }

    /** Ce qu'il faut faire d'une demande de liberation. */
    public sealed interface Liberation {

        /** La reservation est levee : une reprise redevient possible. */
        record Liberee() implements Liberation {
        }

        /**
         * Refus : la comptabilite a deja repondu, donc l'evenement etait bien parti.
         * Liberer ferait republier un etat deja pris en charge.
         */
        record Refusee(String message) implements Liberation {
        }

    }

    /**
     * Reserve la transmission <b>et pose le verrou</b> quand la table l'autorise.
     *
     * <p><b>Un seul point d'entree, un seul geste</b>, comme
     * {@link TransitionIntegration#appliquerAccuse}. Arbitrer d'un cote et ecrire de
     * l'autre laisserait entre les deux une fenetre ou l'etat pourrait changer.
     *
     * @param processus l'etat concerne, <b>charge sous verrou de ligne</b> par l'appelant
     * @param instant horodatage de la reservation
     */
    public static Reservation reserver(ProcessusMensuel processus, LocalDateTime instant) {
        if (processus.getStatut() != StatutEnum.CLOTURE) {
            return new Reservation.EtatNonCloture(
                    "L'etat " + processus.getId() + " de l'unite " + processus.getCodeUnite()
                            + " est au statut " + processus.getStatut() + " : seul un etat CLOTURE "
                            + "part en comptabilite. Aucune reservation n'est posee.");
        }

        if (processus.isTransmisComptabilite()) {
            return new Reservation.DejaTransmise(descriptionEtatDejaTransmis(processus));
        }

        processus.reserverTransmission(instant);
        return new Reservation.Reservee(instant);
    }

    /**
     * Confirme la publication : le statut d'integration passe a
     * {@link StatutIntegrationEnum#EN_ATTENTE}.
     *
     * <p>Sans effet si un accuse comptable est deja arrive — le confirmer alors ferait
     * regresser {@code INTEGRE} en {@code EN_ATTENTE}, ce que {@link TransitionIntegration}
     * refuse par ailleurs a tout accuse.
     */
    public static void confirmer(ProcessusMensuel processus) {
        processus.confirmerTransmission();
    }

    /**
     * Libere une reservation dont l'appelant atteste qu'aucun evenement n'est parti.
     *
     * <p><b>La preuve appartient a l'appelant</b>, et elle est portee par un type, pas par
     * un commentaire : seuls les echecs anterieurs a tout envoi arrivent jusqu'ici. Un
     * echec ambigu laisse la reservation en place — c'est la regle « on ne rejoue jamais
     * une transmission qui a pu partir » du Sprint 5.1, vue de l'autre bout.
     */
    public static Liberation liberer(ProcessusMensuel processus) {
        if (!processus.isTransmisComptabilite()) {
            // Rien a lever : deja libere, ou jamais reserve. Idempotent plutot que fautif,
            // un echec de publication pouvant etre signale deux fois.
            return new Liberation.Liberee();
        }
        if (processus.getStatutIntegration() != null) {
            return new Liberation.Refusee(
                    "L'etat " + processus.getId() + " porte le statut d'integration "
                            + processus.getStatutIntegration() + " : la comptabilite a repondu, "
                            + "donc l'evenement etait bien parti. La reservation n'est pas levee.");
        }
        processus.libererTransmission();
        return new Liberation.Liberee();
    }

    /**
     * Le message d'un refus de seconde transmission. Il nomme le dossier, l'instant de la
     * premiere transmission et la suite qu'elle a recue : une personne doit pouvoir juger
     * en le lisant si elle a affaire a un rejeu banal ou a une anomalie.
     */
    private static String descriptionEtatDejaTransmis(ProcessusMensuel processus) {
        String suite = processus.getStatutIntegration() == null
                ? "la publication n'a jamais ete confirmee (issue incertaine)"
                : "suite comptable : " + processus.getStatutIntegration();

        return "L'etat " + processus.getId() + " de l'unite " + processus.getCodeUnite() + " ("
                + processus.getMoisPaiement() + "/" + processus.getAnneePaiement()
                + ") a deja ete transmis a la comptabilite le "
                + processus.getDateReservationTransmission() + " ; " + suite
                + ". Aucune seconde publication n'a lieu : elle produirait un second jeu "
                + "d'ecritures pour les memes beneficiaires (RG-13).";
    }

}
