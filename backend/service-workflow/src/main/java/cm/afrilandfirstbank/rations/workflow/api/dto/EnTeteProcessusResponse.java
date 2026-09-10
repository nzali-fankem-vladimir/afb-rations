package cm.afrilandfirstbank.rations.workflow.api.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutIntegrationEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.TypeProcessusEnum;

/**
 * L'en-tete d'un etat mensuel tel qu'il voyage vers le service Reporting
 * (Sprint 6.1). Element de {@link RechercheProcessusResponse}.
 *
 * <h2>Ce qu'il porte, et pourquoi pas davantage</h2>
 *
 * <p>Exactement ce que {@code GET /reporting/demandes} doit afficher : la periode,
 * l'unite, le montant total, le statut d'avancement et le statut d'integration
 * comptable. Ni le motif de retour, ni le motif d'ouverture, ni la reference
 * comptable : une liste de suivi n'a pas a diffuser le detail de chaque dossier, et
 * chaque champ ajoute ici est un champ de plus a transporter des milliers de fois.
 *
 * <p><b>Distinct de {@link ProcessusResponse}</b>, qui rend le dossier complet a un
 * acteur du circuit sur {@code GET /processus/{id}}. Les deux pourraient se
 * ressembler ; les confondre ferait qu'un enrichissement du detail alourdirait
 * silencieusement toutes les recherches.
 *
 * <p>{@code statutIntegration} est <b>nul dans deux situations sans rapport</b> —
 * jamais transmis, et publication non confirmee (Sprint 5.3). C'est au Reporting de
 * les distinguer a partir de {@code transmisComptabilite}, qui voyage a cote pour
 * cette raison precise.
 */
public record EnTeteProcessusResponse(
        Long id,
        LocalDate dateDebut,
        LocalDate dateFin,
        Integer moisPaiement,
        Integer anneePaiement,
        String codeUnite,
        TypeProcessusEnum typeProcessus,
        int montantTotal,
        StatutEnum statut,
        boolean transmisComptabilite,
        StatutIntegrationEnum statutIntegration,
        LocalDateTime dateCreation) {

    public static EnTeteProcessusResponse depuis(ProcessusMensuel processus) {
        return new EnTeteProcessusResponse(
                processus.getId(),
                processus.getDateDebut(),
                processus.getDateFin(),
                moisDerive(processus),
                anneeDerivee(processus),
                processus.getCodeUnite(),
                processus.getTypeProcessus(),
                processus.getMontantTotal(),
                processus.getStatut(),
                processus.isTransmisComptabilite(),
                processus.getStatutIntegration(),
                processus.getDateCreation());
    }


    /**
     * Le mois de la periode — <b>nul des qu'elle chevauche deux mois</b>.
     *
     * <h2>Pourquoi ces deux champs survivent a la Maille 1</h2>
     *
     * <p>Le service Transmission construit encore la charge comptable en version 1,
     * qui porte {@code "periode": { mois, annee }} (contrat d'API section 7.1). Sa
     * refonte est le sprint Maille 2, qui attend la mise en oeuvre du contrat
     * accepte par la DFT. En attendant, l'en-tete continue de fournir ce qu'il lit.
     *
     * <h2>Pourquoi nuls plutot que devines</h2>
     *
     * <p>Une periode du 29 septembre au 5 octobre n'a <b>aucun</b> mois. Rendre
     * celui de la date de debut serait plausible, faux, et invisible — la pire des
     * trois proprietes pour une imputation comptable.
     *
     * <p>Le refus n'a demande aucune ligne cote Transmission :
     * {@code ConstructionChargeService} controle deja {@code periode.mois() == null}
     * et leve l'anomalie {@code PERIODE_INVALIDE}, rendue en
     * {@code 500 CHARGE_INCOMPLETE}. L'etat n'est pas publie, et le message dit
     * pourquoi.
     *
     * <p><b>C'est ce qui rend la dependance a la Maille 2 concrete plutot que
     * theorique</b> : tant qu'elle n'est pas faite, une periode a cheval ne part
     * pas en comptabilite, et cela se voit.
     */
    private static Integer moisDerive(ProcessusMensuel processus) {
        return tientDansUnSeulMois(processus) ? processus.getDateDebut().getMonthValue() : null;
    }

    private static Integer anneeDerivee(ProcessusMensuel processus) {
        return tientDansUnSeulMois(processus) ? processus.getDateDebut().getYear() : null;
    }

    private static boolean tientDansUnSeulMois(ProcessusMensuel processus) {
        LocalDate debut = processus.getDateDebut();
        LocalDate fin = processus.getDateFin();
        return debut != null && fin != null
                && debut.getYear() == fin.getYear()
                && debut.getMonthValue() == fin.getMonthValue();
    }

}
