package cm.afrilandfirstbank.rations.transmission.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import cm.afrilandfirstbank.rations.transmission.domaine.StatutIntegrationEnum;
import cm.afrilandfirstbank.rations.transmission.domaine.exception.AccesHorsPorteeException;
import cm.afrilandfirstbank.rations.transmission.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.transmission.domaine.exception.ServiceWorkflowIndisponibleException;

/**
 * {@code GET /transmission/processus/{id}} : les cinq situations, et le fait
 * qu'<b>aucune ne laisse un champ vide sans explication</b> (contrat d'API section 7,
 * US-15, Sprint 5.3).
 *
 * <p>Quatre des cinq situations rendent une reference comptable nulle, pour quatre raisons
 * differentes. Un lecteur qui verrait quatre fois le meme champ vide ne saurait pas
 * laquelle : c'est ce que la {@link SituationIntegration} et le message evitent, et c'est
 * exactement ce que ces tests verifient.
 */
@DisplayName("Consultation du statut d'integration — les cinq situations")
class ConsultationIntegrationServiceTest {

    private static final Long ID = 740L;
    private static final String JETON = "Bearer jeton-de-test";
    private static final String REFERENCE = "CPT-2026-07-000512";
    private static final LocalDateTime TRAITE_LE = LocalDateTime.of(2026, 8, 18, 3, 15);
    private static final LocalDateTime RESERVE_LE = LocalDateTime.of(2026, 8, 17, 18, 4);

    private StatutIntegrationConsulte consulter(IntegrationProcessus integration) {
        return service(new ResultatIntegrationProcessus.IntegrationObtenue(integration))
                .consulter(ID, JETON);
    }

    private ConsultationIntegrationService service(ResultatIntegrationProcessus reponse) {
        return new ConsultationIntegrationService((id, entete) -> reponse);
    }

    // --- Les quatre situations du guide, plus celle nee du verrou -----------------

    @Nested
    @DisplayName("Chaque situation produit une reponse comprehensible")
    class Situations {

        @Test
        @DisplayName("6. processus non transmis : la situation le nomme et le message l'explique")
        void nonTransmis() {
            StatutIntegrationConsulte statut = consulter(new IntegrationProcessus(
                    ID, false, null, null, null, null, null));

            assertThat(statut.situation()).isEqualTo(SituationIntegration.NON_TRANSMIS);
            assertThat(statut.statutIntegration()).isNull();
            assertThat(statut.referenceComptable()).isNull();
            assertThat(statut.message())
                    .contains("n'a pas ete transmis")
                    .contains("cloture");
        }

        @Test
        @DisplayName("7. processus transmis sans accuse : statut en attente, sans reference")
        void transmisSansAccuse() {
            StatutIntegrationConsulte statut = consulter(new IntegrationProcessus(
                    ID, true, StatutIntegrationEnum.EN_ATTENTE, null, null, null, RESERVE_LE));

            assertThat(statut.situation()).isEqualTo(SituationIntegration.EN_ATTENTE_ACCUSE);
            assertThat(statut.statutIntegration()).isEqualTo(StatutIntegrationEnum.EN_ATTENTE);
            assertThat(statut.referenceComptable()).isNull();
            assertThat(statut.dateTransmission()).isEqualTo(RESERVE_LE);
            assertThat(statut.message())
                    .contains("bien ete transmis")
                    .contains("n'a pas encore accuse reception");
        }

        @Test
        @DisplayName("8. processus integre : la reference comptable est rendue")
        void integre() {
            StatutIntegrationConsulte statut = consulter(new IntegrationProcessus(
                    ID, true, StatutIntegrationEnum.INTEGRE, REFERENCE, TRAITE_LE, null,
                    RESERVE_LE));

            assertThat(statut.situation()).isEqualTo(SituationIntegration.INTEGRE);
            assertThat(statut.referenceComptable()).isEqualTo(REFERENCE);
            assertThat(statut.dateTraitement()).isEqualTo(TRAITE_LE);
            assertThat(statut.message()).contains(REFERENCE);
        }

        @Test
        @DisplayName("9. processus rejete : le motif est rendu")
        void rejete() {
            StatutIntegrationConsulte statut = consulter(new IntegrationProcessus(
                    ID, true, StatutIntegrationEnum.REJETE, REFERENCE, TRAITE_LE,
                    "Compte 00002000123456 clos", RESERVE_LE));

            assertThat(statut.situation()).isEqualTo(SituationIntegration.REJETE);
            assertThat(statut.motifIntegration()).isEqualTo("Compte 00002000123456 clos");
            assertThat(statut.message())
                    .contains("refuse")
                    .contains("Compte 00002000123456 clos")
                    .contains("Direction Financiere");
        }

        @Test
        @DisplayName("publication non confirmee : la situation nee du verrou est nommee, pas subie")
        void publicationNonConfirmee() {
            // transmis = true, statut d'integration nul : l'etat intermediaire du verrou.
            // Sans situation nommee, il serait indiscernable de « jamais transmis » pour
            // qui ne regarde que le statut du contrat.
            StatutIntegrationConsulte statut = consulter(new IntegrationProcessus(
                    ID, true, null, null, null, null, RESERVE_LE));

            assertThat(statut.situation())
                    .isEqualTo(SituationIntegration.PUBLICATION_NON_CONFIRMEE);
            assertThat(statut.statutIntegration()).isNull();
            assertThat(statut.dateTransmission()).isEqualTo(RESERVE_LE);
            assertThat(statut.message())
                    .contains("incertaine")
                    .contains("double paiement");
        }

        @Test
        @DisplayName("un rejet sans motif le dit, plutot que d'afficher « null »")
        void rejetSansMotif() {
            StatutIntegrationConsulte statut = consulter(new IntegrationProcessus(
                    ID, true, StatutIntegrationEnum.REJETE, REFERENCE, TRAITE_LE, null,
                    RESERVE_LE));

            assertThat(statut.message())
                    .contains("aucun motif")
                    .doesNotContain("null");
        }
    }

    // --- Refus ---------------------------------------------------------------------

    @Nested
    @DisplayName("Refus")
    class Refus {

        @Test
        @DisplayName("10. consultation hors portee : le refus traverse tel quel, en 403")
        void horsPortee() {
            ConsultationIntegrationService service = new ConsultationIntegrationService(
                    (id, entete) -> {
                        throw new AccesHorsPorteeException(
                                "Vous n'avez pas de portee d'acces sur l'unite du processus 740.");
                    });

            assertThatThrownBy(() -> service.consulter(ID, JETON))
                    .withFailMessage("Le refus de portee doit rester distinct d'un refus de role "
                            + "et d'une panne : les trois appellent trois gestes differents.")
                    .isInstanceOf(AccesHorsPorteeException.class)
                    .hasMessageContaining("portee d'acces");
        }

        @Test
        @DisplayName("role hors perimetre : refus relaye, jamais deguise en panne")
        void roleInsuffisant() {
            ConsultationIntegrationService service = new ConsultationIntegrationService(
                    (id, entete) -> {
                        throw new AccessDeniedException("role insuffisant");
                    });

            assertThatThrownBy(() -> service.consulter(ID, JETON))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("processus inconnu : 404, jamais une panne")
        void processusInconnu() {
            assertThatThrownBy(() -> service(
                    new ResultatIntegrationProcessus.ProcessusInconnu(ID)).consulter(ID, JETON))
                    .isInstanceOf(ProcessusIntrouvableException.class);
        }

        @Test
        @DisplayName("service Workflow muet : 503, aucun statut de paiement devine")
        void workflowMuet() {
            assertThatThrownBy(() -> service(
                    new ResultatIntegrationProcessus.ServiceWorkflowIndisponible("timeout"))
                    .consulter(ID, JETON))
                    .isInstanceOf(ServiceWorkflowIndisponibleException.class);
        }
    }

}
