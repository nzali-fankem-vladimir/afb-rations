package cm.afrilandfirstbank.rations.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.workflow.api.dto.DeclenchementProcessusRequest;
import cm.afrilandfirstbank.rations.workflow.application.ResultatConsolidation.EtatObtenu;
import cm.afrilandfirstbank.rations.workflow.application.ResultatConsolidation.ServiceSaisieIndisponible;
import cm.afrilandfirstbank.rations.workflow.application.ResultatHabilitationUnite.AgentHabilite;
import cm.afrilandfirstbank.rations.workflow.application.ResultatHabilitationUnite.AgentNonHabilite;
import cm.afrilandfirstbank.rations.workflow.application.ResultatHabilitationUnite.ServiceIdentiteIndisponible;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.TypeProcessusEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.AgentNonHabiliteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.FonctionnaliteNonOuverteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusExistantException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ServiceSaisieIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ProcessusMensuelRepository;

/**
 * Declenchement et consultation d'un processus mensuel (Sprint 4.1, US-03).
 *
 * <p><b>Contre la vraie base {@code rations_workflow}</b>, comme les tests des
 * Sprints 3.2 et 3.4 cote Saisie. Le controle d'unicite du processus normal
 * repose sur l'index partiel {@code ux_processus_normal_par_periode} autant que
 * sur le code : un test a base de mocks prouverait que le service refuse ce qu'on
 * lui dit de refuser, c'est-a-dire rien sur le risque reel. Seuls les deux appels
 * reseau — Identite et Saisie — sont simules.
 *
 * <p>Les jeux d'essai portent l'annee <b>2099</b> : aucune donnee reelle ni
 * d'essai manuel ne peut entrer en collision avec eux sur l'index d'unicite.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("ProcessusService — declenchement et consultation")
class ProcessusServiceTest {

    private static final String UNITE = "00002";
    private static final String AUTRE_UNITE = "00007";
    private static final int ANNEE = 2099;
    private static final String JETON = "Bearer jeton-de-test";
    private static final String IP = "10.0.0.12";
    private static final String LOGIN = "jean_mbarga";

    @Autowired
    private ProcessusMensuelRepository processusRepository;

    private HabilitationClient habilitationClient;
    private ConsolidationClient consolidationClient;
    private PublicateurAudit publicateurAudit;
    private ProcessusService processusService;

    @BeforeEach
    void preparer() {
        // @DataJpaTest ne charge pas les beans @Service : le service est instancie a
        // la main, avec son vrai repository derriere. HabilitationService est reel —
        // c'est lui qui traduit les trois issues en refus, et cette traduction fait
        // partie de ce qu'on veut eprouver ; seul le client HTTP est simule.
        habilitationClient = mock(HabilitationClient.class);
        consolidationClient = mock(ConsolidationClient.class);
        publicateurAudit = mock(PublicateurAudit.class);

        processusService = new ProcessusService(
                processusRepository,
                new HabilitationService(habilitationClient),
                consolidationClient,
                publicateurAudit);

        habiliteSur(UNITE);
    }

    // --- Outillage --------------------------------------------------------------

    private void habiliteSur(String codeUnite) {
        when(habilitationClient.verifier(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String demande = invocation.getArgument(0);
                    return codeUnite.equals(demande)
                            ? new AgentHabilite(LOGIN, "AGENT_UNITE", demande)
                            : new AgentNonHabilite("role AGENT_UNITE sans portee sur l'unite " + demande);
                });
    }

    private static DeclenchementProcessusRequest demande(int mois, String codeUnite) {
        return new DeclenchementProcessusRequest(mois, ANNEE, codeUnite, null, null, null);
    }

    private static EtatConsolide etatVide(Long idProcessus, String codeUnite) {
        return new EtatConsolide(idProcessus, codeUnite, null, null, 0, 0, 0, 0L, List.of());
    }

    // --- Declenchement ----------------------------------------------------------

    @Test
    @DisplayName("1. Declenchement nominal : EN_COURS_SAISIE, type NORMAL, montant a zero")
    void declenchementNominal() {
        ProcessusMensuel processus = processusService.declencher(demande(1, UNITE), JETON, IP);

        assertThat(processus.getId()).isNotNull();
        assertThat(processus.getStatut()).isEqualTo(StatutEnum.EN_COURS_SAISIE);
        assertThat(processus.getTypeProcessus()).isEqualTo(TypeProcessusEnum.NORMAL);
        assertThat(processus.getCodeUnite()).isEqualTo(UNITE);
        assertThat(processus.getMoisPaiement()).isEqualTo(1);
        assertThat(processus.getAnneePaiement()).isEqualTo(ANNEE);
        assertThat(processus.getMontantTotal()).isZero();
        assertThat(processus.isTransmisComptabilite()).isFalse();
        assertThat(processus.getIdProcessusOrigine()).isNull();
        assertThat(processus.getDateCreation()).isNotNull();

        // Reellement en base, pas seulement en memoire.
        assertThat(processusRepository.findById(processus.getId())).isPresent();
    }

    @Test
    @DisplayName("2. La portee est verifiee sur l'unite demandee, avec le jeton de l'appelant")
    void porteeVerifieeSurUniteDemandee() {
        processusService.declencher(demande(2, UNITE), JETON, IP);

        verify(habilitationClient).verifier(UNITE, JETON);
    }

    @Test
    @DisplayName("3. Second declenchement, meme unite et meme periode : refus 409, rien de cree")
    void secondDeclenchementRefuse() {
        ProcessusMensuel premier = processusService.declencher(demande(3, UNITE), JETON, IP);

        assertThatThrownBy(() -> processusService.declencher(demande(3, UNITE), JETON, IP))
                .isInstanceOf(ProcessusExistantException.class)
                // Le message nomme le dossier deja ouvert : l'agent doit le rejoindre,
                // pas en ouvrir un second.
                .hasMessageContaining(String.valueOf(premier.getId()))
                .hasMessageContaining(UNITE)
                .hasMessageContaining("EN_COURS_SAISIE");

        assertThat(processusRepository
                .findByCodeUniteAndMoisPaiementAndAnneePaiementAndTypeProcessus(
                        UNITE, 3, ANNEE, TypeProcessusEnum.NORMAL))
                .contains(premier);
    }

    @Test
    @DisplayName("4. Le refus est borne au couple : autre mois et autre unite restent ouvrables")
    void refusBorneAuCouple() {
        processusService.declencher(demande(4, UNITE), JETON, IP);

        // Meme unite, mois different.
        assertThat(processusService.declencher(demande(5, UNITE), JETON, IP).getId()).isNotNull();

        // Meme mois, unite differente — l'appelant doit y etre habilite.
        habiliteSur(AUTRE_UNITE);
        assertThat(processusService.declencher(demande(4, AUTRE_UNITE), JETON, IP).getId()).isNotNull();
    }

    @Test
    @DisplayName("5. Type COMPLEMENTAIRE : refus 422, sans meme interroger le service Identite")
    void typeComplementaireRefuse() {
        DeclenchementProcessusRequest complementaire = new DeclenchementProcessusRequest(
                6, ANNEE, UNITE, TypeProcessusEnum.COMPLEMENTAIRE, 512L, "Beneficiaire omis le 10");

        assertThatThrownBy(() -> processusService.declencher(complementaire, JETON, IP))
                .isInstanceOf(FonctionnaliteNonOuverteException.class)
                .hasMessageContaining("complementaire")
                .hasMessageContaining("DRH");

        // Controle place en tete : inutile d'interroger Identite pour une demande qui
        // ne peut pas aboutir (docs/dispositifs_provisoires.md section 1.3).
        verifyNoInteractions(habilitationClient);
        assertThat(processusRepository
                .findByCodeUniteAndMoisPaiementAndAnneePaiementAndTypeProcessus(
                        UNITE, 6, ANNEE, TypeProcessusEnum.COMPLEMENTAIRE))
                .isEmpty();
        verifyNoInteractions(publicateurAudit);
    }

    @Test
    @DisplayName("6. Agent hors portee : refus, aucun processus cree")
    void agentHorsPorteeRefuse() {
        assertThatThrownBy(() -> processusService.declencher(demande(7, AUTRE_UNITE), JETON, IP))
                .isInstanceOf(AgentNonHabiliteException.class)
                .hasMessageContaining(AUTRE_UNITE);

        assertThat(processusRepository
                .findByCodeUniteAndMoisPaiementAndAnneePaiementAndTypeProcessus(
                        AUTRE_UNITE, 7, ANNEE, TypeProcessusEnum.NORMAL))
                .isEmpty();
    }

    @Test
    @DisplayName("7. Service Identite muet : refus conservateur, aucun processus cree")
    void identiteMuetteRefuse() {
        when(habilitationClient.verifier(anyString(), anyString()))
                .thenReturn(new ServiceIdentiteIndisponible("connexion refusee"));

        assertThatThrownBy(() -> processusService.declencher(demande(8, UNITE), JETON, IP))
                .isInstanceOf(
                        cm.afrilandfirstbank.rations.workflow.domaine.exception
                                .ServiceIdentiteIndisponibleException.class)
                .hasMessageContaining("indisponible");

        assertThat(processusRepository
                .findByCodeUniteAndMoisPaiementAndAnneePaiementAndTypeProcessus(
                        UNITE, 8, ANNEE, TypeProcessusEnum.NORMAL))
                .isEmpty();
    }

    // --- Audit ------------------------------------------------------------------

    @Test
    @DisplayName("8. Le declenchement est trace, avec l'auteur et le delta")
    void declenchementTrace() {
        ProcessusMensuel processus = processusService.declencher(demande(9, UNITE), JETON, IP);

        ArgumentCaptor<EvenementAudit> capture = ArgumentCaptor.forClass(EvenementAudit.class);
        verify(publicateurAudit).publier(capture.capture());
        EvenementAudit evenement = capture.getValue();

        assertThat(evenement.action()).isEqualTo("DECLENCHEMENT_PROCESSUS");
        assertThat(evenement.entiteCible()).isEqualTo("processus_mensuel");
        assertThat(evenement.idEntite()).isEqualTo(processus.getId());
        assertThat(evenement.adresseIp()).isEqualTo(IP);
        assertThat(evenement.dateAction()).isNotNull();
        // idUtilisateur nul : /identite/habilitation ne rend qu'un login, et
        // processus_mensuel ne porte aucune colonne d'auteur (decision Sprint 3.3).
        assertThat(evenement.idUtilisateur()).isNull();
        assertThat(evenement.detailJson())
                .contains(LOGIN)
                .contains("EN_COURS_SAISIE")
                .contains("NORMAL")
                .contains(UNITE);
    }

    @Test
    @DisplayName("9. Un declenchement refuse ne laisse aucune trace de succes")
    void declenchementRefuseNonTrace() {
        processusService.declencher(demande(10, UNITE), JETON, IP);

        assertThatThrownBy(() -> processusService.declencher(demande(10, UNITE), JETON, IP))
                .isInstanceOf(ProcessusExistantException.class);

        // Une seule publication : celle du declenchement qui a reellement eu lieu.
        // L'audit vient apres la modification d'etat, jamais avant (document maitre 7.3).
        verify(publicateurAudit).publier(any(EvenementAudit.class));
    }

    // --- Consultation -----------------------------------------------------------

    @Test
    @DisplayName("10. Processus inexistant : 404, sans question d'habilitation")
    void consultationProcessusInexistant() {
        assertThatThrownBy(() -> processusService.consulter(9_999_999L, JETON))
                .isInstanceOf(ProcessusIntrouvableException.class);

        // On ne demande pas « avez-vous droit sur l'unite de rien ».
        verifyNoInteractions(habilitationClient);
    }

    @Test
    @DisplayName("11. La portee est verifiee sur l'unite DU PROCESSUS, jamais sur un parametre")
    void consultationVerifiePorteeSurUniteDuProcessus() {
        ProcessusMensuel processus = processusService.declencher(demande(11, UNITE), JETON, IP);
        // Le declenchement a lui aussi interroge Identite : on repart d'un compteur
        // vide pour n'observer que l'appel de la consultation.
        clearInvocations(habilitationClient);

        ProcessusMensuel relu = processusService.consulter(processus.getId(), JETON);

        assertThat(relu.getId()).isEqualTo(processus.getId());
        verify(habilitationClient).verifier(processus.getCodeUnite(), JETON);
    }

    @Test
    @DisplayName("12. Consultation hors portee : refus")
    void consultationHorsPorteeRefusee() {
        ProcessusMensuel processus = processusService.declencher(demande(12, UNITE), JETON, IP);

        habiliteSur(AUTRE_UNITE);

        assertThatThrownBy(() -> processusService.consulter(processus.getId(), JETON))
                .isInstanceOf(AgentNonHabiliteException.class);
    }

    // --- Etat consolide ---------------------------------------------------------

    @Test
    @DisplayName("13. L'etat consolide est demande avec l'identifiant ET l'unite du processus")
    void etatConsolideTransmetUniteDuProcessus() {
        ProcessusMensuel processus = processusService.declencher(demande(1, UNITE), JETON, IP);
        when(consolidationClient.consolider(anyLong(), anyString(), anyString()))
                .thenReturn(new EtatObtenu(etatVide(processus.getId(), UNITE)));

        processusService.consulterEtat(processus.getId(), JETON);

        // Le code unite vient de processus_mensuel, pas de la requete : c'est ce qui
        // rend le controle de portee applicable meme sur un etat sans aucune fiche.
        verify(consolidationClient).consolider(processus.getId(), UNITE, JETON);
    }

    @Test
    @DisplayName("14. Etat sans aucune fiche : rendu normalement, ce n'est pas une erreur")
    void etatVideRenduNormalement() {
        ProcessusMensuel processus = processusService.declencher(demande(2, UNITE), JETON, IP);
        when(consolidationClient.consolider(anyLong(), anyString(), anyString()))
                .thenReturn(new EtatObtenu(etatVide(processus.getId(), UNITE)));

        ProcessusService.EtatProcessus etat =
                processusService.consulterEtat(processus.getId(), JETON);

        assertThat(etat.processus().getId()).isEqualTo(processus.getId());
        assertThat(etat.consolidation().nombreLignes()).isZero();
        assertThat(etat.consolidation().montantTotalFcfa()).isZero();
        assertThat(etat.consolidation().journees()).isEmpty();
        // Le montant porte par Workflow vaut zero lui aussi tant que rien n'est soumis.
        assertThat(etat.processus().getMontantTotal()).isZero();
    }

    @Test
    @DisplayName("15. Service Saisie muet : refus conservateur, jamais un total suppose")
    void saisieMuetteRefusee() {
        ProcessusMensuel processus = processusService.declencher(demande(3, UNITE), JETON, IP);
        when(consolidationClient.consolider(anyLong(), anyString(), anyString()))
                .thenReturn(new ServiceSaisieIndisponible("delai de lecture depasse"));

        assertThatThrownBy(() -> processusService.consulterEtat(processus.getId(), JETON))
                .isInstanceOf(ServiceSaisieIndisponibleException.class)
                .hasMessageContaining("Saisie")
                .hasMessageContaining("Aucun montant n'est suppose");
    }

    @Test
    @DisplayName("16. Etat consolide hors portee : Saisie n'est jamais appele")
    void etatConsolideHorsPorteeNAppellePasSaisie() {
        ProcessusMensuel processus = processusService.declencher(demande(4, UNITE), JETON, IP);

        habiliteSur(AUTRE_UNITE);

        assertThatThrownBy(() -> processusService.consulterEtat(processus.getId(), JETON))
                .isInstanceOf(AgentNonHabiliteException.class);

        verify(consolidationClient, never()).consolider(anyLong(), anyString(), anyString());
    }

}
