package cm.afrilandfirstbank.rations.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cm.afrilandfirstbank.rations.reporting.domaine.EnTeteDemande;
import cm.afrilandfirstbank.rations.reporting.domaine.Rapport;
import cm.afrilandfirstbank.rations.reporting.domaine.SituationIntegration;

/**
 * Le calcul unique du rapport — tests 1, 2, 3, 5 et 7 du guide du Sprint 6.2.
 *
 * <p>{@link AgregationService} est bouchonné : ce qui est éprouvé ici n'est pas le
 * croisement des deux sources (déjà couvert par {@code AgregationServiceTest} au
 * Sprint 6.1) mais la <b>synthèse</b> — comment {@link RapportService} transforme
 * une liste d'en-têtes en totaux, et notamment la distinction entre « envoyé »,
 * « non envoyé » et « rejeté » (décision du 4 septembre 2026).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RapportService — calcul unique du rapport (Sprint 6.2, CT-32, CT-33)")
class RapportServiceTest {

    private static final String JETON = "Bearer jeton-utilisateur";

    @Mock
    private AgregationService agregationService;

    private RapportService service;

    @BeforeEach
    void preparer() {
        service = new RapportService(agregationService);
    }

    private EnTeteDemande enTete(long id, String codeUnite, int montant, String statut,
            boolean transmis, String statutIntegration) {
        return new EnTeteDemande(id, 8, 2026, codeUnite, "NORMAL", montant, statut, transmis,
                statutIntegration, LocalDateTime.of(2026, 8, 15, 9, 0));
    }

    @Test
    @DisplayName("1. periode avec donnees : lignes, sous-totaux par agence et synthese corrects")
    void periodeAvecDonnees_totauxCorrects() {
        when(agregationService.rechercher(any(), eq(JETON))).thenReturn(List.of(
                enTete(1, "00002", 30_000, "CLOTURE", true, "INTEGRE"),
                enTete(2, "00002", 20_000, "SOUMIS", false, null),
                enTete(3, "00003", 15_000, "CLOTURE", true, "EN_ATTENTE")));

        Rapport rapport = service.produire(8, 2026, null, "claire_nkolo", JETON);

        assertThat(rapport.vide()).isFalse();
        assertThat(rapport.lignes()).hasSize(3);
        assertThat(rapport.synthese().nombreEtats()).isEqualTo(3);
        assertThat(rapport.synthese().montantTotalPeriode()).isEqualTo(65_000L);

        // Sous-totaux par agence : rapport national (codeUnite absent), deux agences.
        assertThat(rapport.sousTotauxParAgence()).hasSize(2);
        assertThat(rapport.sousTotauxParAgence())
                .filteredOn(s -> s.codeUnite().equals("00002"))
                .singleElement()
                .satisfies(s -> {
                    assertThat(s.nombreEtats()).isEqualTo(2);
                    assertThat(s.montantTotal()).isEqualTo(50_000L);
                });

        assertThat(rapport.synthese().repartitionParStatut())
                .containsEntry("CLOTURE", 2)
                .containsEntry("SOUMIS", 1);
    }

    @Test
    @DisplayName("2. periode sans donnees : rapport vide signale, synthese a zero, pas une erreur")
    void periodeSansDonnees_rapportVideSignale() {
        when(agregationService.rechercher(any(), eq(JETON))).thenReturn(List.of());

        Rapport rapport = service.produire(9, 2026, null, "claire_nkolo", JETON);

        assertThat(rapport.vide()).isTrue();
        assertThat(rapport.lignes()).isEmpty();
        assertThat(rapport.sousTotauxParAgence()).isEmpty();
        assertThat(rapport.synthese().nombreEtats()).isZero();
        assertThat(rapport.synthese().montantTotalPeriode()).isZero();
    }

    @Test
    @DisplayName("3. rapport filtre sur une agence : critere transmis, aucun sous-total par agence")
    void filtreSurUneAgence_neContientQueCetteAgence() {
        ArgumentCaptor<CriteresRecherche> criteresCaptes = ArgumentCaptor.forClass(CriteresRecherche.class);

        when(agregationService.rechercher(criteresCaptes.capture(), eq(JETON))).thenReturn(List.of(
                enTete(1, "00002", 30_000, "CLOTURE", true, "INTEGRE"),
                enTete(2, "00002", 20_000, "SOUMIS", false, null)));

        Rapport rapport = service.produire(8, 2026, "00002", "claire_nkolo", JETON);

        assertThat(criteresCaptes.getValue().codeUnite()).isEqualTo("00002");
        assertThat(criteresCaptes.getValue().mois()).isEqualTo(8);
        assertThat(criteresCaptes.getValue().annee()).isEqualTo(2026);
        // Aucun critere de ligne : ce rapport n'interroge jamais la Saisie (doctrine 6.1).
        assertThat(criteresCaptes.getValue().porteSurLesLignes()).isFalse();

        assertThat(rapport.lignes()).extracting(Rapport.LigneRapport::codeUnite)
                .containsOnly("00002");
        // Une seule agence demandee : pas de sous-total, la synthese suffit.
        assertThat(rapport.sousTotauxParAgence()).isEmpty();
    }

    @Test
    @DisplayName("5. le total du rapport egale la somme des montants des processus de la periode")
    void totalDuRapport_egaleLaSommeDesMontants() {
        List<EnTeteDemande> enTetes = List.of(
                enTete(10, "00002", 12_345, "CLOTURE", true, "INTEGRE"),
                enTete(11, "00003", 7_655, "EN_ATTENTE_DR", false, null),
                enTete(12, "00003", 100, "RETOURNE", false, null));
        when(agregationService.rechercher(any(), eq(JETON))).thenReturn(enTetes);

        Rapport rapport = service.produire(8, 2026, null, "claire_nkolo", JETON);

        long sommeAttendue = enTetes.stream().mapToLong(EnTeteDemande::montantTotal).sum();
        assertThat(rapport.synthese().montantTotalPeriode()).isEqualTo(sommeAttendue);
    }

    @Test
    @DisplayName("7. \"envoye\" n'est pas \"paye\" : le rejet est isole, jamais confondu avec l'envoi")
    void montantEnvoye_distinctDuMontantRejete() {
        when(agregationService.rechercher(any(), eq(JETON))).thenReturn(List.of(
                // Envoye ET rejete par la comptabilite : l'argent n'est jamais parti.
                enTete(1, "00002", 10_000, "CLOTURE", true, "REJETE"),
                // Envoye et integre : le cas normal d'un paiement effectif.
                enTete(2, "00002", 50_000, "CLOTURE", true, "INTEGRE"),
                // Envoye, en attente d'accuse.
                enTete(3, "00002", 24_000, "CLOTURE", true, "EN_ATTENTE"),
                // Jamais envoye : encore en circuit.
                enTete(4, "00002", 5_000, "EN_ATTENTE_DA", false, null)));

        Rapport rapport = service.produire(8, 2026, null, "claire_nkolo", JETON);
        Rapport.Synthese synthese = rapport.synthese();

        // "Envoye" = parti sur le topic, REJETE inclus : ce n'est pas "paye".
        assertThat(synthese.montantEnvoyeComptabilite()).isEqualTo(84_000L);
        assertThat(synthese.montantNonEnvoyeComptabilite()).isEqualTo(5_000L);
        // Le rejet est isole sur sa propre ligne, jamais deduit de la repartition seule.
        assertThat(synthese.montantRejeteComptabilite()).isEqualTo(10_000L);
        assertThat(synthese.montantEnvoyeComptabilite() + synthese.montantNonEnvoyeComptabilite())
                .isEqualTo(synthese.montantTotalPeriode());
        assertThat(synthese.repartitionParSituation())
                .containsEntry(SituationIntegration.REJETE, 1)
                .containsEntry(SituationIntegration.INTEGRE, 1)
                .containsEntry(SituationIntegration.EN_ATTENTE_ACCUSE, 1)
                .containsEntry(SituationIntegration.NON_TRANSMIS, 1);
    }

}
