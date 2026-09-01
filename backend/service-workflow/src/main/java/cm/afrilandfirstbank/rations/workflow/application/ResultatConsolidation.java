package cm.afrilandfirstbank.rations.workflow.application;

/**
 * Les deux issues d'un appel de consolidation au service Saisie.
 *
 * <p><b>Deux issues, pas trois</b>, contrairement a
 * {@link ResultatHabilitationUnite}. Il n'existe pas de « verdict negatif » ici :
 * un processus dont aucune journee n'a ete saisie est un etat <b>normal</b>, rendu
 * en {@code 200} avec zero journee et un total de zero
 * ({@code docs/appel-consolidation.md} section 4). C'est un
 * {@link EtatObtenu} comme un autre.
 *
 * <p>Refuser la soumission d'un etat vide reste ensuite une regle de Workflow, a
 * partir de {@code nombreLignes == 0} — au sous-sprint 4.2, pas ici (point C-03
 * du Sprint 3.4).
 *
 * <p>Type scelle, sans champ booleen : la panne n'a pas de champ « etat » a lire
 * a moitie.
 */
public sealed interface ResultatConsolidation {

    /** {@code 200} exploitable. Inclut le cas legitime de l'etat vide. */
    record EtatObtenu(EtatConsolide etat) implements ResultatConsolidation {
    }

    /**
     * Le service Saisie n'a rien repondu d'exploitable : timeout, connexion
     * refusee, {@code 4xx}, {@code 5xx}, corps illisible. <b>Refus
     * conservateur</b> (section 6 de la convention).
     */
    record ServiceSaisieIndisponible(String motifTechnique) implements ResultatConsolidation {
    }

}
