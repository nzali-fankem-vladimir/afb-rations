package cm.afrilandfirstbank.rations.workflow.application;

/**
 * Verdict d'un appel a {@code GET /identite/moi}.
 *
 * <p>Type scelle, comme {@link ResultatHabilitationUnite} : les trois issues sont
 * enumerees, et le compilateur exige qu'elles soient toutes traitees. Une panne du
 * service Identite ne peut donc pas etre confondue avec un refus, ce qui est
 * precisement la distinction posee au Sprint 2.2 (503 et non 403).
 */
public sealed interface ResultatProfil {

    /** Le profil local existe : son identifiant, son login et son role sont connus. */
    record ProfilObtenu(ActeurSignataire acteur) implements ResultatProfil {
    }

    /**
     * Le service Identite a repondu, et il n'y a pas de profil local pour ce jeton.
     *
     * <p>Un refus legitime : l'habilitation au module est un acte d'administration
     * explicite (CLAUDE.md section 10). Rendu en {@code 403}.
     */
    record ProfilAbsent(String motif) implements ResultatProfil {
    }

    /**
     * Le service Identite n'a pas repondu.
     *
     * <p><b>Distinct du refus</b>, et rendu en {@code 503} : l'agent possede le
     * droit qu'il exerce, un {@code 403} l'enverrait reclamer une habilitation
     * qu'il a deja pendant que la panne resterait invisible (doctrine Sprint 2.2).
     */
    record ServiceIdentiteIndisponible(String motifTechnique) implements ResultatProfil {
    }

}
