package cm.afrilandfirstbank.rations.transmission.infrastructure.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Producteur Kafka de l'echange comptable : celui qui publie l'etat valide sur
 * {@code rations.etat.valide} (contrat d'API section 7.1).
 *
 * <h2>Deux producteurs dans ce service, a ne surtout pas confondre</h2>
 *
 * <p>Ce service porte <b>deux</b> {@code KafkaTemplate} : celui-ci, et celui du journal
 * d'audit fourni par {@code rations-audit-commun} sous le nom {@code kafkaTemplateAudit}.
 * D'ou le qualificatif {@code kafkaTemplateEtatValide} : deux beans du meme type sans
 * nom rendraient l'injection ambigue, et surtout, injecter le mauvais publierait les
 * evenements d'audit sur le topic comptable — ou l'inverse.
 *
 * <p>Les reglages du producteur d'audit ne sont <b>jamais</b> touches ici, ni par
 * {@code spring.kafka.producer.*} : ce sont eux qui garantissent que l'audit ne fait
 * jamais echouer le metier (CLAUDE.md section 15). Les deux producteurs sont construits
 * chacun a partir de sa propre carte de configuration, et ne partagent que l'adresse des
 * brokers.
 *
 * <h2>Pourquoi les reglages sont en Java et non dans le YAML</h2>
 *
 * <p>Parce qu'ils ne sont pas des reglages d'environnement mais des <b>garanties</b> :
 * l'acquittement de toutes les repliques, l'idempotence du producteur, les bornes de
 * temps. Les laisser en YAML les rendrait modifiables par un fichier de deploiement,
 * sans que personne ne relise le raisonnement qui les justifie. Seule l'adresse des
 * brokers vient de l'environnement.
 *
 * <h2>acks=all : un message perdu est un salaire non verse</h2>
 *
 * <p>Le broker ne confirme qu'apres ecriture sur <b>toutes les repliques
 * synchronisees</b>. Avec {@code acks=1}, un leader qui tombe avant la recopie perdrait
 * un message que le producteur a cru recu : l'etat serait marque transmis, donc fige et
 * plus modifiable, sans que la comptabilite l'ait jamais vu. Les quelques millisecondes
 * gagnees n'ont aucun sens dans un geste mensuel. C'est aussi le reglage du producteur
 * d'audit (Sprint 1.3) : une seule convention dans le module. Cout nul en developpement,
 * ou le facteur de replication vaut 1.
 *
 * <h2>Idempotence : les reessais du producteur ne dupliquent pas</h2>
 *
 * <p>{@code enable.idempotence=true} — explicite plutot que subi par defaut. Sans elle,
 * un reessai interne du client apres un acquittement perdu en chemin ecrirait le message
 * <b>deux fois</b> sur le topic : la comptabilite produirait deux jeux d'ecritures pour
 * les memes beneficiaires, exactement ce que RG-13 interdit. Le controle applicatif
 * d'unicite (sous-sprint 5.3) ne verrait rien : de son point de vue, une seule
 * publication a eu lieu.
 *
 * <h2>Serialisation en chaine, pas en objet</h2>
 *
 * <p>Cle et valeur en {@link StringSerializer}, la charge etant convertie en JSON par
 * {@code EtatValideProducer}. On n'utilise <b>pas</b> le {@code JsonSerializer} de
 * spring-kafka : il ajoute par defaut des en-têtes de type portant le <b>nom de classe
 * Java</b> de la charge, que le module de comptabilisation — ecrit par une autre equipe,
 * dans une autre technologie peut-etre — n'a aucune raison de connaitre. Le contrat
 * d'API section 7.1 decrit un objet JSON, pas un objet Java serialise. Meme choix que
 * {@code rations-audit-commun}.
 *
 * <h2>Bornes de temps : nettement plus serrees que celles de l'audit, et pourquoi</h2>
 *
 * <p>Le producteur d'audit accorde 30 s a la livraison ({@code AuditProprietes}). Il le
 * peut : il publie sans attendre, sur un pool separe, et personne ne patiente derriere
 * lui. Ici, <b>un fil HTTP attend</b> — celui du valideur qui vient de cloturer — car
 * c'est le seul moment ou un humain voit qu'un etat n'est pas parti. Reprendre 30 s
 * bloquerait ce fil dix fois trop longtemps.
 *
 * <p>D'ou le budget suivant, arrete au Sprint 5.1 apres calcul du pire cas :
 *
 * <table>
 *   <tr><td>{@code max.block.ms}</td><td>3 s</td><td>attente des metadonnees du topic</td></tr>
 *   <tr><td>{@code request.timeout.ms}</td><td>3 s</td><td>attente d'une reponse du broker</td></tr>
 *   <tr><td>{@code delivery.timeout.ms}</td><td>6 s</td><td>duree totale de livraison</td></tr>
 *   <tr><td>attente de l'accuse</td><td>7 s</td><td>{@code EtatValideProducer}</td></tr>
 * </table>
 *
 * <p>Le pire cas d'un aller-retour complet de transmission — en-tete, detail, publication,
 * les trois a leur limite en meme temps — est donc de <b>17 s</b> : 5 s + 5 s + 7 s. Sans
 * ces bornes, broker eteint, un seul envoi gelerait le fil appelant jusqu'a soixante
 * secondes (defaut Kafka).
 */
@Configuration
public class ConfigurationProducteurEtatValide {

    /** Attente maximale des metadonnees du topic avant de renoncer. */
    static final int BLOCAGE_MAXIMAL_MS = 3_000;

    /** Duree totale accordee a la livraison, reessais internes du client compris. */
    static final int DELAI_DE_LIVRAISON_MS = 6_000;

    /** Attente d'une reponse du broker pour une requete donnee. */
    static final int DELAI_DE_REQUETE_MS = 3_000;

    @Bean("producerFactoryEtatValide")
    public ProducerFactory<String, String> producerFactoryEtatValide(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String brokers) {

        Map<String, Object> configuration = new HashMap<>();
        configuration.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        configuration.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configuration.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // L'etat valide part vers la comptabilite : on veut l'accuse de toutes les
        // repliques, pas seulement du leader.
        configuration.put(ProducerConfig.ACKS_CONFIG, "all");

        // Un reessai interne du client ne doit jamais produire un second message.
        configuration.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        configuration.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, BLOCAGE_MAXIMAL_MS);
        configuration.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, DELAI_DE_LIVRAISON_MS);
        configuration.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, DELAI_DE_REQUETE_MS);

        return new DefaultKafkaProducerFactory<>(configuration);
    }

    /**
     * Convertisseur JSON de la charge comptable, <b>construit ici et non injecte</b>.
     *
     * <h2>Pourquoi il est construit</h2>
     *
     * <p>Raison immediate : Spring Boot 4 n'auto-configure aucun bean
     * {@code com.fasterxml.jackson.databind.ObjectMapper} — la classe est bien au
     * classpath, tiree par des dependances tierces, mais aucun bean n'existe. Le service
     * refusait de demarrer, ce qu'aucun test ne pouvait montrer : ils construisent leur
     * propre convertisseur.
     *
     * <p>Raison de fond, qui vaut meme si un bean reapparaissait : <b>la forme du message
     * qui part en comptabilite ne doit pas dependre d'un reglage partage</b>. Un
     * {@code spring.jackson.*} pose dans un fichier de deploiement — pour l'affichage d'une
     * API, par exemple — changerait silencieusement ce que recoit un module auquel l'equipe
     * n'a pas acces. Meme raisonnement que pour {@code acks} et les bornes de temps :
     * ce sont des garanties, pas des reglages d'environnement.
     *
     * <h2>Les deux reglages, et pourquoi ils sont la</h2>
     *
     * <p>{@code JavaTimeModule} et {@code WRITE_DATES_AS_TIMESTAMPS} desactive : le contrat
     * d'API section 1.1 exige des dates ISO 8601, et le defaut de Jackson serialise une date
     * en tableau de composants ({@code [2026,9,1]}). C'est exactement le defaut releve et
     * corrige au Sprint 2.2 dans {@code DeltaAudit}. La charge de la section 7.1 ne porte
     * aujourd'hui aucune date — mais le Sprint 5.2 en ajoutera une a l'accuse, et le reglage
     * doit etre en place avant, pas apres.
     *
     * <p>Qualifie par son nom : si un bean {@code ObjectMapper} apparaissait un jour dans ce
     * service, l'injection resterait sans ambiguite et la charge comptable garderait sa
     * propre configuration.
     */
    @Bean("convertisseurChargeComptable")
    public ObjectMapper convertisseurChargeComptable() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean("kafkaTemplateEtatValide")
    public KafkaTemplate<String, String> kafkaTemplateEtatValide(
            @Qualifier("producerFactoryEtatValide") ProducerFactory<String, String> fabrique) {
        return new KafkaTemplate<>(fabrique);
    }

}
