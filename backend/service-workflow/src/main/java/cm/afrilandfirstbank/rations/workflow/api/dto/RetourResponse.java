package cm.afrilandfirstbank.rations.workflow.api.dto;

import java.time.LocalDateTime;

import cm.afrilandfirstbank.rations.workflow.application.ResultatRetour;
import cm.afrilandfirstbank.rations.workflow.domaine.EtapeWorkflow;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;

/**
 * Reponse de {@code POST /processus/{id}/retour}.
 *
 * <p>Elle rend le <b>statut atteint</b> et le <b>niveau d'origine</b> cote a cote,
 * et c'est deliberé : c'est la lecture directe de RG-11. Le statut vaut toujours
 * {@code RETOURNE}, que le niveau d'origine soit le chef d'unite ou le directeur
 * reseau — un directeur reseau qui retourne un dossier voit noir sur blanc qu'il
 * repart a l'agent, et non au chef d'unite qui l'avait vise.
 *
 * <p>Aucune piece jointe rendue : un retour n'estampe pas le document (RG-09 ne vaut
 * que pour les validations).
 */
public record RetourResponse(
        Long idProcessus,
        String statut,
        String niveauOrigine,
        EtapeRetourneeResponse etape) {

    /** L'etape {@code RETOURNEE} qui porte le motif consultable par l'agent. */
    public record EtapeRetourneeResponse(
            Long id,
            int ordreEtape,
            String nomEtape,
            String statutEtape,
            String motifRetour,
            LocalDateTime dateCreation) {

        static EtapeRetourneeResponse depuis(EtapeWorkflow etape) {
            return new EtapeRetourneeResponse(
                    etape.getId(),
                    etape.getOrdreEtape(),
                    String.valueOf(etape.getNomEtape()),
                    String.valueOf(etape.getStatutEtape()),
                    etape.getMotifRetour(),
                    etape.getDateCreation());
        }
    }

    public static RetourResponse depuis(ResultatRetour resultat) {
        ProcessusMensuel processus = resultat.processus();

        return new RetourResponse(
                processus.getId(),
                String.valueOf(processus.getStatut()),
                String.valueOf(resultat.niveauOrigine()),
                EtapeRetourneeResponse.depuis(resultat.etape()));
    }

}
