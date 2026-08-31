package cm.afrilandfirstbank.rations.saisie.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.saisie.application.ConsolidationService;
import cm.afrilandfirstbank.rations.saisie.application.EtatConsolide;
import cm.afrilandfirstbank.rations.saisie.application.EtatConsolide.JourneeConsolidee;
import cm.afrilandfirstbank.rations.saisie.application.LigneAvecBeneficiaire;
import cm.afrilandfirstbank.rations.saisie.domaine.Beneficiaire;
import cm.afrilandfirstbank.rations.saisie.domaine.LignePrestation;
import cm.afrilandfirstbank.rations.saisie.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.SessionEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.StatutFicheEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.AgentNonHabiliteException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.UniteNonConcordanteException;
import cm.afrilandfirstbank.rations.saisie.infrastructure.config.RoleJwtConverter;
import cm.afrilandfirstbank.rations.saisie.infrastructure.config.SecurityConfig;

/**
 * Chaine jeton -&gt; securite -&gt; controleur de l'unique endpoint interne du
 * service Saisie, {@code GET /saisie/processus/{id}/etat} (Sprint 3.4), sur le
 * modele de {@code SaisieControllerIT} (Sprint 3.3).
 *
 * <p>{@link ConsolidationService} est simule : RG-06, l'exactitude du total et le
 * recoupement du code unite sont couverts par {@code ConsolidationServiceTest},
 * contre la vraie base. Ce qui est verifie ici est ce que ces tests ne peuvent
 * pas voir — les codes HTTP, <b>l'ouverture aux trois roles du circuit</b>, le
 * refus des autres, le caractere obligatoire du parametre {@code codeUnite}, et
 * le fait que les montants traversent la serialisation JSON en entiers.
 */
@WebMvcTest(controllers = ConsolidationController.class)
@Import({ SecurityConfig.class, RoleJwtConverter.class, GestionnaireErreursApi.class })
class ConsolidationControllerIT {

    private static final String JETON = "Bearer jeton-de-test";
    private static final String SUB = "f31c8b04-77aa-4f52-9c18-6d2e0a9b4c73";
    private static final String ETAT = "/saisie/processus/740/etat";

    @Autowired
    private MockMvc mockMvc;

    /** Requis : autoconfiguration de rations-audit-commun non chargee sous @WebMvcTest. */
    @MockitoBean
    private PublicateurAudit publicateurAudit;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ConsolidationService consolidationService;

    private void keycloakEmet(String role) {
        when(jwtDecoder.decode(anyString())).thenReturn(Jwt.withTokenValue("jeton-de-test")
                .header("alg", "RS256")
                .subject(SUB)
                .claim("preferred_username", "jean_mbarga")
                .claim("realm_access", Map.of("roles", List.of(role)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build());
    }

    /** Un etat a deux journees : 6 500 + 2 500 = 9 000. */
    private static EtatConsolide etatDeDeuxJournees() {
        Beneficiaire mballa = new Beneficiaire("MBALLA", "Paul", "03702009991111", "00002");
        ReflectionTestUtils.setField(mballa, "id", 55L);

        JourneeConsolidee le10 = new JourneeConsolidee(
                11L, LocalDate.of(2026, 8, 10), StatutFicheEnum.EN_SAISIE, 2, 6_500L,
                List.of(ligne(101L, 11L, NatureEnum.RATION, SessionEnum.JOUR, 4_000, mballa),
                        ligne(102L, 11L, NatureEnum.TRANSPORT, SessionEnum.JOUR, 2_500, mballa)));

        JourneeConsolidee le11 = new JourneeConsolidee(
                12L, LocalDate.of(2026, 8, 11), StatutFicheEnum.EN_SAISIE, 1, 2_500L,
                List.of(ligne(103L, 12L, NatureEnum.RATION, SessionEnum.SOIR, 2_500, mballa)));

        return new EtatConsolide(740L, "00002", 8, 2026, 2, 3, 1, 9_000L, List.of(le10, le11));
    }

    private static LigneAvecBeneficiaire ligne(Long id, Long idFiche, NatureEnum nature,
            SessionEnum session, int montant, Beneficiaire beneficiaire) {
        LignePrestation ligne = new LignePrestation(
                idFiche, beneficiaire.getId(), nature, session, montant, 12L);
        ReflectionTestUtils.setField(ligne, "id", id);
        ReflectionTestUtils.setField(ligne, "dateCreation", LocalDateTime.of(2026, 8, 10, 9, 0));
        return new LigneAvecBeneficiaire(ligne, beneficiaire);
    }

    // === Authentification et roles ==========================================

    @Test
    @DisplayName("1. sans jeton : 401, le service n'est jamais appele")
    void sansJeton_refuse() throws Exception {
        mockMvc.perform(get(ETAT).param("codeUnite", "00002"))
                .andExpect(status().isUnauthorized());

        verify(consolidationService, never()).consolider(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("2. role AGENT_UNITE : 200")
    void agentUnite_autorise() throws Exception {
        keycloakEmet("AGENT_UNITE");
        when(consolidationService.consolider(eq(740L), eq("00002"), anyString()))
                .thenReturn(etatDeDeuxJournees());

        mockMvc.perform(get(ETAT).param("codeUnite", "00002").header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("3. role CHEF_UNITE_DA : 200 — il doit lire l'etat qu'il valide")
    void chefUnite_autorise() throws Exception {
        keycloakEmet("CHEF_UNITE_DA");
        when(consolidationService.consolider(eq(740L), eq("00002"), anyString()))
                .thenReturn(etatDeDeuxJournees());

        mockMvc.perform(get(ETAT).param("codeUnite", "00002").header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("4. role DIRECTEUR_RESEAU_DR : 200 — deuxieme niveau de validation")
    void directeurReseau_autorise() throws Exception {
        keycloakEmet("DIRECTEUR_RESEAU_DR");
        when(consolidationService.consolider(eq(740L), eq("00002"), anyString()))
                .thenReturn(etatDeDeuxJournees());

        mockMvc.perform(get(ETAT).param("codeUnite", "00002").header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("5. role hors du circuit (DRH) : 403, le service n'est jamais appele")
    void roleHorsCircuit_refuse() throws Exception {
        keycloakEmet("DRH");

        mockMvc.perform(get(ETAT).param("codeUnite", "00002").header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCES_REFUSE"));

        verify(consolidationService, never()).consolider(anyLong(), anyString(), anyString());
    }

    // === Le parametre codeUnite =============================================

    @Test
    @DisplayName("6. codeUnite absent : 400, aucun repli silencieux")
    void codeUniteAbsent_refuse() throws Exception {
        keycloakEmet("AGENT_UNITE");

        mockMvc.perform(get(ETAT).header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"));

        verify(consolidationService, never()).consolider(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("7. le codeUnite declare est transmis tel quel au service")
    void codeUniteDeclare_transmisAuService() throws Exception {
        keycloakEmet("CHEF_UNITE_DA");
        when(consolidationService.consolider(eq(740L), eq("00003"), anyString()))
                .thenReturn(etatDeDeuxJournees());

        mockMvc.perform(get(ETAT).param("codeUnite", "00003").header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isOk());

        verify(consolidationService).consolider(740L, "00003", JETON);
    }

    // === Portee d'acces et concordance ======================================

    @Test
    @DisplayName("8. consultation hors portee : 403 UTILISATEUR_NON_HABILITE")
    void horsPortee_refuse() throws Exception {
        keycloakEmet("AGENT_UNITE");
        when(consolidationService.consolider(anyLong(), anyString(), anyString()))
                .thenThrow(new AgentNonHabiliteException("Vous n'avez pas de droit sur l'unite 00002."));

        mockMvc.perform(get(ETAT).param("codeUnite", "00002").header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UTILISATEUR_NON_HABILITE"));
    }

    @Test
    @DisplayName("9. unite declaree non concordante : 403 UNITE_NON_CONCORDANTE")
    void uniteNonConcordante_refusee() throws Exception {
        keycloakEmet("AGENT_UNITE");
        when(consolidationService.consolider(anyLong(), anyString(), anyString()))
                .thenThrow(new UniteNonConcordanteException(
                        "Le processus 740 ne releve pas de l'unite 00002 declaree, mais de 00007."));

        mockMvc.perform(get(ETAT).param("codeUnite", "00002").header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UNITE_NON_CONCORDANTE"));
    }

    // === Forme de la reponse ================================================

    @Test
    @DisplayName("10. le JSON porte le total, les sous-totaux et le detail par journee")
    void reponse_porteTotauxEtDetail() throws Exception {
        keycloakEmet("AGENT_UNITE");
        when(consolidationService.consolider(eq(740L), eq("00002"), anyString()))
                .thenReturn(etatDeDeuxJournees());

        mockMvc.perform(get(ETAT).param("codeUnite", "00002").header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idProcessus").value(740))
                .andExpect(jsonPath("$.codeUnite").value("00002"))
                .andExpect(jsonPath("$.moisPaiement").value(8))
                .andExpect(jsonPath("$.anneePaiement").value(2026))
                .andExpect(jsonPath("$.nombreJournees").value(2))
                .andExpect(jsonPath("$.nombreLignes").value(3))
                .andExpect(jsonPath("$.nombreBeneficiaires").value(1))
                .andExpect(jsonPath("$.montantTotalFcfa").value(9000))
                .andExpect(jsonPath("$.journees.length()").value(2))
                .andExpect(jsonPath("$.journees[0].dateJour").value("2026-08-10"))
                .andExpect(jsonPath("$.journees[0].sousTotalFcfa").value(6500))
                .andExpect(jsonPath("$.journees[1].sousTotalFcfa").value(2500));
    }

    @Test
    @DisplayName("11. chaque ligne porte son beneficiaire, sa nature, sa session et son montant")
    void reponse_porteLeDetailDesLignes() throws Exception {
        keycloakEmet("AGENT_UNITE");
        when(consolidationService.consolider(eq(740L), eq("00002"), anyString()))
                .thenReturn(etatDeDeuxJournees());

        mockMvc.perform(get(ETAT).param("codeUnite", "00002").header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.journees[0].lignes[0].beneficiaire.nom").value("MBALLA"))
                .andExpect(jsonPath("$.journees[0].lignes[0].beneficiaire.numCompteCourant")
                        .value("03702009991111"))
                .andExpect(jsonPath("$.journees[0].lignes[0].beneficiaire.codeAgence").value("00002"))
                .andExpect(jsonPath("$.journees[0].lignes[0].nature").value("RATION"))
                .andExpect(jsonPath("$.journees[0].lignes[0].session").value("JOUR"))
                .andExpect(jsonPath("$.journees[0].lignes[0].montantApplique").value(4000))
                .andExpect(jsonPath("$.journees[0].lignes[0].idGrille").value(12));
    }

    @Test
    @DisplayName("12. les montants sont serialises en entiers, jamais en decimaux")
    void montants_serialisesEnEntiers() throws Exception {
        keycloakEmet("AGENT_UNITE");
        when(consolidationService.consolider(eq(740L), eq("00002"), anyString()))
                .thenReturn(etatDeDeuxJournees());

        String json = mockMvc.perform(get(ETAT).param("codeUnite", "00002")
                        .header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(json)
                .as("un montant serialise avec une decimale trahirait un flottant dans la chaine")
                .contains("\"montantTotalFcfa\":9000")
                .contains("\"sousTotalFcfa\":6500")
                .doesNotContain("9000.0")
                .doesNotContain("6500.0");
    }

    @Test
    @DisplayName("13. processus sans aucune journee : 200, total 0, jamais un 404")
    void processusVide_rend200() throws Exception {
        keycloakEmet("CHEF_UNITE_DA");
        when(consolidationService.consolider(eq(740L), eq("00002"), anyString()))
                .thenReturn(new EtatConsolide(740L, "00002", null, null, 0, 0, 0, 0L, List.of()));

        mockMvc.perform(get(ETAT).param("codeUnite", "00002").header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.journees").isEmpty())
                .andExpect(jsonPath("$.nombreJournees").value(0))
                .andExpect(jsonPath("$.montantTotalFcfa").value(0))
                .andExpect(jsonPath("$.codeUnite").value("00002"));
    }

}
