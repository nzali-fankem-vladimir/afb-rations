package cm.afrilandfirstbank.rations.reporting.api.dto;

import java.time.LocalDateTime;

import cm.afrilandfirstbank.rations.reporting.domaine.HistoriqueDemande.EtapeHistorique;

/**
 * Une étape de {@code GET /reporting/processus/{id}/historique} (Sprint 6.1,
 * CT-31).
 *
 * <p>{@code ordreEtape} est ce qui distingue deux passages au même niveau après un
 * retour (Sprint 4.4, doctrine du rang calculé {@code dernier + 1}). Le supprimer
 * ferait perdre la seule information qui prouve que l'historique montre bien la
 * chronologie complète, et pas seulement le dernier passage.
 *
 * <p>{@code loginActeur} et {@code nomActeur} sont nuls quand le compte n'a plus de
 * libellé disponible (compte supprimé de la projection locale, service Identité
 * injoignable) : {@code idActeur} reste alors la seule trace, jamais absente.
 */
public record EtapeHistoriqueResponse(
        int ordreEtape,
        String nomEtape,
        String statutEtape,
        Long idActeur,
        String loginActeur,
        String nomActeur,
        String motifRetour,
        boolean signee,
        LocalDateTime dateAction) {

    public static EtapeHistoriqueResponse depuis(EtapeHistorique etape) {
        return new EtapeHistoriqueResponse(
                etape.ordreEtape(),
                etape.nomEtape(),
                etape.statutEtape(),
                etape.idActeur(),
                etape.loginActeur(),
                etape.nomActeur(),
                etape.motifRetour(),
                etape.signee(),
                etape.dateAction());
    }

}
