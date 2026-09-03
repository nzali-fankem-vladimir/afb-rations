package cm.afrilandfirstbank.rations.transmission.infrastructure.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import cm.afrilandfirstbank.rations.transmission.infrastructure.AccuseComptableConsumer;

/**
 * Consommateur Kafka de l'accuse comptable : la seconde moitie de l'echange
 * (contrat d'API section 7.2, CLAUDE.md section 9.1).
 *
 * <h2>Deserialisation en chaine, et c'est structurel</h2>
 *
 * <p>Cle et valeur en {@link StringDeserializer}. On n'utilise <b>pas</b> le
 * {@code JsonDeserializer} de spring-kafka, et ce n'est pas une preference de style : il
 * convertit <b>avant</b> que le code d'ecoute ne soit appele. Un message malforme le
 * ferait donc echouer <i>dans le conteneur</i>, hors de portee de toute capture, et le
 * message empoisonne serait rejoue indefiniment — bloquant la consommation de tous les
 * suivants, accuses valides compris (guide 5.2 section 10).
 *
 * <p>En chaine, la conversion a lieu dans {@link AccuseComptableConsumer}, ou elle est
 * une simple exception que le code maitrise et transforme en refus trace. C'est aussi
 * symetrique de la publication du Sprint 5.1, qui serialise en chaine pour ne pas imposer
 * de noms de classes Java au module de comptabilisation.
 *
 * <h2>Acquittement apres traitement</h2>
 *
 * <p>{@link AckMode#RECORD} : l'offset est valide apres le retour du code d'ecoute, pour
 * <b>chaque</b> message. Un acquittement avant traitement perdrait silencieusement un
 * accuse si le service Workflow tombait entre les deux ; ici, le message est rejoue.
 *
 * <p>C'est un fonctionnement « au moins une fois », donc un doublon est possible — et
 * c'est acceptable <b>parce que le traitement est idempotent</b> : un accuse deja
 * applique est reconnu comme tel et n'ecrit rien, ni en base ni en audit. Sans cette
 * idempotence, ce reglage corromprait les statuts a chaque rejeu.
 *
 * <h2>Le reessai, et sa borne</h2>
 *
 * <p>{@link DefaultErrorHandler} avec {@link FixedBackOff} : deux nouvelles tentatives,
 * espacees de deux secondes. Il ne s'applique qu'aux echecs <b>temporaires</b> — le
 * service Workflow injoignable —, seul cas ou le code d'ecoute leve. Les refus definitifs
 * (message illisible, processus inconnu, jamais transmis, accuse contradictoire) ne
 * levent pas : ils sont traces et le message avance, car aucun rejeu ne les arrangerait.
 *
 * <p><b>Pourquoi le reessai est borne.</b> Un rejeu illimite tiendrait la partition
 * bloquee tant que le Workflow ne repond pas : plus aucun accuse ne serait traite,
 * y compris ceux d'etats sans rapport. Apres epuisement, l'accuse est abandonne avec
 * trace au prefixe {@code ACCUSE ABANDONNE} et le message avance. Ce qui est perdu est
 * une mise a jour de suivi, pas un paiement : l'etat reste {@code EN_ATTENTE} et
 * retrouvable par requete, exactement comme un etat non transmis l'est reste au Sprint
 * 5.1.
 *
 * <p>Ce reessai n'a rien de commun avec celui de la publication. La, rejouer risquait un
 * <b>double paiement</b> et etait donc reserve aux echecs anterieurs a tout envoi. Ici,
 * on rejoue une <b>lecture suivie d'une ecriture idempotente</b> : la repeter ne produit
 * aucune ecriture comptable.
 *
 * <h2>Un seul fil, et pourquoi l'ordre n'est pas le vrai filet</h2>
 *
 * <p>{@code concurrency = 1} et un seul identifiant de groupe. Kafka garantit qu'une
 * partition n'est lue que par un consommateur du groupe : si le module de
 * comptabilisation pose {@code idProcessus} en cle de partition, tous les accuses d'un
 * meme etat sont traites en serie et dans l'ordre. Le debit n'est pas un enjeu — quelques
 * accuses par mois et par unite.
 *
 * <p><b>Mais cette garantie est une supposition</b> : ce producteur n'est pas le notre et
 * l'equipe n'y a pas acces. S'il partitionne par un identifiant de lot d'envoi, deux
 * accuses du meme etat peuvent arriver dans le desordre malgre {@code concurrency = 1}.
 * Le vrai filet est donc ailleurs : dans le <b>refus strict de contradiction</b> — aucun
 * accuse ne fait regresser un statut, {@code INTEGRE} et {@code REJETE} sont definitifs —
 * et dans la transaction du service Workflow. Point consigne dans
 * {@code docs/points-en-attente.md}, a confirmer avec la DFT.
 *
 * <h2>{@code @EnableKafka}, et pourquoi il est ecrit ici</h2>
 *
 * <p><b>Defaut trouve a la verification manuelle du Sprint 5.2</b>, invisible aux tests : le
 * service demarrait normalement, mais <b>aucun consommateur ne s'abonnait au topic</b> — pas
 * une ligne dans le journal, pas une erreur. Sans {@code @EnableKafka}, l'annotation
 * {@link org.springframework.kafka.annotation.KafkaListener} n'est jamais traitee, et la
 * methode d'ecoute reste une methode ordinaire que personne n'appelle.
 *
 * <p>Le projet declare {@code spring-kafka} directement, sans
 * {@code spring-boot-starter-kafka} : l'auto-configuration qui poserait cette annotation
 * n'est pas entrainee. Cela n'avait jamais eu d'importance — c'est le <b>premier</b>
 * {@code @KafkaListener} du module, les six services n'ayant eu jusqu'ici que des
 * producteurs, qui n'en ont pas besoin. Meme famille de defaut que le bean
 * {@code ObjectMapper} absent au Sprint 5.1 : un cablage qui ne se voit qu'en assemblant, et
 * ici meme qu'en <b>demarrant</b>.
 *
 * <p>{@code CablageConsommateurAccuseTest.ecouteEffectivementEnregistree} en est le test de
 * non-regression : il ne verifie pas que les beans existent, mais que l'ecoute est
 * <b>enregistree</b>.
 */
@Configuration
@EnableKafka
public class ConfigurationConsommateurAccuse {

    /** Nouvelles tentatives apres le premier echec temporaire. */
    static final long TENTATIVES_SUPPLEMENTAIRES = 2L;

    /** Pause entre deux tentatives. */
    static final long PAUSE_ENTRE_TENTATIVES_MS = 2_000L;

    /**
     * Un seul fil : voir l'explication ci-dessus. Constante et non propriete, pour qu'on
     * ne puisse pas la relever depuis un fichier de deploiement sans relire le
     * raisonnement.
     */
    static final int NOMBRE_DE_FILS = 1;

    @Bean("consumerFactoryAccuse")
    public ConsumerFactory<String, String> consumerFactoryAccuse(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String brokers,
            @Value("${app.transmission.groupe-accuse}") String groupe) {

        Map<String, Object> configuration = new HashMap<>();
        configuration.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        configuration.put(ConsumerConfig.GROUP_ID_CONFIG, groupe);
        configuration.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configuration.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        // Un groupe qui s'abonne pour la premiere fois lit depuis le debut du topic.
        // Sinon, les accuses publies avant le premier demarrage du service seraient
        // perdus sans que rien ne le signale -- et le rejeu qu'ils produisent est sans
        // danger, le traitement etant idempotent.
        configuration.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // L'offset est valide par le conteneur apres traitement (AckMode.RECORD), jamais
        // en tache de fond sur une horloge : un acquittement automatique pourrait
        // survenir avant que le traitement n'aboutisse.
        configuration.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return new DefaultKafkaConsumerFactory<>(configuration);
    }

    @Bean("kafkaListenerContainerFactoryAccuse")
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactoryAccuse(
            ConsumerFactory<String, String> consumerFactoryAccuse) {

        ConcurrentKafkaListenerContainerFactory<String, String> fabrique =
                new ConcurrentKafkaListenerContainerFactory<>();
        fabrique.setConsumerFactory(consumerFactoryAccuse);
        fabrique.setConcurrency(NOMBRE_DE_FILS);
        fabrique.getContainerProperties().setAckMode(AckMode.RECORD);
        fabrique.setCommonErrorHandler(new DefaultErrorHandler(
                new FixedBackOff(PAUSE_ENTRE_TENTATIVES_MS, TENTATIVES_SUPPLEMENTAIRES)));
        return fabrique;
    }

}
