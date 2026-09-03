package cm.afrilandfirstbank.rations.transmission.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.transmission.application.ResultatTraitementAccuse;
import cm.afrilandfirstbank.rations.transmission.application.TraitementAccuseService;
import cm.afrilandfirstbank.rations.transmission.domaine.AccuseComptableEvent;
import cm.afrilandfirstbank.rations.transmission.domaine.StatutIntegrationEnum;
import cm.afrilandfirstbank.rations.transmission.infrastructure.AccuseComptableConsumer.EchecTemporaireAccuse;

/**
 * Le consommateur lui-meme : ce qui le fait rejouer un message, et ce qui ne le fait
 * <b>jamais</b> tomber (guide 5.2 test 3 et section 10).
 *
 * <p>L'enjeu est simple a enoncer et grave a manquer : un message mal forme qui remonterait
 * en exception bloquerait la consommation de <b>tous les suivants</b>, accuses valides
 * compris, pour tous les etats.
 */
class AccuseComptableConsumerTest {

    private static final String TOPIC = "rations.etat.accuse";
    private static final Long ID = 740L;

    private final TraitementAccuseService traitementAccuseService =
            mock(TraitementAccuseService.class);
    private final PublicateurAudit publicateurAudit = mock(PublicateurAudit.class);

    private final AccuseComptableConsumer consommateur = new AccuseComptableConsumer(
            new ObjectMapper().registerModule(new JavaTimeModule()),
            traitementAccuseService,
            publicateurAudit);

    // --- Test 3 du guide ----------------------------------------------------------

    @Test
    @DisplayName("Test 3 — un message illisible ne fait pas tomber le consommateur, il est trace "
            + "et le message avance")
    void messageIllisibleNeFaitPasTomberLeConsommateur() {
        assertThatCode(() -> consommateur.consommer(message("{ceci n'est pas du JSON")))
                .doesNotThrowAnyException();

        // Ne pas lever, c'est ce qui fait avancer le message : le gestionnaire d'erreurs
        // ne rejoue que sur exception.
        verifyNoInteractions(traitementAccuseService);
    }

    @Test
    @DisplayName("L'illisibilite est tracee en audit, avec la position exacte du message sur le "
            + "broker : sans elle, personne ne pourrait le rejouer a la main")
    void illisibiliteTraceeAvecLaPosition() {
        consommateur.consommer(message("{ceci n'est pas du JSON"));

        ArgumentCaptor<EvenementAudit> capture = ArgumentCaptor.forClass(EvenementAudit.class);
        verify(publicateurAudit, times(1)).publier(capture.capture());

        EvenementAudit trace = capture.getValue();
        assertThat(trace.action()).isEqualTo("ACCUSE_COMPTABLE_REFUSE");
        assertThat(trace.idEntite()).isNull(); // aucun identifiant n'a pu etre lu
        assertThat(trace.detailJson())
                .contains("ACCUSE_ILLISIBLE")
                .contains(TOPIC)
                .contains("\"offset\"");
    }

    @Test
    @DisplayName("Les autres formes d'illisibilite ne font pas davantage tomber le consommateur")
    void toutesLesFormesDIllisibiliteSontAbsorbees() {
        for (String charge : new String[] { "", "   ", "null", "[]", "\"une chaine\"",
                "{\"idProcessus\":\"pas un nombre\"}", "<xml/>" }) {
            assertThatCode(() -> consommateur.consommer(message(charge)))
                    .describedAs("charge : %s", charge)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("Un message enorme est journalise tronque : un accuse malforme peut etre un "
            + "fichier entier publie par erreur, et saturer le journal serait pire")
    void messageEnormeTronque() {
        String enorme = "{".repeat(50_000);

        assertThatCode(() -> consommateur.consommer(message(enorme))).doesNotThrowAnyException();

        ArgumentCaptor<EvenementAudit> capture = ArgumentCaptor.forClass(EvenementAudit.class);
        verify(publicateurAudit).publier(capture.capture());
        // Le fragment brut ne va qu'au journal, jamais dans la trace d'audit : la
        // multiplier par anomalie noierait le motif.
        assertThat(capture.getValue().detailJson().length())
                .isLessThan(AccuseComptableConsumer.LONGUEUR_FRAGMENT_JOURNALISE * 4);
    }

    // --- Ce qui fait rejouer, et ce qui ne le fait pas -----------------------------

    @Test
    @DisplayName("Un echec temporaire — service Workflow injoignable — leve, et c'est la SEULE "
            + "chose qui fait rejouer un message")
    void echecTemporaireLeve() {
        when(traitementAccuseService.traiter(any(), anyString()))
                .thenReturn(new ResultatTraitementAccuse.EchecTemporaire(ID, "connexion refusee"));

        assertThatThrownBy(() -> consommateur.consommer(message(accuseValide())))
                .isInstanceOf(EchecTemporaireAccuse.class)
                .hasMessageContaining("740")
                .hasMessageContaining("connexion refusee");
    }

    @Test
    @DisplayName("Un refus definitif ne leve pas : le rejouer bloquerait la partition sans "
            + "aucune chance d'aboutir")
    void refusDefinitifNeLevePas() {
        when(traitementAccuseService.traiter(any(), anyString())).thenReturn(
                new ResultatTraitementAccuse.RefusDefinitif(ID, java.util.List.of(
                        cm.afrilandfirstbank.rations.transmission.application.AnomalieAccuse.de(
                                cm.afrilandfirstbank.rations.transmission.domaine
                                        .CodeAnomalieAccuseEnum.PROCESSUS_INCONNU,
                                "inconnu"))));

        assertThatCode(() -> consommateur.consommer(message(accuseValide())))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Une application et un rejeu ne levent pas davantage")
    void applicationEtRejeuNeLeventPas() {
        when(traitementAccuseService.traiter(any(), anyString()))
                .thenReturn(new ResultatTraitementAccuse.Applique(
                        ID, StatutIntegrationEnum.INTEGRE));
        assertThatCode(() -> consommateur.consommer(message(accuseValide())))
                .doesNotThrowAnyException();

        when(traitementAccuseService.traiter(any(), anyString()))
                .thenReturn(new ResultatTraitementAccuse.DejaApplique(
                        ID, StatutIntegrationEnum.INTEGRE));
        assertThatCode(() -> consommateur.consommer(message(accuseValide())))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Une exception imprevue du traitement est absorbee : elle ferait sinon rejouer "
            + "sans fin un message qui n'a aucune chance d'aboutir")
    void exceptionImprevueAbsorbee() {
        when(traitementAccuseService.traiter(any(), anyString()))
                .thenThrow(new IllegalStateException("defaut imprevu"));

        assertThatCode(() -> consommateur.consommer(message(accuseValide())))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Le topic tient lieu d'origine dans la trace : un message Kafka n'a pas "
            + "d'adresse IP d'appelant, et en inventer une serait pire")
    void leTopicTientLieuDOrigine() {
        when(traitementAccuseService.traiter(any(), anyString()))
                .thenReturn(new ResultatTraitementAccuse.Applique(
                        ID, StatutIntegrationEnum.INTEGRE));

        consommateur.consommer(message(accuseValide()));

        ArgumentCaptor<String> origine = ArgumentCaptor.forClass(String.class);
        verify(traitementAccuseService).traiter(any(AccuseComptableEvent.class),
                origine.capture());
        assertThat(origine.getValue()).isEqualTo(TOPIC);
    }

    // --- Outils -------------------------------------------------------------------

    private static ConsumerRecord<String, String> message(String charge) {
        return new ConsumerRecord<>(TOPIC, 0, 17L, String.valueOf(ID), charge);
    }

    private static String accuseValide() {
        return """
                {"idProcessus":740,"statutIntegration":"INTEGRE",\
                "referenceComptable":"CPT-2026-07-000512",\
                "dateTraitement":"2026-08-18T02:15:00Z"}""";
    }

}
