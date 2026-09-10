package cm.afrilandfirstbank.rations.reporting.application;

import java.time.LocalDate;

/**
 * Port de lecture vers le service Workflow (Sprint 6.1).
 *
 * <p>Interface dans la couche application, implementation HTTP dans
 * l'infrastructure : ni le domaine ni l'orchestration ne connaissent
 * {@code RestClient}. C'est la facture de tous les clients du module depuis le
 * Sprint 1.3.
 *
 * <h2>Il n'expose que ce dont le reporting a besoin</h2>
 *
 * <p>Deux methodes, alors que le service Workflow compte neuf endpoints. Le
 * reporting <b>lit</b> : il ne declenche pas d'etat, n'en valide aucun, n'en
 * retourne aucun, et rien ici ne lui en donne le moyen. Un port large serait une
 * invitation permanente a faire ecrire un service de lecture.
 *
 * <h2>Le jeton est relaye, jamais reemis</h2>
 *
 * <p>Les deux methodes recoivent l'en-tete {@code Authorization} de l'utilisateur
 * final et le transmettent tel quel (doctrine Sprint 1.3). C'est ce qui permet au
 * service Workflow de resoudre lui-meme la portee d'acces : elle n'est jamais un
 * parametre, donc jamais forgeable.
 */
public interface WorkflowLectureClient {

    /**
     * Les en-tetes des etats correspondant aux criteres, dans la portee de
     * l'utilisateur du jeton.
     *
     * @param limite volume au-dela duquel le contenu n'est pas transporte ; le
     *        <b>compte reste rendu</b>, ce qui permet de refuser en nommant le
     *        nombre trouve au lieu de rendre une page vide
     */
    ResultatRechercheDemandes rechercher(LocalDate dateDebut, LocalDate dateFin, String codeUnite,
            String statut, int limite, String enteteAutorisation);

    /** Toutes les etapes d'un dossier, dans l'ordre du rang — y compris les passages repetes. */
    ResultatHistorique consulterHistorique(Long idProcessus, String enteteAutorisation);

}
