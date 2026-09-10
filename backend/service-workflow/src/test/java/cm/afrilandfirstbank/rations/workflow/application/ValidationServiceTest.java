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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;

import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.workflow.application.ResultatHabilitationUnite.AgentHabilite;
import cm.afrilandfirstbank.rations.workflow.application.ResultatHabilitationUnite.AgentNonHabilite;
import cm.afrilandfirstbank.rations.workflow.domaine.EtapeWorkflow;
import cm.afrilandfirstbank.rations.workflow.domaine.NomEtapeEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.PieceJointe;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.RoleEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutIntegrationEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEtapeEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.TransitionProcessus;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.AgentNonHabiliteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.DocumentNonProduitException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.RoleNonAttenduException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.SeparationTachesException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ServiceIdentiteIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.SeuilIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.TransitionProcessusInterditeException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.EtapeWorkflowRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ParametreSystemeRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.PieceJointeRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ProcessusMensuelRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.stockage.StockageDocumentsFichier;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * Validation du Chef d'Unite et aiguillage (Sprint 4.3, US-08, US-09, CT-14,
 * CT-15).
 *
 * <h2>Meme parti qu'au Sprint 4.2 : la vraie base, le vrai stockage</h2>
 *
 * <p>Seuls les appels reseau sont simules — habilitation et profil. Les
 * repositories tournent contre {@code rations_workflow}, {@link SeuilService} lit
 * le vrai {@code parametre_systeme}, et {@link SignatureService} estampe un vrai
 * PDF sur un repertoire temporaire.
 *
 * <p>Ce n'est pas du zele. Trois garanties de ce sous-sprint ne vivent que la : que
 * le seuil vient bien de la base, que le compteur de signatures passe a deux sur un
 * document <b>reellement</b> enrichi, et que les signatures precedentes survivent a
 * l'estampage — un stockage simule prouverait qu'on l'a appele, pas que le document
 * porte deux visas.
 *
 * <h2>Aucune valeur de seuil dans ce fichier</h2>
 *
 * <p>Les montants d'essai sont derives du seuil lu en base : {@code seuil} pour la
 * cloture directe, {@code seuil + 1} pour la montee au directeur reseau. Le meme
 * fichier est balaye par le test {@code aucuneValeurDeSeuilEnDur} de
 * {@code AiguillageServiceTest}.
 *
 * <p>Jeux d'essai en <b>annee 2099</b>, comme aux Sprints 4.1 et 4.2 : aucune
 * donnee reelle ne peut entrer en collision sur l'index partiel
 * {@code ux_processus_normal_par_periode}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("ValidationService — validation du chef d'unite et aiguillage")
class ValidationServiceTest {

    private static final String UNITE = "00002";
    private static final int ANNEE = 2099;
    private static final String JETON = "Bearer jeton-de-test";
    private static final String IP = "10.0.0.12";

    private static final String LOGIN_AGENT = "jean_mbarga";
    private static final Long ID_AGENT = 7L;
    private static final String LOGIN_CHEF = "alice_ngo";
    private static final Long ID_CHEF = 9L;
    private static final String LOGIN_DIRECTEUR = "estelle_fotso";
    private static final Long ID_DIRECTEUR = 21L;

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
    private SeuilService seuilService;
    private AppuiTransmission.ClientDeTest transmissionClient;
    private ValidationService validationService;

    /** Compteur de mois, pour que chaque test ouvre sa propre periode. */
    private static int prochainMois = 1;

    @BeforeEach
    void preparer() {
        // @DataJpaTest ne charge pas les beans @Service : ils sont instancies a la
        // main, avec les vrais repositories derriere. SeuilService, AiguillageService,
        // DocumentService et SignatureService sont REELS — leur comportement fait
        // partie de ce qu'on eprouve ici, pas de ce qu'on suppose.
        habilitationClient = mock(HabilitationClient.class);
        profilClient = mock(ProfilClient.class);
        publicateurAudit = mock(PublicateurAudit.class);
        stockage = new StockageDocumentsFichier(racineStockage.toString());
        signatureService = new SignatureService(new DocumentService(), stockage);
        seuilService = new SeuilService(parametreSystemeRepository);
        transmissionClient = new AppuiTransmission.ClientDeTest();

        validationService = new ValidationService(
                processusRepository,
                pieceJointeRepository,
                new HabilitationService(habilitationClient),
                profilClient,
                new SeparationTachesService(etapeRepository),
                new AiguillageService(seuilService),
                signatureService,
                new EnregistrementValidation(processusRepository, etapeRepository,
                        pieceJointeRepository, publicateurAudit),
                AppuiTransmission.declenchement(
                        transmissionClient, processusRepository, publicateurAudit));

        chefHabilite();
        profilDuChef();
    }

    // =====================================================================
    // Les deux branches nominales de RG-08
    // =====================================================================

    @Nested
    @DisplayName("Validation nominale")
    class Nominale {

        /**
         * CT-14. Montant a la valeur exacte du seuil : l'etat est clos par le seul chef
         * d'unite.
         *
         * <p>Le montant choisi n'est pas anodin : c'est la borne. Un test nominal sur un
         * montant confortablement inferieur passerait meme avec une comparaison
         * inversee d'une unite.
         */
        @Test
        @DisplayName("1. Sous le seuil : CLOTURE, deux signatures, etape VALIDATION_DA")
        void validationSousLeSeuil() {
            long seuil = seuilService.seuilAiguillage();
            Dossier dossier = unDossierSoumis(seuil);

            ResultatValidation resultat =
                    validationService.valider(dossier.idProcessus(), JETON, IP);

            assertThat(resultat.processus().getStatut()).isEqualTo(StatutEnum.CLOTURE);
            assertThat(resultat.aiguillage().decision())
                    .isEqualTo(DecisionAiguillage.SOUS_SEUIL_CLOTURE_DIRECTE);
            assertThat(resultat.aiguillage().seuilApplique()).isEqualTo(seuil);

            assertThat(resultat.pieceJointe().getNombreSignatures())
                    .as("Le document est enrichi, pas regenere : le compteur passe de 1 a 2")
                    .isEqualTo(2);
            assertThat(resultat.pieceJointe().getDateDerniereModification()).isNotNull();

            EtapeWorkflow etape = resultat.etape();
            assertThat(etape.getNomEtape()).isEqualTo(NomEtapeEnum.VALIDATION_DA);
            assertThat(etape.getStatutEtape()).isEqualTo(StatutEtapeEnum.VALIDEE);
            assertThat(etape.getIdActeur()).isEqualTo(ID_CHEF);
            assertThat(etape.getOrdreEtape())
                    .as("Deuxieme pas du circuit, apres la soumission de l'agent")
                    .isEqualTo(2);
            assertThat(etape.getSignatureNumerique()).startsWith("SHA-256:");
            assertThat(etape.getDateCreation())
                    .as("L'horodatage de l'etape tient lieu de date de cloture : "
                            + "processus_mensuel ne porte pas de colonne date_cloture "
                            + "(decision Sprint 4.1)")
                    .isNotNull();

            // La base dit la meme chose que la reponse.
            ProcessusMensuel relu = processusRepository.findById(dossier.idProcessus()).orElseThrow();
            assertThat(relu.getStatut()).isEqualTo(StatutEnum.CLOTURE);
            assertThat(etapeRepository.findByIdProcessusOrderByOrdreEtape(dossier.idProcessus()))
                    .extracting(EtapeWorkflow::getNomEtape)
                    .containsExactly(NomEtapeEnum.SOUMISSION_AGENT, NomEtapeEnum.VALIDATION_DA);
        }

        /** CT-15. Un franc au-dessus du seuil : l'etat monte au directeur reseau. */
        @Test
        @DisplayName("2. Au-dessus du seuil : EN_ATTENTE_DR, deux signatures")
        void validationAuDessusDuSeuil() {
            long seuil = seuilService.seuilAiguillage();
            Dossier dossier = unDossierSoumis(seuil + 1);

            ResultatValidation resultat =
                    validationService.valider(dossier.idProcessus(), JETON, IP);

            assertThat(resultat.processus().getStatut()).isEqualTo(StatutEnum.EN_ATTENTE_DR);
            assertThat(resultat.aiguillage().decision())
                    .isEqualTo(DecisionAiguillage.ENVOI_DIRECTEUR_RESEAU);
            assertThat(resultat.pieceJointe().getNombreSignatures()).isEqualTo(2);
            assertThat(resultat.etape().getNomEtape()).isEqualTo(NomEtapeEnum.VALIDATION_DA);
        }

        /**
         * <b>Premier point de cloture</b> : sous le seuil, le visa du chef d'unite cloture, et
         * la cloture met l'etat a la disposition de la comptabilite (Sprint 5.1, US-12).
         *
         * <p>Le drapeau {@code transmis_comptabilite} n'est pose qu'<b>apres accuse du
         * broker</b>. Le poser des la cloture contournerait le verrou de RG-13 : l'etat
         * paraitrait transmis avant de l'etre, et la transmission reelle serait ensuite
         * refusee comme un doublon — l'etat resterait definitivement impaye.
         */
        @Test
        @DisplayName("3. La cloture sous le seuil declenche la transmission et pose le drapeau")
        void clotureSousLeSeuilTransmet() {
            Dossier dossier = unDossierSoumis(seuilService.seuilAiguillage());

            ResultatValidation resultat = validationService.valider(dossier.idProcessus(), JETON, IP);

            assertThat(transmissionClient.processusAppeles()).containsExactly(dossier.idProcessus());
            assertThat(resultat.transmission()).isNotNull();
            assertThat(resultat.transmission().transmis()).isTrue();

            ProcessusMensuel relu = processusRepository.findById(dossier.idProcessus()).orElseThrow();
            assertThat(relu.getStatut()).isEqualTo(StatutEnum.CLOTURE);
            assertThat(relu.isTransmisComptabilite()).isTrue();
            assertThat(relu.getStatutIntegration())
                    .as("EN_ATTENTE signifie « publie, la comptabilite n'a pas encore repondu »")
                    .isEqualTo(StatutIntegrationEnum.EN_ATTENTE);
        }

        /**
         * <b>Le scenario le plus dangereux du sous-sprint</b>, et ce qui doit en rester vrai.
         *
         * <p>Un etat cloture est fige : plus personne ne peut le corriger. S'il n'est pas
         * transmis, ses beneficiaires ne sont pas payes. La cloture <b>reste acquise</b> — le
         * document porte deja le visa, ecrit sur disque hors transaction —, mais le drapeau
         * reste a faux, ce qui rend l'etat retrouvable par requete, et l'echec remonte au
         * valideur : aucune reprise automatique n'etant possible faute de compte de service
         * au realm, c'est le seul moment ou un humain l'apprend.
         */
        @Test
        @DisplayName("3b. Transmission en echec : la cloture tient, le drapeau reste faux")
        void transmissionEnEchecNAnnulePasLaCloture() {
            transmissionClient.repondraToujours(new ResultatDemandeTransmission.EchecApresTentative(
                    "PUBLICATION_ECHOUEE : aucun accuse du broker"));
            Dossier dossier = unDossierSoumis(seuilService.seuilAiguillage());

            ResultatValidation resultat = validationService.valider(dossier.idProcessus(), JETON, IP);

            assertThat(resultat.transmission().transmis()).isFalse();
            assertThat(resultat.transmission().motif()).contains("aucun accuse du broker");

            ProcessusMensuel relu = processusRepository.findById(dossier.idProcessus()).orElseThrow();
            assertThat(relu.getStatut())
                    .as("la cloture est la decision metier, la transmission n'en est que la suite")
                    .isEqualTo(StatutEnum.CLOTURE);
            assertThat(relu.isTransmisComptabilite())
                    .as("poser le drapeau ici rendrait l'etat definitivement impaye (RG-13)")
                    .isFalse();
            assertThat(relu.getStatutIntegration()).isNull();
        }

        /**
         * Un etat aiguille vers le directeur reseau n'est <b>pas</b> cloture : il n'a rien a
         * transmettre, et le service Transmission n'est meme pas appele.
         */
        @Test
        @DisplayName("3c. Une montee au directeur reseau ne transmet rien")
        void monteeAuDirecteurNeTransmetPas() {
            Dossier dossier = unDossierSoumis(seuilService.seuilAiguillage() + 1);

            ResultatValidation resultat = validationService.valider(dossier.idProcessus(), JETON, IP);

            assertThat(resultat.processus().getStatut()).isEqualTo(StatutEnum.EN_ATTENTE_DR);
            assertThat(resultat.transmission()).isNull();
            assertThat(transmissionClient.nombreAppels()).isZero();
        }

        /**
         * Le document enrichi contient les <b>deux</b> mentions, et le fichier a grossi.
         *
         * <p>C'est la seule facon de distinguer un enrichissement d'une regeneration :
         * un document recompose porterait la seconde signature, mais aurait perdu la
         * premiere sans que le compteur ne s'en apercoive.
         */
        @Test
        @DisplayName("4. Le document porte les deux visas : la premiere signature survit")
        void documentEnrichiEtNonRegenere() throws Exception {
            Dossier dossier = unDossierSoumis(seuilService.seuilAiguillage());
            Path fichier = racineStockage.resolve(dossier.cheminRelatif());
            long tailleAvant = Files.size(fichier);
            String texteAvant = texteDuPdf(fichier);

            validationService.valider(dossier.idProcessus(), JETON, IP);

            String texteApres = texteDuPdf(fichier);
            assertThat(texteAvant).contains(LOGIN_AGENT);
            assertThat(texteApres)
                    .as("Les deux visas coexistent : le document est estampe, pas recompose")
                    .contains(LOGIN_AGENT)
                    .contains(LOGIN_CHEF);
            assertThat(Files.size(fichier)).isGreaterThan(tailleAvant);
        }

        /**
         * L'audit porte le montant <b>et</b> le seuil applique : un controle interne
         * doit pouvoir refaire la comparaison lui-meme, meme si le parametre a change
         * depuis.
         */
        @Test
        @DisplayName("5. L'audit trace la decision, le montant et le seuil applique")
        void auditDeLaDecision() {
            long seuil = seuilService.seuilAiguillage();
            Dossier dossier = unDossierSoumis(seuil);

            validationService.valider(dossier.idProcessus(), JETON, IP);

            EvenementAudit evenement = evenementDAudit("VALIDATION_PROCESSUS");

            assertThat(evenement.idUtilisateur()).isEqualTo(ID_CHEF);
            assertThat(evenement.detailJson())
                    .contains("EN_ATTENTE_DA")
                    .contains("CLOTURE")
                    .contains("SOUS_SEUIL_CLOTURE_DIRECTE")
                    .contains(String.valueOf(seuil))
                    .contains(LOGIN_CHEF);

            // Depuis le Sprint 5.1, une cloture produit une SECONDE trace : la mise a
            // disposition comptable. Deux evenements distincts a dessein -- « qui a valide,
            // sur quelle comparaison » et « qu'a-t-on envoye, ou » sont deux questions de
            // controle interne differentes, et les fondre en une seule trace obligerait a
            // les demeler ensuite.
            assertThat(evenementDAudit("TRANSMISSION_COMPTABLE").detailJson())
                    .contains("rations.etat.valide")
                    .contains("offset");
        }
    }

    // =====================================================================
    // Refus : statut, portee, document, seuil
    // =====================================================================

    @Nested
    @DisplayName("Refus")
    class Refus {

        @Test
        @DisplayName("6. Processus inconnu : refus, sans interroger le service Identite")
        void processusInconnu() {
            assertThatThrownBy(() -> validationService.valider(999_999_999L, JETON, IP))
                    .isInstanceOf(ProcessusIntrouvableException.class);

            verify(habilitationClient, never()).verifier(anyString(), anyString());
        }

        @Test
        @DisplayName("7. Etat en cours de saisie : validation refusee")
        void etatEnCoursDeSaisie() {
            ProcessusMensuel processus = processusRepository.save(
                    declencherSur(prochainMois(), ANNEE, UNITE));

            assertThatThrownBy(() -> validationService.valider(processus.getId(), JETON, IP))
                    .isInstanceOf(TransitionProcessusInterditeException.class)
                    .hasMessageContaining("EN_COURS_SAISIE")
                    .hasMessageContaining("pas encore soumis");
        }

        /**
         * Un etat deja cloture ne se revalide pas.
         *
         * <p>Et le refus doit tomber <b>avant</b> toute ecriture : le compteur de
         * signatures reste a deux, faute de quoi une seconde validation graverait un
         * troisieme visa sur un document definitif.
         */
        @Test
        @DisplayName("8. Etat deja cloture : validation refusee, document intact")
        void etatDejaCloture() throws Exception {
            Dossier dossier = unDossierSoumis(seuilService.seuilAiguillage());
            validationService.valider(dossier.idProcessus(), JETON, IP);

            Path fichier = racineStockage.resolve(dossier.cheminRelatif());
            long tailleApresPremiereValidation = Files.size(fichier);

            assertThatThrownBy(() -> validationService.valider(dossier.idProcessus(), JETON, IP))
                    .isInstanceOf(TransitionProcessusInterditeException.class)
                    .hasMessageContaining("CLOTURE")
                    .hasMessageContaining("definitif");

            assertThat(pieceJointeRepository.findByIdProcessus(dossier.idProcessus())
                    .orElseThrow().getNombreSignatures()).isEqualTo(2);
            assertThat(Files.size(fichier)).isEqualTo(tailleApresPremiereValidation);
        }

        @Test
        @DisplayName("9. Hors portee d'acces : refus (403), document intact")
        void horsPorteeDAcces() throws Exception {
            Dossier dossier = unDossierSoumis(seuilService.seuilAiguillage());
            Path fichier = racineStockage.resolve(dossier.cheminRelatif());
            long tailleAvant = Files.size(fichier);

            when(habilitationClient.verifier(anyString(), anyString()))
                    .thenReturn(new AgentNonHabilite("aucune portee sur cette unite"));

            assertThatThrownBy(() -> validationService.valider(dossier.idProcessus(), JETON, IP))
                    .isInstanceOf(AgentNonHabiliteException.class);

            assertThat(Files.size(fichier)).isEqualTo(tailleAvant);
            assertThat(processusRepository.findById(dossier.idProcessus()).orElseThrow()
                    .getStatut()).isEqualTo(StatutEnum.EN_ATTENTE_DA);
        }

        @Test
        @DisplayName("10. Service Identite muet : refus conservateur, rien n'est ecrit")
        void serviceIdentiteMuet() {
            Dossier dossier = unDossierSoumis(seuilService.seuilAiguillage());

            when(profilClient.obtenir(anyString()))
                    .thenReturn(new ResultatProfil.ServiceIdentiteIndisponible("delai depasse"));

            assertThatThrownBy(() -> validationService.valider(dossier.idProcessus(), JETON, IP))
                    .isInstanceOf(ServiceIdentiteIndisponibleException.class);

            assertThat(pieceJointeRepository.findByIdProcessus(dossier.idProcessus())
                    .orElseThrow().getNombreSignatures()).isEqualTo(1);
        }

        /**
         * <b>Le test qui justifie l'ordre des operations.</b>
         *
         * <p>Le seuil est lu avant l'estampage : un parametre illisible arrete la
         * validation <b>avant</b> qu'un visa ne soit grave dans le PDF. Dans l'ordre
         * inverse, le document archive porterait la signature d'une validation que la
         * base ne connaitrait jamais.
         */
        @Test
        @DisplayName("11. Seuil illisible : refus AVANT toute ecriture, document inchange")
        void seuilIllisible() throws Exception {
            Dossier dossier = unDossierSoumis(seuilService.seuilAiguillage());
            Path fichier = racineStockage.resolve(dossier.cheminRelatif());
            long tailleAvant = Files.size(fichier);

            rendreLeSeuilIllisible();

            assertThatThrownBy(() -> validationService.valider(dossier.idProcessus(), JETON, IP))
                    .isInstanceOf(SeuilIndisponibleException.class);

            assertThat(Files.size(fichier))
                    .as("Aucun visa n'a ete grave : le refus precede l'estampage")
                    .isEqualTo(tailleAvant);
            assertThat(pieceJointeRepository.findByIdProcessus(dossier.idProcessus())
                    .orElseThrow().getNombreSignatures()).isEqualTo(1);
            assertThat(processusRepository.findById(dossier.idProcessus()).orElseThrow()
                    .getStatut()).isEqualTo(StatutEnum.EN_ATTENTE_DA);
        }

        /**
         * Un etat en attente sans document est une incoherence, pas un cas metier : la
         * soumission ne peut pas avoir abouti sans produire de piece jointe.
         */
        @Test
        @DisplayName("12. Aucun document pour un etat en attente : refus signale")
        void documentAbsent() {
            ProcessusMensuel processus = declencherSur(prochainMois(), ANNEE, UNITE);
            processus.reporterMontantTotal(1_000);
            TransitionProcessus.soumettre(processus);
            TransitionProcessus.transfererAuChefUnite(processus);
            ProcessusMensuel enregistre = processusRepository.save(processus);

            assertThatThrownBy(() -> validationService.valider(enregistre.getId(), JETON, IP))
                    .isInstanceOf(DocumentNonProduitException.class)
                    .hasMessageContaining("Aucun document");
        }
    }

    // =====================================================================
    // CT-18 de bout en bout
    // =====================================================================

    /**
     * CT-18 sur le circuit complet, et non sur le seul service d'aiguillage.
     *
     * <p>Deux dossiers portant <b>le meme montant</b> sont valides de part et d'autre
     * d'une modification du parametre. Le premier se clot, le second monte au
     * directeur reseau : c'est le seuil, et lui seul, qui a change d'avis.
     */
    @Test
    @DisplayName("13. Seuil modifie entre deux validations : l'aiguillage suit (CT-18)")
    void seuilModifieEntreDeuxValidations() {
        long seuilInitial = seuilService.seuilAiguillage();
        long montant = seuilInitial - 1;

        Dossier premier = unDossierSoumis(montant);
        ResultatValidation avant = validationService.valider(premier.idProcessus(), JETON, IP);

        assertThat(avant.processus().getStatut()).isEqualTo(StatutEnum.CLOTURE);
        assertThat(avant.aiguillage().seuilApplique()).isEqualTo(seuilInitial);

        long nouveauSeuil = montant - 1;
        fixerValeurDuSeuil(String.valueOf(nouveauSeuil));

        Dossier second = unDossierSoumis(montant);
        ResultatValidation apres = validationService.valider(second.idProcessus(), JETON, IP);

        assertThat(apres.processus().getStatut())
                .as("Meme montant, seuil abaisse : l'etat monte desormais au directeur reseau")
                .isEqualTo(StatutEnum.EN_ATTENTE_DR);
        assertThat(apres.aiguillage().seuilApplique()).isEqualTo(nouveauSeuil);
    }

    // =====================================================================
    // Sous-sprint 4.4 : validation de second niveau (US-10, CT-19)
    // =====================================================================

    @Nested
    @DisplayName("Validation du directeur reseau")
    class SecondNiveau {

        /**
         * Test 6 du guide 4.4. Le dossier a depasse le seuil, le chef d'unite l'a vise,
         * le directeur reseau le clot.
         *
         * <p>Trois choses s'y verifient ensemble : la cloture, la <b>troisieme</b>
         * signature sur un document reellement estampe, et l'horodatage de l'etape —
         * qui tient lieu de date de cloture, {@code processus_mensuel} ne portant pas de
         * colonne {@code date_cloture} (decision Sprint 4.1, redite au 4.3).
         */
        @Test
        @DisplayName("6. Validation DR nominale : CLOTURE, trois signatures, etape horodatee")
        void validationDirecteurReseauNominale() {
            Dossier dossier = unDossierChezLeDirecteurReseau();
            profilDuDirecteur();
            directeurHabilite();

            ResultatValidation resultat =
                    validationService.valider(dossier.idProcessus(), JETON, IP);

            assertThat(resultat.processus().getStatut()).isEqualTo(StatutEnum.CLOTURE);
            assertThat(resultat.pieceJointe().getNombreSignatures())
                    .as("agent, chef d'unite, directeur reseau : le document en porte trois")
                    .isEqualTo(3);

            EtapeWorkflow etape = resultat.etape();
            assertThat(etape.getNomEtape()).isEqualTo(NomEtapeEnum.VALIDATION_DR);
            assertThat(etape.getStatutEtape()).isEqualTo(StatutEtapeEnum.VALIDEE);
            assertThat(etape.getIdActeur()).isEqualTo(ID_DIRECTEUR);
            assertThat(etape.getOrdreEtape()).isEqualTo(3);
            assertThat(etape.getSignatureNumerique()).startsWith("SHA-256:");
            assertThat(etape.getDateCreation())
                    .as("l'horodatage de l'etape tient lieu de date de cloture")
                    .isNotNull();

            assertThat(etapeRepository.findByIdProcessusOrderByOrdreEtape(dossier.idProcessus()))
                    .extracting(EtapeWorkflow::getNomEtape)
                    .containsExactly(NomEtapeEnum.SOUMISSION_AGENT, NomEtapeEnum.VALIDATION_DA,
                            NomEtapeEnum.VALIDATION_DR);
        }

        /**
         * <b>Pas d'aiguillage au second niveau.</b> Apres le visa du directeur reseau il
         * n'y a plus d'echelon : le seuil n'est meme pas lu.
         *
         * <p>La preuve est faite en le rendant illisible : si le service d'aiguillage
         * etait rappele « par symetrie », la validation echouerait en
         * {@code SEUIL_INDISPONIBLE}. Elle aboutit, donc le seuil n'a pas ete consulte.
         */
        @Test
        @DisplayName("6b. Aucun aiguillage au second niveau : le seuil n'est meme pas lu")
        void aucunAiguillageAuSecondNiveau() {
            Dossier dossier = unDossierChezLeDirecteurReseau();
            profilDuDirecteur();
            directeurHabilite();

            rendreLeSeuilIllisible();

            ResultatValidation resultat =
                    validationService.valider(dossier.idProcessus(), JETON, IP);

            assertThat(resultat.processus().getStatut()).isEqualTo(StatutEnum.CLOTURE);
            assertThat(resultat.aiguillage())
                    .as("aucune decision d'aiguillage : il n'y avait rien a arbitrer")
                    .isNull();
        }

        /** Test 7 du guide : le directeur reseau devant un dossier encore chez le DA. */
        @Test
        @DisplayName("7. Validation DR d'un etat EN_ATTENTE_DA : refusee, sur le role attendu")
        void directeurReseauSurUnEtatChezLeChefUnite() {
            Dossier dossier = unDossierSoumis(seuilService.seuilAiguillage());
            profilDuDirecteur();
            directeurHabilite();

            assertThatThrownBy(() -> validationService.valider(dossier.idProcessus(), JETON, IP))
                    .isInstanceOf(RoleNonAttenduException.class)
                    .hasMessageContaining("chef d'unite")
                    .hasMessageContaining("DIRECTEUR_RESEAU_DR");

            assertThat(processusRepository.findById(dossier.idProcessus()).orElseThrow()
                    .getStatut()).isEqualTo(StatutEnum.EN_ATTENTE_DA);
        }

        /** Test 8 du guide : le chef d'unite devant un dossier monte au directeur reseau. */
        @Test
        @DisplayName("8. Validation d'un etat EN_ATTENTE_DR par un CHEF_UNITE_DA : refusee")
        void chefUniteSurUnEtatChezLeDirecteurReseau() {
            Dossier dossier = unDossierChezLeDirecteurReseau();
            // Le profil et l'habilitation restent ceux du chef d'unite (@BeforeEach).

            assertThatThrownBy(() -> validationService.valider(dossier.idProcessus(), JETON, IP))
                    .isInstanceOf(RoleNonAttenduException.class)
                    .hasMessageContaining("directeur reseau")
                    .hasMessageContaining("CHEF_UNITE_DA");

            assertThat(pieceJointeRepository.findByIdProcessus(dossier.idProcessus())
                    .orElseThrow().getNombreSignatures())
                    .as("aucun troisieme visa n'a ete grave")
                    .isEqualTo(2);
        }

        /**
         * <b>Second point de cloture</b> : le visa du directeur reseau est terminal, et il
         * transmet comme le premier.
         *
         * <p>C'est la garantie que les deux points sont couverts. Le declenchement n'est pas
         * ecrit deux fois : il est branche sur ce qui les definit tous les deux — le statut
         * atteint vaut {@code CLOTURE}. Deux appels separes se seraient ressembles a s'y
         * meprendre, et en oublier un aurait laisse une moitie des etats de la banque sans
         * jamais partir en paiement, sans aucune erreur visible.
         */
        @Test
        @DisplayName("9. La cloture par le DR declenche aussi la transmission")
        void clotureSecondNiveauTransmet() {
            Dossier dossier = unDossierChezLeDirecteurReseau();
            profilDuDirecteur();
            directeurHabilite();

            ResultatValidation resultat =
                    validationService.valider(dossier.idProcessus(), JETON, IP);

            assertThat(transmissionClient.processusAppeles())
                    .containsExactly(dossier.idProcessus());
            assertThat(resultat.transmission().transmis()).isTrue();

            ProcessusMensuel relu =
                    processusRepository.findById(dossier.idProcessus()).orElseThrow();
            assertThat(relu.getStatut()).isEqualTo(StatutEnum.CLOTURE);
            assertThat(relu.isTransmisComptabilite())
                    .as("la transmission est declenchee sur les DEUX branches du seuil")
                    .isTrue();
            assertThat(relu.getStatutIntegration()).isEqualTo(StatutIntegrationEnum.EN_ATTENTE);
        }

        @Test
        @DisplayName("9b. L'audit du second niveau ne porte ni seuil ni decision d'aiguillage")
        void auditDuSecondNiveau() {
            Dossier dossier = unDossierChezLeDirecteurReseau();
            profilDuDirecteur();
            directeurHabilite();

            validationService.valider(dossier.idProcessus(), JETON, IP);

            EvenementAudit evenement = evenementDAudit("VALIDATION_PROCESSUS");

            assertThat(evenement.idUtilisateur()).isEqualTo(ID_DIRECTEUR);
            assertThat(evenement.detailJson())
                    .contains("VALIDATION_DR")
                    .contains("CLOTURE")
                    .contains(LOGIN_DIRECTEUR);
            assertThat(evenement.detailJson())
                    .as("inscrire un seuil a vide laisserait croire qu'une comparaison a eu lieu")
                    .doesNotContain("seuilApplique")
                    .doesNotContain("aiguillage");
        }
    }

    /**
     * Le premier evenement d'audit portant cette action.
     *
     * <p>Depuis le Sprint 5.1, une cloture en publie <b>deux</b> :
     * {@code VALIDATION_PROCESSUS} puis {@code TRANSMISSION_COMPTABLE}. Un
     * {@code verify(publicateurAudit).publier(...)} sans filtre echouerait donc, non parce
     * qu'une trace manque, mais parce qu'il y en a une de plus — et il faudrait la relacher
     * en {@code atLeastOnce()}, ce qui cesserait de dire quoi que ce soit. On nomme donc
     * l'action attendue.
     */
    private EvenementAudit evenementDAudit(String action) {
        ArgumentCaptor<EvenementAudit> capture = ArgumentCaptor.forClass(EvenementAudit.class);
        verify(publicateurAudit, org.mockito.Mockito.atLeastOnce()).publier(capture.capture());

        return capture.getAllValues().stream()
                .filter(evenement -> action.equals(evenement.action()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Aucun evenement d'audit " + action + " publie. Publies : "
                                + capture.getAllValues().stream().map(EvenementAudit::action)
                                        .toList()));
    }

    // =====================================================================
    // Sous-sprint 4.4 : separation des taches en situation (RG-12, CT-17)
    // =====================================================================

    @Nested
    @DisplayName("Separation des taches")
    class SeparationDesTaches {

        /**
         * Test 1 du guide 4.4, en situation reelle : l'agent qui a soumis se presente
         * pour valider.
         *
         * <p>Il porte ici le role du chef d'unite — c'est le cas du cumul, tranche a
         * l'etape 1 : refus strict. Le refus doit tomber <b>avant</b> l'estampage, sans
         * quoi le document porterait un visa que la base ne connaitrait pas.
         */
        @Test
        @DisplayName("1. Le soumissionnaire tente de valider : refus, document intact")
        void leSoumissionnaireNeValidePas() throws Exception {
            Dossier dossier = unDossierSoumis(seuilService.seuilAiguillage());
            Path fichier = racineStockage.resolve(dossier.cheminRelatif());
            long tailleAvant = Files.size(fichier);

            // Meme identifiant local que l'agent qui a soumis, promu chef d'unite.
            when(profilClient.obtenir(anyString())).thenReturn(new ResultatProfil.ProfilObtenu(
                    new ActeurSignataire(ID_AGENT, LOGIN_AGENT, RoleEnum.CHEF_UNITE_DA)));

            assertThatThrownBy(() -> validationService.valider(dossier.idProcessus(), JETON, IP))
                    .isInstanceOf(SeparationTachesException.class)
                    .hasMessageContaining("Vous avez soumis cet etat");

            assertThat(Files.size(fichier))
                    .as("le refus precede l'estampage : le document n'a pas bouge")
                    .isEqualTo(tailleAvant);
            assertThat(processusRepository.findById(dossier.idProcessus()).orElseThrow()
                    .getStatut()).isEqualTo(StatutEnum.EN_ATTENTE_DA);
        }

        /** Test 2 du guide : un chef d'unite qui n'a pas soumis valide normalement. */
        @Test
        @DisplayName("2. Un chef d'unite n'ayant pas soumis valide sans obstacle")
        void leChefUniteNonSoumissionnaireValide() {
            Dossier dossier = unDossierSoumis(seuilService.seuilAiguillage());

            ResultatValidation resultat =
                    validationService.valider(dossier.idProcessus(), JETON, IP);

            assertThat(resultat.processus().getStatut()).isEqualTo(StatutEnum.CLOTURE);
        }

        /**
         * Test 3 du guide : celui qui a vise au premier niveau se presente au second.
         *
         * <p>Conforme a la decision de l'etape 1 — lecture 3 de RG-12, les deux cumuls
         * sont interdits. Sans ce controle, une seule personne pourrait engager la
         * banque de bout en bout sur un dossier au-dela du seuil, ce que le second
         * niveau d'approbation existe precisement pour empecher.
         */
        @Test
        @DisplayName("3. Celui qui a valide au premier niveau ne valide pas au second")
        void pasDeDoubleVisaDeValidation() {
            Dossier dossier = unDossierChezLeDirecteurReseau();

            // Le chef d'unite du cycle courant, promu directeur reseau.
            when(profilClient.obtenir(anyString())).thenReturn(new ResultatProfil.ProfilObtenu(
                    new ActeurSignataire(ID_CHEF, LOGIN_CHEF, RoleEnum.DIRECTEUR_RESEAU_DR)));
            directeurHabilite();

            assertThatThrownBy(() -> validationService.valider(dossier.idProcessus(), JETON, IP))
                    .isInstanceOf(SeparationTachesException.class)
                    .hasMessageContaining("deja valide cet etat au niveau du chef d'unite");
        }
    }

    // =====================================================================
    // Outils
    // =====================================================================

    /** Un dossier soumis : processus EN_ATTENTE_DA, document a une signature. */
    private record Dossier(Long idProcessus, String cheminRelatif) {
    }

    /**
     * Reconstitue l'etat que la soumission du Sprint 4.2 laisse derriere elle :
     * processus {@code EN_ATTENTE_DA} au montant voulu, document reellement ecrit
     * portant le visa de l'agent, etape {@code SOUMISSION_AGENT} enregistree.
     *
     * <p>Le document est produit par le <b>vrai</b> {@link SignatureService} : c'est
     * ce qui permet de verifier ensuite que l'estampage conserve la premiere mention.
     */
    private Dossier unDossierSoumis(long montantTotal) {
        int mois = prochainMois();

        ProcessusMensuel processus = declencherSur(mois, ANNEE, UNITE);
        processus.reporterMontantTotal(Math.toIntExact(montantTotal));
        TransitionProcessus.soumettre(processus);
        TransitionProcessus.transfererAuChefUnite(processus);
        ProcessusMensuel enregistre = processusRepository.save(processus);

        ActeurSignataire agent = new ActeurSignataire(ID_AGENT, LOGIN_AGENT, RoleEnum.AGENT_UNITE);
        ResultatSignature signature = signatureService.creerEtSigner(
                enregistre, uneConsolidation(enregistre, montantTotal), agent,
                LocalDateTime.of(ANNEE, mois, 28, 9, 30));

        EtapeWorkflow soumission = new EtapeWorkflow(
                enregistre.getId(), ID_AGENT, 1, NomEtapeEnum.SOUMISSION_AGENT);
        soumission.validerAvecSignature(signature.empreinte());
        etapeRepository.save(soumission);

        PieceJointe pieceJointe = new PieceJointe(
                enregistre.getId(), signature.document().cheminRelatif());
        pieceJointeRepository.save(pieceJointe);

        entityManager.flush();

        return new Dossier(enregistre.getId(), signature.document().cheminRelatif());
    }

    /** Un etat a une journee et une ligne, dont le montant fait le total voulu. */
    private EtatConsolide uneConsolidation(ProcessusMensuel processus, long montantTotal) {
        EtatConsolide.Beneficiaire mballa = new EtatConsolide.Beneficiaire(
                55L, "MBALLA", "Paul", "03702009991111", UNITE);

        EtatConsolide.Ligne ligne = new EtatConsolide.Ligne(
                101L, 11L, 55L, mballa, "RATION", "JOUR",
                Math.toIntExact(montantTotal), 12L,
                processus.getDateDebut().withDayOfMonth(10).atTime(9, 0));

        EtatConsolide.Journee journee = new EtatConsolide.Journee(
                11L, processus.getDateDebut().withDayOfMonth(10), "ENREGISTREE",
                1, montantTotal, List.of(ligne));

        return new EtatConsolide(processus.getId(), UNITE, processus.getDateDebut(),
                processus.getDateFin(), 1, 1, 1, montantTotal, List.of(journee));
    }

    /**
     * Le texte <b>rendu</b> du PDF, page par page.
     *
     * <p>Extrait par iText et non lu en octets bruts : le contenu des pages est
     * compresse dans le fichier, et une recherche dans les octets ne trouverait
     * jamais un visa qui s'y trouve pourtant. C'est le texte tel qu'un lecteur le
     * verrait a l'ecran qui fait foi ici.
     */
    private String texteDuPdf(Path fichier) throws Exception {
        StringBuilder texte = new StringBuilder();
        try (PdfDocument pdf = new PdfDocument(new PdfReader(fichier.toFile()))) {
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                texte.append(PdfTextExtractor.getTextFromPage(pdf.getPage(page)))
                        .append(System.lineSeparator());
            }
        }
        return texte.toString();
    }

    /**
     * Un dossier deja vise par le chef d'unite et monte au directeur reseau : statut
     * {@code EN_ATTENTE_DR}, document a deux signatures, parcours de deux etapes.
     *
     * <p>Construit en faisant reellement valider le dossier par le chef d'unite plutot
     * qu'en fabriquant l'etat a la main : c'est le seul moyen d'avoir un document qui
     * porte veritablement deux visas, et un parcours que le decoupage en cycles de
     * RG-12 lira comme le fera la production.
     */
    private Dossier unDossierChezLeDirecteurReseau() {
        Dossier dossier = unDossierSoumis(seuilService.seuilAiguillage() + 1);

        validationService.valider(dossier.idProcessus(), JETON, IP);
        entityManager.flush();
        entityManager.clear();

        // Le compteur d'appels au publicateur repart a zero : les tests du second
        // niveau n'observent que leur propre evenement.
        org.mockito.Mockito.clearInvocations(publicateurAudit);

        return dossier;
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

    private void chefHabilite() {
        when(habilitationClient.verifier(anyString(), anyString()))
                .thenReturn(new AgentHabilite(LOGIN_CHEF, RoleEnum.CHEF_UNITE_DA.name(), UNITE));
    }

    private void profilDuChef() {
        when(profilClient.obtenir(anyString())).thenReturn(new ResultatProfil.ProfilObtenu(
                new ActeurSignataire(ID_CHEF, LOGIN_CHEF, RoleEnum.CHEF_UNITE_DA)));
    }

    /**
     * Chaque dossier ouvre sa propre periode : l'index partiel
     * {@code ux_processus_normal_par_periode} interdit deux etats NORMAL sur le meme
     * couple unite / periode, y compris a l'interieur d'un meme test.
     */
    private static int prochainMois() {
        prochainMois = prochainMois % 12 + 1;
        return prochainMois;
    }

    private void rendreLeSeuilIllisible() {
        fixerValeurDuSeuil("cent mille");
    }

    private void fixerValeurDuSeuil(String valeur) {
        Query requete = entityManager
                .createQuery("update ParametreSysteme p set p.valeur = :valeur where p.code = :code")
                .setParameter("valeur", valeur)
                .setParameter("code", SeuilService.CODE_SEUIL_AIGUILLAGE);
        requete.executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }


    /**
     * Un etat declenche sur le mois indique, borne du premier au dernier jour.
     *
     * <p><b>Le mois n'est evalue qu'une fois</b>, ce qui compte : les jeux d'essai
     * l'obtiennent souvent d'un compteur {@code prochainMois()} a effet de bord, et
     * l'inliner deux fois pour composer les deux bornes produirait une periode a
     * cheval sur deux mois differents.
     *
     * <p>Les periodes mensuelles restent DISJOINTES entre elles, ce qui est
     * desormais indispensable : la contrainte d'exclusion
     * {@code ex_processus_normal_sans_chevauchement} refuse deux etats NORMAL dont
     * les periodes se recouvrent, meme partiellement (Maille 1).
     */
    private static ProcessusMensuel declencherSur(int mois, int annee, String codeUnite) {
        LocalDate debut = LocalDate.of(annee, mois, 1);
        return TransitionProcessus.declencher(debut, debut.plusMonths(1).minusDays(1), codeUnite);
    }

}
