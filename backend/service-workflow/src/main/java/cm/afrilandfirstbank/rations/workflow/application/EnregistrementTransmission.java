package cm.afrilandfirstbank.rations.workflow.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import cm.afrilandfirstbank.rations.commun.audit.DeltaAudit;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.workflow.application.ResultatDemandeTransmission.Transmise;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ProcessusMensuelRepository;

/**
 * Pose le drapeau de RG-13 sur un etat effectivement publie, dans sa propre transaction.
 *
 * <h2>Une transaction a part, courte, et apres coup</h2>
 *
 * <p>Classe separee pour la raison technique du Sprint 4.2 :
 * {@code @Transactional} sur une methode appelee depuis la meme classe n'a <b>aucun
 * effet</b> avec les mandataires Spring, et rien ne le signalerait.
 *
 * <p>{@link Propagation#REQUIRES_NEW} : cette ecriture ne doit dependre d'aucune
 * transaction ambiante. Elle survient apres le commit de la validation, sur un fil du pool
 * de transmission, et elle est le <b>constat</b> d'un fait deja acquis — l'evenement est
 * sur le broker. Une transaction englobante qui echouerait ensuite effacerait ce constat et
 * laisserait un etat publie mais repute non transmis : la reprise republierait, et la
 * comptabilite produirait deux jeux d'ecritures.
 *
 * <h2>Constat, jamais anticipation</h2>
 *
 * <p>Appelee uniquement sur un {@link Transmise}, c'est-a-dire apres un accuse du broker.
 * La poser plus tot — a la cloture, ou avant la publication — ferait paraitre transmis un
 * etat qui ne l'est pas, et le controle d'unicite du sous-sprint 5.3 refuserait ensuite la
 * vraie transmission comme un doublon : l'etat resterait definitivement impaye. C'est
 * l'une des erreurs interdites de CLAUDE.md section 15, et la meme discipline que le
 * compteur de signatures du Sprint 4.2, qui ne s'incremente qu'apres confirmation
 * d'ecriture disque.
 */
@Component
public class EnregistrementTransmission {

    private static final String ENTITE_CIBLE = "processus_mensuel";
    private static final String ACTION_TRANSMISSION = "TRANSMISSION_COMPTABLE";

    private final ProcessusMensuelRepository processusMensuelRepository;
    private final PublicateurAudit publicateurAudit;

    public EnregistrementTransmission(ProcessusMensuelRepository processusMensuelRepository,
            PublicateurAudit publicateurAudit) {
        this.processusMensuelRepository = processusMensuelRepository;
        this.publicateurAudit = publicateurAudit;
    }

    /**
     * Marque l'etat comme transmis et ouvre l'attente de l'accuse comptable.
     *
     * <p>Les deux champs avancent ensemble parce qu'ils disent la meme chose vue de deux
     * cotes : {@code transmis_comptabilite} repond « le module a-t-il envoye ? »,
     * {@code statut_integration = EN_ATTENTE} repond « qu'en sait-on du cote comptable ? ».
     * La contrainte {@code ck_processus_integration_apres_transmission} (migration V4)
     * interdit d'ailleurs en base un statut d'integration sans transmission.
     *
     * @param accuse la position du message sur le broker, reprise dans la trace d'audit
     * @throws IllegalStateException si l'etat porte deja le drapeau. Le cas ne devrait pas
     *         survenir avant le sous-sprint 5.3 ; il est refuse au plus pres de la donnee
     *         plutot que d'etre ecrase en silence
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void constaterTransmission(Long idProcessus, Transmise accuse, String adresseIp) {
        ProcessusMensuel processus = processusMensuelRepository.findById(idProcessus)
                .orElseThrow(() -> new ProcessusIntrouvableException(idProcessus));

        processus.constaterTransmissionComptable();
        ProcessusMensuel enregistre = processusMensuelRepository.save(processus);

        publicateurAudit.publier(EvenementAudit.de(
                null,
                ACTION_TRANSMISSION,
                ENTITE_CIBLE,
                enregistre.getId(),
                adresseIp,
                DeltaAudit.nouveau()
                        .champ("transmisComptabilite", false, true)
                        .champ("statutIntegration", null, enregistre.getStatutIntegration())
                        .contexte("topic", accuse.topic())
                        .contexte("partition", accuse.partition())
                        .contexte("offset", accuse.offset())
                        .contexte("codeUnite", enregistre.getCodeUnite())
                        .contexte("moisPaiement", enregistre.getMoisPaiement())
                        .contexte("anneePaiement", enregistre.getAnneePaiement())
                        .contexte("montantTotal", accuse.montantTotal())
                        .contexte("nombreLignes", accuse.nombreLignes())
                        .enJson()));
    }

}
