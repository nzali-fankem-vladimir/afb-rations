package cm.afrilandfirstbank.rations.workflow.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
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
import cm.afrilandfirstbank.rations.workflow.application.ManqueCompletude;
import cm.afrilandfirstbank.rations.workflow.application.ProcessusService;
import cm.afrilandfirstbank.rations.workflow.application.ResultatSoumission;
import cm.afrilandfirstbank.rations.workflow.application.SoumissionService;
import cm.afrilandfirstbank.rations.workflow.application.ProcessusService.EtatProcessus;
import cm.afrilandfirstbank.rations.workflow.domaine.CodeManqueEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.EtapeWorkflow;
import cm.afrilandfirstbank.rations.workflow.domaine.NomEtapeEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.PieceJointe;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.TransitionProcessus;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.AgentNonHabiliteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.DocumentNonProduitException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.EtatIncompletException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.FonctionnaliteNonOuverteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.PieceJointeExistanteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusExistantException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.TransitionProcessusInterditeException;
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

    @MockitoBean
    private SoumissionService soumissionService;

    /** Empreinte de reference : 8 caracteres de prefixe plus 64 de SHA-256. */
    private static final String EMPREINTE =
            "SHA-256:3f2a91c70b8d4e6a15c9d82f4b7e0a36d51c8f92a4e7b30c6d18f5a9e2c4b7d03";

    /**
     * Une soumission aboutie : les trois ecritures qu'elle produit.
     *
     * <p>Les identifiants sont poses par reflexion : ils sont normalement attribues
     * par la persistance, absente sous {@code @WebMvcTest}. Ouvrir un mutateur
     * d'identifiant sur les entites pour les seuls besoins du test creerait une
     * porte que rien n'oblige a garder fermee.
     */
    private static ResultatSoumission uneSoumission() {
        ProcessusMensuel processus = unProcessus();
        processus.reporterMontantTotal(9000);
        TransitionProcessus.soumettre(processus);
        TransitionProcessus.transfererAuChefUnite(processus);

        EtapeWorkflow etape = new EtapeWorkflow(ID, 7L, 1, NomEtapeEnum.SOUMISSION_AGENT);
        etape.validerAvecSignature(EMPREINTE);
        ReflectionTestUtils.setField(etape, "id", 31L);
        ReflectionTestUtils.setField(etape, "dateCreation", LocalDateTime.of(2026, 9, 1, 10, 24));

        PieceJointe pieceJointe = new PieceJointe(ID, "2026/08/etat-rations-00002-202608-p740.pdf");
        ReflectionTestUtils.setField(pieceJointe, "id", 12L);
        ReflectionTestUtils.setField(pieceJointe, "dateCreation", LocalDateTime.of(2026, 9, 1, 10, 24));

        return new ResultatSoumission(processus, pieceJointe, etape);
    }

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
    @DisplayName("17. Aucun endpoint de validation ni de retour : ils relevent des sous-sprints 4.3 et 4.4")
    void endpointsDesSousSprintsSuivantsAbsents() throws Exception {
        keycloakEmet("AGENT_UNITE");

        // Revise au Sprint 4.2 : /soumission EXISTE desormais et sort donc de cette
        // liste. Les deux autres viendront aux sous-sprints 4.3 et 4.4. Un endpoint
        // declare mais inoperant est pire qu'un endpoint absent : il se decouvre a
        // l'usage.
        for (String chemin : List.of("/processus/740/validation", "/processus/740/retour")) {
            mockMvc.perform(post(chemin)
                            .header(HttpHeaders.AUTHORIZATION, JETON)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isNotFound());
        }
    }

    // --- Soumission (Sprint 4.2) --------------------------------------------------

    @Test
    @DisplayName("18. Soumission par l'agent : 200, avec le statut, le montant, la piece jointe et l'etape")
    void soumissionNominale() throws Exception {
        keycloakEmet("AGENT_UNITE");
        when(soumissionService.soumettre(eq(ID), anyString(), anyString()))
                .thenReturn(uneSoumission());

        mockMvc.perform(post("/processus/{id}/soumission", ID)
                        .header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idProcessus").value(ID))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_DA"))
                .andExpect(jsonPath("$.codeUnite").value(UNITE))
                .andExpect(jsonPath("$.montantTotalFcfa").value(9000))
                // CT-12 : la reponse temoigne des TROIS effets du geste.
                .andExpect(jsonPath("$.pieceJointe.nombreSignatures").value(1))
                .andExpect(jsonPath("$.pieceJointe.typeMime").value("application/pdf"))
                .andExpect(jsonPath("$.etape.nomEtape").value("SOUMISSION_AGENT"))
                .andExpect(jsonPath("$.etape.statutEtape").value("VALIDEE"))
                .andExpect(jsonPath("$.etape.signatureNumerique").value(EMPREINTE));
    }

    @Test
    @DisplayName("19. Le jeton est relaye tel quel au service, jamais reforge")
    void jetonRelayeALaSoumission() throws Exception {
        keycloakEmet("AGENT_UNITE");
        when(soumissionService.soumettre(eq(ID), anyString(), anyString()))
                .thenReturn(uneSoumission());

        mockMvc.perform(post("/processus/{id}/soumission", ID)
                        .header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isOk());

        verify(soumissionService).soumettre(eq(ID), eq(JETON), anyString());
    }

    @Test
    @DisplayName("20. Soumission par un CHEF_UNITE_DA : 403, et le refus est publie en audit")
    void soumissionParUnAutreRole() throws Exception {
        keycloakEmet("CHEF_UNITE_DA");

        // RG-12 : un chef d'unite ne soumet pas a la place de son agent, ce serait
        // un cumul saisie / validation sur un meme dossier.
        mockMvc.perform(post("/processus/{id}/soumission", ID)
                        .header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCES_REFUSE"));

        verify(soumissionService, never()).soumettre(anyLong(), anyString(), anyString());
        verify(publicateurAudit).publier(any());
    }

    @Test
    @DisplayName("21. Soumission sans jeton : 401, service jamais appele, AUCUN audit")
    void soumissionSansJeton() throws Exception {
        mockMvc.perform(post("/processus/{id}/soumission", ID))
                .andExpect(status().isUnauthorized());

        verify(soumissionService, never()).soumettre(anyLong(), anyString(), anyString());
        // Un 401 n'est pas une tentative d'acces : c'est une absence
        // d'authentification, refusee en amont par le filtre (doctrine Sprint 1.3).
        verify(publicateurAudit, never()).publier(any());
    }

    @Test
    @DisplayName("22. CT-13 : etat incomplet, 422 ETAT_INCOMPLET avec la LISTE des manques")
    void soumissionEtatIncomplet() throws Exception {
        keycloakEmet("AGENT_UNITE");
        when(soumissionService.soumettre(eq(ID), anyString(), anyString()))
                .thenThrow(new EtatIncompletException(
                        "L'etat 08/2026 de l'unite 00002 ne peut pas etre soumis : 2 point(s) a corriger.",
                        List.of(
                                new ManqueCompletude(CodeManqueEnum.LIGNE_HORS_PERIODE,
                                        "1 ligne(s) hors de la periode 08/2026 : 05/03/2026."),
                                new ManqueCompletude(CodeManqueEnum.BENEFICIAIRE_SANS_COMPTE,
                                        "1 ligne(s) sans numero de compte courant."))));

        mockMvc.perform(post("/processus/{id}/soumission", ID)
                        .header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ETAT_INCOMPLET"))
                // Les cinq champs du contrat restent presents.
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/processus/740/soumission"))
                // Et le champ ajoute au Sprint 4.2 porte la liste, pas de la prose.
                .andExpect(jsonPath("$.manques", hasSize(2)))
                .andExpect(jsonPath("$.manques[0].code").value("LIGNE_HORS_PERIODE"))
                .andExpect(jsonPath("$.manques[0].message",
                        containsString("05/03/2026")))
                .andExpect(jsonPath("$.manques[1].code").value("BENEFICIAIRE_SANS_COMPTE"));
    }

    @Test
    @DisplayName("23. Le champ manques est ABSENT des autres erreurs : le format reste uniforme")
    void champManquesAbsentDesAutresErreurs() throws Exception {
        keycloakEmet("AGENT_UNITE");
        when(soumissionService.soumettre(eq(ID), anyString(), anyString()))
                .thenThrow(new ProcessusIntrouvableException(ID));

        mockMvc.perform(post("/processus/{id}/soumission", ID)
                        .header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.manques").doesNotExist());
    }

    @Test
    @DisplayName("24. Etat deja soumis : 422 TRANSITION_INTERDITE")
    void soumissionEtatDejaSoumis() throws Exception {
        keycloakEmet("AGENT_UNITE");
        when(soumissionService.soumettre(eq(ID), anyString(), anyString()))
                .thenThrow(new TransitionProcessusInterditeException(
                        "L'etat 08/2026 de l'unite 00002 ne peut pas etre soumis : son statut est "
                                + "EN_ATTENTE_DA. Il est deja dans le circuit de validation."));

        mockMvc.perform(post("/processus/{id}/soumission", ID)
                        .header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TRANSITION_INTERDITE"));
    }

    @Test
    @DisplayName("25. Piece jointe deja generee : 409, quelque chose est bien duplique")
    void soumissionPieceJointeExistante() throws Exception {
        keycloakEmet("AGENT_UNITE");
        when(soumissionService.soumettre(eq(ID), anyString(), anyString()))
                .thenThrow(new PieceJointeExistanteException(
                        "Un document a deja ete genere pour l'etat 08/2026 de l'unite 00002."));

        mockMvc.perform(post("/processus/{id}/soumission", ID)
                        .header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PIECE_JOINTE_EXISTANTE"));
    }

    @Test
    @DisplayName("26. Document non produit : 500, et non un refus metier")
    void soumissionDocumentNonProduit() throws Exception {
        keycloakEmet("AGENT_UNITE");
        when(soumissionService.soumettre(eq(ID), anyString(), anyString()))
                .thenThrow(new DocumentNonProduitException(
                        "Le document n'a pas pu etre ecrit (disque plein). "
                                + "Aucune soumission n'est enregistree."));

        // Ce n'est ni une maladresse de l'agent ni une regle de gestion : c'est une
        // defaillance du serveur. Le presenter en 422 enverrait l'agent corriger une
        // saisie qui n'a rien de faux.
        mockMvc.perform(post("/processus/{id}/soumission", ID)
                        .header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NON_PRODUIT"))
                .andExpect(jsonPath("$.message",
                        containsString("Aucune soumission n'est enregistree")));
    }

}
