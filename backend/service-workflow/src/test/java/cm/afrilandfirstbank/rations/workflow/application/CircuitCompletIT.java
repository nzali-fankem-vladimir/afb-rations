package cm.afrilandfirstbank.rations.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;

import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.workflow.api.dto.DeclenchementProcessusRequest;
import cm.afrilandfirstbank.rations.workflow.api.dto.ProcessusResponse;
import cm.afrilandfirstbank.rations.workflow.application.ResultatHabilitationUnite.AgentHabilite;
import cm.afrilandfirstbank.rations.workflow.domaine.EtapeWorkflow;
import cm.afrilandfirstbank.rations.workflow.domaine.NomEtapeEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.RoleEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEtapeEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.SeparationTachesException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.EtapeWorkflowRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ParametreSystemeRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.PieceJointeRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ProcessusMensuelRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.stockage.StockageDocumentsFichier;

import jakarta.persistence.EntityManager;

/**
 * Le circuit de validation de bout en bout, sur ses deux branches et sur son cycle
 * de correction (tests 15 a 18 du guide 4.4).
 *
 * <h2>Ce que ce fichier eprouve, et que les tests de service ne peuvent pas</h2>
 *
 * <p>Chaque service a ses propres tests. Ce qu'ils ne voient pas, c'est ce qui se
 * passe <b>entre</b> eux : qu'un dossier soumis puis valide puis retourne puis
 * resoumis reste coherent, que les rangs d'etape ne se marchent pas dessus, que le
 * document suit, et que la separation des taches ne bloque personne au deuxieme
 * tour. Ce sont exactement les proprietes qui n'apparaissent qu'a l'enchainement.
 *
 * <p>Les services sont <b>reels</b> et cablés entre eux comme en production ; seuls
 * les trois appels reseau sortants sont simules — habilitation, profil et
 * consolidation. Le stockage est un vrai repertoire, le seuil vient de la vraie
 * table {@code parametre_systeme}.
 *
 * <p>Jeux d'essai en <b>annee 2097</b> : aucune collision avec les autres suites sur
 * l'index partiel {@code ux_processus_normal_par_periode}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Circuit complet — les deux branches du seuil, le retour et la reprise")
class CircuitCompletIT {

    private static final String UNITE = "00007";
    private static final int ANNEE = 2097;
    private static final String JETON = "Bearer jeton-de-test";
    private static final String IP = "10.0.0.55";

    private static final String LOGIN_AGENT = "clarisse_mbarga";
    private static final Long ID_AGENT = 41L;
    private static final String LOGIN_CHEF = "raymond_ndzana";
    private static final Long ID_CHEF = 57L;
    private static final String LOGIN_DIRECTEUR = "estelle_fotso";
    private static final Long ID_DIRECTEUR = 12L;

    @TempDir
    Path racineStockage;

    @Autowired
    private ProcessusMensuelRepository processusRepository;
    @Autowired
    private EtapeWorkflowRepository etapeRepository;
    @Autowired
    private PieceJointeRepository pieceJointeRepository;
    @Autowired
    private ParametreSystemeRepository parametreSystemeRepository;
    @Autowired
    private EntityManager entityManager;

    private HabilitationClient habilitationClient;
    private ProfilClient profilClient;
    private ConsolidationClient consolidationClient;
    private StockageDocumentsFichier stockage;
    private SeuilService seuilService;

    private ProcessusService processusService;
    private SoumissionService soumissionService;
    private ValidationService validationService;
    private RetourService retourService;

    private static int prochainMois = 1;

    @BeforeEach
    void preparer() {
        habilitationClient = mock(HabilitationClient.class);
        profilClient = mock(ProfilClient.class);
        consolidationClient = mock(ConsolidationClient.class);
        PublicateurAudit publicateurAudit = mock(PublicateurAudit.class);

        stockage = new StockageDocumentsFichier(racineStockage.toString());
        SignatureService signatureService = new SignatureService(new DocumentService(), stockage);
        seuilService = new SeuilService(parametreSystemeRepository);
        HabilitationService habilitationService = new HabilitationService(habilitationClient);
        SeparationTachesService separationTachesService =
                new SeparationTachesService(etapeRepository);

        processusService = new ProcessusService(processusRepository, etapeRepository,
                habilitationService, consolidationClient, publicateurAudit);

        soumissionService = new SoumissionService(processusRepository, pieceJointeRepository,
                habilitationService, profilClient, consolidationClient, new CompletudeService(),
                signatureService, new EnregistrementSoumission(processusRepository, etapeRepository,
                        pieceJointeRepository, publicateurAudit));

        validationService = new ValidationService(processusRepository, pieceJointeRepository,
                habilitationService, profilClient, separationTachesService,
                new AiguillageService(seuilService), signatureService,
                new EnregistrementValidation(processusRepository, etapeRepository,
                        pieceJointeRepository, publicateurAudit));

        retourService = new RetourService(processusRepository, habilitationService, profilClient,
                new EnregistrementRetour(processusRepository, etapeRepository, publicateurAudit));

        // Toutes les portees sont ouvertes : ce fichier eprouve le circuit, pas le
        // controle de portee, qui a ses propres tests dans chaque service.
        when(habilitationClient.verifier(anyString(), anyString()))
                .thenReturn(new AgentHabilite(LOGIN_AGENT, RoleEnum.AGENT_UNITE.name(), UNITE));
    }

    // =====================================================================
    // 17. La branche courte : sous le seuil, deux validations suffisent
    // =====================================================================

    @Test
    @DisplayName("17. Sous le seuil : declenchement, soumission, validation DA, cloture")
    void circuitCompletSousLeSeuil() throws Exception {
        long montant = seuilService.seuilAiguillage();
        Long idProcessus = declencher();

        agent();
        montantConsolide(idProcessus, montant);
        soumissionService.soumettre(idProcessus, JETON, IP);
        rafraichir();

        assertThat(statut(idProcessus)).isEqualTo(StatutEnum.EN_ATTENTE_DA);

        chefUnite();
        ResultatValidation validation = validationService.valider(idProcessus, JETON, IP);
        rafraichir();

        assertThat(validation.processus().getStatut()).isEqualTo(StatutEnum.CLOTURE);
        assertThat(parcours(idProcessus))
                .extracting(EtapeWorkflow::getNomEtape)
                .containsExactly(NomEtapeEnum.SOUMISSION_AGENT, NomEtapeEnum.VALIDATION_DA);
        assertThat(nombreSignatures(idProcessus))
                .as("le directeur reseau n'est pas intervenu : deux visas, pas trois")
                .isEqualTo(2);

        assertThat(texteDuDocument(idProcessus))
                .contains(LOGIN_AGENT)
                .contains(LOGIN_CHEF)
                .doesNotContain(LOGIN_DIRECTEUR);

        assertThat(processusRepository.findById(idProcessus).orElseThrow()
                .isTransmisComptabilite())
                .as("RG-13 : la transmission est le Sprint 5, la cloture ne la declenche pas")
                .isFalse();
    }

    // =====================================================================
    // 18. La branche longue : au-dessus du seuil, trois validations
    // =====================================================================

    @Test
    @DisplayName("18. Au-dessus du seuil : soumission, validation DA, validation DR, cloture")
    void circuitCompletAuDessusDuSeuil() throws Exception {
        long montant = seuilService.seuilAiguillage() + 1;
        Long idProcessus = declencher();

        agent();
        montantConsolide(idProcessus, montant);
        soumissionService.soumettre(idProcessus, JETON, IP);
        rafraichir();

        chefUnite();
        ResultatValidation premierNiveau = validationService.valider(idProcessus, JETON, IP);
        rafraichir();

        assertThat(premierNiveau.processus().getStatut()).isEqualTo(StatutEnum.EN_ATTENTE_DR);
        assertThat(premierNiveau.aiguillage().decision())
                .isEqualTo(DecisionAiguillage.ENVOI_DIRECTEUR_RESEAU);

        directeurReseau();
        ResultatValidation secondNiveau = validationService.valider(idProcessus, JETON, IP);
        rafraichir();

        assertThat(secondNiveau.processus().getStatut()).isEqualTo(StatutEnum.CLOTURE);
        assertThat(secondNiveau.aiguillage())
                .as("plus d'echelon apres le second visa : aucun aiguillage")
                .isNull();

        assertThat(parcours(idProcessus))
                .extracting(EtapeWorkflow::getNomEtape)
                .containsExactly(NomEtapeEnum.SOUMISSION_AGENT, NomEtapeEnum.VALIDATION_DA,
                        NomEtapeEnum.VALIDATION_DR);
        assertThat(parcours(idProcessus))
                .extracting(EtapeWorkflow::getOrdreEtape)
                .containsExactly(1, 2, 3);
        assertThat(nombreSignatures(idProcessus)).isEqualTo(3);

        assertThat(texteDuDocument(idProcessus))
                .as("les trois visas coexistent sur la piece unique")
                .contains(LOGIN_AGENT)
                .contains(LOGIN_CHEF)
                .contains(LOGIN_DIRECTEUR);

        assertThat(processusRepository.findById(idProcessus).orElseThrow()
                .isTransmisComptabilite())
                .as("RG-13 : faux sur les DEUX branches a la fin du Sprint 4")
                .isFalse();
    }

    // =====================================================================
    // 15 et 16. Retour, reprise, resoumission
    // =====================================================================

    /**
     * Test 15 du guide. Apres un retour, l'etat doit redevenir modifiable pour l'agent.
     *
     * <p>Le service Saisie ne peut pas etre appele d'ici — il vit dans un autre
     * service, avec sa propre base. Ce qui est verifiable ici, et qui est exactement
     * ce dont Saisie depend, c'est le <b>libelle de statut rendu par
     * {@code GET /processus/{id}}</b> : {@code StatutProcessusEnum.depuisLibelle} le
     * lit, et {@code estModifiable()} tient {@code RETOURNE} pour modifiable (decision
     * Sprint 3.3). Un libelle different, et toute ecriture de ligne serait refusee en
     * {@code 503} sans aucune erreur de compilation nulle part.
     */
    @Test
    @DisplayName("15. Apres retour, le detail rend RETOURNE et le motif, et l'etat est modifiable")
    void apresRetourLEtatEstModifiableParLAgent() {
        Long idProcessus = unDossierChezLeChefUnite(seuilService.seuilAiguillage());

        chefUnite();
        retourService.retourner(idProcessus, "Journee du 12 saisie deux fois.", JETON, IP);
        rafraichir();

        ProcessusResponse detail = ProcessusResponse.depuis(
                processusService.consulter(idProcessus, JETON));

        assertThat(detail.statut())
                .as("libelle lu par StatutProcessusEnum cote Saisie : RETOURNE est modifiable")
                .isEqualTo(StatutEnum.RETOURNE);
        assertThat(detail.motifRetour()).isEqualTo("Journee du 12 saisie deux fois.");
    }

    /**
     * Test 16 du guide, et le plus complet du fichier : le dossier fait deux tours.
     *
     * <p>Quatre proprietes s'y verifient ensemble, dont aucune n'est visible sur un
     * seul tour de circuit :
     *
     * <ol>
     *   <li>la resoumission repart du <b>debut</b> du circuit ({@code EN_ATTENTE_DA}),
     *       jamais du niveau ou le retour avait eu lieu (RG-07) ;</li>
     *   <li>le document est <b>regenere</b> : il porte le montant corrige et une seule
     *       signature ;</li>
     *   <li>les rangs d'etape continuent la serie, sans quoi le decoupage en cycles ne
     *       saurait plus ou commence le tour courant ;</li>
     *   <li>le <b>meme</b> chef d'unite — souvent le seul habilite sur l'unite — peut
     *       valider la version corrigee qu'il avait lui-meme retournee.</li>
     * </ol>
     */
    @Test
    @DisplayName("16. Retour, correction, resoumission : le circuit repart du debut")
    void retourPuisResoumissionCompleteLeCircuit() throws Exception {
        long montantInitial = seuilService.seuilAiguillage();
        Long idProcessus = unDossierChezLeChefUnite(montantInitial);

        // --- Le chef d'unite retourne ---------------------------------------------
        chefUnite();
        retourService.retourner(idProcessus, "Le transport du 12 est en double.", JETON, IP);
        rafraichir();

        assertThat(statut(idProcessus)).isEqualTo(StatutEnum.RETOURNE);

        // --- L'agent corrige et resoumet -------------------------------------------
        long montantCorrige = montantInitial - 3_000;
        agent();
        montantConsolide(idProcessus, montantCorrige);

        ResultatSoumission resoumission = soumissionService.soumettre(idProcessus, JETON, IP);
        rafraichir();

        assertThat(resoumission.processus().getStatut())
                .as("RG-07 : la resoumission repart du debut, pas du niveau du retour")
                .isEqualTo(StatutEnum.EN_ATTENTE_DA);
        assertThat(resoumission.processus().getMontantTotal())
                .isEqualTo(Math.toIntExact(montantCorrige));

        assertThat(nombreSignatures(idProcessus))
                .as("le document a ete regenere depuis l'etat corrige : une seule signature")
                .isEqualTo(1);
        assertThat(texteDuDocument(idProcessus))
                .as("les visas d'avant le retour ont disparu avec l'ancien fichier")
                .contains(LOGIN_AGENT)
                .doesNotContain(LOGIN_CHEF);

        assertThat(parcours(idProcessus))
                .extracting(EtapeWorkflow::getOrdreEtape)
                .as("les rangs continuent la serie : le cycle courant commence au rang 3")
                .containsExactly(1, 2, 3);
        assertThat(parcours(idProcessus))
                .extracting(EtapeWorkflow::getStatutEtape)
                .containsExactly(StatutEtapeEnum.VALIDEE, StatutEtapeEnum.RETOURNEE,
                        StatutEtapeEnum.VALIDEE);

        // --- Le MEME chef d'unite valide la version corrigee -----------------------
        chefUnite();
        ResultatValidation validation = validationService.valider(idProcessus, JETON, IP);
        rafraichir();

        assertThat(validation.processus().getStatut())
                .as("le decoupage en cycles evite le blocage a vie du seul DA de l'unite")
                .isEqualTo(StatutEnum.CLOTURE);
        assertThat(nombreSignatures(idProcessus)).isEqualTo(2);
    }

    /**
     * Le pendant du test 16 : ce que le decoupage en cycles ne relache <b>pas</b>.
     *
     * <p>L'agent qui vient de resoumettre reste bloque — sa soumission appartient au
     * cycle courant. Sans ce test, on pourrait croire que le decoupage a simplement
     * desactive RG-12.
     */
    @Test
    @DisplayName("16b. Le decoupage en cycles ne relache pas RG-12 pour le soumissionnaire")
    void leDecoupageEnCyclesNeRelachePasLaRegle() {
        Long idProcessus = unDossierChezLeChefUnite(seuilService.seuilAiguillage());

        chefUnite();
        retourService.retourner(idProcessus, "Piece manquante.", JETON, IP);
        rafraichir();

        agent();
        montantConsolide(idProcessus, seuilService.seuilAiguillage());
        soumissionService.soumettre(idProcessus, JETON, IP);
        rafraichir();

        // Le meme agent, promu chef d'unite, tente de valider ce qu'il vient de
        // resoumettre.
        when(profilClient.obtenir(anyString())).thenReturn(new ResultatProfil.ProfilObtenu(
                new ActeurSignataire(ID_AGENT, LOGIN_AGENT, RoleEnum.CHEF_UNITE_DA)));

        assertThatThrownBy(() -> validationService.valider(idProcessus, JETON, IP))
                .isInstanceOf(SeparationTachesException.class)
                .hasMessageContaining("Vous avez soumis cet etat");
    }

    // =====================================================================
    // Outils
    // =====================================================================

    private Long declencher() {
        agent();
        ProcessusMensuel processus = processusService.declencher(
                new DeclenchementProcessusRequest(prochainMois(), ANNEE, UNITE, null, null, null),
                JETON, IP);
        rafraichir();
        return processus.getId();
    }

    /** Un dossier declenche, soumis, et arrete en attente du chef d'unite. */
    private Long unDossierChezLeChefUnite(long montant) {
        Long idProcessus = declencher();
        agent();
        montantConsolide(idProcessus, montant);
        soumissionService.soumettre(idProcessus, JETON, IP);
        rafraichir();
        return idProcessus;
    }

    private void agent() {
        when(profilClient.obtenir(anyString())).thenReturn(new ResultatProfil.ProfilObtenu(
                new ActeurSignataire(ID_AGENT, LOGIN_AGENT, RoleEnum.AGENT_UNITE)));
    }

    private void chefUnite() {
        when(profilClient.obtenir(anyString())).thenReturn(new ResultatProfil.ProfilObtenu(
                new ActeurSignataire(ID_CHEF, LOGIN_CHEF, RoleEnum.CHEF_UNITE_DA)));
    }

    private void directeurReseau() {
        when(profilClient.obtenir(anyString())).thenReturn(new ResultatProfil.ProfilObtenu(
                new ActeurSignataire(ID_DIRECTEUR, LOGIN_DIRECTEUR,
                        RoleEnum.DIRECTEUR_RESEAU_DR)));
    }

    /** Ce que le service Saisie rendrait pour cet etat : une journee, une ligne. */
    private void montantConsolide(Long idProcessus, long montantTotal) {
        ProcessusMensuel processus = processusRepository.findById(idProcessus).orElseThrow();

        EtatConsolide.Beneficiaire beneficiaire = new EtatConsolide.Beneficiaire(
                88L, "TCHOUMBA", "Isabelle", "03707004455667", UNITE);

        EtatConsolide.Ligne ligne = new EtatConsolide.Ligne(
                301L, 31L, 88L, beneficiaire, "RATION", "JOUR",
                Math.toIntExact(montantTotal), 12L,
                LocalDateTime.of(ANNEE, processus.getMoisPaiement(), 12, 7, 45));

        EtatConsolide.Journee journee = new EtatConsolide.Journee(
                31L, LocalDate.of(ANNEE, processus.getMoisPaiement(), 12), "ENREGISTREE",
                1, montantTotal, List.of(ligne));

        EtatConsolide etat = new EtatConsolide(idProcessus, UNITE, processus.getMoisPaiement(),
                ANNEE, 1, 1, 1, montantTotal, List.of(journee));

        when(consolidationClient.consolider(anyLong(), anyString(), anyString()))
                .thenReturn(new ResultatConsolidation.EtatObtenu(etat));
    }

    private StatutEnum statut(Long idProcessus) {
        return processusRepository.findById(idProcessus).orElseThrow().getStatut();
    }

    private List<EtapeWorkflow> parcours(Long idProcessus) {
        return etapeRepository.findByIdProcessusOrderByOrdreEtape(idProcessus);
    }

    private int nombreSignatures(Long idProcessus) {
        return pieceJointeRepository.findByIdProcessus(idProcessus).orElseThrow()
                .getNombreSignatures();
    }

    /**
     * Le texte <b>rendu</b> du PDF. Extrait par iText : le contenu des pages est
     * compresse dans le fichier, et une recherche dans les octets bruts ne trouverait
     * jamais un visa qui s'y trouve pourtant.
     */
    private String texteDuDocument(Long idProcessus) throws Exception {
        String cheminRelatif = pieceJointeRepository.findByIdProcessus(idProcessus).orElseThrow()
                .getCheminFichier();

        StringBuilder texte = new StringBuilder();
        try (PdfDocument pdf = new PdfDocument(
                new PdfReader(racineStockage.resolve(cheminRelatif).toFile()))) {
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                texte.append(PdfTextExtractor.getTextFromPage(pdf.getPage(page)))
                        .append(System.lineSeparator());
            }
        }
        return texte.toString();
    }

    /** Vide le contexte de persistance : la lecture suivante repart de la base. */
    private void rafraichir() {
        entityManager.flush();
        entityManager.clear();
    }

    private static int prochainMois() {
        prochainMois = prochainMois % 12 + 1;
        return prochainMois;
    }

}
