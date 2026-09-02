package cm.afrilandfirstbank.rations.transmission.infrastructure;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import cm.afrilandfirstbank.rations.transmission.application.PublicateurEtatValide;
import cm.afrilandfirstbank.rations.transmission.application.ResultatPublication;
import cm.afrilandfirstbank.rations.transmission.application.ResultatPublication.PublicationEchouee;
import cm.afrilandfirstbank.rations.transmission.application.ResultatPublication.Publiee;
import cm.afrilandfirstbank.rations.transmission.domaine.EtatValideEvent;

/**
 * Publie l'etat valide sur {@code rations.etat.valide} et <b>attend l'accuse du
 * broker</b> (contrat d'API section 7.1, US-12).
 *
 * <h2>Attendue, contrairement a la publication d'audit</h2>
 *
 * <p>{@code rations-audit-commun} publie sans attendre : une trace perdue vaut mieux
 * qu'une operation metier bloquee. Ici, c'est l'inverse. Le resultat de cette methode
 * commande l'ecriture du drapeau {@code transmis_comptabilite} (RG-13) : le poser sans
 * savoir si l'evenement est parti figerait un etat repute transmis et jamais paye, et le
 * controle d'unicite refuserait ensuite la vraie transmission comme un doublon. On
 * attend donc, dans une borne de temps, et l'on rend un oui ou un non.
 *
 * <h2>La cle de partition est l'identifiant du processus</h2>
 *
 * <p>Kafka ne garantit l'ordre qu'a l'interieur d'une partition, et deux messages de
 * meme cle y tombent ensemble. Prendre l'identifiant du processus garantit donc que tout
 * ce qui concerne un meme etat arrive dans l'ordre d'envoi — ce qui comptera des qu'un
 * etat pourra donner lieu a plusieurs messages : reprise apres incident, ou etat
 * complementaire du Sprint 6bis. Le code unite aurait concentre l'activite d'une grosse
 * agence sur une seule partition, pour un ordre entre dossiers independants qui ne
 * signifie rien.
 *
 * <h2>Aucune exception ne sort d'ici</h2>
 *
 * <p>Toutes les issues sont ramenees a {@link ResultatPublication} : une panne de broker
 * n'est pas un incident de programmation et n'a pas a remonter en pile jusqu'a une
 * reponse HTTP. Le service applicatif decide de la suite, avec un type scelle et un
 * {@code switch} exhaustif.
 *
 * <p><b>Y compris les echecs synchrones.</b> {@code send()} ne rend pas toujours un futur :
 * broker injoignable, il <b>leve des l'appel</b> un {@code KafkaException} enveloppant le
 * depassement de {@code max.block.ms}. Capturer les seules exceptions du futur laissait
 * celui-la remonter jusqu'au filet general du gestionnaire d'erreurs, qui rendait
 * {@code 500 ERREUR_INTERNE} au lieu du {@code 503 PUBLICATION_ECHOUEE} prevu — sans nommer
 * la cause ni ecrire le prefixe de supervision. Releve a la verification manuelle du
 * Sprint 5.1, broker arrete.
 *
 * <h2>Le nom du topic n'est pas ecrit ici</h2>
 *
 * <p>Il vient d'une propriete, avec repli sur le nom de developpement du Sprint 0.5. Le
 * nommage definitif en environnement partage reste un point en attente DSI (D-07) :
 * CLAUDE.md section 9 demande de ne pas le figer ailleurs que dans la configuration.
 */
@Component
public class EtatValideProducer implements PublicateurEtatValide {

    private static final Logger journal = LoggerFactory.getLogger(EtatValideProducer.class);

    /**
     * Prefixe reperable dans les journaux, sur le modele de {@code AUDIT PERDU}
     * ({@code rations-audit-commun}) et {@code INCOHERENCE GRILLE} (Sprint 2.4). Une
     * supervision doit pouvoir alerter sur cette chaine sans connaitre le code.
     */
    private static final String PREFIXE_ECHEC = "PUBLICATION ETAT VALIDE EN ECHEC";

    /**
     * Attente maximale de l'accuse, <b>7 s</b>. Superieure d'une seconde au delai de
     * livraison du producteur (6 s), pour que ce soit <b>le producteur</b> qui rende son
     * verdict — avec son motif — plutot que cette attente qui le tranche a sa place par un
     * delai depasse sans explication.
     *
     * <p>Le budget est serre parce qu'un fil HTTP attend derriere : celui du valideur qui
     * vient de cloturer (arbitrage Sprint 5.1, etape 7). Voir
     * {@code ConfigurationProducteurEtatValide} pour le calcul du pire cas.
     */
    private static final long ATTENTE_ACCUSE_SECONDES = 7;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper convertisseurJson;
    private final String topic;

    /**
     * @param kafkaTemplate <b>celui de l'echange comptable</b>, qualifie explicitement.
     *        Sans ce qualificatif, Spring pourrait injecter {@code kafkaTemplateAudit} —
     *        les etats valides partiraient alors sur le topic du journal d'audit, ou
     *        personne ne les attend
     * @param convertisseurJson <b>celui de la charge comptable</b>, construit par
     *        {@code ConfigurationProducteurEtatValide} et qualifie. Spring Boot 4
     *        n'auto-configure aucun {@code ObjectMapper} de ce type, et surtout : la forme du
     *        message qui part en comptabilite ne doit pas dependre d'un {@code spring.jackson.*}
     *        pose dans un fichier de deploiement
     */
    public EtatValideProducer(
            @Qualifier("kafkaTemplateEtatValide") KafkaTemplate<String, String> kafkaTemplate,
            @Qualifier("convertisseurChargeComptable") ObjectMapper convertisseurJson,
            @Value("${app.transmission.topic-etat-valide:rations.etat.valide}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.convertisseurJson = convertisseurJson;
        this.topic = topic;
    }

    @Override
    public ResultatPublication publier(EtatValideEvent charge) {
        String cle = clePartition(charge);
        String corps;

        try {
            corps = convertisseurJson.writeValueAsString(charge);
        } catch (JsonProcessingException serialisationImpossible) {
            // Anomalie de programmation plutot que panne d'infrastructure, mais la
            // consequence est la meme et la reponse aussi : rien ne part, on le dit.
            journal.error("{} : la charge du processus {} n'a pas pu etre convertie en JSON. "
                            + "Rien n'a ete publie.",
                    PREFIXE_ECHEC, charge.idProcessus(), serialisationImpossible);
            return new PublicationEchouee(
                    "serialisation JSON impossible : " + serialisationImpossible.getMessage());
        }

        try {
            SendResult<String, String> accuse = kafkaTemplate.send(topic, cle, corps)
                    .get(ATTENTE_ACCUSE_SECONDES, TimeUnit.SECONDS);

            var metadonnees = accuse.getRecordMetadata();
            journal.info("Etat valide {} publie sur {} (partition {}, offset {}), {} ligne(s), "
                            + "{} FCFA.",
                    charge.idProcessus(), metadonnees.topic(), metadonnees.partition(),
                    metadonnees.offset(), charge.lignes().size(), charge.montantTotal());

            return new Publiee(metadonnees.topic(), metadonnees.partition(), metadonnees.offset());

        } catch (InterruptedException interruption) {
            // Le drapeau d'interruption se repose : l'avaler laisserait un fil qui ne
            // sait plus qu'on lui a demande de s'arreter.
            Thread.currentThread().interrupt();
            journal.error("{} : attente de l'accuse interrompue pour le processus {}. On ignore "
                    + "si le message est parti.", PREFIXE_ECHEC, charge.idProcessus());
            return new PublicationEchouee("attente de l'accuse interrompue");

        } catch (TimeoutException delaiDepasse) {
            journal.error("{} : aucun accuse du broker en {} s pour le processus {}.",
                    PREFIXE_ECHEC, ATTENTE_ACCUSE_SECONDES, charge.idProcessus(), delaiDepasse);
            return new PublicationEchouee(
                    "aucun accuse du broker en " + ATTENTE_ACCUSE_SECONDES + " s");

        } catch (ExecutionException echecDeLivraison) {
            Throwable cause = echecDeLivraison.getCause() == null
                    ? echecDeLivraison
                    : echecDeLivraison.getCause();
            journal.error("{} : le broker a refuse ou n'a pas pu recevoir l'etat {} sur le topic "
                    + "{} : {}", PREFIXE_ECHEC, charge.idProcessus(), topic, cause.getMessage(),
                    cause);
            return new PublicationEchouee(cause.getClass().getSimpleName() + " : "
                    + cause.getMessage());

        } catch (RuntimeException echecSynchrone) {
            // FILET INDISPENSABLE, ET NON UNE PRECAUTION DE STYLE.
            //
            // send() ne rend pas toujours un futur : broker injoignable, il LEVE, des
            // l'appel, un KafkaException enveloppant le depassement de max.block.ms
            // (« Topic ... not present in metadata after 3000 ms »). Les trois captures
            // ci-dessus ne visent que les exceptions du futur et le laissaient passer.
            //
            // Constate a la verification manuelle du Sprint 5.1, broker arrete : le service
            // rendait 500 ERREUR_INTERNE par le filet general du gestionnaire d'erreurs, au
            // lieu du 503 PUBLICATION_ECHOUEE prevu. Le classement restait prudent -- donc
            // aucun risque de double paiement --, mais le message ne nommait plus la cause,
            // et le prefixe de supervision n'etait pas ecrit.
            Throwable cause = echecSynchrone.getCause() == null
                    ? echecSynchrone
                    : echecSynchrone.getCause();
            journal.error("{} : l'envoi de l'etat {} sur le topic {} a echoue des l'appel : {}",
                    PREFIXE_ECHEC, charge.idProcessus(), topic, cause.getMessage(), echecSynchrone);
            return new PublicationEchouee(cause.getClass().getSimpleName() + " : "
                    + cause.getMessage());
        }
    }

    /**
     * Identifiant du processus, en chaine. Jamais {@code null} en pratique : la charge a
     * ete controlee avant d'arriver ici, et {@code IDENTIFIANT_ABSENT} l'aurait refusee.
     * Le repli existe pour que ce producteur ne depende pas de cette garantie — une cle
     * nulle fait retomber Kafka sur la repartition tournante, ce qui reste preferable a
     * une exception au moment de publier.
     */
    private String clePartition(EtatValideEvent charge) {
        return charge.idProcessus() == null ? null : String.valueOf(charge.idProcessus());
    }

}
