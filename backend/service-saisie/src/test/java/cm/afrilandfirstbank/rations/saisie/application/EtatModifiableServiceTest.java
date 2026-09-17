package cm.afrilandfirstbank.rations.saisie.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cm.afrilandfirstbank.rations.saisie.application.ResultatVerificationProcessus.ProcessusVerifie;
import cm.afrilandfirstbank.rations.saisie.domaine.StatutProcessusEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.EtatNonModifiableException;

/**
 * Regression trouvee en verification reelle le 17 septembre 2026 (Sprint
 * 7F.5, guide etape 6) : reouvrir l'onglet de saisie journaliere d'un etat
 * deja soumis faisait echouer {@code POST /saisie/fiches} en {@code 500} brut
 * au lieu du {@code 422 ETAT_NON_MODIFIABLE} attendu.
 *
 * <p><b>La cause.</b> Le message de {@link EtatModifiableService#exigerEcriturePossible}
 * formatait {@code dateDebut}/{@code dateFin} avec {@code %02d/%d}, herite de
 * l'epoque ou le processus portait {@code moisPaiement}/{@code anneePaiement}
 * (des {@code int}). Depuis la Maille 1 (period en intervalle de dates, point
 * M-04), ces deux champs sont des {@code LocalDate} : {@code String.format}
 * levait {@code IllegalFormatConversionException}, une exception non geree par
 * {@code GestionnaireErreursApi}, qui devenait donc un {@code 500} sans code
 * exploitable.
 *
 * <p><b>Aucun test n'existait pour {@link EtatModifiableService} avant celui-ci</b> :
 * c'est ce qui a laisse la regression invisible depuis la migration de la
 * Maille 1 jusqu'a la premiere reprise reelle d'un etat retourne au Sprint
 * 7F.5 — le premier sous-sprint frontend a exercer ce chemin par un vrai clic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EtatModifiableService — portier de l'ecriture (regression du message de refus, Sprint 7F.5)")
class EtatModifiableServiceTest {

    private static final String JETON = "Bearer jeton-utilisateur";

    @Mock
    private VerificationProcessusClient verificationProcessusClient;

    @Mock
    private HabilitationClient habilitationClient;

    private EtatModifiableService service;

    @BeforeEach
    void preparer() {
        service = new EtatModifiableService(verificationProcessusClient, habilitationClient);
    }

    @Test
    @DisplayName("Etat SOUMIS (periode en intervalle de dates) : refus 422 exploitable, jamais une exception de formatage")
    void etatNonModifiable_refuseAvecUnMessageExploitable_sansExceptionDeFormatage() {
        ProcessusVerifie processus = new ProcessusVerifie(
                9400001L, StatutProcessusEnum.SOUMIS, "00002",
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 13));

        when(verificationProcessusClient.verifier(9400001L, JETON)).thenReturn(processus);

        assertThatThrownBy(() -> service.exigerEcriturePossible(9400001L, JETON))
                .isInstanceOf(EtatNonModifiableException.class)
                .hasMessageContaining("2026-09-07")
                .hasMessageContaining("2026-09-13")
                .hasMessageContaining("00002")
                .hasMessageContaining("SOUMIS");
    }

    @Test
    @DisplayName("Etat RETOURNE : ecriture autorisee, l'habilitation est verifiee")
    void etatRetourne_ecritureAutorisee() {
        ProcessusVerifie processus = new ProcessusVerifie(
                9400002L, StatutProcessusEnum.RETOURNE, "00002",
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 13));

        when(verificationProcessusClient.verifier(9400002L, JETON)).thenReturn(processus);
        when(habilitationClient.verifier("00002", JETON))
                .thenReturn(new ResultatHabilitationUnite.AgentHabilite("jean_mbarga", "00002"));

        service.exigerEcriturePossible(9400002L, JETON);
    }

}
