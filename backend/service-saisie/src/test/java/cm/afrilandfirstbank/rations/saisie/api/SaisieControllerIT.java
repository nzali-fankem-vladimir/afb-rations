package cm.afrilandfirstbank.rations.saisie.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
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

import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.saisie.application.CommandeCreationLigne;
import cm.afrilandfirstbank.rations.saisie.application.FicheJournaliereService;
import cm.afrilandfirstbank.rations.saisie.application.FicheJournaliereService.FicheOuverte;
import cm.afrilandfirstbank.rations.saisie.application.LigneAvecBeneficiaire;
import cm.afrilandfirstbank.rations.saisie.application.LigneService;
import cm.afrilandfirstbank.rations.saisie.domaine.Beneficiaire;
import cm.afrilandfirstbank.rations.saisie.domaine.FicheJournaliere;
import cm.afrilandfirstbank.rations.saisie.domaine.LignePrestation;
import cm.afrilandfirstbank.rations.saisie.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.SessionEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.AgentNonHabiliteException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.DoublonLigneException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.EtatNonModifiableException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.GrilleIndisponibleException;
import cm.afrilandfirstbank.rations.saisie.infrastructure.config.RoleJwtConverter;
import cm.afrilandfirstbank.rations.saisie.infrastructure.config.SecurityConfig;

/**
 * Chaîne jeton -> sécurité -> contrôleur des cinq endpoints du service Saisie
 * (Sprint 3.3), sur le modèle de {@code GrilleControllerIT} (Sprint 2.2).
 *
 * <p>Les couches applicatives ({@link FicheJournaliereService},
 * {@link LigneService}) sont simulées : RG-05, RG-03, RG-04, la vérification de
 * portée et du caractère modifiable sont couvertes par leurs propres tests
 * (Sprint 3.1, 3.2, et les tests unitaires de {@code EtatModifiableService} à
 * écrire séparément si besoin). Ce qui est vérifié ici est ce qu'eux ne
 * peuvent pas voir — les codes HTTP, la protection par rôle, le format JSON de
 * la réponse, et la garantie que le montant transmis par le client n'atteint
 * jamais la commande envoyée au service.
 *
 * <p>Le décodeur de jetons est simulé et la requête porte un véritable en-tête
 * {@code Authorization}, pour qu'elle traverse réellement
 * {@link RoleJwtConverter} — le raccourci MockMvc {@code jwt()} attribuerait des
 * autorités par défaut et contournerait ce convertisseur.
 */
@WebMvcTest(controllers = SaisieController.class)
@Import({ SecurityConfig.class, RoleJwtConverter.class, GestionnaireErreursApi.class })
class SaisieControllerIT {

    private static final String JETON = "Bearer jeton-de-test";
    private static final String SUB_AGENT = "f31c8b04-77aa-4f52-9c18-6d2e0a9b4c73";
    private static final String SUB_DRH = "2e6b91d7-45cc-4a18-8d20-77f1c4b90e3a";

    @Autowired
    private MockMvc mockMvc;

    /** Requis : autoconfiguration de rations-audit-commun non chargée sous @WebMvcTest. */
    @MockitoBean
    private PublicateurAudit publicateurAudit;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private FicheJournaliereService ficheJournaliereService;

    @MockitoBean
    private LigneService ligneService;

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

    private static FicheJournaliere fiche(Long id, Long idProcessus, LocalDate jour) {
        FicheJournaliere fiche = new FicheJournaliere(idProcessus, jour, "00002", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        ReflectionTestUtils.setField(fiche, "id", id);
        ReflectionTestUtils.setField(fiche, "dateCreation", LocalDateTime.of(2026, 8, 18, 8, 0));
        return fiche;
    }

    private static Beneficiaire beneficiaire(Long id, String nom, String prenom, String compte) {
        Beneficiaire beneficiaire = new Beneficiaire(nom, prenom, compte, "00002");
        ReflectionTestUtils.setField(beneficiaire, "id", id);
        return beneficiaire;
    }

    private static LignePrestation ligne(Long id, Long idFiche, Long idBeneficiaire, NatureEnum nature,
            SessionEnum session, int montant, Long idGrille) {
        LignePrestation ligne = new LignePrestation(idFiche, idBeneficiaire, nature, session, montant, idGrille);
        ReflectionTestUtils.setField(ligne, "id", id);
        ReflectionTestUtils.setField(ligne, "dateCreation", LocalDateTime.of(2026, 8, 18, 9, 0));
        return ligne;
    }

    // === Ouverture de fiche : POST /saisie/fiches ===========================

    @Test
    @DisplayName("1. POST /saisie/fiches sans jeton : 401")
    void ouvertureSansJetonRefusee() throws Exception {
        mockMvc.perform(post("/saisie/fiches")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"idProcessus":740,"dateJour":"2026-08-18"}"""))
                .andExpect(status().isUnauthorized());

        verify(ficheJournaliereService, never()).ouvrir(any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("2. POST /saisie/fiches, agent hors portee sur l'unite du processus : 403")
    void ouvertureHorsPorteeRefusee() throws Exception {
        keycloakEmet(SUB_AGENT, "jean_mbarga", "AGENT_UNITE");
        when(ficheJournaliereService.ouvrir(eq(740L), any(), anyString(), any()))
                .thenThrow(new AgentNonHabiliteException(
                        "Vous n'avez pas de droit sur l'unite 00002."));

        mockMvc.perform(post("/saisie/fiches")
                .header(HttpHeaders.AUTHORIZATION, JETON)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"idProcessus":740,"dateJour":"2026-08-18"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UTILISATEUR_NON_HABILITE"));
    }

    @Test
    @DisplayName("3. POST /saisie/fiches, premiere ouverture d'un jour : 201, fiche vierge")
    void premiereOuvertureCreeUneFicheVierge() throws Exception {
        keycloakEmet(SUB_AGENT, "jean_mbarga", "AGENT_UNITE");
        FicheJournaliere ficheCreee = fiche(501L, 740L, LocalDate.of(2026, 8, 18));
        when(ficheJournaliereService.ouvrir(eq(740L), eq(LocalDate.of(2026, 8, 18)), anyString(), any()))
                .thenReturn(new FicheOuverte(ficheCreee, List.of(), true));

        mockMvc.perform(post("/saisie/fiches")
                .header(HttpHeaders.AUTHORIZATION, JETON)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"idProcessus":740,"dateJour":"2026-08-18"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(501))
                .andExpect(jsonPath("$.idProcessus").value(740))
                .andExpect(jsonPath("$.dateJour").value("2026-08-18"))
                .andExpect(jsonPath("$.lignes").isEmpty())
                .andExpect(jsonPath("$.nombreLignes").value(0))
                .andExpect(jsonPath("$.sousTotalFcfa").value(0));
    }

    @Test
    @DisplayName("4. POST /saisie/fiches, seconde ouverture du meme jour : la meme fiche, lignes non videes")
    void secondeOuvertureRendLaMemeFicheAvecSesLignes() throws Exception {
        keycloakEmet(SUB_AGENT, "jean_mbarga", "AGENT_UNITE");
        FicheJournaliere ficheExistante = fiche(501L, 740L, LocalDate.of(2026, 8, 18));
        Beneficiaire beneficiaire = beneficiaire(88L, "MBARGA", "Jean", "00002000123456");
        LignePrestation ligneExistante = ligne(1205L, 501L, 88L, NatureEnum.RATION, SessionEnum.JOUR, 2500, 12L);

        when(ficheJournaliereService.ouvrir(eq(740L), eq(LocalDate.of(2026, 8, 18)), anyString(), any()))
                .thenReturn(new FicheOuverte(
                        ficheExistante, List.of(new LigneAvecBeneficiaire(ligneExistante, beneficiaire)), false));

        mockMvc.perform(post("/saisie/fiches")
                .header(HttpHeaders.AUTHORIZATION, JETON)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"idProcessus":740,"dateJour":"2026-08-18"}"""))
                // C'est le test le plus important du sprint : RG-05 n'est pas une
                // remise a zero. La seconde ouverture rend 200, pas 201, et la
                // ligne deja saisie est toujours la.
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(501))
                .andExpect(jsonPath("$.lignes").isNotEmpty())
                .andExpect(jsonPath("$.lignes[0].id").value(1205))
                .andExpect(jsonPath("$.lignes[0].montantApplique").value(2500))
                .andExpect(jsonPath("$.nombreLignes").value(1))
                .andExpect(jsonPath("$.sousTotalFcfa").value(2500));
    }

    @Test
    @DisplayName("5. POST /saisie/fiches, jour different : fiche distincte, vierge")
    void ouvertureJourDifferentCreeUneAutreFiche() throws Exception {
        keycloakEmet(SUB_AGENT, "jean_mbarga", "AGENT_UNITE");
        FicheJournaliere autreFiche = fiche(502L, 740L, LocalDate.of(2026, 8, 19));
        when(ficheJournaliereService.ouvrir(eq(740L), eq(LocalDate.of(2026, 8, 19)), anyString(), any()))
                .thenReturn(new FicheOuverte(autreFiche, List.of(), true));

        mockMvc.perform(post("/saisie/fiches")
                .header(HttpHeaders.AUTHORIZATION, JETON)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"idProcessus":740,"dateJour":"2026-08-19"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(502))
                .andExpect(jsonPath("$.dateJour").value("2026-08-19"))
                .andExpect(jsonPath("$.lignes").isEmpty());
    }

    // === Gestion des lignes ==================================================

    private static final String CORPS_LIGNE_NOMINALE = """
            {"idFicheJournaliere":501,"beneficiaire":{"nom":"MBARGA","prenom":"Jean",
            "numCompteCourant":"00002000123456","codeAgence":"00002"},
            "nature":"RATION","session":"JOUR"}""";

    @Test
    @DisplayName("6. POST /saisie/lignes, creation nominale : 201, montant resolu depuis la grille")
    void creationNominale() throws Exception {
        keycloakEmet(SUB_AGENT, "jean_mbarga", "AGENT_UNITE");
        Beneficiaire beneficiaire = beneficiaire(88L, "MBARGA", "Jean", "00002000123456");
        LignePrestation ligneCreee = ligne(1205L, 501L, 88L, NatureEnum.RATION, SessionEnum.JOUR, 2500, 12L);
        when(ligneService.creer(any(), anyString(), any()))
                .thenReturn(new LigneAvecBeneficiaire(ligneCreee, beneficiaire));

        mockMvc.perform(post("/saisie/lignes")
                .header(HttpHeaders.AUTHORIZATION, JETON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CORPS_LIGNE_NOMINALE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1205))
                .andExpect(jsonPath("$.idBeneficiaire").value(88))
                .andExpect(jsonPath("$.nature").value("RATION"))
                .andExpect(jsonPath("$.session").value("JOUR"))
                .andExpect(jsonPath("$.montantApplique").value(2500))
                .andExpect(jsonPath("$.idGrille").value(12));
    }

    @Test
    @DisplayName("7. POST /saisie/lignes, doublon : 409 DOUBLON_LIGNE")
    void creationEnDoublonRefusee() throws Exception {
        keycloakEmet(SUB_AGENT, "jean_mbarga", "AGENT_UNITE");
        when(ligneService.creer(any(), anyString(), any()))
                .thenThrow(new DoublonLigneException(
                        "MBARGA Jean (compte 00002000123456) figure deja sur la journee du 2026-08-18 "
                                + "en RATION / JOUR."));

        mockMvc.perform(post("/saisie/lignes")
                .header(HttpHeaders.AUTHORIZATION, JETON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CORPS_LIGNE_NOMINALE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOUBLON_LIGNE"));
    }

    @Test
    @DisplayName("8. POST /saisie/lignes, aucune grille active : 422 GRILLE_INDISPONIBLE")
    void creationSansGrilleRefusee() throws Exception {
        keycloakEmet(SUB_AGENT, "jean_mbarga", "AGENT_UNITE");
        when(ligneService.creer(any(), anyString(), any()))
                .thenThrow(new GrilleIndisponibleException(
                        "Aucune grille tarifaire n'est en vigueur pour RATION / JOUR au 2026-08-18."));

        mockMvc.perform(post("/saisie/lignes")
                .header(HttpHeaders.AUTHORIZATION, JETON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CORPS_LIGNE_NOMINALE))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("GRILLE_INDISPONIBLE"));
    }

    @Test
    @DisplayName("9. POST /saisie/lignes, montant transmis par le client : ignore")
    void montantClientIgnore() throws Exception {
        keycloakEmet(SUB_AGENT, "jean_mbarga", "AGENT_UNITE");
        Beneficiaire beneficiaire = beneficiaire(88L, "MBARGA", "Jean", "00002000123456");
        LignePrestation ligneCreee = ligne(1205L, 501L, 88L, NatureEnum.RATION, SessionEnum.JOUR, 2500, 12L);
        when(ligneService.creer(any(), anyString(), any()))
                .thenReturn(new LigneAvecBeneficiaire(ligneCreee, beneficiaire));

        // Le client tente d'imposer 999999 : le DTO n'a pas de champ montant,
        // Jackson l'ignore silencieusement (@JsonIgnoreProperties). La reponse
        // porte le montant du service, jamais celui envoye.
        mockMvc.perform(post("/saisie/lignes")
                .header(HttpHeaders.AUTHORIZATION, JETON)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"idFicheJournaliere":501,"montantApplique":999999,
                        "beneficiaire":{"nom":"MBARGA","prenom":"Jean",
                        "numCompteCourant":"00002000123456","codeAgence":"00002"},
                        "nature":"RATION","session":"JOUR"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.montantApplique").value(2500));

        org.mockito.ArgumentCaptor<CommandeCreationLigne> commande =
                org.mockito.ArgumentCaptor.forClass(CommandeCreationLigne.class);
        verify(ligneService).creer(commande.capture(), anyString(), any());
        // CommandeCreationLigne n'a structurellement aucun champ montant : rien
        // n'a pu etre lu, rien n'a pu etre transmis.
        assertNoMontantField(commande.getValue());
    }

    private static void assertNoMontantField(CommandeCreationLigne commande) {
        for (var champ : commande.getClass().getRecordComponents()) {
            org.assertj.core.api.Assertions.assertThat(champ.getName().toLowerCase())
                    .doesNotContain("montant");
        }
    }

    @Test
    @DisplayName("10. PUT /saisie/lignes/1205, changement de session : nouveau montant resolu")
    void modificationSessionRecalculeLeMontant() throws Exception {
        keycloakEmet(SUB_AGENT, "jean_mbarga", "AGENT_UNITE");
        Beneficiaire beneficiaire = beneficiaire(88L, "MBARGA", "Jean", "00002000123456");
        LignePrestation ligneRevisee =
                ligne(1205L, 501L, 88L, NatureEnum.RATION, SessionEnum.SOIR, 3000, 13L);
        when(ligneService.modifier(eq(1205L), eq(NatureEnum.RATION), eq(SessionEnum.SOIR), anyString(), any()))
                .thenReturn(new LigneAvecBeneficiaire(ligneRevisee, beneficiaire));

        mockMvc.perform(put("/saisie/lignes/1205")
                .header(HttpHeaders.AUTHORIZATION, JETON)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nature":"RATION","session":"SOIR"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session").value("SOIR"))
                .andExpect(jsonPath("$.montantApplique").value(3000))
                .andExpect(jsonPath("$.idGrille").value(13));
    }

    @Test
    @DisplayName("11. PUT /saisie/lignes/1205, la nouvelle combinaison est deja prise : 409")
    void modificationCreantUnDoublonRefusee() throws Exception {
        keycloakEmet(SUB_AGENT, "jean_mbarga", "AGENT_UNITE");
        when(ligneService.modifier(eq(1205L), any(), any(), anyString(), any()))
                .thenThrow(new DoublonLigneException(
                        "MBARGA Jean (compte 00002000123456) figure deja sur la journee du 2026-08-18 "
                                + "en RATION / SOIR."));

        mockMvc.perform(put("/saisie/lignes/1205")
                .header(HttpHeaders.AUTHORIZATION, JETON)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nature":"RATION","session":"SOIR"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOUBLON_LIGNE"));
    }

    @Test
    @DisplayName("12. DELETE /saisie/lignes/1205 : 204")
    void suppressionRendNoContent() throws Exception {
        keycloakEmet(SUB_AGENT, "jean_mbarga", "AGENT_UNITE");

        mockMvc.perform(delete("/saisie/lignes/1205")
                .header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isNoContent());

        verify(ligneService, times(1)).supprimer(eq(1205L), anyString(), anyString());
    }

    @Test
    @DisplayName("12b. Sprint 6.3 : l'adresse d'origine atteint le service sur la modification ET la suppression")
    void adresseOrigineTransmiseSurLesTroisEcrituresDeLigne() throws Exception {
        keycloakEmet(SUB_AGENT, "jean_mbarga", "AGENT_UNITE");
        Beneficiaire beneficiaire = beneficiaire(88L, "MBARGA", "Jean", "00002000123456");
        LignePrestation ligneRevisee =
                ligne(1205L, 501L, 88L, NatureEnum.RATION, SessionEnum.SOIR, 3000, 13L);
        when(ligneService.modifier(eq(1205L), any(), any(), anyString(), any()))
                .thenReturn(new LigneAvecBeneficiaire(ligneRevisee, beneficiaire));

        mockMvc.perform(put("/saisie/lignes/1205")
                .with(requete -> { requete.setRemoteAddr("10.20.30.40"); return requete; })
                .header(HttpHeaders.AUTHORIZATION, JETON)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nature":"RATION","session":"SOIR"}"""))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/saisie/lignes/1205")
                .with(requete -> { requete.setRemoteAddr("10.20.30.40"); return requete; })
                .header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isNoContent());

        // Jusqu'au Sprint 6.3, ces deux appels passaient null en adresse, alors
        // que la creation la renseignait : trois ecritures sur la meme entite,
        // deux facons de les tracer. C'etait un oubli, et rien ne le signalait.
        verify(ligneService).modifier(eq(1205L), any(), any(), anyString(), eq("10.20.30.40"));
        verify(ligneService).supprimer(eq(1205L), anyString(), eq("10.20.30.40"));
    }

    @Test
    @DisplayName("13. POST /saisie/lignes, etat non modifiable : 422 ETAT_NON_MODIFIABLE")
    void ecritureSurEtatNonModifiableRefusee() throws Exception {
        keycloakEmet(SUB_AGENT, "jean_mbarga", "AGENT_UNITE");
        when(ligneService.creer(any(), anyString(), any()))
                .thenThrow(new EtatNonModifiableException(
                        "L'etat de la periode 08/2026 pour l'unite 00002 est SOUMIS : "
                                + "il n'est plus modifiable."));

        mockMvc.perform(post("/saisie/lignes")
                .header(HttpHeaders.AUTHORIZATION, JETON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CORPS_LIGNE_NOMINALE))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ETAT_NON_MODIFIABLE"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("SOUMIS")));
    }

    // === Consultation : GET /saisie/fiches/{id}/lignes =======================

    @Test
    @DisplayName("14. GET /saisie/fiches/501/lignes : lignes avec montant et sous-total")
    void consultationRendLesLignesEtLeSousTotal() throws Exception {
        keycloakEmet(SUB_AGENT, "jean_mbarga", "AGENT_UNITE");
        FicheJournaliere ficheConsultee = fiche(501L, 740L, LocalDate.of(2026, 8, 18));
        Beneficiaire beneficiaire1 = beneficiaire(88L, "MBARGA", "Jean", "00002000123456");
        Beneficiaire beneficiaire2 = beneficiaire(89L, "ATANGANA", "Paul", "00002000987654");
        LignePrestation ligne1 = ligne(1205L, 501L, 88L, NatureEnum.RATION, SessionEnum.JOUR, 2500, 12L);
        LignePrestation ligne2 = ligne(1206L, 501L, 89L, NatureEnum.TRANSPORT, SessionEnum.JOUR, 1500, 14L);

        when(ficheJournaliereService.consulter(eq(501L), anyString())).thenReturn(ficheConsultee);
        when(ligneService.listerLignes(501L)).thenReturn(List.of(
                new LigneAvecBeneficiaire(ligne1, beneficiaire1),
                new LigneAvecBeneficiaire(ligne2, beneficiaire2)));

        mockMvc.perform(get("/saisie/fiches/501/lignes")
                .header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lignes.length()").value(2))
                .andExpect(jsonPath("$.lignes[0].beneficiaire.nom").value("MBARGA"))
                .andExpect(jsonPath("$.nombreLignes").value(2))
                .andExpect(jsonPath("$.sousTotalFcfa").value(4000));
    }

    @Test
    @DisplayName("15. GET /saisie/fiches/501/lignes, consultation hors portee : 403")
    void consultationHorsPorteeRefusee() throws Exception {
        keycloakEmet(SUB_AGENT, "jean_mbarga", "AGENT_UNITE");
        when(ficheJournaliereService.consulter(eq(501L), anyString()))
                .thenThrow(new AgentNonHabiliteException("Vous n'avez pas de droit sur l'unite 00002."));

        mockMvc.perform(get("/saisie/fiches/501/lignes")
                .header(HttpHeaders.AUTHORIZATION, JETON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UTILISATEUR_NON_HABILITE"));

        verify(ligneService, never()).listerLignes(anyLong());
    }

    // === Rôle hors périmètre (couvre le "jeton hors portee" du role) ========

    @Test
    @DisplayName("POST /saisie/fiches avec un jeton DRH : 403, la saisie est reservee a l'agent d'unite")
    void ouvertureParRoleEtrangerRefusee() throws Exception {
        keycloakEmet(SUB_DRH, "agnes_tchinda", "DRH");

        mockMvc.perform(post("/saisie/fiches")
                .header(HttpHeaders.AUTHORIZATION, JETON)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"idProcessus":740,"dateJour":"2026-08-18"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCES_REFUSE"));

        verify(ficheJournaliereService, never()).ouvrir(any(), any(), anyString(), any());
    }

}
