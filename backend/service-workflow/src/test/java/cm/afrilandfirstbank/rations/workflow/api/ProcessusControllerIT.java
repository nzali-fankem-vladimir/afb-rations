package cm.afrilandfirstbank.rations.workflow.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.workflow.application.EtatConsolide;
import cm.afrilandfirstbank.rations.workflow.application.ProcessusService;
import cm.afrilandfirstbank.rations.workflow.application.ProcessusService.EtatProcessus;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.TransitionProcessus;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.AgentNonHabiliteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.FonctionnaliteNonOuverteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusExistantException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ServiceIdentiteIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ServiceSaisieIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.config.RoleJwtConverter;
import cm.afrilandfirstbank.rations.workflow.infrastructure.config.SecurityConfig;

/**
 * Les trois endpoints du Sprint 4.1, traverses de bout en bout :
 * <b>jeton -&gt; securite -&gt; controleur -&gt; gestionnaire d'erreurs</b>.
 *
 * <p>Ce que {@code ProcessusServiceTest} ne peut pas voir : les codes HTTP, les
 * roles ouverts a chaque endpoint, le format d'erreur uniforme, la publication
 * des refus en audit (CT-04), et <b>les noms de champs de la reponse</b> — dont
 * cinq sont un contrat inter-services que le service Saisie lit deja
 * (action B-04, {@code docs/dispositifs_provisoires.md}).
 */
@WebMvcTest(controllers = ProcessusController.class)
@Import({ SecurityConfig.class, RoleJwtConverter.class, GestionnaireErreursApi.class })
class ProcessusControllerIT {

    private static final String JETON = "Bearer jeton-de-test";
    private static final String SUB = "f31c8b04-77aa-4f52-9c18-6d2e0a9b4c73";
    private static final String LOGIN = "jean_mbarga";
    private static final String UNITE = "00002";
    private static final Long ID = 740L;

    private static final String CORPS_NORMAL = """
            {"moisPaiement":8,"anneePaiement":2026,"codeUnite":"00002"}""";

    @Autowired
    private MockMvc mockMvc;

    /** Requis : autoconfiguration de rations-audit-commun non chargee sous @WebMvcTest. */
    @MockitoBean
    private PublicateurAudit publicateurAudit;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ProcessusService processusService;

    private void keycloakEmet(String role) {
        when(jwtDecoder.decode(anyString())).thenReturn(Jwt.withTokenValue("jeton-de-test")
                .header("alg", "RS256")
                .subject(SUB)
                .claim("preferred_username", LOGIN)
                .claim("realm_access", Map.of("roles", List.of(role)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build());
    }

    /** Processus d'aout 2026 pour l'unite 00002, en cours de saisie. */
    private static ProcessusMensuel unProcessus() {
        ProcessusMensuel processus = TransitionProcessus.declencher(8, 2026, UNITE);
        ReflectionTestUtils.setField(processus, "id", ID);
        ReflectionTestUtils.setField(processus, "dateCreation", LocalDateTime.of(2026, 8, 1, 9, 0));
        return processus;
    }

    /** Un etat a deux journees : 6 500 + 2 500 = 9 000. */
    private static EtatConsolide uneConsolidation() {
        EtatConsolide.Beneficiaire mballa = new EtatConsolide.Beneficiaire(
                55L, "MBALLA", "Paul", "03702009991111", UNITE);

        EtatConsolide.Journee le10 = new EtatConsolide.Journee(
                11L, LocalDate.of(2026, 8, 10), "EN_SAISIE", 2, 6_500L,
                List.of(new EtatConsolide.Ligne(101L, 11L, 55L, mballa, "RATION", "JOUR",
                                4_000, 12L, LocalDateTime.of(2026, 8, 10, 9, 0)),
                        new EtatConsolide.Ligne(102L, 11L, 55L, mballa, "TRANSPORT", "JOUR",
                                2_500, 13L, LocalDateTime.of(2026, 8, 10, 9, 5))));

        EtatConsolide.Journee le11 = new EtatConsolide.Journee(
                12L, LocalDate.of(2026, 8, 11), "EN_SAISIE", 1, 2_500L,
                List.of(new EtatConsolide.Ligne(103L, 12L, 55L, mballa, "RATION", "SOIR",
                        2_500, 14L, LocalDateTime.of(2026, 8, 11, 9, 0))));

        return new EtatConsolide(ID, UNITE, 8, 2026, 2, 3, 1, 9_000L, List.of(le10, le11));
    }

    // =====================================================================
    // POST /processus
    // =====================================================================

    @Test
    @DisplayName("1. Sans jeton : 401, le service n'est jamais appele")
    void sansJetonRefuse() throws Exception {
        mockMvc.perform(post("/processus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPS_NORMAL))
                .andExpect(status().isUnauthorized());

        verify(processusService, never()).declencher(any(), anyString(), anyString());
        // Un 401 n'est pas trace : c'est une absence d'authentification, pas une
        // action hors perimetre (docs/publication-audit.md section 4).
        verifyNoInteractions(publicateurAudit);
    }

    @Test
    @DisplayName("2. AGENT_UNITE : 201, en-tete Location, statut EN_COURS_SAISIE")
    void declenchementParAgent() throws Exception {
        keycloakEmet("AGENT_UNITE");
        when(processusService.declencher(any(), anyString(), anyString())).thenReturn(unProcessus());

        mockMvc.perform(post("/processus")
                        .header(HttpHeaders.AUTHORIZATION, JETON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPS_NORMAL))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/processus/740"))
                .andExpect(jsonPath("$.idProcessus").value(740))
                .andExpect(jsonPath("$.statut").value("EN_COURS_SAISIE"))
                .andExpect(jsonPath("$.typeProcessus").value("NORMAL"))
                .andExpect(jsonPath("$.montantTotal").value(0))
                .andExpect(jsonPath("$.transmisComptabilite").value(false));
    }

    @Test
    @DisplayName("3. Le jeton est relaye tel quel au service, jamais reemis")
    void jetonRelayeTelQuel() throws Exception {
        keycloakEmet("AGENT_UNITE");
        when(processusService.declencher(any(), anyString(), anyString())).thenReturn(unProcessus());

        mockMvc.perform(post("/processus")
                        .header(HttpHeaders.AUTHORIZATION, JETON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPS_NORMAL))
                .andExpect(status().isCreated());

        verify(processusService).declencher(any(), eq(JETON), anyString());
    }

    @Test
    @DisplayName("4. CHEF_UNITE_DA sur le declenchement : 403 ACCES_REFUSE, trace en audit")
    void declenchementInterditAuChefUnite() throws Exception {
        keycloakEmet("CHEF_UNITE_DA");

        mockMvc.perform(post("/processus")
                        .header(HttpHeaders.AUTHORIZATION, JETON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPS_NORMAL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCES_REFUSE"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.path").value("/processus"));

        verify(processusService, never()).declencher(any(), anyString(), anyString());
        // CT-04 : le refus de role est publie sur rations.audit.evenement.
        verify(publicateurAudit).publier(any(EvenementAudit.class));
    }

    @Test
    @DisplayName("5. Mois hors bornes : 400 REQUETE_INVALIDE, le service n'est pas appele")
    void moisInvalideRefuse() throws Exception {
        keycloakEmet("AGENT_UNITE");

        mockMvc.perform(post("/processus")
                        .header(HttpHeaders.AUTHORIZATION, JETON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"moisPaiement":13,"anneePaiement":2026,"codeUnite":"00002"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("mois")));

        verify(processusService, never()).declencher(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("6. Code unite mal forme : 400 REQUETE_INVALIDE")
    void codeUniteMalFormeRefuse() throws Exception {
        keycloakEmet("AGENT_UNITE");

        mockMvc.perform(post("/processus")
                        .header(HttpHeaders.AUTHORIZATION, JETON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"moisPaiement":8,"anneePaiement":2026,"codeUnite":"2"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"));

        verify(processusService, never()).declencher(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("7. Type COMPLEMENTAIRE : 422 FONCTIONNALITE_NON_OUVERTE, message explicite")
    void typeComplementaireRefuse() throws Exception {
        keycloakEmet("AGENT_UNITE");
        when(processusService.declencher(any(), anyString(), anyString()))
                .thenThrow(new FonctionnaliteNonOuverteException(
                        "L'ouverture d'un etat complementaire n'est pas encore ouverte. "
                                + "Rapprochez-vous de la DRH."));

        mockMvc.perform(post("/processus")
                        .header(HttpHeaders.AUTHORIZATION, JETON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"moisPaiement":7,"anneePaiement":2026,"codeUnite":"00002",
                                 "typeProcessus":"COMPLEMENTAIRE","idProcessusOrigine":512,
                                 "motifOuverture":"Beneficiaire omis les 10 et 15 juillet"}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FONCTIONNALITE_NON_OUVERTE"))
                // Un code distinct : un 403 ferait chercher qui peut donner le droit,
                // alors que personne ne le peut aujourd'hui.
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("DRH")));
    }

    @Test
    @DisplayName("8. Second declenchement, meme unite et periode : 409 PROCESSUS_EXISTANT")
    void doublonRefuse() throws Exception {
        keycloakEmet("AGENT_UNITE");
        when(processusService.declencher(any(), anyString(), anyString()))
                .thenThrow(new ProcessusExistantException(
                        "Un etat mensuel est deja ouvert pour l'unite 00002 en 8/2026 "
                                + "(processus n° 740, statut EN_COURS_SAISIE)."));

        mockMvc.perform(post("/processus")
                        .header(HttpHeaders.AUTHORIZATION, JETON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPS_NORMAL))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROCESSUS_EXISTANT"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("740")));
    }

    @Test
    @DisplayName("9. Hors portee : 403 UTILISATEUR_NON_HABILITE, trace en audit (CT-04)")
    void horsPorteeRefuse() throws Exception {
        keycloakEmet("AGENT_UNITE");
        when(processusService.declencher(any(), anyString(), anyString()))
                .thenThrow(new AgentNonHabiliteException(
                        "Vous n'avez pas de droit sur l'unite 00007."));

        mockMvc.perform(post("/processus")
                        .header(HttpHeaders.AUTHORIZATION, JETON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPS_NORMAL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UTILISATEUR_NON_HABILITE"));

        verify(publicateurAudit).publier(any(EvenementAudit.class));
    }

    @Test
    @DisplayName("10. Service Identite muet : 503, et non 403 — la panne doit rester visible")
    void identiteMuetteRendUn503() throws Exception {
        keycloakEmet("AGENT_UNITE");
        when(processusService.declencher(any(), anyString(), anyString()))
                .thenThrow(new ServiceIdentiteIndisponibleException(
                        "Le service Identite est momentanement indisponible."));

        mockMvc.perform(post("/processus")
                        .header(HttpHeaders.AUTHORIZATION, JETON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPS_NORMAL))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_IDENTITE_INDISPONIBLE"));

        verify(publicateurAudit).publier(any(EvenementAudit.class));
    }

    // =====================================================================
    // GET /processus/{id}
    // =====================================================================

    @Test
    @DisplayName("11. Les cinq champs lus par le service Saisie sont presents (action B-04)")
    void contratInterServicesRespecte() throws Exception {
        keycloakEmet("AGENT_UNITE");
        when(processusService.consulter(anyLong(), anyString())).thenReturn(unProcessus());

        mockMvc.perform(get("/processus/740").header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isOk())
                // ProcessusReponse (service Saisie) lit exactement ces cinq noms. Les
                // renommer ferait refuser toute ecriture de ligne en 503, sans aucune
                // erreur de compilation nulle part.
                .andExpect(jsonPath("$.idProcessus").value(740))
                .andExpect(jsonPath("$.statut").value("EN_COURS_SAISIE"))
                .andExpect(jsonPath("$.codeUnite").value("00002"))
                .andExpect(jsonPath("$.moisPaiement").value(8))
                .andExpect(jsonPath("$.anneePaiement").value(2026));
    }

    @Test
    @DisplayName("12. Les trois roles du circuit peuvent consulter")
    void lesTroisRolesDuCircuitConsultent() throws Exception {
        when(processusService.consulter(anyLong(), anyString())).thenReturn(unProcessus());

        for (String role : List.of("AGENT_UNITE", "CHEF_UNITE_DA", "DIRECTEUR_RESEAU_DR")) {
            keycloakEmet(role);
            mockMvc.perform(get("/processus/740").header(HttpHeaders.AUTHORIZATION, JETON))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("13. Role hors circuit (DRH) : 403 ACCES_REFUSE")
    void roleHorsCircuitRefuse() throws Exception {
        keycloakEmet("DRH");

        mockMvc.perform(get("/processus/740").header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCES_REFUSE"));

        verify(processusService, never()).consulter(anyLong(), anyString());
    }

    @Test
    @DisplayName("14. Processus inexistant : 404 PROCESSUS_INTROUVABLE, non trace")
    void processusInexistant() throws Exception {
        keycloakEmet("AGENT_UNITE");
        when(processusService.consulter(anyLong(), anyString()))
                .thenThrow(new ProcessusIntrouvableException(999L));

        mockMvc.perform(get("/processus/999").header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROCESSUS_INTROUVABLE"));

        // Une erreur d'usage est une maladresse, pas une tentative.
        verifyNoInteractions(publicateurAudit);
    }

    // =====================================================================
    // GET /processus/{id}/etat
    // =====================================================================

    @Test
    @DisplayName("15. Etat consolide : les deux moities de RG-06, montants entiers")
    void etatConsolideRendu() throws Exception {
        keycloakEmet("CHEF_UNITE_DA");
        when(processusService.consulterEtat(anyLong(), anyString()))
                .thenReturn(new EtatProcessus(unProcessus(), uneConsolidation()));

        mockMvc.perform(get("/processus/740/etat").header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isOk())
                // Ce que Workflow detient.
                .andExpect(jsonPath("$.statut").value("EN_COURS_SAISIE"))
                .andExpect(jsonPath("$.typeProcessus").value("NORMAL"))
                .andExpect(jsonPath("$.montantTotalPorte").value(0))
                // Ce que Saisie produit.
                .andExpect(jsonPath("$.nombreJournees").value(2))
                .andExpect(jsonPath("$.nombreLignes").value(3))
                .andExpect(jsonPath("$.montantTotalFcfa").value(9000))
                .andExpect(jsonPath("$.journees[0].sousTotalFcfa").value(6500))
                .andExpect(jsonPath("$.journees[0].lignes[0].montantApplique").value(4000))
                .andExpect(jsonPath("$.journees[0].lignes[0].beneficiaire.codeAgence").value("00002"))
                // Aucune decimale : le montant qui commandera l'aiguillage RG-08
                // traverse la serialisation en entier.
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("9000.0"))));
    }

    @Test
    @DisplayName("16. Service Saisie muet : 503 SERVICE_SAISIE_INDISPONIBLE")
    void saisieMuetteRendUn503() throws Exception {
        keycloakEmet("AGENT_UNITE");
        when(processusService.consulterEtat(anyLong(), anyString()))
                .thenThrow(new ServiceSaisieIndisponibleException(
                        "Le service Saisie est momentanement indisponible : "
                                + "aucun montant n'est suppose."));

        mockMvc.perform(get("/processus/740/etat").header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_SAISIE_INDISPONIBLE"));
    }

    @Test
    @DisplayName("17. Aucun endpoint de soumission, de validation ni de retour")
    void endpointsDesSousSprintsSuivantsAbsents() throws Exception {
        keycloakEmet("AGENT_UNITE");

        // Ils viendront aux sous-sprints 4.2 a 4.4. Un endpoint declare mais
        // inoperant est pire qu'un endpoint absent : il se decouvre a l'usage.
        for (String chemin : List.of("/processus/740/soumission", "/processus/740/validation",
                "/processus/740/retour")) {
            mockMvc.perform(post(chemin)
                            .header(HttpHeaders.AUTHORIZATION, JETON)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isNotFound());
        }
    }

}
