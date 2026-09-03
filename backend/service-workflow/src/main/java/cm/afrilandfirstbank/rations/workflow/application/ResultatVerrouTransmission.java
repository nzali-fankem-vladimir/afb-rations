package cm.afrilandfirstbank.rations.workflow.application;

import java.time.LocalDateTime;

import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutIntegrationEnum;

/**
 * Ce qu'un geste du verrou de transmission a donne, rendu au service Transmission
 * (Sprint 5.3, RG-13).
 *
 * <p><b>Le champ {@link #resultat} est la reponse explicite que le guide exige</b> : une
 * seconde demande n'est pas forcement une anomalie — un rejeu legitime existe — et lui
 * opposer une erreur technique ferait croire a une panne. {@code RESERVEE} et
 * {@code DEJA_TRANSMISE} sont donc deux succes, distingues par un champ et non par un code
 * HTTP. C'est l'idiome du Sprint 5.2, ou {@code APPLIQUE} et {@code DEJA_APPLIQUE}
 * partagent le meme {@code 200}.
 *
 * @param resultat le geste effectivement accompli
 * @param message ce qui s'est passe, en clair. Destine a une personne : il nomme le
 *        dossier, la date de la premiere transmission et la suite qu'elle a recue
 * @param dateReservation instant de la reservation en vigueur, nul si aucune
 * @param transmisComptabilite etat du drapeau de RG-13 apres le geste
 * @param statutIntegration statut d'integration apres le geste, nul tant que la
 *        publication n'a pas ete confirmee
 */
public record ResultatVerrouTransmission(
        Resultat resultat,
        String message,
        LocalDateTime dateReservation,
        boolean transmisComptabilite,
        StatutIntegrationEnum statutIntegration) {

    /** Les cinq issues possibles des trois gestes du verrou. */
    public enum Resultat {

        /** Le verrou vient d'etre pose : l'appelant peut publier, et lui seul. */
        RESERVEE,

        /** L'etat etait deja transmis : aucune publication n'a lieu (RG-13). */
        DEJA_TRANSMISE,

        /** La publication est confirmee : l'attente de l'accuse comptable est ouverte. */
        CONFIRMEE,

        /** La reservation est levee : une reprise redevient possible. */
        LIBEREE,

        /**
         * La liberation est refusee : la comptabilite a deja repondu, donc l'evenement
         * etait bien parti. Lever la reservation ferait republier un etat deja pris en
         * charge.
         */
        LIBERATION_REFUSEE
    }

    static ResultatVerrouTransmission de(Resultat resultat, String message,
            ProcessusMensuel processus) {
        return new ResultatVerrouTransmission(
                resultat,
                message,
                processus.getDateReservationTransmission(),
                processus.isTransmisComptabilite(),
                processus.getStatutIntegration());
    }

}
