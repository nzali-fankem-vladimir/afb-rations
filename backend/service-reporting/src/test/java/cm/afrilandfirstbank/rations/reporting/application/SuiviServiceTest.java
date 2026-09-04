package cm.afrilandfirstbank.rations.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cm.afrilandfirstbank.rations.reporting.domaine.HistoriqueDemande;
import cm.afrilandfirstbank.rations.reporting.domaine.HistoriqueDemande.EtapeHistorique;
import cm.afrilandfirstbank.rations.reporting.domaine.LibelleActeur;
import cm.afrilandfirstbank.rations.reporting.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.reporting.domaine.exception.ServiceWorkflowIndisponibleException;
import cm.afrilandfirstbank.rations.reporting.domaine.exception.UtilisateurNonHabiliteException;

/**
 * Pagination et historique — tests 9 à 11 du guide du Sprint 6.1.
 *
 * <p>{@link AgregationService} est bouchonné : ce qui est vérifié ici est la
 * pagination en mémoire et la traduction de l'historique brut du Workflow en
 * historique nommé, jamais le croisement des deux sources (déjà couvert par
 * {@code AgregationServiceTest}).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SuiviService — pagination et historique (Sprint 6.1, CT-31)")
class SuiviServiceTest {

    private static final String JETON = "Bearer jeton-utilisateur";
    private static final Long ID_PROCESSUS = 740L;

    @Mock
    private AgregationService agregationService;

    @Mock
    private WorkflowLectureClient workflowClient;

    @Mock
    private IdentiteLectureClient identiteClient;

    private SuiviService service;

    private void preparer() {
        service = new SuiviService(agregationService, workflowClient, identiteClient);
    }

    @Test
    @DisplayName("9. processus valide sans retour : les etapes sont rendues dans l'ordre")
    void historiqueSansRetour_etapesDansLOrdre() {
        preparer();

        HistoriqueDemande brut = new HistoriqueDemande(ID_PROCESSUS, 8, 2026, "00002", "CLOTURE", List.of(
                etape(1, "SOUMISSION_AGENT", "VALIDEE", 10L, null),
                etape(2, "VALIDATION_DA", "VALIDEE", 20L, null)));

        when(workflowClient.consulterHistorique(ID_PROCESSUS, JETON))
                .thenReturn(new ResultatHistorique.Obtenu(brut));
        when(identiteClient.libelles(anyCollection(), eq(JETON))).thenReturn(Map.of(
                10L, new LibelleActeur(10L, "jean_mbarga", "Jean Mbarga"),
                20L, new LibelleActeur(20L, "alice_ngo", "Alice Ngo")));

        HistoriqueDemande resultat = service.consulterHistorique(ID_PROCESSUS, JETON);

        assertThat(resultat.etapes()).extracting(EtapeHistorique::ordreEtape).containsExactly(1, 2);
        assertThat(resultat.etapes()).extracting(EtapeHistorique::nomEtape)
                .containsExactly("SOUMISSION_AGENT", "VALIDATION_DA");
        assertThat(resultat.etapes().get(0).loginActeur()).isEqualTo("jean_mbarga");
        assertThat(resultat.etapes().get(1).loginActeur()).isEqualTo("alice_ngo");
    }

    @Test
    @DisplayName("10. retour puis resoumission : tous les passages successifs restent visibles, jamais ecrases")
    void historiqueApresRetourEtResoumission_tousLesPassagesVisibles() {
        preparer();

        // Premier cycle : soumis, valide au DA, retourne. Second cycle : resoumis,
        // valide au DA de nouveau. Deux SOUMISSION_AGENT, deux VALIDATION_DA :
        // c'est precisement ce que le rang (ordreEtape) distingue.
        HistoriqueDemande brut = new HistoriqueDemande(ID_PROCESSUS, 8, 2026, "00002", "CLOTURE", List.of(
                etape(1, "SOUMISSION_AGENT", "VALIDEE", 10L, null),
                etape(2, "VALIDATION_DA", "RETOURNEE", 20L, "Montant incoherent sur la journee du 12"),
                etape(3, "SOUMISSION_AGENT", "VALIDEE", 10L, null),
                etape(4, "VALIDATION_DA", "VALIDEE", 20L, null)));

        when(workflowClient.consulterHistorique(ID_PROCESSUS, JETON))
                .thenReturn(new ResultatHistorique.Obtenu(brut));
        when(identiteClient.libelles(anyCollection(), eq(JETON))).thenReturn(Map.of());

        HistoriqueDemande resultat = service.consulterHistorique(ID_PROCESSUS, JETON);

        assertThat(resultat.etapes()).hasSize(4);
        assertThat(resultat.etapes()).extracting(EtapeHistorique::ordreEtape)
                .containsExactly(1, 2, 3, 4);

        // Les deux VALIDATION_DA (rangs 2 et 4) ne sont ni fusionnees ni ecrasees :
        // seul le premier passage porte le motif de retour.
        long nombreValidationDA = resultat.etapes().stream()
                .filter(e -> "VALIDATION_DA".equals(e.nomEtape())).count();
        assertThat(nombreValidationDA).isEqualTo(2);
        assertThat(resultat.etapes().get(1).motifRetour()).contains("incoherent");
        assertThat(resultat.etapes().get(3).motifRetour()).isNull();
    }

    @Test
    @DisplayName("11. consultation hors portee : UtilisateurNonHabiliteException, relayee du Workflow")
    void consultationHorsPortee_refusRelaye() {
        preparer();

        when(workflowClient.consulterHistorique(ID_PROCESSUS, JETON))
                .thenReturn(new ResultatHistorique.AccesRefuse(
                        "vous n'avez pas de droit sur l'unite de ce dossier"));

        assertThatThrownBy(() -> service.consulterHistorique(ID_PROCESSUS, JETON))
                .isInstanceOf(UtilisateurNonHabiliteException.class);

        verify(identiteClient, never()).libelles(any(), any());
    }

    @Test
    @DisplayName("Dossier introuvable : ProcessusIntrouvableException, distincte d'un refus de portee")
    void dossierIntrouvable_exceptionDistincte() {
        preparer();

        when(workflowClient.consulterHistorique(ID_PROCESSUS, JETON))
                .thenReturn(new ResultatHistorique.ProcessusIntrouvable(
                        "aucun etat mensuel ne porte l'identifiant " + ID_PROCESSUS));

        assertThatThrownBy(() -> service.consulterHistorique(ID_PROCESSUS, JETON))
                .isInstanceOf(ProcessusIntrouvableException.class);
    }

    @Test
    @DisplayName("Service Workflow injoignable sur l'historique : refus net")
    void serviceWorkflowInjoignable_refusNet() {
        preparer();

        when(workflowClient.consulterHistorique(ID_PROCESSUS, JETON))
                .thenReturn(new ResultatHistorique.ServiceIndisponible("delai depasse"));

        assertThatThrownBy(() -> service.consulterHistorique(ID_PROCESSUS, JETON))
                .isInstanceOf(ServiceWorkflowIndisponibleException.class);
    }

    @Test
    @DisplayName("Identite injoignable pour les libelles : non bloquant, l'historique s'affiche avec les identifiants nus")
    void identiteInjoignablePourLesLibelles_nonBloquant() {
        preparer();

        HistoriqueDemande brut = new HistoriqueDemande(ID_PROCESSUS, 8, 2026, "00002", "CLOTURE", List.of(
                etape(1, "SOUMISSION_AGENT", "VALIDEE", 10L, null)));

        when(workflowClient.consulterHistorique(ID_PROCESSUS, JETON))
                .thenReturn(new ResultatHistorique.Obtenu(brut));
        // Le client Identite est non bloquant par construction : il rend une carte
        // vide en cas de panne, jamais une exception (voir IdentiteLectureClient).
        when(identiteClient.libelles(anyCollection(), eq(JETON))).thenReturn(Map.of());

        HistoriqueDemande resultat = service.consulterHistorique(ID_PROCESSUS, JETON);

        assertThat(resultat.etapes()).hasSize(1);
        assertThat(resultat.etapes().get(0).idActeur()).isEqualTo(10L);
        assertThat(resultat.etapes().get(0).loginActeur()).isNull();
    }

    private EtapeHistorique etape(int ordre, String nom, String statut, Long idActeur, String motifRetour) {
        return new EtapeHistorique(ordre, nom, statut, idActeur, null, null, motifRetour,
                statut.equals("VALIDEE"), LocalDateTime.of(2026, 8, ordre, 9, 0));
    }

}
