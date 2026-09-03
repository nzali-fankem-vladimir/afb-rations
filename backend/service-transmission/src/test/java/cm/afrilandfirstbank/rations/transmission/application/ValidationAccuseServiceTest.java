package cm.afrilandfirstbank.rations.transmission.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import cm.afrilandfirstbank.rations.transmission.application.ResultatValidationAccuse.AccuseInvalide;
import cm.afrilandfirstbank.rations.transmission.application.ResultatValidationAccuse.AccuseRecevable;
import cm.afrilandfirstbank.rations.transmission.domaine.AccuseComptableEvent;
import cm.afrilandfirstbank.rations.transmission.domaine.CodeAnomalieAccuseEnum;
import cm.afrilandfirstbank.rations.transmission.domaine.StatutIntegrationEnum;

/**
 * Le controle de la charge recue sur {@code rations.etat.accuse} (guide 5.2 etape 2,
 * tests 6 et 7).
 *
 * <p>Ce que ces tests protegent : un accuse mal forme ne doit ni faire tomber le
 * consommateur, ni etre applique a moitie, ni se confondre avec un « message illisible »
 * quand il est parfaitement lisible mais faux.
 */
class ValidationAccuseServiceTest {

    private final ValidationAccuseService service = new ValidationAccuseService();

    private static final String DATE_ISO = "2026-08-18T02:15:00Z";

    // --- Nominal ------------------------------------------------------------------

    @Test
    @DisplayName("Un accuse d'integration conforme au contrat section 7.2 est recevable")
    void accuseIntegreConforme() {
        AccuseComptableEvent accuse = new AccuseComptableEvent(
                740L, "INTEGRE", "CPT-2026-07-000512", DATE_ISO, null);

        ResultatValidationAccuse resultat = service.valider(accuse);

        assertThat(resultat).isInstanceOf(AccuseRecevable.class);
        AccuseRecevable recevable = (AccuseRecevable) resultat;
        assertThat(recevable.idProcessus()).isEqualTo(740L);
        assertThat(recevable.statutIntegration()).isEqualTo(StatutIntegrationEnum.INTEGRE);
        assertThat(recevable.referenceComptable()).isEqualTo("CPT-2026-07-000512");
        assertThat(recevable.dateTraitement())
                .isEqualTo(OffsetDateTime.of(2026, 8, 18, 2, 15, 0, 0, ZoneOffset.UTC));
        assertThat(recevable.motif()).isNull();
    }

    @Test
    @DisplayName("Un accuse de rejet motive est recevable, et le motif est conserve")
    void accuseRejeteAvecMotif() {
        AccuseComptableEvent accuse = new AccuseComptableEvent(
                740L, "REJETE", null, DATE_ISO, "Compte 00002000123456 clos depuis le 12/07.");

        AccuseRecevable recevable = (AccuseRecevable) service.valider(accuse);

        assertThat(recevable.statutIntegration()).isEqualTo(StatutIntegrationEnum.REJETE);
        assertThat(recevable.motif()).isEqualTo("Compte 00002000123456 clos depuis le 12/07.");
    }

    @Test
    @DisplayName("Le motif est lu sous ses trois noms : le contrat ne le nomme pas dans son "
            + "exemple JSON, et un desaccord de nom perdrait TOUT motif de rejet")
    void motifAccepteSousSesAlias() throws Exception {
        // Le test doit passer par Jackson : les alias sont une affaire de
        // deserialisation, et construire le record a la main ne prouverait rien.
        // Sans eux, un producteur nommant le champ autrement ferait refuser tous les
        // accuses de rejet pour MOTIF_REJET_ABSENT, et le motif du refus comptable
        // serait perdu.
        for (String nomDuChamp : new String[] { "motif", "motifRejet", "motifIntegration" }) {
            String json = """
                    {"idProcessus":740,"statutIntegration":"REJETE","dateTraitement":"2026-08-18T02:15:00Z","%s":"Compte clos."}"""
                    .formatted(nomDuChamp);

            AccuseComptableEvent accuse =
                    new ObjectMapper().readValue(json, AccuseComptableEvent.class);
            AccuseRecevable recevable = (AccuseRecevable) service.valider(accuse);

            assertThat(recevable.motif())
                    .describedAs("motif lu sous le nom %s", nomDuChamp)
                    .isEqualTo("Compte clos.");
        }
    }

    @Test
    @DisplayName("Un champ inconnu ne fait pas echouer la lecture : le module de comptabilisation "
            + "doit pouvoir enrichir son accuse sans casser ce consommateur")
    void champInconnuIgnore() throws Exception {
        String json = """
                {"idProcessus":740,"statutIntegration":"INTEGRE","referenceComptable":"CPT-1","dateTraitement":"2026-08-18T02:15:00Z","numeroDeLot":"LOT-2026-08-014","operateur":"SYS"}""";

        AccuseComptableEvent accuse =
                new ObjectMapper().readValue(json, AccuseComptableEvent.class);

        assertThat(service.valider(accuse)).isInstanceOf(AccuseRecevable.class);
    }

    @Test
    @DisplayName("Une date absente est acceptee : un manque n'est pas une contradiction, et la "
            + "colonne est nullable")
    void dateAbsenteAcceptee() {
        AccuseRecevable recevable = (AccuseRecevable) service.valider(
                new AccuseComptableEvent(740L, "INTEGRE", "CPT-1", null, null));

        assertThat(recevable.dateTraitement()).isNull();
    }

    // --- Test 7 du guide ----------------------------------------------------------

    @Test
    @DisplayName("Test 7 — un statut hors du contrat est refuse, et NOMME : ce n'est pas un "
            + "message illisible")
    void statutInconnuRefuse() {
        AccuseInvalide refus = (AccuseInvalide) service.valider(
                new AccuseComptableEvent(740L, "TRAITE", "CPT-1", DATE_ISO, null));

        assertThat(refus.codes()).containsExactly(CodeAnomalieAccuseEnum.STATUT_INCONNU);
        // Le message nomme la valeur fautive : c'est toute la difference avec un refus
        // « message illisible », qui laisserait chercher.
        assertThat(refus.anomalies().getFirst().message())
                .contains("TRAITE")
                .contains("EN_ATTENTE", "INTEGRE", "REJETE");
    }

    @Test
    @DisplayName("Un statut absent est refuse, sous un code distinct du statut inconnu")
    void statutAbsentRefuse() {
        AccuseInvalide refus = (AccuseInvalide) service.valider(
                new AccuseComptableEvent(740L, null, "CPT-1", DATE_ISO, null));

        assertThat(refus.codes()).containsExactly(CodeAnomalieAccuseEnum.STATUT_ABSENT);
    }

    // --- Test 6 du guide ----------------------------------------------------------

    @Test
    @DisplayName("Test 6 — un rejet sans motif est refuse : le contrat l'exige, et le suivi doit "
            + "dire pourquoi l'etat a ete refuse")
    void rejetSansMotifRefuse() {
        AccuseInvalide refus = (AccuseInvalide) service.valider(
                new AccuseComptableEvent(740L, "REJETE", null, DATE_ISO, null));

        assertThat(refus.codes()).containsExactly(CodeAnomalieAccuseEnum.MOTIF_REJET_ABSENT);
    }

    @Test
    @DisplayName("Une suite d'espaces n'est pas un motif : champ present, motif absent")
    void rejetAvecMotifBlancRefuse() {
        AccuseInvalide refus = (AccuseInvalide) service.valider(
                new AccuseComptableEvent(740L, "REJETE", null, DATE_ISO, "   "));

        assertThat(refus.codes()).containsExactly(CodeAnomalieAccuseEnum.MOTIF_REJET_ABSENT);
    }

    @Test
    @DisplayName("Le motif n'est exige que sur un rejet : une integration sans motif est normale")
    void motifNonExigeHorsRejet() {
        assertThat(service.valider(
                new AccuseComptableEvent(740L, "INTEGRE", "CPT-1", DATE_ISO, null)))
                .isInstanceOf(AccuseRecevable.class);
        assertThat(service.valider(
                new AccuseComptableEvent(740L, "EN_ATTENTE", null, DATE_ISO, null)))
                .isInstanceOf(AccuseRecevable.class);
    }

    // --- Autres refus -------------------------------------------------------------

    @Test
    @DisplayName("Sans idProcessus, l'accuse ne designe rien : refuse")
    void identifiantAbsentRefuse() {
        AccuseInvalide refus = (AccuseInvalide) service.valider(
                new AccuseComptableEvent(null, "INTEGRE", "CPT-1", DATE_ISO, null));

        assertThat(refus.codes()).containsExactly(CodeAnomalieAccuseEnum.IDENTIFIANT_ABSENT);
    }

    @Test
    @DisplayName("Une date sans decalage horaire est refusee : lui en supposer un inventerait une "
            + "information sur un traitement de paiement")
    void dateSansDecalageRefusee() {
        AccuseInvalide refus = (AccuseInvalide) service.valider(
                new AccuseComptableEvent(740L, "INTEGRE", "CPT-1", "2026-08-18T02:15:00", null));

        assertThat(refus.codes())
                .containsExactly(CodeAnomalieAccuseEnum.DATE_TRAITEMENT_ILLISIBLE);
        assertThat(refus.anomalies().getFirst().message()).contains("2026-08-18T02:15:00Z");
    }

    @Test
    @DisplayName("Toutes les anomalies sont rapportees, pas seulement la premiere : un message a "
            + "la fois sans identifiant et de statut inconnu se voit d'un coup d'oeil")
    void toutesLesAnomaliesRapportees() {
        AccuseInvalide refus = (AccuseInvalide) service.valider(
                new AccuseComptableEvent(null, "TRAITE", null, "hier", null));

        assertThat(refus.codes()).containsExactlyInAnyOrder(
                CodeAnomalieAccuseEnum.IDENTIFIANT_ABSENT,
                CodeAnomalieAccuseEnum.STATUT_INCONNU,
                CodeAnomalieAccuseEnum.DATE_TRAITEMENT_ILLISIBLE);
    }

    @Test
    @DisplayName("Aucune exception ne sort du controle, quelle que soit la charge : le "
            + "consommateur ne doit jamais tomber")
    void aucuneExceptionNeSort() {
        assertThat(service.valider(new AccuseComptableEvent(null, null, null, null, null)))
                .isInstanceOf(AccuseInvalide.class);
        assertThat(service.valider(new AccuseComptableEvent(0L, "", "", "", "")))
                .isInstanceOf(AccuseInvalide.class);
    }

}
