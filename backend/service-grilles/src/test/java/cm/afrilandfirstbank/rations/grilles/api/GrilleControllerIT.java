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
import java.time.LocalDateTime;
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
import cm.afrilandfirstbank.rations.grilles.application.DecisionGrilleService;
import cm.afrilandfirstbank.rations.grilles.application.DecisionGrilleService.ResultatValidation;
import cm.afrilandfirstbank.rations.grilles.application.GrilleService;
import cm.afrilandfirstbank.rations.grilles.domaine.GrilleTarifaire;
import cm.afrilandfirstbank.rations.grilles.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.SessionEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.StatutGrilleEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.TransitionGrille;
import cm.afrilandfirstbank.rations.grilles.domaine.exception.ConflitGrilleException;
import cm.afrilandfirstbank.rations.grilles.domaine.exception.GrilleIntrouvableException;
import cm.afrilandfirstbank.rations.grilles.domaine.exception.MotifRejetRequisException;
import cm.afrilandfirstbank.rations.grilles.domaine.exception.TransitionGrilleInterditeException;
import cm.afrilandfirstbank.rations.grilles.infrastructure.identite.IdentiteIndisponibleException;
import cm.afrilandfirstbank.rations.grilles.infrastructure.config.RoleJwtConverter;
import cm.afrilandfirstbank.rations.grilles.infrastructure.config.SecurityConfig;

/**
 * Chaine jeton -> securite -> controleur des quatre endpoints du service :
 * proposition par l'ARH (Sprint 2.2), decision par la DRH (Sprint 2.3).
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

    @MockitoBean
    private DecisionGrilleService decisionGrilleService;

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

    // --- POST /grilles/{id}/validation et /rejet (Sprint 2.3) ---------------

    private static ResultatValidation bascule(GrilleTarifaire validee, GrilleTarifaire ancienne) {
        return new ResultatValidation(validee, ancienne);
    }

    /** Grille cible telle que le service la rend apres validation : ACTIVE, validateur pose. */
    private static GrilleTarifaire grilleValidee(Long id, LocalDate dateDebut) {
        GrilleTarifaire grille = grilleEnAttente(id, NatureEnum.TRANSPORT, SessionEnum.SOIR,
                3000, dateDebut);
        TransitionGrille.valider(grille, 7L, LocalDateTime.of(2026, 8, 27, 11, 4), "TCHINDA Agnes");
        return grille;
    }

    /** Ancienne grille telle que le service la rend apres fermeture : ACTIVE avec une date de fin. */
    private static GrilleTarifaire grilleFermee(Long id, LocalDate dateFin) {
        GrilleTarifaire grille = grilleEnAttente(id, NatureEnum.TRANSPORT, SessionEnum.SOIR,
                2500, LocalDate.of(2026, 1, 1));
        TransitionGrille.valider(grille, 7L, LocalDateTime.of(2025, 12, 20, 15, 41), "TCHINDA Agnes");
        TransitionGrille.fermer(grille, dateFin);
        return grille;
    }

    @Test
    @DisplayName("11. POST /grilles/{id}/validation sans jeton : 401")
    void validationSansJetonRefusee() throws Exception {
        mockMvc.perform(post("/grilles/29/validation"))
                .andExpect(status().isUnauthorized());

        verify(decisionGrilleService, never()).valider(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("12. POST /grilles/{id}/validation avec un jeton ARH : 403, la decision est reservee a la DRH")
    void validationParArhRefusee() throws Exception {
        keycloakEmet(SUB_ARH, "claire_nkolo", "ARH");

        // Le coeur de RG-14 : l'ARH qui pourrait valider ses propres propositions
        // annulerait le double regard que la regle institue.
        mockMvc.perform(post("/grilles/29/validation").header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCES_REFUSE"));

        verify(decisionGrilleService, never()).valider(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("13. POST /grilles/{id}/validation avec un jeton DRH : 200, nouvelle ACTIVE et ancienne fermee")
    void validationParDrhAcceptee() throws Exception {
        keycloakEmet(SUB_DRH, "agnes_tchinda", "DRH");
        when(decisionGrilleService.valider(eq(29L), anyString(), anyString()))
                .thenReturn(bascule(grilleValidee(29L, LocalDate.of(2026, 9, 1)),
                        grilleFermee(12L, LocalDate.of(2026, 8, 31))));

        mockMvc.perform(post("/grilles/29/validation").header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grille.id").value(29))
                .andExpect(jsonPath("$.grille.statutValidation").value("ACTIVE"))
                .andExpect(jsonPath("$.grille.dateFin").doesNotExist())
                .andExpect(jsonPath("$.grille.validateur").value("TCHINDA Agnes"))
                // Ce que l'interface doit pouvoir afficher : ce qui a ete remplace,
                // et depuis quand la remplacante s'applique.
                .andExpect(jsonPath("$.ancienneFermee.id").value(12))
                .andExpect(jsonPath("$.ancienneFermee.dateFin").value("2026-08-31"))
                .andExpect(jsonPath("$.ancienneFermee.statutValidation").value("ACTIVE"));
    }

    @Test
    @DisplayName("13bis. premiere grille du couple : 200, ancienneFermee absente")
    void validationSansAncienne() throws Exception {
        keycloakEmet(SUB_DRH, "agnes_tchinda", "DRH");
        when(decisionGrilleService.valider(eq(29L), anyString(), anyString()))
                .thenReturn(bascule(grilleValidee(29L, LocalDate.of(2026, 9, 1)), null));

        mockMvc.perform(post("/grilles/29/validation").header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grille.statutValidation").value("ACTIVE"))
                .andExpect(jsonPath("$.ancienneFermee").doesNotExist());
    }

    @Test
    @DisplayName("14. POST /grilles/{id}/validation sur un identifiant inexistant : 404")
    void validationSurGrilleInexistante() throws Exception {
        keycloakEmet(SUB_DRH, "agnes_tchinda", "DRH");
        when(decisionGrilleService.valider(eq(999L), anyString(), anyString()))
                .thenThrow(new GrilleIntrouvableException(
                        "Aucune grille tarifaire ne porte l'identifiant 999."));

        mockMvc.perform(post("/grilles/999/validation").header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GRILLE_INTROUVABLE"))
                .andExpect(jsonPath("$.path").value("/grilles/999/validation"));
    }

    @Test
    @DisplayName("14bis. validation d'une grille au statut incompatible : 422 TRANSITION_INTERDITE")
    void validationStatutIncompatible() throws Exception {
        keycloakEmet(SUB_DRH, "agnes_tchinda", "DRH");
        when(decisionGrilleService.valider(eq(29L), anyString(), anyString()))
                .thenThrow(new TransitionGrilleInterditeException(
                        "Transition de statut interdite : ACTIVE -> ACTIVE."));

        // 422 et non 409 : rien n'est duplique, c'est une regle de gestion qui
        // refuse. La DRH lit typiquement une liste chargee quelques minutes plus
        // tot ; le message doit lui dire dans quel etat la grille se trouve.
        mockMvc.perform(post("/grilles/29/validation").header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TRANSITION_INTERDITE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ACTIVE")));
    }

    @Test
    @DisplayName("14ter. service Identite injoignable pendant une validation : 503, rien n'a bascule")
    void validationServiceIdentiteInjoignable() throws Exception {
        keycloakEmet(SUB_DRH, "agnes_tchinda", "DRH");
        when(decisionGrilleService.valider(eq(29L), anyString(), anyString()))
                .thenThrow(new IdentiteIndisponibleException(
                        "Le service Identite n'a pas repondu dans le delai imparti."));

        // Doctrine 1.3 rendue en 503 (decision 2.2) : la DRH possede le droit
        // qu'elle exerce, un 403 l'enverrait reclamer une habilitation acquise.
        mockMvc.perform(post("/grilles/29/validation").header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_IDENTITE_INDISPONIBLE"));
    }

    @Test
    @DisplayName("15. POST /grilles/{id}/rejet sans motif : 400, le champ est obligatoire")
    void rejetSansMotifRefuse() throws Exception {
        keycloakEmet(SUB_DRH, "agnes_tchinda", "DRH");

        mockMvc.perform(post("/grilles/29/rejet")
                .header(HttpHeaders.AUTHORIZATION, JETON)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("motif")));

        verify(decisionGrilleService, never()).rejeter(any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("15bis. motif fait d'espaces : 400 — une chaine vide n'est pas un motif")
    void rejetMotifEnEspacesRefuse() throws Exception {
        keycloakEmet(SUB_DRH, "agnes_tchinda", "DRH");

        mockMvc.perform(post("/grilles/29/rejet")
                .header(HttpHeaders.AUTHORIZATION, JETON)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motif\":\"     \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"));

        verify(decisionGrilleService, never()).rejeter(any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("15ter. le service refuse un motif vide qui aurait franchi la couche api : 422 MOTIF_OBLIGATOIRE")
    void rejetMotifRefuseParLeService() throws Exception {
        keycloakEmet(SUB_DRH, "agnes_tchinda", "DRH");
        when(decisionGrilleService.rejeter(eq(29L), anyString(), anyString(), anyString()))
                .thenThrow(new MotifRejetRequisException(
                        "Le rejet d'une grille exige un motif (RG-10)."));

        // La regle vit aux deux etages, et le code du contrat differe de celui de
        // la validation de forme : 422 MOTIF_OBLIGATOIRE, meme code que le retour
        // d'un processus sans motif (contrat d'API section 5).
        mockMvc.perform(post("/grilles/29/rejet")
                .header(HttpHeaders.AUTHORIZATION, JETON)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motif\":\"quelconque\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
    }

    @Test
    @DisplayName("POST /grilles/{id}/rejet avec un jeton DRH et un motif : 200, statut REJETEE")
    void rejetParDrhAccepte() throws Exception {
        keycloakEmet(SUB_DRH, "agnes_tchinda", "DRH");
        GrilleTarifaire rejetee = grilleEnAttente(29L, NatureEnum.TRANSPORT, SessionEnum.SOIR,
                3000, LocalDate.of(2026, 9, 1));
        TransitionGrille.rejeter(rejetee, "Montant superieur au bareme en vigueur",
                LocalDateTime.of(2026, 8, 27, 11, 30), 7L, "TCHINDA Agnes");
        when(decisionGrilleService.rejeter(eq(29L), anyString(), anyString(), anyString()))
                .thenReturn(rejetee);

        mockMvc.perform(post("/grilles/29/rejet")
                .header(HttpHeaders.AUTHORIZATION, JETON)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motif\":\"Montant superieur au bareme en vigueur\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutValidation").value("REJETEE"))
                .andExpect(jsonPath("$.motifRejet").value("Montant superieur au bareme en vigueur"))
                .andExpect(jsonPath("$.validateur").value("TCHINDA Agnes"));
    }

    @Test
    @DisplayName("POST /grilles/{id}/rejet avec un jeton ARH : 403")
    void rejetParArhRefuse() throws Exception {
        keycloakEmet(SUB_ARH, "claire_nkolo", "ARH");

        mockMvc.perform(post("/grilles/29/rejet")
                .header(HttpHeaders.AUTHORIZATION, JETON)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motif\":\"Trop cher\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCES_REFUSE"));

        verify(decisionGrilleService, never()).rejeter(any(), anyString(), anyString(), anyString());
    }

}
