package cm.afrilandfirstbank.rations.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
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
import java.util.stream.Stream;

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
import cm.afrilandfirstbank.rations.workflow.application.ResultatConsolidation.EtatObtenu;
import cm.afrilandfirstbank.rations.workflow.application.ResultatHabilitationUnite.AgentHabilite;
import cm.afrilandfirstbank.rations.workflow.application.ResultatHabilitationUnite.AgentNonHabilite;
import cm.afrilandfirstbank.rations.workflow.domaine.NomEtapeEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.PieceJointe;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.RoleEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEtapeEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.TransitionProcessus;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.AgentNonHabiliteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.EtatIncompletException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.PieceJointeExistanteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ServiceIdentiteIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ServiceSaisieIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.TransitionProcessusInterditeException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.EtapeWorkflowRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.PieceJointeRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ProcessusMensuelRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.stockage.StockageDocumentsFichier;

/**
 * Tests de la soumission (Sprint 4.2, US-07, CT-12, CT-13).
 *
 * <h2>Contre la vraie base, et le vrai stockage de fichiers</h2>
 *
 * <p>Seuls les <b>appels reseau</b> sont simules : habilitation, profil,
 * consolidation. Tout le reste est reel — les trois repositories contre
 * {@code rations_workflow}, et {@link StockageDocumentsFichier} sur un repertoire
 * temporaire.
 *
 * <p>Ce n'est pas du zele. Trois des garanties de ce sous-sprint ne vivent que la :
 * la contrainte {@code id_processus UNIQUE} de {@code piece_jointe}, le refus
 * d'ecraser un fichier existant, et le fait qu'un document soit reellement ecrit
 * sur le disque avant qu'une signature ne soit comptee en base. Un stockage simule
 * prouverait qu'on l'a appele, pas que le document existe.
 *
 * <p>Jeux d'essai en <b>annee 2099</b>, comme au Sprint 4.1 : aucune donnee reelle
 * ne peut entrer en collision sur l'index partiel
 * {@code ux_processus_normal_par_periode}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("SoumissionService — soumission de l'etat mensuel")
class SoumissionServiceTest {

    private static final String UNITE = "00002";
    private static final String AUTRE_UNITE = "00007";
    private static final int ANNEE = 2099;
    private static final String JETON = "Bearer jeton-de-test";
    private static final String IP = "10.0.0.12";
    private static final String LOGIN = "jean_mbarga";
    private static final Long ID_ACTEUR = 7L;

    @TempDir
    Path racineStockage;

    @Autowired
    private ProcessusMensuelRepository processusRepository;
    @Autowired
    private EtapeWorkflowRepository etapeRepository;
    @Autowired
    private PieceJointeRepository pieceJointeRepository;

    private HabilitationClient habilitationClient;
    private ProfilClient profilClient;
    private ConsolidationClient consolidationClient;
    private PublicateurAudit publicateurAudit;
    private StockageDocumentsFichier stockage;
    private SoumissionService soumissionService;

    @BeforeEach
    void preparer() {
        // @DataJpaTest ne charge pas les beans @Service : ils sont instancies a la
        // main, avec les vrais repositories derriere. CompletudeService, DocumentService
        // et SignatureService sont REELS — leur comportement fait partie de ce qu'on
        // eprouve ici, pas de ce qu'on suppose.
        habilitationClient = mock(HabilitationClient.class);
        profilClient = mock(ProfilClient.class);
        consolidationClient = mock(ConsolidationClient.class);
        publicateurAudit = mock(PublicateurAudit.class);
        stockage = new StockageDocumentsFichier(racineStockage.toString());

        soumissionService = new SoumissionService(
                processusRepository,
                pieceJointeRepository,
                new HabilitationService(habilitationClient),
                profilClient,
                consolidationClient,
                new CompletudeService(),
                new SignatureService(new DocumentService(), stockage),
                new EnregistrementSoumission(processusRepository, etapeRepository,
                        pieceJointeRepository, publicateurAudit));

        habiliteSur(UNITE);
        profilConnu();
    }

    // --- 1. Soumission nominale (CT-12) ----------------------------------------

    @Test
    @DisplayName("1. soumission nominale : EN_ATTENTE_DA, montant reporte, piece jointe signee, etape creee")
    void soumissionNominale() {
        ProcessusMensuel processus = processusEnSaisie(1);
        consolidationRend(etatComplet(processus));

        ResultatSoumission resultat = soumissionService.soumettre(processus.getId(), JETON, IP);

        // Le statut : la soumission mene toujours au Chef d'Unite. L'aiguillage au
        // seuil (RG-08) n'intervient qu'apres SA validation, au sous-sprint 4.3.
        assertThat(resultat.processus().getStatut()).isEqualTo(StatutEnum.EN_ATTENTE_DA);
        assertThat(resultat.processus().getMontantTotal()).isEqualTo(8000);

        // La piece jointe, ecrite et signee une fois.
        assertThat(resultat.pieceJointe().getNombreSignatures()).isEqualTo(1);
        assertThat(resultat.pieceJointe().getTypeMime()).isEqualTo("application/pdf");
        assertThat(resultat.pieceJointe().getCheminFichier())
                .isEqualTo("%d/%02d/etat-rations-00002-%s-p%d.pdf".formatted(
                        processus.getDateDebut().getYear(),
                        processus.getDateDebut().getMonthValue(),
                        processus.getDateDebut().format(
                                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")),
                        processus.getId()));

        // L'etape du circuit.
        assertThat(resultat.etape().getNomEtape()).isEqualTo(NomEtapeEnum.SOUMISSION_AGENT);
        assertThat(resultat.etape().getStatutEtape()).isEqualTo(StatutEtapeEnum.VALIDEE);
        assertThat(resultat.etape().getOrdreEtape()).isEqualTo(1);
        assertThat(resultat.etape().getIdActeur()).isEqualTo(ID_ACTEUR);
        assertThat(resultat.etape().getSignatureNumerique()).startsWith("SHA-256:");
        assertThat(resultat.etape().getDateCreation()).isNotNull();
    }

    @Test
    @DisplayName("2. les trois ecritures sont reellement en base, pas seulement rendues")
    void ecrituresReellementPersistees() {
        ProcessusMensuel processus = processusEnSaisie(2);
        consolidationRend(etatComplet(processus));

        soumissionService.soumettre(processus.getId(), JETON, IP);

        assertThat(processusRepository.findById(processus.getId()))
                .get()
                .satisfies(relu -> {
                    assertThat(relu.getStatut()).isEqualTo(StatutEnum.EN_ATTENTE_DA);
                    assertThat(relu.getMontantTotal()).isEqualTo(8000);
                });
        assertThat(pieceJointeRepository.findByIdProcessus(processus.getId())).isPresent();
        assertThat(etapeRepository.findAll())
                .anyMatch(etape -> etape.getIdProcessus().equals(processus.getId()));
    }

    @Test
    @DisplayName("3. le fichier PDF existe reellement sur le stockage, et il est signe")
    void fichierEcritSurLeStockage() throws Exception {
        ProcessusMensuel processus = processusEnSaisie(3);
        consolidationRend(etatComplet(processus));

        ResultatSoumission resultat = soumissionService.soumettre(processus.getId(), JETON, IP);

        Path fichier = racineStockage.resolve(resultat.pieceJointe().getCheminFichier());
        assertThat(fichier).exists();
        assertThat(Files.size(fichier)).isPositive();
        assertThat(Files.readAllBytes(fichier)).startsWith("%PDF-".getBytes());
    }

    @Test
    @DisplayName("4. l'empreinte enregistree sur l'etape est bien celle du fichier ecrit")
    void empreinteDeLEtapeCorrespondAuFichier() throws Exception {
        ProcessusMensuel processus = processusEnSaisie(4);
        consolidationRend(etatComplet(processus));

        ResultatSoumission resultat = soumissionService.soumettre(processus.getId(), JETON, IP);

        // C'est le lien qui donne son sens a la signature : la trace en base doit
        // pouvoir etre recoupee avec le document archive, sans rien d'autre que lui.
        Path fichier = racineStockage.resolve(resultat.pieceJointe().getCheminFichier());
        assertThat(resultat.etape().getSignatureNumerique())
                .isEqualTo(SignatureService.empreinte(Files.readAllBytes(fichier)));
    }

    // --- 2. Le montant (RG-06, RG-08) -------------------------------------------

    @Test
    @DisplayName("5. le montant enregistre est EXACTEMENT celui de l'etat consolide, jamais recalcule")
    void montantEgalACeluiDeLEtatConsolide() {
        ProcessusMensuel processus = processusEnSaisie(5);

        // Total annonce volontairement en desaccord avec la somme des lignes (8 000).
        // Workflow RECOPIE, il ne readditionne pas : c'est la moitie de RG-06 qui lui
        // revient (decision Sprint 3.4). Recalculer creerait un second chemin de
        // calcul, et le montant qui commande l'aiguillage pourrait differer du detail.
        consolidationRend(etatAvecTotal(processus, 123_456L));

        ResultatSoumission resultat = soumissionService.soumettre(processus.getId(), JETON, IP);

        assertThat(resultat.processus().getMontantTotal()).isEqualTo(123_456);
    }

    @Test
    @DisplayName("6. un montant total absent est un REFUS, jamais un zero")
    void montantAbsentRefuse() {
        ProcessusMensuel processus = processusEnSaisie(6);
        consolidationRend(etatAvecTotal(processus, null));

        // Zero et « absent » commanderaient le meme aiguillage (« sous le seuil »),
        // l'un a juste titre, l'autre par accident (decision Sprint 4.1 section 6).
        assertThatThrownBy(() -> soumissionService.soumettre(processus.getId(), JETON, IP))
                .isInstanceOf(ServiceSaisieIndisponibleException.class)
                .hasMessageContaining("Un total absent n'est pas un total nul");

        assertRienNEstEcrit(processus);
    }

    @Test
    @DisplayName("7. un montant qui deborde la colonne est refuse, jamais tronque")
    void montantQuiDeborde() {
        ProcessusMensuel processus = processusEnSaisie(7);
        consolidationRend(etatAvecTotal(processus, Integer.MAX_VALUE + 1L));

        // montant_total est un INTEGER en base ; une conversion silencieuse rendrait
        // le total NEGATIF, et ce negatif commanderait l'aiguillage.
        assertThatThrownBy(() -> soumissionService.soumettre(processus.getId(), JETON, IP))
                .isInstanceOf(ServiceSaisieIndisponibleException.class)
                .hasMessageContaining("depasse la capacite");

        assertRienNEstEcrit(processus);
    }

    // --- 3. Completude (CT-13) ---------------------------------------------------

    @Test
    @DisplayName("8. etat vide : refus, avec le manque liste et RIEN d'ecrit")
    void etatVideRefuse() {
        ProcessusMensuel processus = processusEnSaisie(8);
        consolidationRend(new EtatConsolide(processus.getId(), UNITE,
                processus.getDateDebut(), processus.getDateFin(),
                0, 0, 0, 0L, List.of()));

        assertThatThrownBy(() -> soumissionService.soumettre(processus.getId(), JETON, IP))
                .isInstanceOfSatisfying(EtatIncompletException.class, refus -> {
                    assertThat(refus.getManques()).hasSize(1);
                    assertThat(refus.getManques().get(0).message())
                            .contains("aucune ligne de prestation");
                });

        assertRienNEstEcrit(processus);
        assertAucunFichierEcrit();
    }

    @Test
    @DisplayName("9. etat incomplet : le refus LISTE les manques, il ne se contente pas d'echouer")
    void etatIncompletListeLesManques() {
        ProcessusMensuel processus = processusEnSaisie(9);

        LocalDate debut = processus.getDateDebut();
        int mois = debut.getMonthValue();
        int annee = debut.getYear();

        // Une ligne hors periode, une sans montant, un beneficiaire sans compte.
        var sansCompte = new EtatConsolide.Beneficiaire(56L, "ESSAMA", "Paul", null, "00002");
        var correct = new EtatConsolide.Beneficiaire(55L, "MBARGA", "Jean", "03702009991111", "00002");

        // Meme mois, annee precedente : hors periode sans ambiguite.
        var horsPeriode = new EtatConsolide.Journee(11L, LocalDate.of(annee - 1, mois, 5),
                "EN_SAISIE", 1, 2500L, List.of(ligne(101L, correct, 2500, annee - 1, mois)));
        var dansPeriode = new EtatConsolide.Journee(12L, LocalDate.of(annee, mois, 4), "EN_SAISIE",
                2, 1500L, List.of(ligne(102L, correct, null, annee, mois),
                        ligne(103L, sansCompte, 1500, annee, mois)));

        consolidationRend(new EtatConsolide(processus.getId(), UNITE,
                processus.getDateDebut(), processus.getDateFin(),
                2, 3, 2, 4000L, List.of(horsPeriode, dansPeriode)));

        assertThatThrownBy(() -> soumissionService.soumettre(processus.getId(), JETON, IP))
                .isInstanceOfSatisfying(EtatIncompletException.class, refus ->
                        assertThat(refus.getManques())
                                .extracting(manque -> manque.code().name())
                                .containsExactly("LIGNE_HORS_PERIODE", "LIGNE_SANS_MONTANT",
                                        "BENEFICIAIRE_SANS_COMPTE"));

        assertRienNEstEcrit(processus);
        assertAucunFichierEcrit();
    }

    // --- 4. Statuts refuses ------------------------------------------------------

    @Test
    @DisplayName("10. un etat DEJA SOUMIS ne se resoumet pas")
    void etatDejaSoumisRefuse() {
        ProcessusMensuel processus = processusEnSaisie(10);
        consolidationRend(etatComplet(processus));
        soumissionService.soumettre(processus.getId(), JETON, IP);

        assertThatThrownBy(() -> soumissionService.soumettre(processus.getId(), JETON, IP))
                .isInstanceOf(TransitionProcessusInterditeException.class)
                .hasMessageContaining("son statut est EN_ATTENTE_DA")
                .hasMessageContaining("deja dans le circuit de validation");
    }

    @Test
    @DisplayName("11. un etat CLOTURE ne se soumet pas, et le message nomme la voie legitime")
    void etatClotureRefuse() {
        ProcessusMensuel processus = processusEnSaisie(11);
        TransitionProcessus.soumettre(processus);
        TransitionProcessus.transfererAuChefUnite(processus);
        TransitionProcessus.cloturerApresValidationChefUnite(processus);
        processusRepository.saveAndFlush(processus);

        assertThatThrownBy(() -> soumissionService.soumettre(processus.getId(), JETON, IP))
                .isInstanceOf(TransitionProcessusInterditeException.class)
                .hasMessageContaining("Un etat cloture est definitif")
                // RG-13 : le message renvoie a l'etat complementaire, jamais a une
                // reouverture, qui rendrait possible une seconde transmission.
                .hasMessageContaining("etat complementaire");

        // Ni le service Saisie ni le service Identite ne sont interroges pour un
        // refus que le statut suffit a trancher.
        verify(consolidationClient, never()).consolider(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("12. un etat RETOURNE est resoumissible : la reprise est portee par la resoumission")
    void etatRetourneResoumissible() {
        // Depuis le sous-sprint 4.4, RETOURNE ouvre la soumission (US-11, CT-24) : la
        // transition RETOURNE -> EN_COURS_SAISIE est appliquee dans la transaction de
        // resoumission, juste avant EN_COURS_SAISIE -> SOUMIS. Aucun endpoint de
        // reprise n'existe, le contrat d'API en compte six.
        ProcessusMensuel processus = processusEnSaisie(12);
        TransitionProcessus.soumettre(processus);
        TransitionProcessus.transfererAuChefUnite(processus);
        TransitionProcessus.retournerParChefUnite(processus, "Journee du 3 manquante.");
        processusRepository.saveAndFlush(processus);

        // Le document du premier cycle existe deja : la resoumission le REGENERE
        // depuis l'etat corrige, elle ne l'enrichit pas.
        pieceJointeRepository.saveAndFlush(new PieceJointe(
                processus.getId(), NommageDocument.cheminRelatif(processus)));
        stockage.ecrireNouveau(NommageDocument.cheminRelatif(processus),
                "ancien document".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        consolidationRend(etatComplet(processus));
        profilConnu();

        ResultatSoumission resultat = soumissionService.soumettre(processus.getId(), JETON, IP);

        assertThat(resultat.processus().getStatut()).isEqualTo(StatutEnum.EN_ATTENTE_DA);
        assertThat(resultat.pieceJointe().getNombreSignatures())
                .as("le compteur repart a un : les visas d'avant le retour ont disparu "
                        + "avec l'ancien fichier")
                .isEqualTo(1);
        // Le rang de l'etape est calcule depuis le parcours enregistre. Ici, le
        // premier cycle a ete simule sur l'entite sans creer d'etape en base : le
        // rang vaut donc un. Le calcul lui-meme est eprouve par le circuit complet
        // (CircuitCompletIT), ou les etapes existent reellement.
    }

    @Test
    @DisplayName("13. un processus inexistant : 404, sans meme interroger le service Identite")
    void processusInexistant() {
        assertThatThrownBy(() -> soumissionService.soumettre(999_999L, JETON, IP))
                .isInstanceOf(ProcessusIntrouvableException.class);

        verify(habilitationClient, never()).verifier(anyString(), anyString());
    }

    // --- 5. Portee d'acces et profil ---------------------------------------------

    @Test
    @DisplayName("14. soumission hors portee d'acces : refus, et rien n'est ecrit")
    void horsPorteeDAcces() {
        ProcessusMensuel processus = processusEnSaisie(14, AUTRE_UNITE);
        habiliteSur(UNITE); // l'agent n'a pas de droit sur AUTRE_UNITE

        assertThatThrownBy(() -> soumissionService.soumettre(processus.getId(), JETON, IP))
                .isInstanceOf(AgentNonHabiliteException.class);

        assertRienNEstEcrit(processus);
        assertAucunFichierEcrit();
        verify(consolidationClient, never()).consolider(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("15. service Identite muet sur /identite/moi : 503, aucun document produit")
    void profilIndisponible() {
        ProcessusMensuel processus = processusEnSaisie(15);
        consolidationRend(etatComplet(processus));
        when(profilClient.obtenir(anyString()))
                .thenReturn(new ResultatProfil.ServiceIdentiteIndisponible("connexion refusee"));

        assertThatThrownBy(() -> soumissionService.soumettre(processus.getId(), JETON, IP))
                .isInstanceOf(ServiceIdentiteIndisponibleException.class)
                .hasMessageContaining("Aucun document n'a ete produit");

        assertRienNEstEcrit(processus);
        assertAucunFichierEcrit();
    }

    @Test
    @DisplayName("16. aucun profil local : refus, et rien n'est ecrit")
    void profilAbsent() {
        ProcessusMensuel processus = processusEnSaisie(16);
        consolidationRend(etatComplet(processus));
        when(profilClient.obtenir(anyString()))
                .thenReturn(new ResultatProfil.ProfilAbsent("aucun profil actif"));

        assertThatThrownBy(() -> soumissionService.soumettre(processus.getId(), JETON, IP))
                .isInstanceOf(AgentNonHabiliteException.class);

        assertRienNEstEcrit(processus);
        assertAucunFichierEcrit();
    }

    @Test
    @DisplayName("17. service Saisie muet : refus conservateur, aucun montant suppose")
    void saisieIndisponible() {
        ProcessusMensuel processus = processusEnSaisie(17);
        when(consolidationClient.consolider(anyLong(), anyString(), anyString()))
                .thenReturn(new ResultatConsolidation.ServiceSaisieIndisponible("delai depasse"));

        assertThatThrownBy(() -> soumissionService.soumettre(processus.getId(), JETON, IP))
                .isInstanceOf(ServiceSaisieIndisponibleException.class);

        assertRienNEstEcrit(processus);
        assertAucunFichierEcrit();
    }

    // --- 6. Unicite de la piece jointe -------------------------------------------

    @Test
    @DisplayName("18. une seule piece jointe par processus, quoi qu'il arrive")
    void unePieceJointeParProcessus() {
        ProcessusMensuel processus = processusEnSaisie(18);
        consolidationRend(etatComplet(processus));

        soumissionService.soumettre(processus.getId(), JETON, IP);

        try {
            soumissionService.soumettre(processus.getId(), JETON, IP);
        } catch (RuntimeException refusAttendu) {
            // le refus est verifie par le test 19
        }

        assertThat(pieceJointeRepository.findAll())
                .filteredOn(piece -> piece.getIdProcessus().equals(processus.getId()))
                .hasSize(1);
    }

    @Test
    @DisplayName("19. une seconde soumission est refusee AVANT toute generation de document")
    void secondeSoumissionRefuseeAvantGeneration() {
        ProcessusMensuel processus = processusEnSaisie(19);
        consolidationRend(etatComplet(processus));
        soumissionService.soumettre(processus.getId(), JETON, IP);

        long fichiersApresPremiere = compterFichiers();

        // Le statut est deja EN_ATTENTE_DA : c'est lui qui refuse en premier, avant
        // meme le controle de piece jointe. Aucun second fichier n'est ecrit.
        assertThatThrownBy(() -> soumissionService.soumettre(processus.getId(), JETON, IP))
                .isInstanceOf(TransitionProcessusInterditeException.class);

        assertThat(compterFichiers()).isEqualTo(fichiersApresPremiere);
    }

    @Test
    @DisplayName("20. une piece jointe orpheline bloque la soumission d'un etat encore en saisie")
    void pieceJointeExistanteSurEtatEnSaisie() {
        ProcessusMensuel processus = processusEnSaisie(20);
        consolidationRend(etatComplet(processus));
        pieceJointeRepository.saveAndFlush(
                new cm.afrilandfirstbank.rations.workflow.domaine.PieceJointe(
                        processus.getId(), "un/chemin/quelconque.pdf"));

        // Le cas nait d'une transaction en echec apres ecriture : le statut est
        // reste EN_COURS_SAISIE alors qu'un document existe. Le controle applicatif
        // rend une phrase la ou la contrainte d'unicite rendrait un message technique.
        assertThatThrownBy(() -> soumissionService.soumettre(processus.getId(), JETON, IP))
                .isInstanceOf(PieceJointeExistanteException.class)
                .hasMessageContaining("deja ete soumis");

        assertAucunFichierEcrit();
    }

    // --- 7. Audit -----------------------------------------------------------------

    @Test
    @DisplayName("21. la soumission est tracee, avec l'identifiant de l'auteur et l'empreinte")
    void soumissionTracee() {
        ProcessusMensuel processus = processusEnSaisie(21);
        consolidationRend(etatComplet(processus));

        ResultatSoumission resultat = soumissionService.soumettre(processus.getId(), JETON, IP);

        ArgumentCaptor<EvenementAudit> capture = ArgumentCaptor.forClass(EvenementAudit.class);
        verify(publicateurAudit).publier(capture.capture());

        EvenementAudit evenement = capture.getValue();
        assertThat(evenement.action()).isEqualTo("SOUMISSION_PROCESSUS");
        assertThat(evenement.entiteCible()).isEqualTo("processus_mensuel");
        assertThat(evenement.idEntite()).isEqualTo(processus.getId());
        assertThat(evenement.adresseIp()).isEqualTo(IP);

        // Premier evenement de ce service a porter un idUtilisateur : le
        // declenchement du Sprint 4.1 le laissait nul, /identite/habilitation ne
        // rendant qu'un login.
        assertThat(evenement.idUtilisateur()).isEqualTo(ID_ACTEUR);

        assertThat(evenement.detailJson())
                .contains("EN_COURS_SAISIE")
                .contains("EN_ATTENTE_DA")
                .contains(LOGIN)
                .contains(resultat.etape().getSignatureNumerique());
    }

    @Test
    @DisplayName("22. un refus ne laisse AUCUNE trace de succes")
    void refusNonTraceCommeSucces() {
        ProcessusMensuel processus = processusEnSaisie(22);
        consolidationRend(new EtatConsolide(processus.getId(), UNITE,
                processus.getDateDebut(), processus.getDateFin(),
                0, 0, 0, 0L, List.of()));

        assertThatThrownBy(() -> soumissionService.soumettre(processus.getId(), JETON, IP))
                .isInstanceOf(EtatIncompletException.class);

        verify(publicateurAudit, never()).publier(org.mockito.ArgumentMatchers.any());
    }

    // --- Outillage ------------------------------------------------------------------

    private void assertRienNEstEcrit(ProcessusMensuel processus) {
        assertThat(processusRepository.findById(processus.getId()))
                .get()
                .satisfies(relu -> {
                    assertThat(relu.getStatut()).isEqualTo(StatutEnum.EN_COURS_SAISIE);
                    assertThat(relu.getMontantTotal()).isZero();
                });
        assertThat(pieceJointeRepository.findByIdProcessus(processus.getId())).isEmpty();
        assertThat(etapeRepository.findAll())
                .noneMatch(etape -> etape.getIdProcessus().equals(processus.getId()));
    }

    private void assertAucunFichierEcrit() {
        assertThat(compterFichiers()).isZero();
    }

    private long compterFichiers() {
        try (Stream<Path> contenu = Files.walk(racineStockage)) {
            return contenu.filter(Files::isRegularFile).count();
        } catch (Exception echec) {
            throw new IllegalStateException("Repertoire de stockage illisible.", echec);
        }
    }

    /**
     * Un processus en saisie, sur une periode propre a ce test.
     *
     * <p>Chaque test a besoin de sa propre periode : l'index partiel
     * {@code ux_processus_normal_par_periode} n'autorise qu'un seul processus
     * NORMAL par couple unite / periode, et les tests partagent la meme unite.
     *
     * <p>Le rang est donc converti en une periode <b>reelle</b> : mois de 1 a 12,
     * annee incrementee au-dela. Prendre le rang pour un mois donnerait des
     * periodes comme « 20/2099 », que rien ne rejette aujourd'hui mais qui rendent
     * les messages d'erreur absurdes a la lecture.
     */
    private ProcessusMensuel processusEnSaisie(int rang) {
        return processusEnSaisie(rang, UNITE);
    }

    private ProcessusMensuel processusEnSaisie(int rang, String codeUnite) {
        int mois = ((rang - 1) % 12) + 1;
        int annee = ANNEE + (rang - 1) / 12;
        return processusRepository.saveAndFlush(
                declencherSur(mois, annee, codeUnite));
    }

    private void habiliteSur(String codeUnite) {
        when(habilitationClient.verifier(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String demande = invocation.getArgument(0);
                    return codeUnite.equals(demande)
                            ? new AgentHabilite(LOGIN, "AGENT_UNITE", demande)
                            : new AgentNonHabilite(
                                    "role AGENT_UNITE sans portee sur l'unite " + demande);
                });
    }

    private void profilConnu() {
        when(profilClient.obtenir(anyString())).thenReturn(new ResultatProfil.ProfilObtenu(
                new ActeurSignataire(ID_ACTEUR, LOGIN, RoleEnum.AGENT_UNITE)));
    }

    private void consolidationRend(EtatConsolide etat) {
        when(consolidationClient.consolider(anyLong(), anyString(), anyString()))
                .thenReturn(new EtatObtenu(etat));
    }

    private EtatConsolide etatComplet(ProcessusMensuel processus) {
        return etatAvecTotal(processus, 8000L);
    }

    /**
     * Un etat consolide complet, <b>dans la periode du processus</b>.
     *
     * <p>Les journees sont datees d'apres {@code processus}, jamais d'apres une
     * periode fixe. Une premiere version de ces fixtures fabriquait des journees de
     * janvier pour des processus d'autres mois : le controle de coherence de
     * periode les a toutes refusees, a juste titre. Le defaut etait dans le jeu
     * d'essai, et le controle a fait exactement son travail.
     */
    private EtatConsolide etatAvecTotal(ProcessusMensuel processus, Long total) {
        LocalDate debut = processus.getDateDebut();
        int mois = debut.getMonthValue();
        int annee = debut.getYear();

        var beneficiaire = new EtatConsolide.Beneficiaire(55L, "MBARGA", "Jean",
                "03702009991111", "00002");
        var autre = new EtatConsolide.Beneficiaire(56L, "ESSAMA", "Paul",
                "03702009992222", "00002");

        var jour3 = new EtatConsolide.Journee(11L, LocalDate.of(annee, mois, 3), "EN_SAISIE",
                2, 4000L, List.of(ligne(101L, beneficiaire, 2500, annee, mois),
                        ligne(102L, autre, 1500, annee, mois)));
        var jour4 = new EtatConsolide.Journee(12L, LocalDate.of(annee, mois, 4), "EN_SAISIE",
                1, 4000L, List.of(ligne(103L, beneficiaire, 4000, annee, mois)));

        return new EtatConsolide(processus.getId(), UNITE,
                processus.getDateDebut(), processus.getDateFin(), 2, 3, 2, total,
                List.of(jour3, jour4));
    }

    private EtatConsolide.Ligne ligne(Long id, EtatConsolide.Beneficiaire beneficiaire,
            Integer montant, int annee, int mois) {
        return new EtatConsolide.Ligne(id, 11L, beneficiaire.id(), beneficiaire,
                "RATION", "JOUR", montant, 12L, LocalDateTime.of(annee, mois, 3, 9, 0));
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
