package cm.afrilandfirstbank.rations.reporting.application;

import cm.afrilandfirstbank.rations.reporting.domaine.HistoriqueDemande;

/**
 * Les issues d'une demande d'historique aupres du service Workflow (Sprint 6.1).
 *
 * <p>Un dossier <b>introuvable</b> a son issue propre, distincte d'un refus de
 * droit : les deux menent a deux gestes differents, et les confondre — comme le
 * ferait un {@code 403} systematique « pour ne rien reveler » — enverrait un
 * lecteur legitime reclamer une habilitation qu'il possede deja pour un dossier qui
 * n'existe pas.
 */
public sealed interface ResultatHistorique {

    record Obtenu(HistoriqueDemande historique) implements ResultatHistorique {
    }

    /** Aucun etat mensuel ne porte cet identifiant. */
    record ProcessusIntrouvable(String motif) implements ResultatHistorique {
    }

    /** Refus de droit prononce par le service Workflow, relaye tel quel. */
    record AccesRefuse(String motif) implements ResultatHistorique {
    }

    record ServiceIndisponible(String motifTechnique) implements ResultatHistorique {
    }

}
