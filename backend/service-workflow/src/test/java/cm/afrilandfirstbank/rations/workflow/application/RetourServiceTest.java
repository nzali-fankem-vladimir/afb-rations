package cm.afrilandfirstbank.rations.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.workflow.application.ResultatHabilitationUnite.AgentHabilite;
import cm.afrilandfirstbank.rations.workflow.application.ResultatHabilitationUnite.AgentNonHabilite;
import cm.afrilandfirstbank.rations.workflow.domaine.EtapeWorkflow;
import cm.afrilandfirstbank.rations.workflow.domaine.NiveauValidation;
import cm.afrilandfirstbank.rations.workflow.domaine.NomEtapeEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.PieceJointe;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.RoleEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEtapeEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.TransitionProcessus;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.AgentNonHabiliteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.MotifRetourRequisException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.RoleNonAttenduException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.TransitionProcessusInterditeException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.EtapeWorkflowRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ParametreSystemeRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.PieceJointeRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ProcessusMensuelRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.stockage.StockageDocumentsFichier;

import jakarta.persistence.EntityManager;

/**
 * Retour motive a l'agent : RG-10, RG-11, US-10, US-11, CT-16, CT-20, CT-23.
 *
 * <h2>Le test qui compte</h2>
 *
 * <p>{@link #retourDirecteurReseauRamAneALAgent()} verrouille RG-11 — le piege du
 * sous-sprint. Renvoyer un dossier du directeur reseau au chef d'unite parait
 * naturel et serait faux : le chef d'unite a deja vise une version que le directeur
 * reseau vient de refuser, et il n'a pas la main sur les lignes de saisie.
 *
 * <h2>Meme parti qu'aux Sprints 4.2 et 4.3 : la vraie base, le vrai stockage</h2>
 *
 * <p>Seuls les appels reseau sont simules. Deux garanties de ce sous-sprint ne
 * vivent que la : que le motif atterrit reellement dans
 * {@code etape_workflow.motif_retour}, et que le retour <b>ne touche pas au
 * document</b> — un stockage simule prouverait qu'on ne l'a pas appele, pas que le
 * fichier est intact.
 *
 * <p>Jeux d'essai en <b>annee 2098</b> : aucune collision possible avec les autres
 * suites sur l'index partiel {@code ux_processus_normal_par_periode}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("RetourService — retour motive a l'agent (RG-10, RG-11)")
class RetourServiceTest {

    private static final String UNITE = "00003";
    private static final int ANNEE = 2098;
    private static final String JETON = "Bearer jeton-de-test";
    private static final String IP = "10.0.0.31";

    private static final String MOTIF = "Montant du 12 aout incoherent avec la grille en vigueur.";

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
    private PublicateurAudit publicateurAudit;
    private StockageDocumentsFichier stockage;
    private SignatureService signatureService;
    private ValidationService validationService;
    private RetourService retourService;
    private ProcessusService processusService;

    /** Compteur de mois : chaque dossier ouvre sa propre periode. */
    private static int prochainMois = 1;

    @BeforeEach
    void preparer() {
        habilitationClient = mock(HabilitationClient.class);
        profilClient = mock(ProfilClient.class);
        publicateurAudit = mock(PublicateurAudit.class);
        stockage = new StockageDocumentsFichier(racineStockage.toString());
        signatureService = new SignatureService(new DocumentService(), stockage);

        HabilitationService habilitationService = new HabilitationService(habilitationClient);

        retourService = new RetourService(
                processusRepository,
                habilitationService,
                profilClient,
                new EnregistrementRetour(processusRepository, etapeRepository, publicateurAudit));

        // Le vrai service de validation, pour construire un dossier reellement monte au
        // directeur reseau plutot qu'un etat fabrique a la main.
        validationService = new ValidationService(
                processusRepository,
                pieceJointeRepository,
                habilitationService,
                profilClient,
                new SeparationTachesService(etapeRepository),
                new AiguillageService(new SeuilService(parametreSystemeRepository)),
                signatureService,
                new EnregistrementValidation(processusRepository, etapeRepository,
                        pieceJointeRepository, publicateurAudit),
                AppuiTransmission.declenchement(new AppuiTransmission.ClientDeTest(),
                        processusRepository, publicateurAudit));

        processusService = new ProcessusService(
                processusRepository,
                etapeRepository,
                habilitationService,
                mock(ConsolidationClient.class),
                publicateurAudit);

        chefHabilite();
        profilDuChef();
    }

    // =====================================================================
    // 10 et 11 : le retour ramene toujours a l'agent (RG-11)
    // =====================================================================

    @Test
    @DisplayName("10. Retour du chef d'unite avec motif : statut RETOURNE, motif enregistre")
    void retourChefUniteAvecMotif() {
        Dossier dossier = unDossierSoumis();

        ResultatRetour resultat = retourService.retourner(dossier.idProcessus(), MOTIF, JETON, IP);

        assertThat(resultat.processus().getStatut()).isEqualTo(StatutEnum.RETOURNE);
        assertThat(resultat.niveauOrigine()).isEqualTo(NiveauValidation.CHEF_UNITE);

        EtapeWorkflow etape = resultat.etape();
        assertThat(etape.getNomEtape()).isEqualTo(NomEtapeEnum.VALIDATION_DA);
        assertThat(etape.getStatutEtape()).isEqualTo(StatutEtapeEnum.RETOURNEE);
        assertThat(etape.getMotifRetour()).isEqualTo(MOTIF);
        assertThat(etape.getIdActeur()).isEqualTo(ID_CHEF);
        assertThat(etape.getOrdreEtape()).isEqualTo(2);
        assertThat(etape.getSignatureNumerique())
                .as("RG-09 ne vaut que pour les validations : un refus n'est pas un visa")
                .isNull();

        assertThat(processusRepository.findById(dossier.idProcessus()).orElseThrow().getStatut())
                .isEqualTo(StatutEnum.RETOURNE);
    }

    /**
     * <b>LE test du sous-sprint.</b> Un retour du directeur reseau redescend
     * directement a l'agent, jamais au chef d'unite qui avait vise.
     */
    @Test
    @DisplayName("11. Retour du directeur reseau : RETOURNE, et surtout PAS EN_ATTENTE_DA")
    void retourDirecteurReseauRamAneALAgent() {
        Dossier dossier = unDossierChezLeDirecteurReseau();
        profilDuDirecteur();
        directeurHabilite();

        ResultatRetour resultat = retourService.retourner(dossier.idProcessus(), MOTIF, JETON, IP);

        assertThat(resultat.processus().getStatut())
                .as("RG-11 : le retour ramene a l'agent, jamais au niveau intermediaire")
                .isEqualTo(StatutEnum.RETOURNE)
                .isNotEqualTo(StatutEnum.EN_ATTENTE_DA);

        assertThat(resultat.niveauOrigine()).isEqualTo(NiveauValidation.DIRECTEUR_RESEAU);
        assertThat(resultat.etape().getNomEtape()).isEqualTo(NomEtapeEnum.VALIDATION_DR);
        assertThat(resultat.etape().getStatutEtape()).isEqualTo(StatutEtapeEnum.RETOURNEE);
        assertThat(resultat.etape().getOrdreEtape()).isEqualTo(3);

        ProcessusMensuel relu =
                processusRepository.findById(dossier.idProcessus()).orElseThrow();
        assertThat(relu.getStatut()).isEqualTo(StatutEnum.RETOURNE);
    }

    // =====================================================================
    // 12 et 13 : RG-10, le motif obligatoire
    // =====================================================================

    @Test
    @DisplayName("12. Retour sans motif : refuse, et le dossier ne bouge pas")
    void retourSansMotif() {
        Dossier dossier = unDossierSoumis();

        assertThatThrownBy(() -> retourService.retourner(dossier.idProcessus(), null, JETON, IP))
                .isInstanceOf(MotifRetourRequisException.class)
                .hasMessageContaining("RG-10");

        assertThat(processusRepository.findById(dossier.idProcessus()).orElseThrow().getStatut())
                .isEqualTo(StatutEnum.EN_ATTENTE_DA);

        // Le refus tombe avant tout appel reseau : c'est une faute de la requete, pas
        // du dossier.
        verify(habilitationClient, never()).verifier(anyString(), anyString());
    }

    @Test
    @DisplayName("13. Retour avec un motif d'espaces seulement : refuse aussi")
    void retourAvecMotifDEspaces() {
        Dossier dossier = unDossierSoumis();

        // Un champ present et un motif absent. isEmpty() aurait laisse passer.
        assertThatThrownBy(
                () -> retourService.retourner(dossier.idProcessus(), "     ", JETON, IP))
                .isInstanceOf(MotifRetourRequisException.class)
                .hasMessageContaining("suite d'espaces n'est pas un motif");

        assertThat(etapeRepository.findByIdProcessusOrderByOrdreEtape(dossier.idProcessus()))
                .as("aucune etape RETOURNEE n'a ete creee")
                .extracting(EtapeWorkflow::getStatutEtape)
                .containsExactly(StatutEtapeEnum.VALIDEE);
    }

    // =====================================================================
    // 14 : le motif est visible par l'agent (US-11)
    // =====================================================================

    @Test
    @DisplayName("14. Le motif est visible dans le detail du processus")
    void motifVisibleDansLeDetail() {
        Dossier dossier = unDossierSoumis();
        retourService.retourner(dossier.idProcessus(), MOTIF, JETON, IP);
        entityManager.flush();
        entityManager.clear();

        ProcessusService.DetailProcessus detail =
                processusService.consulter(dossier.idProcessus(), JETON);

        assertThat(detail.processus().getStatut()).isEqualTo(StatutEnum.RETOURNE);
        assertThat(detail.motifRetour())
                .as("sans le motif, l'agent recoit un refus sans savoir quoi corriger")
                .isEqualTo(MOTIF);
    }

    @Test
    @DisplayName("14b. Le motif disparait du detail une fois l'etat resoumis")
    void motifAbsentHorsEtatRetourne() {
        Dossier dossier = unDossierSoumis();

        ProcessusService.DetailProcessus detail =
                processusService.consulter(dossier.idProcessus(), JETON);

        assertThat(detail.processus().getStatut()).isEqualTo(StatutEnum.EN_ATTENTE_DA);
        assertThat(detail.motifRetour())
                .as("une correction deja faite, affichee sur un dossier reparti dans le "
                        + "circuit, se lirait comme un reproche en cours")
                .isNull();
    }

    // =====================================================================
    // Refus et effets de bord
    // =====================================================================

    @Test
    @DisplayName("15. Le retour ne touche pas au document : fichier et compteur intacts")
    void leRetourNeToucheNiAuFichierNiAuCompteur() throws Exception {
        Dossier dossier = unDossierSoumis();
        Path fichier = racineStockage.resolve(dossier.cheminRelatif());
        long tailleAvant = Files.size(fichier);

        retourService.retourner(dossier.idProcessus(), MOTIF, JETON, IP);

        assertThat(Files.size(fichier)).isEqualTo(tailleAvant);
        assertThat(pieceJointeRepository.findByIdProcessus(dossier.idProcessus())
                .orElseThrow().getNombreSignatures())
                .as("aucun visa n'a ete appose : le compteur reste a un")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("16. Retour d'un etat en cours de saisie : refuse")
    void retourDUnEtatEnSaisie() {
        ProcessusMensuel processus = processusRepository.save(
                TransitionProcessus.declencher(prochainMois(), ANNEE, UNITE));

        assertThatThrownBy(() -> retourService.retourner(processus.getId(), MOTIF, JETON, IP))
                .isInstanceOf(TransitionProcessusInterditeException.class)
                .hasMessageContaining("EN_COURS_SAISIE")
                .hasMessageContaining("rien a lui retourner");
    }

    @Test
    @DisplayName("17. Retour d'un etat cloture : refuse, l'etat clos est definitif")
    void retourDUnEtatCloture() {
        Dossier dossier = unDossierSoumis();
        validationService.valider(dossier.idProcessus(), JETON, IP);
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> retourService.retourner(dossier.idProcessus(), MOTIF, JETON, IP))
                .isInstanceOf(TransitionProcessusInterditeException.class)
                .hasMessageContaining("CLOTURE")
                .hasMessageContaining("complementaire");
    }

    @Test
    @DisplayName("18. Retour par un role qui ne tient pas le niveau attendu : refuse")
    void retourParUnRoleNonAttendu() {
        Dossier dossier = unDossierSoumis();
        profilDuDirecteur();
        directeurHabilite();

        assertThatThrownBy(() -> retourService.retourner(dossier.idProcessus(), MOTIF, JETON, IP))
                .isInstanceOf(RoleNonAttenduException.class)
                .hasMessageContaining("chef d'unite");
    }

    @Test
    @DisplayName("19. Hors portee d'acces : refus, le dossier ne bouge pas")
    void horsPorteeDAcces() {
        Dossier dossier = unDossierSoumis();
        when(habilitationClient.verifier(anyString(), anyString()))
                .thenReturn(new AgentNonHabilite("aucune portee sur cette unite"));

        assertThatThrownBy(() -> retourService.retourner(dossier.idProcessus(), MOTIF, JETON, IP))
                .isInstanceOf(AgentNonHabiliteException.class);

        assertThat(processusRepository.findById(dossier.idProcessus()).orElseThrow().getStatut())
                .isEqualTo(StatutEnum.EN_ATTENTE_DA);
    }

    @Test
    @DisplayName("20. Processus inconnu : refus sans interroger le service Identite")
    void processusInconnu() {
        assertThatThrownBy(() -> retourService.retourner(999_999_999L, MOTIF, JETON, IP))
                .isInstanceOf(ProcessusIntrouvableException.class);

        verify(habilitationClient, never()).verifier(anyString(), anyString());
    }

    @Test
    @DisplayName("21. L'audit trace le retour ET son motif")
    void auditDuRetour() {
        Dossier dossier = unDossierSoumis();

        retourService.retourner(dossier.idProcessus(), MOTIF, JETON, IP);

        ArgumentCaptor<EvenementAudit> capture = ArgumentCaptor.forClass(EvenementAudit.class);
        verify(publicateurAudit).publier(capture.capture());
        EvenementAudit evenement = capture.getValue();

        assertThat(evenement.action()).isEqualTo("RETOUR_PROCESSUS");
        assertThat(evenement.idUtilisateur()).isEqualTo(ID_CHEF);
        assertThat(evenement.detailJson())
                .contains("EN_ATTENTE_DA")
                .contains("RETOURNE")
                .contains(LOGIN_CHEF)
                .as("le motif vit aussi dans une base qu'aucun service metier ne peut reecrire")
                .contains(MOTIF);
    }

    // =====================================================================
    // Outils
    // =====================================================================

    private record Dossier(Long idProcessus, String cheminRelatif) {
    }

    /**
     * Reconstitue l'etat que la soumission laisse derriere elle : processus
     * {@code EN_ATTENTE_DA}, document reellement ecrit portant le visa de l'agent,
     * etape {@code SOUMISSION_AGENT} enregistree.
     */
    private Dossier unDossierSoumis() {
        int mois = prochainMois();

        ProcessusMensuel processus = TransitionProcessus.declencher(mois, ANNEE, UNITE);
        processus.reporterMontantTotal(8_000);
        TransitionProcessus.soumettre(processus);
        TransitionProcessus.transfererAuChefUnite(processus);
        ProcessusMensuel enregistre = processusRepository.save(processus);

        ActeurSignataire agent = new ActeurSignataire(ID_AGENT, LOGIN_AGENT, RoleEnum.AGENT_UNITE);
        ResultatSignature signature = signatureService.creerEtSigner(
                enregistre, uneConsolidation(enregistre, 8_000L), agent,
                LocalDateTime.of(ANNEE, mois, 28, 9, 30));

        EtapeWorkflow soumission = new EtapeWorkflow(
                enregistre.getId(), ID_AGENT, 1, NomEtapeEnum.SOUMISSION_AGENT);
        soumission.validerAvecSignature(signature.empreinte());
        etapeRepository.save(soumission);

        pieceJointeRepository.save(
                new PieceJointe(enregistre.getId(), signature.document().cheminRelatif()));

        entityManager.flush();

        return new Dossier(enregistre.getId(), signature.document().cheminRelatif());
    }

    /**
     * Un dossier au-dela du seuil, reellement vise par le chef d'unite puis monte au
     * directeur reseau. Construit par le vrai service de validation : le parcours est
     * celui que la production produira.
     */
    private Dossier unDossierChezLeDirecteurReseau() {
        int mois = prochainMois();

        long montant = montantAuDessusDuSeuil();

        ProcessusMensuel processus = TransitionProcessus.declencher(mois, ANNEE, UNITE);
        processus.reporterMontantTotal(Math.toIntExact(montant));
        TransitionProcessus.soumettre(processus);
        TransitionProcessus.transfererAuChefUnite(processus);
        ProcessusMensuel enregistre = processusRepository.save(processus);

        ActeurSignataire agent = new ActeurSignataire(ID_AGENT, LOGIN_AGENT, RoleEnum.AGENT_UNITE);
        ResultatSignature signature = signatureService.creerEtSigner(
                enregistre, uneConsolidation(enregistre, montant), agent,
                LocalDateTime.of(ANNEE, mois, 28, 9, 30));

        EtapeWorkflow soumission = new EtapeWorkflow(
                enregistre.getId(), ID_AGENT, 1, NomEtapeEnum.SOUMISSION_AGENT);
        soumission.validerAvecSignature(signature.empreinte());
        etapeRepository.save(soumission);

        pieceJointeRepository.save(
                new PieceJointe(enregistre.getId(), signature.document().cheminRelatif()));
        entityManager.flush();

        validationService.valider(enregistre.getId(), JETON, IP);
        entityManager.flush();
        entityManager.clear();

        org.mockito.Mockito.clearInvocations(publicateurAudit);

        return new Dossier(enregistre.getId(), signature.document().cheminRelatif());
    }

    /**
     * Un montant au-dela du seuil, <b>lu en base</b> : aucune valeur de seuil n'est
     * ecrite dans ce fichier (doctrine Sprint 4.3).
     */
    private long montantAuDessusDuSeuil() {
        return new SeuilService(parametreSystemeRepository).seuilAiguillage() + 1;
    }

    private EtatConsolide uneConsolidation(ProcessusMensuel processus, long montantTotal) {
        EtatConsolide.Beneficiaire kamdem = new EtatConsolide.Beneficiaire(
                77L, "KAMDEM", "Serge", "03703001234567", UNITE);

        EtatConsolide.Ligne ligne = new EtatConsolide.Ligne(
                201L, 21L, 77L, kamdem, "TRANSPORT", "SOIR",
                Math.toIntExact(montantTotal), 12L,
                LocalDateTime.of(ANNEE, processus.getMoisPaiement(), 12, 8, 0));

        EtatConsolide.Journee journee = new EtatConsolide.Journee(
                21L, LocalDate.of(ANNEE, processus.getMoisPaiement(), 12), "ENREGISTREE",
                1, montantTotal, List.of(ligne));

        return new EtatConsolide(processus.getId(), UNITE, processus.getMoisPaiement(),
                ANNEE, 1, 1, 1, montantTotal, List.of(journee));
    }

    private void chefHabilite() {
        when(habilitationClient.verifier(anyString(), anyString()))
                .thenReturn(new AgentHabilite(LOGIN_CHEF, RoleEnum.CHEF_UNITE_DA.name(), UNITE));
    }

    private void profilDuChef() {
        when(profilClient.obtenir(anyString())).thenReturn(new ResultatProfil.ProfilObtenu(
                new ActeurSignataire(ID_CHEF, LOGIN_CHEF, RoleEnum.CHEF_UNITE_DA)));
    }

    private void directeurHabilite() {
        when(habilitationClient.verifier(anyString(), anyString())).thenReturn(
                new AgentHabilite(LOGIN_DIRECTEUR, RoleEnum.DIRECTEUR_RESEAU_DR.name(), UNITE));
    }

    private void profilDuDirecteur() {
        when(profilClient.obtenir(anyString())).thenReturn(new ResultatProfil.ProfilObtenu(
                new ActeurSignataire(ID_DIRECTEUR, LOGIN_DIRECTEUR,
                        RoleEnum.DIRECTEUR_RESEAU_DR)));
    }

    private static int prochainMois() {
        prochainMois = prochainMois % 12 + 1;
        return prochainMois;
    }

}
