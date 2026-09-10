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
import cm.afrilandfirstbank.rations.workflow.domaine.StatutIntegrationEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.TypeProcessusEnum;
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
    private OuvertureComplementaireService ouvertureComplementaireService;
    private SoumissionService soumissionService;
    private ValidationService validationService;
    private RetourService retourService;
    private AppuiTransmission.ClientDeTest transmissionClient;

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
        transmissionClient = new AppuiTransmission.ClientDeTest();

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
                        pieceJointeRepository, publicateurAudit),
                AppuiTransmission.declenchement(
                        transmissionClient, processusRepository, publicateurAudit));

        retourService = new RetourService(processusRepository, habilitationService, profilClient,
                new EnregistrementRetour(processusRepository, etapeRepository, publicateurAudit));

        // Sprint 6bis.1 : l'ouverture d'un etat complementaire, avec le vrai service de
        // lecture du drapeau derriere — c'est le parametre_systeme reel qui commande.
        ouvertureComplementaireService = new OuvertureComplementaireService(
                processusRepository, etapeRepository, habilitationService,
                new FonctionnaliteService(parametreSystemeRepository), publicateurAudit);

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

        // Sprint 5.1 : la cloture met l'etat a la disposition de la comptabilite. Le
        // drapeau de RG-13 n'est pose qu'apres accuse du broker, et le statut d'integration
        // avance avec lui -- les deux disent le meme fait vu de deux cotes.
        assertThat(transmissionClient.processusAppeles()).containsExactly(idProcessus);
        ProcessusMensuel transmis = processusRepository.findById(idProcessus).orElseThrow();
        assertThat(transmis.isTransmisComptabilite())
                .as("RG-13 : le drapeau est pose apres l'accuse du broker")
                .isTrue();
        assertThat(transmis.getStatutIntegration())
                .isEqualTo(StatutIntegrationEnum.EN_ATTENTE);
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

        // La branche longue transmet aussi : le declenchement est branche sur le statut
        // atteint, pas sur le niveau qui a valide -- c'est ce qui garantit que les DEUX
        // points de cloture sont couverts sans qu'aucun ne puisse etre oublie.
        assertThat(transmissionClient.processusAppeles()).containsExactly(idProcessus);
        ProcessusMensuel transmis = processusRepository.findById(idProcessus).orElseThrow();
        assertThat(transmis.isTransmisComptabilite())
                .as("RG-13 : vrai sur les DEUX branches du seuil")
                .isTrue();
        assertThat(transmis.getStatutIntegration())
                .isEqualTo(StatutIntegrationEnum.EN_ATTENTE);
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
    // 11 (guide 5.3). La chaine complete, du declenchement au statut comptable
    // =====================================================================

    /**
     * Test 11 du guide 5.3, dans sa part automatisable.
     *
     * <p>Il enchaine, sur les services reels et la vraie base : declenchement, saisie
     * consolidee, soumission signee, validation, cloture, transmission avec le <b>verrou
     * de RG-13</b>, seconde demande refusee, puis l'accuse comptable applique et le statut
     * remonte jusqu'a {@code INTEGRE}. C'est toute la chaine du module, du Sprint 4.1 au
     * Sprint 5.3.
     *
     * <p><b>Ce qui n'y figure pas, et pourquoi</b> : le passage reel par Kafka. Le pom
     * epingle {@code spring-kafka} en 3.3.0 alors que les {@code kafka-clients} sont en
     * 4.2.1, et {@code @EmbeddedKafka} echoue sur une classe deplacee par Kafka 4 (dette
     * T-01, arbitree au Sprint 5.2). La publication est donc simulee ici, et le bout en
     * bout sur le broker reel se fait a la verification manuelle, comme le guide le
     * prescrit a sa section 8.
     */
    @Test
    @DisplayName("11. Chaine complete : cloture, transmission unique, accuse comptable, INTEGRE")
    void chaineCompleteJusquAuStatutComptable() {
        long montant = seuilService.seuilAiguillage();
        Long idProcessus = declencher();

        agent();
        montantConsolide(idProcessus, montant);
        soumissionService.soumettre(idProcessus, JETON, IP);
        rafraichir();

        chefUnite();
        ResultatValidation validation = validationService.valider(idProcessus, JETON, IP);
        rafraichir();

        assertThat(validation.processus().getStatut()).isEqualTo(StatutEnum.CLOTURE);
        assertThat(validation.transmission().transmis()).isTrue();
        assertThat(transmissionClient.processusAppeles()).containsExactly(idProcessus);

        ProcessusMensuel apresTransmission = processusRepository.findById(idProcessus).orElseThrow();
        assertThat(apresTransmission.isTransmisComptabilite()).isTrue();
        assertThat(apresTransmission.getStatutIntegration())
                .isEqualTo(StatutIntegrationEnum.EN_ATTENTE);
        assertThat(apresTransmission.getDateReservationTransmission())
                .as("sans horodatage, une publication d'issue incertaine serait indiscernable "
                        + "d'un etat en transit normal")
                .isNotNull();

        // CT-22 : une seconde demande ne republie rien. Le verrou est la seule chose qui
        // s'y oppose, et il est ici le vrai, sur la vraie ligne.
        VerrouTransmissionService verrou =
                new VerrouTransmissionService(processusRepository, mock(PublicateurAudit.class));
        assertThat(verrou.reserver(idProcessus, IP).resultat())
                .isEqualTo(ResultatVerrouTransmission.Resultat.DEJA_TRANSMISE);

        // L'accuse comptable revient sur rations.etat.accuse et remonte dans le suivi.
        IntegrationComptableService integration = new IntegrationComptableService(
                processusRepository, mock(PublicateurAudit.class));
        integration.appliquerAccuse(idProcessus, StatutIntegrationEnum.INTEGRE,
                "CPT-2026-07-000512", "2026-08-18T02:15:00Z", null, IP);
        rafraichir();

        ProcessusMensuel apresAccuse = processusRepository.findById(idProcessus).orElseThrow();
        assertThat(apresAccuse.getStatutIntegration()).isEqualTo(StatutIntegrationEnum.INTEGRE);
        assertThat(apresAccuse.getReferenceComptable()).isEqualTo("CPT-2026-07-000512");
        assertThat(apresAccuse.getDateTraitement()).isNotNull();

        // Et le verrou ne se libere plus : la comptabilite a repondu, l'evenement etait
        // bien parti.
        assertThat(verrou.liberer(idProcessus, "tentative tardive", IP).resultat())
                .isEqualTo(ResultatVerrouTransmission.Resultat.LIBERATION_REFUSEE);
    }

    // =====================================================================
    // Outils
    // =====================================================================

    // =====================================================================
    // Sprint 6bis.1 : la regularisation d'une periode close, de bout en bout
    // =====================================================================

    /**
     * <b>Le scenario complet du sous-sprint 6bis.1</b>, joue par les vrais services :
     * un dossier qui a connu <i>deux</i> cycles est clos, puis regularise.
     *
     * <p>Ce test couvre a lui seul les tests 15, 16 et 17 du guide, et il verrouille au
     * passage le piege du Sprint 4.4 sur le decoupage en cycles.
     *
     * <h2>1. Une origine close sur un SECOND cycle</h2>
     *
     * <p>Le dossier depasse le seuil, monte au directeur reseau, en revient <b>retourne</b>,
     * est corrige, resoumis, revalide, puis clos. Il porte donc plusieurs etapes
     * {@code VALIDEE}, dont une <i>annulee par un retour</i>.
     *
     * <p>C'est exactement le cas ou un calcul naif de la date de cloture — prendre la
     * premiere etape validee — ferait courir le delai de regularisation depuis un visa
     * qui ne vaut plus rien. Ici les deux cycles sont recents, donc le test ne prouve pas
     * la borne ; ce qu'il prouve, c'est que le parcours reellement produit par le circuit
     * est celui que {@code OuvertureComplementaireService} sait lire. La borne, elle, est
     * eprouvee sur des dates anciennes dans {@code OuvertureComplementaireServiceTest}.
     *
     * <h2>2. La saisie fonctionne sans modification du service Saisie</h2>
     *
     * <p>Le complementaire nait {@code EN_COURS_SAISIE}, et c'est tout ce que le service
     * Saisie regarde : sa liste des statuts modifiables ne connait pas la notion de type
     * (voir {@code StatutProcessusEnum} cote Saisie). Aucune adaptation n'a ete
     * necessaire, et ce test dit pourquoi.
     *
     * <h2>3. Un complementaire monte TOUJOURS au directeur reseau</h2>
     *
     * <p>Le complementaire porte ici 5 000 XAF, tres en dessous du seuil : un etat normal
     * de ce montant se cloturerait chez le chef d'unite. Celui-ci monte quand meme.
     *
     * <p><b>Regle provisoire, retenue au Sprint 6bis.1 avant l'arbitrage du metier.</b>
     * Trois lectures etaient possibles ; celle-ci est la plus exigeante, et c'est
     * pourquoi elle est retenue en attendant : quand on decide sans le metier, on decide
     * dans le sens qui demande <i>plus</i> d'approbation. Comparer le montant du
     * complementaire au seuil aurait permis de fractionner une regularisation en
     * plusieurs etats restant chacun sous la barre ; le laisser clore par le seul chef
     * d'unite aurait autorise un complementaire de n'importe quel montant sans second
     * regard.
     *
     * <p>Si le metier arbitre autrement, la bascule tient dans
     * {@code AiguillageService} : la comparaison de RG-08 n'existe qu'a cet endroit. Voir
     * {@code docs/decisions/2026-09-05-ouverture-etat-complementaire-et-drapeau.md} § 8.
     */
    @Test
    @DisplayName("21. Regularisation d'une periode close, apres un dossier a deux cycles")
    void regularisationApresUnDossierADeuxCycles() throws Exception {
        long seuil = seuilService.seuilAiguillage();

        // --- Cycle 1 : soumission, visa du chef, montee au DR, puis retour ------------
        Long idOrigine = unDossierChezLeChefUnite(seuil + 1);

        chefUnite();
        validationService.valider(idOrigine, JETON, IP);
        rafraichir();
        assertThat(statut(idOrigine)).isEqualTo(StatutEnum.EN_ATTENTE_DR);

        directeurReseau();
        retourService.retourner(idOrigine, "Montant du 12 a verifier", JETON, IP);
        rafraichir();
        assertThat(statut(idOrigine)).isEqualTo(StatutEnum.RETOURNE);

        // --- Cycle 2 : correction, resoumission, deux visas, cloture ------------------
        agent();
        montantConsolide(idOrigine, seuil + 1);
        soumissionService.soumettre(idOrigine, JETON, IP);
        rafraichir();

        chefUnite();
        validationService.valider(idOrigine, JETON, IP);
        rafraichir();

        directeurReseau();
        validationService.valider(idOrigine, JETON, IP);
        rafraichir();

        ProcessusMensuel origine = processusRepository.findById(idOrigine).orElseThrow();
        assertThat(origine.getStatut()).isEqualTo(StatutEnum.CLOTURE);
        assertThat(parcours(idOrigine))
                .as("deux cycles : six etapes, dont un retour au milieu")
                .hasSize(6);

        // Photographie de l'origine avant la regularisation.
        int montantOrigine = origine.getMontantTotal();
        List<String> signaturesOrigine = parcours(idOrigine).stream()
                .map(EtapeWorkflow::getSignatureNumerique)
                .toList();

        // --- La regularisation -------------------------------------------------------
        ouvrirLeDrapeauDeRattrapage();
        agent();

        ProcessusMensuel complementaire = ouvertureComplementaireService.ouvrir(
                new DeclenchementProcessusRequest(
                        origine.getDateDebut(), origine.getDateFin(),
                        origine.getCodeUnite(), TypeProcessusEnum.COMPLEMENTAIRE,
                        idOrigine, "TCHOUMBA Isabelle omise le 22"),
                JETON, IP);
        rafraichir();

        Long idComplementaire = complementaire.getId();
        assertThat(complementaire.getTypeProcessus()).isEqualTo(TypeProcessusEnum.COMPLEMENTAIRE);
        assertThat(complementaire.getIdProcessusOrigine()).isEqualTo(idOrigine);
        assertThat(statut(idComplementaire))
                .as("le service Saisie tient EN_COURS_SAISIE pour modifiable, "
                        + "sans regarder le type : la saisie fonctionne sans adaptation")
                .isEqualTo(StatutEnum.EN_COURS_SAISIE);

        // --- Le circuit s'applique, avec le second niveau obligatoire ------------------
        // Montant volontairement DERISOIRE devant le seuil : un etat normal de ce
        // montant se cloturerait chez le chef d'unite. Le complementaire, lui, monte.
        long montantComplementaire = 5_000L;
        assertThat(montantComplementaire).isLessThan(seuil);

        agent();
        montantConsolide(idComplementaire, montantComplementaire);
        soumissionService.soumettre(idComplementaire, JETON, IP);
        rafraichir();
        assertThat(statut(idComplementaire)).isEqualTo(StatutEnum.EN_ATTENTE_DA);

        chefUnite();
        ResultatValidation premierNiveauComplementaire =
                validationService.valider(idComplementaire, JETON, IP);
        rafraichir();

        assertThat(premierNiveauComplementaire.processus().getStatut())
                .as("un etat COMPLEMENTAIRE monte au directeur reseau quel que soit son "
                        + "montant : le visa du chef d'unite ne suffit pas a clore une "
                        + "regularisation sur une periode deja payee")
                .isEqualTo(StatutEnum.EN_ATTENTE_DR);
        assertThat(premierNiveauComplementaire.aiguillage().decision())
                .isEqualTo(DecisionAiguillage.COMPLEMENTAIRE_ENVOI_DIRECTEUR_RESEAU);
        assertThat(premierNiveauComplementaire.aiguillage().seuilApplique())
                .as("aucune comparaison n'a eu lieu : inscrire un seuil ferait croire "
                        + "le contraire")
                .isNull();

        directeurReseau();
        ResultatValidation clotureComplementaire =
                validationService.valider(idComplementaire, JETON, IP);
        rafraichir();

        assertThat(clotureComplementaire.processus().getStatut()).isEqualTo(StatutEnum.CLOTURE);
        assertThat(parcours(idComplementaire))
                .extracting(EtapeWorkflow::getNomEtape)
                .as("trois etapes : le second niveau est obligatoire pour un complementaire")
                .containsExactly(NomEtapeEnum.SOUMISSION_AGENT, NomEtapeEnum.VALIDATION_DA,
                        NomEtapeEnum.VALIDATION_DR);
        assertThat(texteDuDocument(idComplementaire))
                .contains(LOGIN_AGENT)
                .contains(LOGIN_CHEF)
                .contains(LOGIN_DIRECTEUR);

        // La cloture transmet, comme pour un etat normal.
        assertThat(transmissionClient.processusAppeles()).contains(idComplementaire);

        // --- Et l'origine n'a pas bouge d'un iota ------------------------------------
        ProcessusMensuel origineRelue = processusRepository.findById(idOrigine).orElseThrow();
        assertThat(origineRelue.getStatut()).isEqualTo(StatutEnum.CLOTURE);
        assertThat(origineRelue.getMontantTotal()).isEqualTo(montantOrigine);
        assertThat(origineRelue.getIdProcessusOrigine()).isNull();
        assertThat(parcours(idOrigine))
                .as("les six etapes de l'origine, et leurs signatures, sont intactes")
                .hasSize(6)
                .extracting(EtapeWorkflow::getSignatureNumerique)
                .isEqualTo(signaturesOrigine);
    }

    /**
     * Ouvre le drapeau pour les besoins du test. La transaction de
     * {@code @DataJpaTest} l'annule ensuite : la valeur de la migration V2 —
     * {@code false} — reste celle de la base.
     */
    private void ouvrirLeDrapeauDeRattrapage() {
        entityManager
                .createQuery("update ParametreSysteme p set p.valeur = 'true' where p.code = :code")
                .setParameter("code", FonctionnaliteService.CODE_RATTRAPAGE_ACTIF)
                .executeUpdate();
        rafraichir();
    }

    private Long declencher() {
        agent();
        ProcessusMensuel processus = processusService.declencher(
                requeteSurUnePeriodeNeuve(),
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
                processus.getDateDebut().withDayOfMonth(12).atTime(7, 45));

        EtatConsolide.Journee journee = new EtatConsolide.Journee(
                31L, processus.getDateDebut().withDayOfMonth(12), "ENREGISTREE",
                1, montantTotal, List.of(ligne));

        EtatConsolide etat = new EtatConsolide(idProcessus, UNITE, processus.getDateDebut(),
                processus.getDateFin(), 1, 1, 1, montantTotal, List.of(journee));

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


    /**
     * Une demande de declenchement sur une periode neuve, disjointe des precedentes.
     *
     * <p>Le mois du compteur n'est evalue <b>qu'une fois</b> : compose deux fois pour
     * former les deux bornes, il produirait une periode a cheval sur deux mois
     * differents. Et les periodes doivent rester disjointes — la contrainte
     * {@code ex_processus_normal_sans_chevauchement} refuse desormais deux etats
     * NORMAL qui se recouvrent, meme partiellement (Maille 1).
     */
    private DeclenchementProcessusRequest requeteSurUnePeriodeNeuve() {
        LocalDate debut = LocalDate.of(ANNEE, prochainMois(), 1);
        return new DeclenchementProcessusRequest(
                debut, debut.plusMonths(1).minusDays(1), UNITE, null, null, null);
    }

}
