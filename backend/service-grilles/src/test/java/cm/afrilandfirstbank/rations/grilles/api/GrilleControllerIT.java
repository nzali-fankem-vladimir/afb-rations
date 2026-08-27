package cm.afrilandfirstbank.rations.grilles.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.grilles.application.GrilleService;
import cm.afrilandfirstbank.rations.grilles.domaine.GrilleTarifaire;
import cm.afrilandfirstbank.rations.grilles.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.SessionEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.StatutGrilleEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.TransitionGrille;
import cm.afrilandfirstbank.rations.grilles.domaine.exception.ConflitGrilleException;
import cm.afrilandfirstbank.rations.grilles.infrastructure.config.RoleJwtConverter;
import cm.afrilandfirstbank.rations.grilles.infrastructure.config.SecurityConfig;

/**
 * Chaine jeton -> securite -> controleur des deux endpoints du sous-sprint 2.2.
 *
 * <p>Le service applicatif est simule : la logique metier est couverte par
 * {@code GrilleServiceTest} et {@code UniciteGrilleServiceTest}. Ce qui est
 * verifie ici est ce qu'eux ne peuvent pas voir — les codes HTTP, la protection
 * par role, le format de la reponse.
 *
 * <p>Comme dans service-identite, le decodeur de jetons est simule et la requete
 * porte un veritable en-tete {@code Authorization}, pour qu'elle traverse
 * reellement {@link RoleJwtConverter} : le raccourci MockMvc {@code jwt()}
 * attribue des autorites par defaut et contournerait ce convertisseur, donnant un
 * faux negatif sur {@code @PreAuthorize}.
 */
@WebMvcTest(controllers = GrilleController.class)
@Import({ SecurityConfig.class, RoleJwtConverter.class, GestionnaireErreursApi.class })
class GrilleControllerIT {

    private static final String JETON = "Bearer jeton-de-test";
    private static final String SUB_ARH = "8c1f0a52-9d33-4e7a-9f01-3b7c5d8e2a41";
    private static final String SUB_DRH = "2e6b91d7-45cc-4a18-8d20-77f1c4b90e3a";

    private static final String CORPS_TRANSPORT_SOIR = """
            {"nature":"TRANSPORT","session":"SOIR","montantFcfa":3000,"dateDebut":"2026-09-01"}""";

    @Autowired
    private MockMvc mockMvc;

    /**
     * Le port d'audit vient de l'autoconfiguration de rations-audit-commun, que la
     * tranche @WebMvcTest ne charge pas. Le gestionnaire d'erreurs en depend pour
     * tracer les refus (CT-04) : sans ce mock, le contexte ne demarre pas.
     */
    @MockitoBean
    private PublicateurAudit publicateurAudit;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private GrilleService grilleService;

    private void keycloakEmet(String sub, String login, String role) {
        when(jwtDecoder.decode(anyString())).thenReturn(Jwt.withTokenValue("jeton-de-test")
                .header("alg", "RS256")
                .subject(sub)
                .claim("preferred_username", login)
                .claim("realm_access", Map.of("roles", List.of(role)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build());
    }

    private static GrilleTarifaire grilleEnAttente(Long id, NatureEnum nature, SessionEnum session,
            int montant, LocalDate dateDebut) {
        GrilleTarifaire grille = new GrilleTarifaire(nature, session, montant, dateDebut,
                4L, "NKOLO Claire");
        TransitionGrille.soumettre(grille);
        ReflectionTestUtils.setField(grille, "id", id);
        return grille;
    }

    // --- POST /grilles ------------------------------------------------------

    @Test
    @DisplayName("7. POST /grilles sans jeton : 401")
    void creationSansJetonRefusee() throws Exception {
        mockMvc.perform(post("/grilles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CORPS_TRANSPORT_SOIR))
                .andExpect(status().isUnauthorized());

        verify(grilleService, never()).creerEtSoumettre(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("8. POST /grilles avec un jeton DRH : 403, la creation est reservee a l'ARH")
    void creationParDrhRefusee() throws Exception {
        keycloakEmet(SUB_DRH, "agnes_tchinda", "DRH");

        // La DRH tranche les grilles, elle ne les propose pas : c'est la separation
        // des roles voulue par RG-14, pas une restriction arbitraire.
        mockMvc.perform(post("/grilles")
                .header(HttpHeaders.AUTHORIZATION, JETON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CORPS_TRANSPORT_SOIR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCES_REFUSE"))
                .andExpect(jsonPath("$.path").value("/grilles"));

        verify(grilleService, never()).creerEtSoumettre(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("9. POST /grilles avec un jeton ARH, couple libre : 201, statut EN_ATTENTE_DRH")
    void creationParArhAcceptee() throws Exception {
        keycloakEmet(SUB_ARH, "claire_nkolo", "ARH");
        when(grilleService.creerEtSoumettre(any(), anyString(), anyString()))
                .thenReturn(grilleEnAttente(29L, NatureEnum.TRANSPORT, SessionEnum.SOIR,
                        3000, LocalDate.of(2026, 9, 1)));

        mockMvc.perform(post("/grilles")
                .header(HttpHeaders.AUTHORIZATION, JETON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CORPS_TRANSPORT_SOIR))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(29))
                .andExpect(jsonPath("$.nature").value("TRANSPORT"))
                .andExpect(jsonPath("$.session").value("SOIR"))
                .andExpect(jsonPath("$.montantFcfa").value(3000))
                .andExpect(jsonPath("$.dateDebut").value("2026-09-01"))
                .andExpect(jsonPath("$.statutValidation").value("EN_ATTENTE_DRH"))
                // Libelle lisible, pas un identifiant technique (decision Sprint 2.2).
                .andExpect(jsonPath("$.createur").value("NKOLO Claire"))
                // Aucune decision n'a encore ete prise.
                .andExpect(jsonPath("$.validateur").doesNotExist())
                .andExpect(jsonPath("$.dateFin").doesNotExist())
                .andExpect(jsonPath("$.dateValidation").doesNotExist())
                .andExpect(jsonPath("$.motifRejet").doesNotExist());
    }

    @Test
    @DisplayName("10. POST /grilles, couple deja actif sur la periode : 409, message citant nature et session")
    void creationEnConflitRefusee() throws Exception {
        keycloakEmet(SUB_ARH, "claire_nkolo", "ARH");
        when(grilleService.creerEtSoumettre(any(), anyString(), anyString()))
                .thenThrow(new ConflitGrilleException(ConflitGrilleException.CODE_GRILLE_ACTIVE,
                        "Une grille TRANSPORT / SOIR est active depuis le 01/08/2026, a 1500 FCFA. "
                                + "Une nouvelle grille doit prendre effet APRES cette date."));

        mockMvc.perform(post("/grilles")
                .header(HttpHeaders.AUTHORIZATION, JETON)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nature":"TRANSPORT","session":"SOIR","montantFcfa":3000,"dateDebut":"2026-08-01"}""")
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRILLE_ACTIVE_EXISTANTE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("TRANSPORT")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("SOIR")))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("10 bis. POST /grilles, proposition deja en attente : 409 avec un code distinct")
    void creationSurPropositionEnAttenteRefusee() throws Exception {
        keycloakEmet(SUB_ARH, "claire_nkolo", "ARH");
        when(grilleService.creerEtSoumettre(any(), anyString(), anyString()))
                .thenThrow(new ConflitGrilleException(ConflitGrilleException.CODE_PROPOSITION_EN_ATTENTE,
                        "Une grille TRANSPORT / SOIR attend deja la decision de la Directrice RH."));

        mockMvc.perform(post("/grilles")
                .header(HttpHeaders.AUTHORIZATION, JETON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CORPS_TRANSPORT_SOIR))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRILLE_EN_ATTENTE_EXISTANTE"));
    }

    @Test
    @DisplayName("POST /grilles, montant nul : 400 au format d'erreur uniforme")
    void montantNulRefuse() throws Exception {
        keycloakEmet(SUB_ARH, "claire_nkolo", "ARH");

        mockMvc.perform(post("/grilles")
                .header(HttpHeaders.AUTHORIZATION, JETON)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nature":"RATION","session":"JOUR","montantFcfa":0,"dateDebut":"2026-09-01"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("montantFcfa")));

        verify(grilleService, never()).creerEtSoumettre(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("POST /grilles, nature hors enumeration : 400, pas 500")
    void natureInconnueRefusee() throws Exception {
        keycloakEmet(SUB_ARH, "claire_nkolo", "ARH");

        mockMvc.perform(post("/grilles")
                .header(HttpHeaders.AUTHORIZATION, JETON)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nature":"CARBURANT","session":"JOUR","montantFcfa":1500,"dateDebut":"2026-09-01"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"));
    }

    // --- GET /grilles -------------------------------------------------------

    @Test
    @DisplayName("11. GET /grilles avec un jeton ARH : 200, pagination au format de reference")
    void listeParArh() throws Exception {
        keycloakEmet(SUB_ARH, "claire_nkolo", "ARH");
        when(grilleService.lister(any(), any()))
                .thenReturn(new PageImpl<>(
                        List.of(grilleEnAttente(29L, NatureEnum.TRANSPORT, SessionEnum.SOIR,
                                3000, LocalDate.of(2026, 9, 1))),
                        PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/grilles")
                .header(HttpHeaders.AUTHORIZATION, JETON)
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                // Les six champs du format arrete au Sprint 1.2, aucun de plus.
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(29))
                .andExpect(jsonPath("$.content[0].createur").value("NKOLO Claire"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.dernierePage").value(true));
    }

    @Test
    @DisplayName("12. GET /grilles?statut=EN_ATTENTE_DRH : le filtre est transmis au service")
    void listeFiltreeSurEnAttente() throws Exception {
        keycloakEmet(SUB_DRH, "agnes_tchinda", "DRH");
        when(grilleService.lister(eq(StatutGrilleEnum.EN_ATTENTE_DRH), any()))
                .thenReturn(new PageImpl<>(
                        List.of(grilleEnAttente(29L, NatureEnum.TRANSPORT, SessionEnum.SOIR,
                                3000, LocalDate.of(2026, 9, 1))),
                        PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/grilles")
                .header(HttpHeaders.AUTHORIZATION, JETON)
                .param("statut", "EN_ATTENTE_DRH")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].statutValidation").value("EN_ATTENTE_DRH"));

        // Le filtre n'est pas applique en Java apres coup : il descend jusqu'a la
        // requete, sans quoi la pagination porterait sur le mauvais total.
        verify(grilleService).lister(eq(StatutGrilleEnum.EN_ATTENTE_DRH), any());
    }

    @Test
    @DisplayName("GET /grilles sans jeton : 401")
    void listeSansJetonRefusee() throws Exception {
        mockMvc.perform(get("/grilles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /grilles avec un role hors ARH et DRH : 403")
    void listeParRoleInsuffisantRefusee() throws Exception {
        keycloakEmet("4bf9cd35-4f6b-4b62-a005-12b1481d33fa", "jean_mbarga", "AGENT_UNITE");

        mockMvc.perform(get("/grilles").header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCES_REFUSE"));
    }

    @Test
    @DisplayName("GET /grilles?statut=INEXISTANT : 400 au format uniforme, valeurs admises citees")
    void statutInconnuRefuse() throws Exception {
        keycloakEmet(SUB_ARH, "claire_nkolo", "ARH");

        mockMvc.perform(get("/grilles")
                .header(HttpHeaders.AUTHORIZATION, JETON)
                .param("statut", "EN_COURS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("EN_ATTENTE_DRH")));
    }

    // --- Absence d'endpoint de decision (perimetre du sous-sprint) -----------

    @Test
    @DisplayName("aucun endpoint de validation n'existe dans ce sous-sprint")
    void aucunEndpointDeValidation() throws Exception {
        keycloakEmet(SUB_DRH, "agnes_tchinda", "DRH");

        // La validation et le rejet relevent du sous-sprint 2.3. Ce test echouera
        // le jour ou ils seront ajoutes : c'est voulu, il devra etre retire
        // sciemment plutot que l'absence de decision etre perdue de vue.
        mockMvc.perform(post("/grilles/29/validation").header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/grilles/29/rejet").header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isNotFound());
    }

}
