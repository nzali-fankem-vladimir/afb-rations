package cm.afrilandfirstbank.rations.transmission.application;

/**
 * Les deux issues d'un appel de consolidation au service Saisie.
 *
 * <p><b>Deux et non trois</b> : il n'existe pas de « verdict negatif ». Un processus
 * sans aucune journee est rendu en {@code 200}, avec zero journee et un total de zero
 * ({@code docs/appel-consolidation.md} section 4) — c'est un {@link EtatObtenu} comme
 * un autre. Le refuser revient au controle de completude de la charge, qui sait dire
 * <i>pourquoi</i> il refuse.
 */
public sealed interface ResultatConsolidation {

    /** {@code 200} exploitable, y compris le cas legitime de l'etat vide. */
    record EtatObtenu(EtatConsolide etat) implements ResultatConsolidation {
    }

    /**
     * Le service Saisie n'a rien repondu d'exploitable : timeout, connexion refusee,
     * {@code 4xx}, {@code 5xx}, corps illisible. <b>Refus conservateur</b> : on ne
     * publie jamais une charge partielle vers la comptabilite. Un detail incomplet
     * produirait des paiements manquants que rien ne signalerait.
     */
    record ServiceSaisieIndisponible(String motifTechnique) implements ResultatConsolidation {
    }

}
