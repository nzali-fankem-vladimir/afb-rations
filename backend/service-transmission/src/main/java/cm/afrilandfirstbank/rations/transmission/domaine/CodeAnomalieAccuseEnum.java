package cm.afrilandfirstbank.rations.transmission.domaine;

/**
 * Les raisons pour lesquelles un accuse recu sur {@code rations.etat.accuse} n'est pas
 * exploitable, ou ne peut pas etre applique.
 *
 * <h2>Distincte de {@link CodeAnomalieEnum}, deliberement</h2>
 *
 * <p>Celle-la dit pourquoi une charge <b>ne part pas</b> vers la comptabilite ; celle-ci
 * dit pourquoi un accuse <b>venu</b> de la comptabilite n'est pas applique. Deux sens de
 * circulation, deux responsabilites : une anomalie de charge designe un defaut de notre
 * cote, une anomalie d'accuse designe le plus souvent un defaut de l'autre. Les melanger
 * dans une seule enumeration rendrait illisible, en supervision, la question « qui doit
 * corriger ? ».
 *
 * <h2>Toutes sont definitives</h2>
 *
 * <p>Aucune de ces anomalies ne s'arrange en reessayant : un JSON tronque le restera, un
 * processus inconnu ne naitra pas, un statut deja pose ne se defera pas. Elles font donc
 * <b>avancer</b> le message apres trace, faute de quoi un seul accuse fautif bloquerait
 * la consommation de tous les suivants, accuses valides compris.
 *
 * <p>Les echecs <b>temporaires</b> — service Workflow injoignable — n'ont volontairement
 * aucune constante ici : ils ne sont pas des anomalies de l'accuse, ils sont rejoues.
 */
public enum CodeAnomalieAccuseEnum {

    // --- Charge inexploitable ------------------------------------------------------

    /**
     * Le message n'est pas un JSON lisible, ou sa forme ne correspond a rien
     * d'exploitable. Prefixe de supervision {@code ACCUSE ILLISIBLE}.
     */
    ACCUSE_ILLISIBLE,

    /**
     * Aucun {@code idProcessus}. C'est la seule cle de rapprochement avec
     * {@code processus_mensuel} : sans elle, l'accuse ne designe rien.
     */
    IDENTIFIANT_ABSENT,

    /** Aucun statut d'integration : l'accuse ne dit pas ce qu'il accuse. */
    STATUT_ABSENT,

    /**
     * Statut hors de {@code EN_ATTENTE} / {@code INTEGRE} / {@code REJETE} (contrat
     * section 7.2). Refus nomme, et non « message illisible » : la difference compte
     * pour qui lit le journal.
     */
    STATUT_INCONNU,

    /**
     * Statut {@code REJETE} sans motif. Le contrat l'exige — « en cas de rejet, un motif
     * accompagne l'accuse » — et le suivi (US-15) doit pouvoir dire <i>pourquoi</i> un
     * etat a ete refuse. Un rejet sans motif laisserait l'unite devant un refus muet.
     * Une suite d'espaces n'est pas un motif, meme discipline qu'au Sprint 4.4 pour le
     * motif de retour.
     */
    MOTIF_REJET_ABSENT,

    /**
     * {@code dateTraitement} presente mais illisible. Le contrat section 1.1 exige de
     * l'ISO 8601 ; une date sans decalage horaire est <b>ambigue</b>, et lui en supposer
     * un reviendrait a inventer une information sur un traitement de paiement. Absente,
     * la date est acceptee et la colonne reste nulle : c'est un manque, pas une
     * contradiction.
     */
    DATE_TRAITEMENT_ILLISIBLE,

    // --- Accuse lisible, mais inapplicable -----------------------------------------

    /**
     * Le service Workflow ne connait pas cet identifiant. Soit la comptabilite s'est
     * trompee, soit l'accuse s'adresse a un autre module. Prefixe de supervision
     * {@code ACCUSE ORPHELIN}.
     */
    PROCESSUS_INCONNU,

    /**
     * L'etat n'a <b>jamais</b> ete transmis ({@code transmis_comptabilite} a faux) : la
     * comptabilite dit avoir traite quelque chose qu'on ne lui a jamais envoye.
     * Incoherence serieuse, d'un cote ou de l'autre. Prefixe {@code ACCUSE INCOHERENT}.
     *
     * <p>Le drapeau de transmission n'est <b>jamais</b> pose a cette occasion : il
     * ferait croire a une transmission qui n'a pas eu lieu, et RG-13 refuserait ensuite
     * la vraie comme un doublon — l'etat resterait impaye a jamais.
     */
    PROCESSUS_NON_TRANSMIS,

    /**
     * L'accuse contredit un statut deja recu : regression vers {@code EN_ATTENTE},
     * bascule entre {@code INTEGRE} et {@code REJETE}, ou meme statut avec une reference
     * ou une date differente. Prefixe {@code ACCUSE CONTRADICTOIRE}.
     *
     * <p>Le module <b>refuse et signale, il n'arbitre jamais</b> — meme doctrine
     * qu'{@code INCOHERENCE_GRILLE} au Sprint 2.4. Il n'a aucun moyen de savoir lequel
     * des deux messages dit vrai, et ecraser silencieusement ferait passer un etat paye
     * pour rejete sans que personne ne le voie.
     */
    ACCUSE_CONTRADICTOIRE

}
