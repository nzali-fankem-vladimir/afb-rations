package cm.afrilandfirstbank.rations.workflow.application;

import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.workflow.application.ResultatHabilitationUnite.AgentHabilite;
import cm.afrilandfirstbank.rations.workflow.application.ResultatHabilitationUnite.AgentNonHabilite;
import cm.afrilandfirstbank.rations.workflow.application.ResultatHabilitationUnite.ServiceIdentiteIndisponible;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.AgentNonHabiliteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ServiceIdentiteIndisponibleException;

/**
 * Porte unique de la verification de portee d'acces (RG-12) dans le service
 * Workflow.
 *
 * <p>Toute operation qui touche a un processus passe par
 * {@link #exigerHabilitationSurUnite(String, String)} : declenchement,
 * consultation, et — aux sous-sprints 4.2 a 4.4 — soumission, validation et
 * retour. Une seule porte, parce qu'un controle recopie a six endroits finit par
 * manquer au septieme, comme {@code EtatModifiableService} cote Saisie.
 *
 * <p><b>Le role n'est que le premier filtre.</b> {@code @PreAuthorize} sur le
 * controleur dit « ce role peut faire ce geste » ; ce service dit « cette
 * personne peut le faire <i>sur ce dossier</i> ». Un agent d'unite habilite sur
 * {@code 00002} n'a rien a faire sur un processus de {@code 00007}, et son role
 * ne le distingue pas de l'agent legitime.
 *
 * <p><b>Refus conservateur.</b> Les trois issues de
 * {@link ResultatHabilitationUnite} se resument a une regle : seul
 * {@link AgentHabilite} laisse passer. Le verdict negatif et la panne levent deux
 * exceptions distinctes, parce qu'elles appellent deux gestes differents de la
 * part de l'utilisateur — demander une habilitation, ou reessayer plus tard — et
 * sont tracees avec deux motifs distincts.
 */
@Service
public class HabilitationService {

    private final HabilitationClient habilitationClient;

    public HabilitationService(HabilitationClient habilitationClient) {
        this.habilitationClient = habilitationClient;
    }

    /**
     * Verifie que l'utilisateur du jeton a le droit d'agir sur un dossier de cette
     * unite, et rend son identite telle que le service Identite la connait.
     *
     * <p><b>Jamais mis en cache</b> : une habilitation peut changer entre deux
     * appels ({@code docs/appel-habilitation.md} section 3). Chaque operation
     * repose la question.
     *
     * @throws AgentNonHabiliteException verdict negatif d'Identite ({@code 403})
     * @throws ServiceIdentiteIndisponibleException Identite muet ({@code 503})
     */
    public AgentHabilite exigerHabilitationSurUnite(String codeUnite, String enteteAutorisation) {
        ResultatHabilitationUnite resultat = habilitationClient.verifier(codeUnite, enteteAutorisation);

        return switch (resultat) {
            case AgentHabilite habilite -> habilite;

            case AgentNonHabilite refus -> throw new AgentNonHabiliteException(
                    "Vous n'avez pas de droit sur l'unite " + codeUnite + " : " + refus.motif()
                            + ". Rapprochez-vous de l'administrateur du module.");

            case ServiceIdentiteIndisponible panne -> throw new ServiceIdentiteIndisponibleException(
                    "Le service Identite est momentanement indisponible ; l'operation sur l'unite "
                            + codeUnite + " est refusee par precaution (" + panne.motifTechnique()
                            + "). Reessayez dans un instant.");
        };
    }

}
