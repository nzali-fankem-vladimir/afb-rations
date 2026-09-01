package cm.afrilandfirstbank.rations.workflow.application;

import cm.afrilandfirstbank.rations.workflow.domaine.NomEtapeEnum;

/**
 * Les deux issues du controle de separation des taches (RG-12).
 *
 * <p><b>Type scelle, sans champ booleen</b>, comme {@link ResultatHabilitationUnite}
 * (Sprint 1.3) : {@link Autorise} est le seul cas qui laisse agir. Un
 * {@code resultat.autorise()} pourrait etre mal lu ou mal initialise ; ici, le
 * refus n'a tout simplement pas de champ a mal lire, et le {@code switch} qui les
 * distingue est exhaustif.
 */
public sealed interface ResultatSeparationTaches {

    /** Cet acteur n'a rien fait sur la version courante du dossier : il peut agir. */
    record Autorise() implements ResultatSeparationTaches {
    }

    /**
     * Cet acteur a deja agi sur la version courante du dossier.
     *
     * @param etapeDejaRealisee ce qu'il y a deja fait — soumettre n'appelle pas le
     *        meme message que valider au premier niveau
     * @param motif phrase destinee a l'utilisateur, disant ce qui bloque et quelle
     *        est la suite
     */
    record Refuse(NomEtapeEnum etapeDejaRealisee, String motif)
            implements ResultatSeparationTaches {
    }

}
