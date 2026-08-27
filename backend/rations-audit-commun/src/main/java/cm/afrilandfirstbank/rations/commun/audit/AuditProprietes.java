package cm.afrilandfirstbank.rations.commun.audit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Reglages de la publication d'audit, prefixe {@code rations.audit}.
 *
 * <p>Les valeurs par defaut ne sont pas neutres : ce sont elles qui font tenir
 * la garantie « l'audit ne fait jamais echouer le metier ». Les modifier sans
 * comprendre pourquoi elles sont la revient a la desactiver.
 *
 * @param topic destination des evenements. <b>Point DSI D-07</b>
 *        ({@code docs/points-en-attente.md}) : le nommage en environnement
 *        partage n'est pas arrete. La valeur de developpement n'apparait qu'ici
 *        et dans {@code infra/docker/kafka-topics.sh} ; le jour de la reponse
 *        DSI, ces deux points suffisent, et la surcharge par
 *        {@code RATIONS_AUDIT_TOPIC} evite meme d'y toucher.
 * @param blocageMaximal borne du {@code max.block.ms} du producteur Kafka.
 *        <b>Le reglage le plus important du module.</b> Par defaut Kafka bloque
 *        le thread appelant jusqu'a 60 secondes en attente des metadonnees du
 *        cluster : broker eteint, une attribution de role prendrait une minute
 *        avant d'aboutir. L'operation « n'echouerait » pas, mais serait
 *        inutilisable. Deux secondes bornent cette attente.
 * @param delaiDeLivraison {@code delivery.timeout.ms} : duree totale pendant
 *        laquelle Kafka reessaie avant d'abandonner. L'abandon survient sur le
 *        thread reseau du producteur, jamais sur le thread metier.
 * @param delaiDeRequete {@code request.timeout.ms}. Contrainte Kafka :
 *        {@code delivery.timeout.ms >= linger.ms + request.timeout.ms}.
 * @param threads taille du pool dedie a la publication. Un pool propre, non
 *        partage avec le trafic HTTP : une saturation de l'audit ne doit pas
 *        consommer les threads qui servent les utilisateurs.
 * @param capaciteFile profondeur de la file d'attente. Une fois pleine, les
 *        evenements sont abandonnes avec une trace, jamais mis en attente
 *        bloquante ni renvoyes a l'appelant.
 */
@ConfigurationProperties(prefix = "rations.audit")
public record AuditProprietes(
        String topic,
        Duration blocageMaximal,
        Duration delaiDeLivraison,
        Duration delaiDeRequete,
        Integer threads,
        Integer capaciteFile) {

    public static final String TOPIC_PAR_DEFAUT = "rations.audit.evenement";

    public AuditProprietes {
        topic = topic == null || topic.isBlank() ? TOPIC_PAR_DEFAUT : topic;
        blocageMaximal = blocageMaximal == null ? Duration.ofSeconds(2) : blocageMaximal;
        delaiDeLivraison = delaiDeLivraison == null ? Duration.ofSeconds(30) : delaiDeLivraison;
        delaiDeRequete = delaiDeRequete == null ? Duration.ofSeconds(10) : delaiDeRequete;
        threads = threads == null ? 2 : threads;
        capaciteFile = capaciteFile == null ? 500 : capaciteFile;

        // Kafka refuse de demarrer le producteur si cette contrainte est violee.
        // Mieux vaut le dire ici, avec un message qui nomme les deux reglages,
        // qu'au premier envoi sous une exception de configuration obscure.
        if (delaiDeLivraison.compareTo(delaiDeRequete) < 0) {
            throw new IllegalArgumentException(
                    "rations.audit.delai-de-livraison (%s) doit etre superieur ou egal a "
                            + "rations.audit.delai-de-requete (%s) : contrainte Kafka "
                            + "delivery.timeout.ms >= linger.ms + request.timeout.ms."
                                    .formatted(delaiDeLivraison, delaiDeRequete));
        }
    }

}
