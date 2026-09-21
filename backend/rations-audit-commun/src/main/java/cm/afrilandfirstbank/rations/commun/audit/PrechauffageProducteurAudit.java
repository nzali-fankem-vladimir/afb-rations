package cm.afrilandfirstbank.rations.commun.audit;

import java.time.Duration;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Prechauffage du producteur d'audit, Sprint 8.2.
 *
 * <p><b>Le defaut qu'il ferme.</b> Le producteur Kafka est cree paresseusement,
 * au premier {@code send()}. Ce premier envoi doit alors ouvrir la connexion au
 * broker et en recuperer les metadonnees. Sur un poste ou le demarrage d'une JVM
 * est lent, cela depasse les deux secondes de {@code max.block.ms} (mesure : 2,3 s,
 * la reponse arrivant 88 ms apres l'abandon) : <b>le premier evenement d'audit de
 * chaque service demarre est perdu</b>, les suivants partent. Constate en
 * conteneur, ou chaque demarrage est a froid.
 *
 * <p><b>Ce qu'il fait.</b> Des que l'application est prete, il demande au
 * producteur les metadonnees du topic d'audit, sur le pool d'audit et non sur le
 * fil de demarrage. Le producteur est ainsi cree et connecte avant le premier
 * evenement reel.
 *
 * <p><b>Ce qu'il ne change pas, et c'est le point.</b> La borne de deux secondes
 * ({@code max.block.ms}) n'est PAS relevee : CLAUDE.md section 15 l'interdit, car
 * c'est elle qui garantit que l'audit ne fait jamais echouer ni ralentir le
 * metier. Le prechauffage est asynchrone, borne, et n'echoue jamais : toute
 * exception est avalee et journalisee.
 *
 * <p><b>Un appel qui depasse la borne ne rate pas son but.</b> Meme quand
 * {@code partitionsFor} abandonne apres deux secondes, le producteur est cree et
 * sa connexion continue en arriere-plan (c'est exactement ce que montre le defaut
 * decrit plus haut : la reponse arrive apres l'abandon). Les tentatives suivantes
 * trouvent le producteur pret.
 *
 * <p><b>Limite.</b> Il ne garantit rien si le broker est absent au demarrage : les
 * tentatives echouent, un {@code WARN} le dit, et l'audit reste soumis a la regle
 * assumee « perdre une trace plutot que bloquer le metier ». La garantie totale
 * releve de l'outbox transactionnel (docs/points-en-attente.md).
 */
public class PrechauffageProducteurAudit {

    private static final Logger log = LoggerFactory.getLogger(PrechauffageProducteurAudit.class);

    /** Nombre maximal de tentatives ; chacune peut bloquer jusqu'a {@code max.block.ms}. */
    static final int TENTATIVES_MAX = 3;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final Executor executeur;
    private final Duration pauseEntreTentatives;

    public PrechauffageProducteurAudit(KafkaTemplate<String, String> kafkaTemplate, String topic,
            Executor executeur, Duration pauseEntreTentatives) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.executeur = executeur;
        this.pauseEntreTentatives = pauseEntreTentatives;
    }

    /** Declenche le prechauffage sans jamais retenir ni faire echouer le demarrage. */
    @EventListener(ApplicationReadyEvent.class)
    public void demarrer() {
        try {
            executeur.execute(this::preparer);
        } catch (RuntimeException e) {
            log.warn("PRECHAUFFAGE AUDIT non lance : {}. Aucun effet sur le metier.", e.toString());
        }
    }

    void preparer() {
        for (int tentative = 1; tentative <= TENTATIVES_MAX; tentative++) {
            try {
                kafkaTemplate.partitionsFor(topic);
                log.info("PRECHAUFFAGE AUDIT : producteur pret sur le topic {} (tentative {}).", topic, tentative);
                return;
            } catch (RuntimeException e) {
                log.info("PRECHAUFFAGE AUDIT : tentative {} sur {} sans reponse du broker ({}).",
                        tentative, TENTATIVES_MAX, e.toString());
                if (tentative < TENTATIVES_MAX && !patienter()) {
                    return;
                }
            }
        }
        log.warn("PRECHAUFFAGE AUDIT INCOMPLET : le broker n'a pas repondu apres {} tentatives. Les premiers "
                + "evenements d'audit peuvent se perdre. Aucun effet sur le metier.", TENTATIVES_MAX);
    }

    /** @return {@code false} si l'attente a ete interrompue (arret du service) : on renonce alors. */
    private boolean patienter() {
        try {
            Thread.sleep(pauseEntreTentatives.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

}
