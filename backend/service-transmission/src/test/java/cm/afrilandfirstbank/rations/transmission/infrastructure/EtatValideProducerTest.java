package cm.afrilandfirstbank.rations.transmission.infrastructure;

import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cm.afrilandfirstbank.rations.transmission.infrastructure.config.ConfigurationProducteurEtatValide;
import cm.afrilandfirstbank.rations.transmission.application.ResultatPublication;
import cm.afrilandfirstbank.rations.transmission.application.ResultatPublication.EchecAvantEnvoi;
import cm.afrilandfirstbank.rations.transmission.application.ResultatPublication.EchecIssueIncertaine;
import cm.afrilandfirstbank.rations.transmission.application.ResultatPublication.Publiee;
import cm.afrilandfirstbank.rations.transmission.domaine.EtatValideEvent;
import cm.afrilandfirstbank.rations.transmission.domaine.EtatValideEvent.LigneEtat;
import cm.afrilandfirstbank.rations.transmission.domaine.EtatValideEvent.Periode;

/**
 * Ce qui part reellement sur le topic.
 *
 * <p>Le test de {@code ConstructionChargeService} prouve que la charge est complete et
 * coherente ; celui-ci prouve qu'elle arrive au broker <b>sous la forme que le contrat
 * decrit</b>. Ce sont deux choses differentes : une charge parfaite serialisee sous d'autres
 * noms de champs serait rejetee, ou pire, mal interpretee par un module que l'equipe ne
 * controle pas et auquel elle n'a pas acces.
 *
 * <p>Le JSON est donc confronte, champ par champ, a l'exemple du contrat d'API section 7.1.
 */
@DisplayName("EtatValideProducer — ce qui part sur rations.etat.valide")
class EtatValideProducerTest {

    private static final String TOPIC = "rations.etat.valide";

    /**
     * La charge de l'exemple du contrat d'API section 7.1, a la virgule pres. Unite
     * {@code 00002} a la racine — la ligne de debit — et agence {@code 00002} sur la ligne —
     * la ligne de credit : ici les deux coincident, comme dans l'exemple.
     */
    private static final EtatValideEvent CHARGE_DU_CONTRAT = new EtatValideEvent(
            740L,
            EtatValideEvent.VERSION_COURANTE,
            new Periode(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12),
                    "DU 06/07/2026 AU 12/07/2026"),
            "00002",
            "64380090200",
            "NORMAL",
            84_000L,
            List.of(new LigneEtat("Mbarga", "Jean", "03702099911", "00002",
                    "RATION", "JOUR", 2_500)));

    private KafkaTemplate<String, String> kafkaTemplate;
    private EtatValideProducer producteur;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void preparer() {
        kafkaTemplate = mock(KafkaTemplate.class);
        // LE convertisseur de production, pas une copie. Construire ici un
        // `new ObjectMapper()` nu reintroduirait exactement la divergence que le
        // Sprint 5.1 avait deja payee : le test passerait sur une forme de message
        // que la production ne produit pas. Depuis la Maille 2, la charge porte des
        // LocalDate, et un mapper sans JavaTimeModule echoue a les serialiser.
        producteur = new EtatValideProducer(
                kafkaTemplate, new ConfigurationProducteurEtatValide().convertisseurChargeComptable(),
                TOPIC);
    }

    private void brokerAccepte(int partition, long offset) {
        ProducerRecord<String, String> enregistrement =
                new ProducerRecord<>(TOPIC, partition, null, "corps");
        RecordMetadata metadonnees = new RecordMetadata(
                new TopicPartition(TOPIC, partition), offset, 0, 0L, 0, 0);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(
                        new SendResult<>(enregistrement, metadonnees)));
    }

    private void brokerRefuse(Throwable cause) {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(cause));
    }

    private JsonNode corpsPublie() throws Exception {
        ArgumentCaptor<String> corps = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(kafkaTemplate)
                .send(anyString(), anyString(), corps.capture());
        return new ConfigurationProducteurEtatValide().convertisseurChargeComptable()
                .readTree(corps.getValue());
    }

    // --- La forme du message ------------------------------------------------------

    @Nested
    @DisplayName("Le JSON publie est celui du contrat section 7.1")
    class FormeDuMessage {

        @Test
        @DisplayName("la racine porte les huit cles du contrat v2, et rien d'autre")
        void racineConformeAuContrat() throws Exception {
            brokerAccepte(0, 42L);

            producteur.publier(CHARGE_DU_CONTRAT);
            JsonNode racine = corpsPublie();

            assertThat(racine.get("idProcessus").asLong()).isEqualTo(740L);
            assertThat(racine.get("versionCharge").asInt()).isEqualTo(2);
            assertThat(racine.get("periode").get("dateDebut").asText())
                    .as("ISO 8601, convention du module — et non un tableau de composants")
                    .isEqualTo("2026-07-06");
            assertThat(racine.get("periode").get("dateFin").asText()).isEqualTo("2026-07-12");
            assertThat(racine.get("periode").get("libelle").asText())
                    .as("le fragment que la comptabilite prefixe ; il se lit sur un releve")
                    .isEqualTo("DU 06/07/2026 AU 12/07/2026");
            assertThat(racine.get("codeUnite").asText()).isEqualTo("00002");
            assertThat(racine.get("compteCharge").asText()).isEqualTo("64380090200");
            assertThat(racine.get("typeProcessus").asText()).isEqualTo("NORMAL");
            assertThat(racine.get("montantTotal").asLong()).isEqualTo(84_000L);
            assertThat(racine.get("lignes").isArray()).isTrue();

            assertThat(racine.fieldNames()).toIterable().containsExactlyInAnyOrder(
                    "idProcessus", "versionCharge", "periode", "codeUnite", "compteCharge",
                    "typeProcessus", "montantTotal", "lignes");
        }

        @Test
        @DisplayName("chaque ligne porte les sept cles du contrat, et rien d'autre")
        void ligneConformeAuContrat() throws Exception {
            brokerAccepte(0, 42L);

            producteur.publier(CHARGE_DU_CONTRAT);
            JsonNode ligne = corpsPublie().get("lignes").get(0);

            assertThat(ligne.get("nom").asText()).isEqualTo("Mbarga");
            assertThat(ligne.get("prenom").asText()).isEqualTo("Jean");
            assertThat(ligne.get("numCompteCourant").asText()).isEqualTo("03702099911");
            assertThat(ligne.get("codeAgence").asText()).isEqualTo("00002");
            assertThat(ligne.get("nature").asText()).isEqualTo("RATION");
            assertThat(ligne.get("session").asText()).isEqualTo("JOUR");
            assertThat(ligne.get("montant").asInt()).isEqualTo(2_500);

            assertThat(ligne.fieldNames()).toIterable().containsExactlyInAnyOrder(
                    "nom", "prenom", "numCompteCourant", "codeAgence", "nature", "session",
                    "montant");
        }

        /**
         * Le {@code JsonSerializer} de spring-kafka ajouterait des en-tetes de type portant le
         * nom de classe Java de la charge. Le module de comptabilisation — autre equipe,
         * peut-etre autre technologie — n'a aucune raison de connaitre
         * {@code cm.afrilandfirstbank...}. On serialise donc en chaine, a la main.
         */
        @Test
        @DisplayName("aucun nom de classe Java ne fuit dans le message")
        void aucuneFuiteDeTypeJava() throws Exception {
            brokerAccepte(0, 42L);

            producteur.publier(CHARGE_DU_CONTRAT);

            ArgumentCaptor<String> corps = ArgumentCaptor.forClass(String.class);
            org.mockito.Mockito.verify(kafkaTemplate)
                    .send(anyString(), anyString(), corps.capture());

            assertThat(corps.getValue())
                    .doesNotContain("cm.afrilandfirstbank")
                    .doesNotContain("EtatValideEvent");
        }
    }

    // --- La cle de partition ------------------------------------------------------

    @Nested
    @DisplayName("Cle de partition")
    class ClePartition {

        /**
         * Kafka ne garantit l'ordre qu'a l'interieur d'une partition, et deux messages de meme
         * cle y tombent ensemble. L'identifiant du processus garantit donc que tout ce qui
         * concerne un meme etat arrive dans l'ordre d'envoi.
         */
        @Test
        @DisplayName("c'est l'identifiant du processus, sur le topic configure")
        void cleEgaleIdentifiantDuProcessus() {
            brokerAccepte(0, 42L);

            producteur.publier(CHARGE_DU_CONTRAT);

            ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> cle = ArgumentCaptor.forClass(String.class);
            org.mockito.Mockito.verify(kafkaTemplate)
                    .send(topic.capture(), cle.capture(), anyString());

            assertThat(topic.getValue()).isEqualTo(TOPIC);
            assertThat(cle.getValue()).isEqualTo("740");
        }
    }

    // --- Les issues ---------------------------------------------------------------

    @Nested
    @DisplayName("Le resultat dit oui ou non, jamais peut-etre")
    class Issues {

        @Test
        @DisplayName("accuse du broker : la position du message est rendue")
        void accuseRendu() {
            brokerAccepte(3, 512L);

            ResultatPublication resultat = producteur.publier(CHARGE_DU_CONTRAT);

            assertThat(resultat).isInstanceOf(Publiee.class);
            Publiee publiee = (Publiee) resultat;
            assertThat(publiee.topic()).isEqualTo(TOPIC);
            assertThat(publiee.partition()).isEqualTo(3);
            assertThat(publiee.offset()).isEqualTo(512L);
        }

        /**
         * Une panne de broker n'est pas un incident de programmation : elle n'a pas a remonter
         * en pile. Le service applicatif decide de la suite sur un type scelle.
         */
        /**
         * <b>Le cas que la verification manuelle a pris en defaut.</b>
         *
         * <p>Broker injoignable, {@code send()} ne rend pas un futur en echec : il <b>leve
         * des l'appel</b> un {@code KafkaException} enveloppant le depassement de
         * {@code max.block.ms}. Capturer les seules exceptions du futur laissait celui-la
         * remonter jusqu'au filet general du gestionnaire d'erreurs, qui rendait
         * {@code 500 ERREUR_INTERNE} au lieu du {@code 503 PUBLICATION_ECHOUEE} prevu.
         *
         * <p>Le classement restait prudent — donc aucun risque de double paiement —, mais le
         * message ne nommait plus la cause et le prefixe de supervision n'etait pas ecrit.
         */
        @Test
        @DisplayName("send() qui leve des l'appel : un echec motive, jamais une exception")
        void echecSynchroneSansException() {
            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .thenThrow(new org.springframework.kafka.KafkaException("Send failed",
                            new org.apache.kafka.common.errors.TimeoutException(
                                    "Topic rations.etat.valide not present in metadata after 3000 ms.")));

            ResultatPublication resultat = producteur.publier(CHARGE_DU_CONTRAT);

            // Sprint 5.3 : ce cas-la est PROUVE anterieur a tout envoi — le message n'a
            // jamais ete remis au client Kafka. C'est le seul echec qui autorise a liberer
            // la reservation du verrou de RG-13, donc le seul dont une reprise est sure.
            assertThat(resultat).isInstanceOf(EchecAvantEnvoi.class);
            assertThat(((EchecAvantEnvoi) resultat).motifTechnique())
                    .contains("TimeoutException")
                    .contains("not present in metadata");
        }

        @Test
        @DisplayName("broker muet : un echec motive, jamais une exception")
        void echecSansException() {
            brokerRefuse(new org.apache.kafka.common.errors.TimeoutException(
                    "Expiring 1 record(s) for rations.etat.valide"));

            ResultatPublication resultat = producteur.publier(CHARGE_DU_CONTRAT);

            // Echec du futur : le message a ete remis au client Kafka et l'on ignore ce
            // qu'il en est advenu. La reservation reste posee, on ne rejoue pas.
            assertThat(resultat).isInstanceOf(EchecIssueIncertaine.class);
            assertThat(((EchecIssueIncertaine) resultat).motifTechnique())
                    .contains("TimeoutException");
        }
    }

}
