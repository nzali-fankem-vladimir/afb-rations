package cm.afrilandfirstbank.rations.workflow.application;

/**
 * Les trois issues d'une demande de portee d'acces au service Identite
 * (Sprint 6.1).
 *
 * <p>Meme decoupage que {@link ResultatProfil} et {@link ResultatHabilitationUnite},
 * et pour la meme raison : un refus et une panne appellent deux gestes differents
 * de la part de l'utilisateur — demander une habilitation, ou reessayer plus tard.
 * Les confondre enverrait un lecteur parfaitement habilite reclamer un droit qu'il
 * possede deja, pendant que l'incident resterait invisible.
 *
 * <p>Type <b>scelle</b> : le {@code switch} qui l'applique est exhaustif, et une
 * quatrieme issue ajoutee plus tard fera echouer la compilation aux endroits exacts
 * a corriger.
 */
public sealed interface ResultatPortee {

    /** Le service Identite a repondu : voici les unites que cet utilisateur peut voir. */
    record PorteeObtenue(PorteeAccesUtilisateur portee) implements ResultatPortee {
    }

    /**
     * Le service Identite a repondu qu'aucun profil local n'est ouvert pour ce
     * compte. Refus legitime, pas une panne.
     */
    record ProfilAbsent(String motif) implements ResultatPortee {
    }

    /**
     * Le service Identite n'a pas repondu, ou a repondu quelque chose
     * d'inexploitable. <b>Refus conservateur</b> (doctrine Sprint 1.3) : on ne
     * devine jamais une portee.
     */
    record ServiceIdentiteIndisponible(String motifTechnique) implements ResultatPortee {
    }

}
