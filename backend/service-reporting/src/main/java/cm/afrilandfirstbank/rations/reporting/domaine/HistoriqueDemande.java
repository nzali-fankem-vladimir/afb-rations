package cm.afrilandfirstbank.rations.reporting.domaine;

import java.time.LocalDateTime;
import java.util.List;

/**
 * L'historique complet d'un dossier : son en-tete et la suite de ses etapes
 * (Sprint 6.1, CT-31).
 *
 * <p>Recopie de ce que rend {@code GET /processus/{id}/historique} cote Workflow.
 */
public record HistoriqueDemande(
        Long idProcessus,
        Integer moisPaiement,
        Integer anneePaiement,
        String codeUnite,
        String statut,
        List<EtapeHistorique> etapes) {

    public HistoriqueDemande {
        etapes = etapes == null ? List.of() : List.copyOf(etapes);
    }

    /**
     * Une etape du circuit.
     *
     * <h2>Le rang est ce qui rend les passages successifs lisibles</h2>
     *
     * <p>Depuis le Sprint 4.4, le rang est calcule {@code dernier + 1}, y compris a
     * la resoumission. Un dossier retourne puis resoumis porte donc deux etapes
     * {@code SOUMISSION_AGENT} de rangs differents, et le cas echeant deux
     * {@code VALIDATION_DA}. Ne montrer que le dernier passage a chaque niveau
     * cacherait le refus et sa correction — c'est-a-dire ce que le controle interne
     * vient chercher.
     *
     * @param loginActeur rempli par le service Reporting a partir de
     *        {@code GET /identite/utilisateurs/libelles} ; <b>nul</b> quand le
     *        compte n'existe plus dans la projection locale, ou quand le service
     *        Identite n'a pas repondu. L'identifiant, lui, est toujours la.
     * @param motifRetour renseigne pour les seules etapes {@code RETOURNEE} (RG-10)
     * @param signee l'etape porte-t-elle une signature (RG-09)
     */
    public record EtapeHistorique(
            int ordreEtape,
            String nomEtape,
            String statutEtape,
            Long idActeur,
            String loginActeur,
            String nomActeur,
            String motifRetour,
            boolean signee,
            LocalDateTime dateAction) {

        /** La meme etape, nommee par son acteur. */
        public EtapeHistorique avecActeur(String login, String nom) {
            return new EtapeHistorique(ordreEtape, nomEtape, statutEtape, idActeur,
                    login, nom, motifRetour, signee, dateAction);
        }

    }

}
