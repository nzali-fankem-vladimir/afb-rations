package cm.afrilandfirstbank.rations.saisie.infrastructure.grilles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import cm.afrilandfirstbank.rations.saisie.application.ResolutionMontantClient;
import cm.afrilandfirstbank.rations.saisie.application.ResultatResolutionMontant;
import cm.afrilandfirstbank.rations.saisie.application.ResultatResolutionMontant.AucuneGrilleApplicable;
import cm.afrilandfirstbank.rations.saisie.application.ResultatResolutionMontant.MontantResolu;
import cm.afrilandfirstbank.rations.saisie.application.ResultatResolutionMontant.ServiceGrillesIndisponible;
import cm.afrilandfirstbank.rations.saisie.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.SessionEnum;

/**
 * Traduction des reponses du service Grilles en trois cas distincts (RG-03,
 * Sprint 3.2, {@code docs/appel-resolution-montant.md} section 2).
 *
 * <p>Le service Grilles n'est <b>pas</b> demarre pour ces tests :
 * {@code MockRestServiceServer} s'attache au {@code RestClient.Builder} injecte
 * et joue les reponses. C'est precisement la raison pour laquelle le
 * constructeur est injecte plutot que construit dans le client (contrainte
 * relevee au Sprint 3.1).
 *
 * <p>Ce qui est verifie ici n'est pas « le montant est bon » — cela releve du
 * service Grilles, teste au Sprint 2.4 — mais que <b>l'absence de tarif et la
 * panne ne se confondent jamais</b>, et que la date transmise est bien celle de
 * la prestation.
 */
@DisplayName("ResolutionMontantHttpClient — les trois cas de reponse (RG-03)")
class ResolutionMontantClientTest {

    private static final String URL_GRILLES = "http://service-grilles:8083";
    private static final String JETON_AGENT = "Bearer jeton-de-l-agent";
    private static final LocalDate JOURNEE = LocalDate.of(2026, 7, 10);

    private MockRestServiceServer serviceGrilles;
    private ResolutionMontantClient client;

    @BeforeEach
    void preparer() {
        RestClient.Builder constructeur = RestClient.builder();
        serviceGrilles = MockRestServiceServer.bindTo(constructeur).build();
        client = new ResolutionMontantHttpClient(constructeur, URL_GRILLES);
    }

    @Test
    @DisplayName("200 disponible=true : montant resolu, avec la grille qui l'a fourni")
    void reponseAvecTarif_rendUnMontantResolu() {
        serviceGrilles.expect(requestTo(URL_GRILLES
                        + "/grilles/active?nature=RATION&session=JOUR&date=2026-07-10"))
                .andExpect(method(HttpMethod.GET))
                // Le jeton de l'utilisateur final est relaye tel quel : aucun
                // compte de service n'existe dans le realm (decision Sprint 1.3).
                .andExpect(header(HttpHeaders.AUTHORIZATION, JETON_AGENT))
                .andRespond(withSuccess("""
                        {
                          "disponible": true,
                          "nature": "RATION",
                          "session": "JOUR",
                          "date": "2026-07-10",
                          "montantFcfa": 2500,
                          "idGrille": 12,
                          "dateDebut": "2026-07-01",
                          "dateFin": "2026-07-31"
                        }""", MediaType.APPLICATION_JSON));

        ResultatResolutionMontant resultat =
                client.resoudre(NatureEnum.RATION, SessionEnum.JOUR, JOURNEE, JETON_AGENT);

        assertThat(resultat).isInstanceOf(MontantResolu.class);
        MontantResolu montant = (MontantResolu) resultat;
        assertThat(montant.montantFcfa()).isEqualTo(2500);
        assertThat(montant.idGrille()).isEqualTo(12L);
        assertThat(montant.dateDebut()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(montant.dateFin()).isEqualTo(LocalDate.of(2026, 7, 31));
        serviceGrilles.verify();
    }

    @Test
    @DisplayName("200 disponible=false : absence de tarif, refus METIER — jamais confondu avec une panne")
    void reponseSansTarif_rendUneAbsenceDeGrille() {
        serviceGrilles.expect(requestTo(URL_GRILLES
                        + "/grilles/active?nature=TRANSPORT&session=SOIR&date=2026-07-10"))
                .andRespond(withSuccess("""
                        {
                          "disponible": false,
                          "nature": "TRANSPORT",
                          "session": "SOIR",
                          "date": "2026-07-10",
                          "montantFcfa": null,
                          "idGrille": null,
                          "dateDebut": null,
                          "dateFin": null
                        }""", MediaType.APPLICATION_JSON));

        ResultatResolutionMontant resultat =
                client.resoudre(NatureEnum.TRANSPORT, SessionEnum.SOIR, JOURNEE, JETON_AGENT);

        // L'absence de tarif se lit dans le champ disponible, jamais dans un code
        // HTTP : la reponse est un 200 parfaitement normal.
        assertThat(resultat).isInstanceOf(AucuneGrilleApplicable.class);
        AucuneGrilleApplicable absence = (AucuneGrilleApplicable) resultat;
        assertThat(absence.nature()).isEqualTo(NatureEnum.TRANSPORT);
        assertThat(absence.session()).isEqualTo(SessionEnum.SOIR);
        assertThat(absence.date()).isEqualTo(JOURNEE);
        serviceGrilles.verify();
    }

    @Test
    @DisplayName("connexion refusee : refus TECHNIQUE, distinct de l'absence de tarif")
    void serviceInjoignable_rendUneIndisponibiliteTechnique() {
        serviceGrilles.expect(requestTo(URL_GRILLES
                        + "/grilles/active?nature=RATION&session=JOUR&date=2026-07-10"))
                .andRespond(withException(new IOException("connexion refusee")));

        ResultatResolutionMontant resultat =
                client.resoudre(NatureEnum.RATION, SessionEnum.JOUR, JOURNEE, JETON_AGENT);

        assertThat(resultat).isInstanceOf(ServiceGrillesIndisponible.class);
        serviceGrilles.verify();
    }

    @Test
    @DisplayName("500 : refus TECHNIQUE, et surtout pas une absence de tarif")
    void erreurServeur_rendUneIndisponibiliteTechnique() {
        serviceGrilles.expect(requestTo(URL_GRILLES
                        + "/grilles/active?nature=RATION&session=JOUR&date=2026-07-10"))
                .andRespond(withServerError());

        ResultatResolutionMontant resultat =
                client.resoudre(NatureEnum.RATION, SessionEnum.JOUR, JOURNEE, JETON_AGENT);

        assertThat(resultat)
                .as("un 5xx ne dit pas qu'il n'y a pas de tarif, il dit que la question n'a pas de reponse")
                .isInstanceOf(ServiceGrillesIndisponible.class);
        serviceGrilles.verify();
    }

    @Test
    @DisplayName("500 INCOHERENCE_GRILLE : refus technique, motif identifiable, jamais reessaye")
    void incoherenceDeGrilles_estIdentifieeCommeTelle() {
        serviceGrilles.expect(requestTo(URL_GRILLES
                        + "/grilles/active?nature=RATION&session=JOUR&date=2026-07-10"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "timestamp": "2026-07-10T09:00:00",
                                  "status": 500,
                                  "code": "INCOHERENCE_GRILLE",
                                  "message": "Plusieurs grilles actives couvrent RATION / JOUR au 2026-07-10.",
                                  "path": "/grilles/active"
                                }"""));

        ResultatResolutionMontant resultat =
                client.resoudre(NatureEnum.RATION, SessionEnum.JOUR, JOURNEE, JETON_AGENT);

        assertThat(resultat).isInstanceOf(ServiceGrillesIndisponible.class);
        // Le motif reste reconnaissable : c'est un incident de donnees cote
        // Grilles, pas une panne reseau. Un seul appel a ete emis, aucun reessai
        // (decision Sprint 3.2) — serviceGrilles.verify() le garantit, il
        // echouerait sur un appel supplementaire non attendu.
        assertThat(((ServiceGrillesIndisponible) resultat).motifTechnique())
                .contains("INCOHERENCE_GRILLE");
        serviceGrilles.verify();
    }

    @Test
    @DisplayName("200 annoncant un montant sans le fournir : refus technique, jamais un repli sur zero")
    void reponseContradictoire_neProduitJamaisUnMontantNul() {
        serviceGrilles.expect(requestTo(URL_GRILLES
                        + "/grilles/active?nature=RATION&session=JOUR&date=2026-07-10"))
                .andRespond(withSuccess("""
                        {
                          "disponible": true,
                          "nature": "RATION",
                          "session": "JOUR",
                          "date": "2026-07-10",
                          "montantFcfa": null,
                          "idGrille": null,
                          "dateDebut": null,
                          "dateFin": null
                        }""", MediaType.APPLICATION_JSON));

        ResultatResolutionMontant resultat =
                client.resoudre(NatureEnum.RATION, SessionEnum.JOUR, JOURNEE, JETON_AGENT);

        assertThat(resultat).isInstanceOf(ServiceGrillesIndisponible.class);
        serviceGrilles.verify();
    }

}
