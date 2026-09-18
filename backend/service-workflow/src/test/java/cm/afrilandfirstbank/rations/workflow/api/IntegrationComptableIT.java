package cm.afrilandfirstbank.rations.workflow.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.workflow.application.DocumentTelechargementService;
import cm.afrilandfirstbank.rations.workflow.application.FonctionnaliteService;
import cm.afrilandfirstbank.rations.workflow.application.IntegrationComptableService;
import cm.afrilandfirstbank.rations.workflow.application.OuvertureComplementaireService;
import cm.afrilandfirstbank.rations.workflow.application.ProcessusService;
import cm.afrilandfirstbank.rations.workflow.application.RechercheProcessusService;
import cm.afrilandfirstbank.rations.workflow.application.ResultatIntegrationComptable;
import cm.afrilandfirstbank.rations.workflow.application.RetourService;
import cm.afrilandfirstbank.rations.workflow.application.SoumissionService;
import cm.afrilandfirstbank.rations.workflow.application.ValidationService;
import cm.afrilandfirstbank.rations.workflow.application.VerrouTransmissionService;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutIntegrationEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.TransitionIntegration.Decision;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.AccuseContradictoireException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusNonTransmisException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.config.RoleJwtConverter;
import cm.afrilandfirstbank.rations.workflow.infrastructure.config.SecurityConfig;

/**
 * L'endpoint interne {@code PUT /processus/{id}/integration}, traverse de bout en bout :
 * <b>secret partage -&gt; chaine de securite dediee -&gt; controleur -&gt; gestionnaire
 * d'erreurs</b> (Sprint 5.2).
 *
 * <p>Ce que les tests de service ne peuvent pas voir : que la route est bien gardee par le
 * secret et non par OAuth2, que la chaine dediee ne deborde pas sur les six endpoints du
 * contrat, et que les trois refus portent bien les codes {@code 404}, {@code 422} et
 * {@code 409} — trois codes distincts pour trois gestes differents.
 */
@WebMvcTest(controllers = ProcessusController.class)
@Import({ SecurityConfig.class, RoleJwtConverter.class, GestionnaireErreursApi.class })
class IntegrationComptableIT {

    private static final Long ID = 740L;

    /** Valeur du repli de developpement, celle que porte {@code application-dev.yml}. */
    private static final String CLE = "changeme-in-development";

    private static final String CORPS = """
            {"statutIntegration":"INTEGRE","referenceComptable":"CPT-2026-07-000512",\
            "dateTraitement":"2026-08-18T02:15:00Z"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicateurAudit publicateurAudit;

    /** Requis depuis la Maille 2 : le controleur lit le compte de charge. */
    @MockitoBean
    private FonctionnaliteService fonctionnaliteService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ProcessusService processusService;

    /**
     * Requis depuis le Sprint 6bis.1 : {@code POST /processus} aiguille sur le type
     * demande, et le controleur depend donc des deux services d'ouverture. Sans ce
     * mock, le contexte ne s'assemble pas et les dix tests de ce fichier echouent
     * d'un coup, pour une raison etrangere a leur objet.
     */
    @MockitoBean
    private OuvertureComplementaireService ouvertureComplementaireService;

    @MockitoBean
    private SoumissionService soumissionService;

    @MockitoBean
    private ValidationService validationService;

    @MockitoBean
    private VerrouTransmissionService verrouTransmissionService;

    @MockitoBean
    private RetourService retourService;

    @MockitoBean
    private IntegrationComptableService integrationComptableService;

    /** Requis depuis le Sprint 6.1 : le controleur sert aussi les endpoints internes de suivi. */
    @MockitoBean
    private RechercheProcessusService rechercheProcessusService;

    /** Requis depuis le Sprint 7F.8 : le controleur sert aussi {@code GET /processus/{id}/document}. */
    @MockitoBean
    private DocumentTelechargementService documentTelechargementService;

    // --- Le secret partage --------------------------------------------------------

    @Test
    @DisplayName("Sans en-tete X-Cle-Interne, l'appel est refuse en 401 — et le service n'est "
            + "jamais atteint : c'est une ECRITURE sur le statut de paiement d'un etat")
    void sansCleRefuse() throws Exception {
        mockMvc.perform(put("/processus/{id}/integration", ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CORPS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CLE_INTERNE_INVALIDE"));

        verifyNoInteractions(integrationComptableService);
    }

    @Test
    @DisplayName("Avec une mauvaise cle, meme refus et meme message : distinguer « absent » de "
            + "« invalide » apprendrait deja quelque chose a qui sonde la route")
    void mauvaiseCleRefusee() throws Exception {
        mockMvc.perform(put("/processus/{id}/integration", ID)
                .header("X-Cle-Interne", "une-autre-cle")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CORPS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CLE_INTERNE_INVALIDE"));

        verifyNoInteractions(integrationComptableService);
    }

    @Test
    @DisplayName("Le refus ne divulgue jamais la cle attendue, ni sa longueur, ni son prefixe")
    void leRefusNeDivulgueRien() throws Exception {
        String corpsDuRefus = mockMvc.perform(put("/processus/{id}/integration", ID)
                .header("X-Cle-Interne", "mauvaise")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CORPS))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(corpsDuRefus)
                .doesNotContain(CLE)
                .doesNotContain("changeme")
                // Ni la valeur presentee : la renvoyer la ferait apparaitre dans les
                // journaux d'acces de tout intermediaire HTTP.
                .doesNotContain("mauvaise")
                .doesNotContain(String.valueOf(CLE.length()));
    }

    @Test
    @DisplayName("La chaine du secret ne deborde pas : les endpoints du contrat restent proteges "
            + "par OAuth2, et un appel sans jeton y rend 401 sans passer par le filtre")
    void laChaineDuSecretNeDebordePas() throws Exception {
        // Sans jeton, GET /processus/{id} doit etre refuse par OAuth2 -- et NON par le
        // filtre du secret, dont le code d'erreur serait alors visible.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/processus/{id}", ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    // --- Nominal ------------------------------------------------------------------

    @Test
    @DisplayName("Avec la bonne cle, un accuse applique rend 200 et resultat APPLIQUE")
    void accuseApplique() throws Exception {
        when(integrationComptableService.appliquerAccuse(
                anyLong(), any(), any(), any(), any(), anyString()))
                .thenReturn(unResultat(new Decision.Appliquer()));

        mockMvc.perform(put("/processus/{id}/integration", ID)
                .header("X-Cle-Interne", CLE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CORPS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idProcessus").value(ID))
                .andExpect(jsonPath("$.resultat").value("APPLIQUE"))
                .andExpect(jsonPath("$.statutIntegration").value("INTEGRE"))
                .andExpect(jsonPath("$.referenceComptable").value("CPT-2026-07-000512"));
    }

    @Test
    @DisplayName("Test 8 — un rejeu rend 200 et resultat DEJA_APPLIQUE : ce n'est pas une "
            + "erreur, et l'appelant doit pouvoir le distinguer pour ne pas tracer deux fois")
    void rejeuRendDejaApplique() throws Exception {
        when(integrationComptableService.appliquerAccuse(
                anyLong(), any(), any(), any(), any(), anyString()))
                .thenReturn(unResultat(new Decision.DejaApplique()));

        mockMvc.perform(put("/processus/{id}/integration", ID)
                .header("X-Cle-Interne", CLE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CORPS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultat").value("DEJA_APPLIQUE"));
    }

    // --- Les trois refus, trois codes distincts ------------------------------------

    @Test
    @DisplayName("Test 4 — processus inconnu : 404 PROCESSUS_INTROUVABLE")
    void processusInconnu() throws Exception {
        when(integrationComptableService.appliquerAccuse(
                anyLong(), any(), any(), any(), any(), anyString()))
                .thenThrow(new ProcessusIntrouvableException(ID));

        mockMvc.perform(put("/processus/{id}/integration", ID)
                .header("X-Cle-Interne", CLE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CORPS))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROCESSUS_INTROUVABLE"));
    }

    @Test
    @DisplayName("Test 5 — etat jamais transmis : 422 PROCESSUS_NON_TRANSMIS. Rien n'est "
            + "duplique, c'est une regle de gestion qui refuse")
    void processusNonTransmis() throws Exception {
        when(integrationComptableService.appliquerAccuse(
                anyLong(), any(), any(), any(), any(), anyString()))
                .thenThrow(new ProcessusNonTransmisException(
                        "L'etat 740 n'a jamais ete transmis a la comptabilite."));

        mockMvc.perform(put("/processus/{id}/integration", ID)
                .header("X-Cle-Interne", CLE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CORPS))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PROCESSUS_NON_TRANSMIS"));
    }

    @Test
    @DisplayName("Test 9 — accuse contradictoire : 409 ACCUSE_CONTRADICTOIRE. Deux affirmations "
            + "concurrentes sur la meme ressource, c'est la definition d'un conflit")
    void accuseContradictoire() throws Exception {
        when(integrationComptableService.appliquerAccuse(
                anyLong(), any(), any(), any(), any(), anyString()))
                .thenThrow(new AccuseContradictoireException(
                        "L'etat 740 porte deja INTEGRE et l'accuse recu porte REJETE."));

        mockMvc.perform(put("/processus/{id}/integration", ID)
                .header("X-Cle-Interne", CLE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CORPS))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCUSE_CONTRADICTOIRE"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("REJETE")));
    }

    @Test
    @DisplayName("Un statut absent est refuse en 400 : ici l'appelant est notre propre service, "
            + "et une valeur hors domaine serait un defaut de notre code")
    void statutAbsentRefuse() throws Exception {
        mockMvc.perform(put("/processus/{id}/integration", ID)
                .header("X-Cle-Interne", CLE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"referenceComptable\":\"CPT-1\"}"))
                .andExpect(status().isBadRequest());
    }

    // --- Outils -------------------------------------------------------------------

    private static ResultatIntegrationComptable unResultat(Decision decision) {
        return new ResultatIntegrationComptable(
                ID,
                decision,
                StatutIntegrationEnum.INTEGRE,
                "CPT-2026-07-000512",
                LocalDateTime.of(2026, 8, 18, 3, 15),
                null);
    }

}
