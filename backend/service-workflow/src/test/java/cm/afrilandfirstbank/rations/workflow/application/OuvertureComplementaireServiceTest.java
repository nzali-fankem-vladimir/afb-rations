package cm.afrilandfirstbank.rations.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.workflow.api.dto.DeclenchementProcessusRequest;
import cm.afrilandfirstbank.rations.workflow.application.ResultatHabilitationUnite.AgentHabilite;
import cm.afrilandfirstbank.rations.workflow.application.ResultatHabilitationUnite.AgentNonHabilite;
import cm.afrilandfirstbank.rations.workflow.application.ResultatHabilitationUnite.ServiceIdentiteIndisponible;
import cm.afrilandfirstbank.rations.workflow.domaine.EtapeWorkflow;
import cm.afrilandfirstbank.rations.workflow.domaine.NomEtapeEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.RoleEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.TransitionProcessus;
import cm.afrilandfirstbank.rations.workflow.domaine.TypeProcessusEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.AgentNonHabiliteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.DelaiRegularisationDepasseException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.DelaiRegularisationIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.EtatNonClotureException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.FonctionnaliteNonOuverteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.MotifOuvertureRequisException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.OrigineRequiseException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.PeriodeNonConcordanteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ServiceIdentiteIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.UniteNonConcordanteException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.EtapeWorkflowRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ParametreSystemeRepository;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ProcessusMensuelRepository;

import jakarta.persistence.EntityManager;

/**
 * Ouverture d'un etat complementaire sur une periode close (Sprint 6bis.1, US-17,
 * CT-34).
 *
 * <h2>Contre la vraie base, comme les Sprints 4.1 a 4.4</h2>
 *
 * <p>L'index partiel {@code ux_processus_normal_par_periode} fait partie de ce qu'on
 * eprouve : il doit autoriser <b>plusieurs</b> etats COMPLEMENTAIRE sur une periode ou
 * il n'autorise qu'un seul NORMAL. Un test a base de mocks ne dirait rien de cette
 * propriete, qui vit en base et nulle part ailleurs. Seul l'appel reseau a Identite est
 * simule ; {@link FonctionnaliteService} lit le vrai {@code parametre_systeme}.
 *
 * <h2>Les deux tests qui comptent le plus</h2>
 *
 * <p>Les tests 1 et 2 prouvent que la fonctionnalite reste <b>fermee</b> tant que le
 * metier n'a pas tranche, et que ce refus arrive <b>avant tout le reste</b>. Le test 6
 * prouve que l'etat d'origine ressort <b>strictement inchange</b> : c'est tout l'interet
 * de la solution retenue — on ne rouvre jamais un etat clos.
 *
 * <p>Jeux d'essai sur l'unite <b>00013</b> en <b>annee 2095</b> : aucune collision
 * possible avec les autres suites sur l'index d'unicite.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("OuvertureComplementaireService — regularisation d'une periode close")
class OuvertureComplementaireServiceTest {

    private static final String UNITE = "00013";
    private static final String AUTRE_UNITE = "00021";
    private static final int ANNEE = 2095;
    private static final String JETON = "Bearer jeton-de-test";
    private static final String IP = "10.0.0.33";
    private static final String LOGIN_AGENT = "sylvie_atangana";
    private static final String MOTIF = "MBARGA Jean omis les 10 et 15 du mois";

    @Autowired
    private ProcessusMensuelRepository processusRepository;
    @Autowired
    private EtapeWorkflowRepository etapeRepository;
    @Autowired
    private ParametreSystemeRepository parametreSystemeRepository;
    @Autowired
    private EntityManager entityManager;

    private HabilitationClient habilitationClient;
    private PublicateurAudit publicateurAudit;
    private FonctionnaliteService fonctionnaliteService;
    private OuvertureComplementaireService ouvertureService;

    /** Chaque origine ouvre sa propre periode : l'index d'unicite l'exige. */
    private static int prochainMois = 0;

    @BeforeEach
    void preparer() {
        habilitationClient = mock(HabilitationClient.class);
        publicateurAudit = mock(PublicateurAudit.class);
        fonctionnaliteService = new FonctionnaliteService(parametreSystemeRepository);

        ouvertureService = new OuvertureComplementaireService(
                processusRepository,
                etapeRepository,
                new HabilitationService(habilitationClient),
                fonctionnaliteService,
                publicateurAudit);

        habiliteSur(UNITE);
    }

    // =====================================================================
    // Le drapeau : les deux tests les plus importants du sous-sprint
    // =====================================================================

    @Nested
    @DisplayName("Drapeau de fonctionnalite")
    class Drapeau {

        /**
         * Test 1 du guide. Drapeau ferme — la valeur de la migration V2 —, la demande
         * est refusee <b>quelle que soit sa qualite par ailleurs</b> : ici l'origine
         * existe, elle est close, dans les delais, sur la bonne unite, avec un motif.
         * Tout est juste, et le refus tombe quand meme.
         */
        @Test
        @DisplayName("1. Drapeau ferme : ouverture refusee, meme sur une demande parfaite")
        void drapeauFermeRefuseToutDemande() {
            ProcessusMensuel origine = uneOrigineCloturee(10);

            assertThatThrownBy(() -> ouvertureService.ouvrir(demandePour(origine), JETON, IP))
                    .isInstanceOf(FonctionnaliteNonOuverteException.class)
                    .hasMessageContaining("complementaire")
                    // Un code distinct d'un refus d'habilitation : le message dit a qui
                    // s'adresser, et ce n'est pas l'administrateur du module.
                    .hasMessageContaining("DRH");

            assertThat(complementairesDe(origine)).isEmpty();
            verifyNoInteractions(publicateurAudit);
        }

        /**
         * Test 2 du guide, et le plus subtil des deux. Le refus du drapeau doit tomber
         * <b>avant toute autre verification</b> — ici l'origine n'existe meme pas.
         *
         * <p>Si l'ordre etait inverse, l'agent recevrait un {@code 404} qui laisserait
         * croire que la regularisation est ouverte et que seul le numero d'origine est
         * faux. Il chercherait le bon, puis se heurterait au mur qu'on aurait pu lui
         * montrer d'emblee.
         *
         * <p>La preuve est faite par l'absence d'interaction : le service Identite n'est
         * pas derange, et aucun processus n'est cree.
         */
        @Test
        @DisplayName("2. Drapeau ferme : le refus precede TOUT, meme sur une origine inexistante")
        void refusDuDrapeauPrecedeToutAutreControle() {
            DeclenchementProcessusRequest surOrigineFantome = new DeclenchementProcessusRequest(
                    1, ANNEE, UNITE, TypeProcessusEnum.COMPLEMENTAIRE, 999_999_999L, MOTIF);

            assertThatThrownBy(() -> ouvertureService.ouvrir(surOrigineFantome, JETON, IP))
                    .as("le drapeau se lit avant l'existence de l'origine")
                    .isInstanceOf(FonctionnaliteNonOuverteException.class);

            verifyNoInteractions(habilitationClient);
            verifyNoInteractions(publicateurAudit);
        }

        /**
         * Le refus du drapeau precede aussi les fautes de la requete elle-meme : un
         * motif vide et une origine absente ne changent rien au verdict. C'est la meme
         * propriete que le test 2, vue de l'autre bord.
         */
        @Test
        @DisplayName("3. Drapeau ferme : ni le motif absent ni l'origine absente ne changent le refus")
        void refusDuDrapeauPrecedeLesFautesDeRequete() {
            DeclenchementProcessusRequest videe = new DeclenchementProcessusRequest(
                    1, ANNEE, UNITE, TypeProcessusEnum.COMPLEMENTAIRE, null, "   ");

            assertThatThrownBy(() -> ouvertureService.ouvrir(videe, JETON, IP))
                    .isInstanceOf(FonctionnaliteNonOuverteException.class);
        }

        /**
         * Test 4 du guide : parametre absent de la base, comportement ferme par defaut,
         * conforme a la decision de l'etape 2. L'absence ne s'interprete jamais en
         * faveur de l'execution.
         */
        @Test
        @DisplayName("4. Parametre absent de la base : ferme par defaut, ouverture refusee")
        void parametreAbsentFermeParDefaut() {
            ProcessusMensuel origine = uneOrigineCloturee(10);
            supprimerLeDrapeau();

            assertThatThrownBy(() -> ouvertureService.ouvrir(demandePour(origine), JETON, IP))
                    .isInstanceOf(FonctionnaliteNonOuverteException.class);

            assertThat(complementairesDe(origine)).isEmpty();
        }

        /**
         * Test 3 du guide : drapeau ouvert, le controle passe a la suite des
         * verifications. La preuve est qu'un <i>autre</i> refus est desormais prononce —
         * celui de l'origine inexistante —, ce qui n'aurait pas pu arriver drapeau ferme.
         */
        @Test
        @DisplayName("5. Drapeau ouvert : le controle passe a la suite des verifications")
        void drapeauOuvertLaisseContinuer() {
            ouvrirLeDrapeau();

            DeclenchementProcessusRequest surOrigineFantome = new DeclenchementProcessusRequest(
                    1, ANNEE, UNITE, TypeProcessusEnum.COMPLEMENTAIRE, 999_999_999L, MOTIF);

            assertThatThrownBy(() -> ouvertureService.ouvrir(surOrigineFantome, JETON, IP))
                    .as("le drapeau ne bloque plus : c'est l'origine qui manque")
                    .isInstanceOf(ProcessusIntrouvableException.class);
        }
    }

    // =====================================================================
    // L'ouverture nominale, drapeau ouvert
    // =====================================================================

    @Nested
    @DisplayName("Ouverture nominale")
    class Nominale {

        @BeforeEach
        void ouvrirLaFonctionnalite() {
            ouvrirLeDrapeau();
        }

        /** Test 5 du guide : type COMPLEMENTAIRE, origine referencee, motif enregistre. */
        @Test
        @DisplayName("6. Ouverture nominale : COMPLEMENTAIRE, origine referencee, motif enregistre")
        void ouvertureNominale() {
            ProcessusMensuel origine = uneOrigineCloturee(10);

            ProcessusMensuel complementaire =
                    ouvertureService.ouvrir(demandePour(origine), JETON, IP);

            assertThat(complementaire.getId()).isNotNull();
            assertThat(complementaire.getTypeProcessus())
                    .isEqualTo(TypeProcessusEnum.COMPLEMENTAIRE);
            assertThat(complementaire.getIdProcessusOrigine()).isEqualTo(origine.getId());
            assertThat(complementaire.getMotifOuverture()).isEqualTo(MOTIF);
            assertThat(complementaire.getStatut())
                    .as("l'agent va saisir les lignes oubliees")
                    .isEqualTo(StatutEnum.EN_COURS_SAISIE);
            assertThat(complementaire.getMontantTotal()).isZero();
            assertThat(complementaire.isTransmisComptabilite()).isFalse();

            // La periode et l'unite viennent de l'origine, jamais de la requete.
            assertThat(complementaire.getCodeUnite()).isEqualTo(origine.getCodeUnite());
            assertThat(complementaire.getMoisPaiement()).isEqualTo(origine.getMoisPaiement());
            assertThat(complementaire.getAnneePaiement()).isEqualTo(origine.getAnneePaiement());

            // Reellement en base, pas seulement en memoire.
            assertThat(processusRepository.findById(complementaire.getId())).isPresent();
        }

        /**
         * <b>Test 6 du guide — le plus important apres les tests du drapeau.</b>
         *
         * <p>L'etat d'origine ressort strictement inchange : statut, montant, drapeau de
         * transmission, statut d'integration, date de reservation, et surtout ses
         * <b>etapes de validation</b>, qui portent les signatures.
         *
         * <p>C'est tout l'interet de la solution retenue : on ne rouvre jamais l'etat
         * d'origine, ce qui preserve l'integrite du controle interne. Un etat clos
         * modifie apres coup rendrait injustifiables les montants deja payes.
         */
        @Test
        @DisplayName("7. L'etat d'origine est STRICTEMENT inchange apres l'ouverture")
        void origineStrictementInchangee() {
            ProcessusMensuel origine = uneOrigineCloturee(10);

            // Photographie de l'origine avant l'ouverture.
            StatutEnum statutAvant = origine.getStatut();
            int montantAvant = origine.getMontantTotal();
            boolean transmisAvant = origine.isTransmisComptabilite();
            LocalDateTime creationAvant = origine.getDateCreation();
            List<String> signaturesAvant = signaturesDe(origine);

            ouvertureService.ouvrir(demandePour(origine), JETON, IP);
            vider();

            ProcessusMensuel relue = processusRepository.findById(origine.getId()).orElseThrow();
            assertThat(relue.getStatut()).isEqualTo(statutAvant).isEqualTo(StatutEnum.CLOTURE);
            assertThat(relue.getMontantTotal()).isEqualTo(montantAvant);
            assertThat(relue.isTransmisComptabilite()).isEqualTo(transmisAvant);
            assertThat(relue.getStatutIntegration()).isNull();
            assertThat(relue.getDateReservationTransmission()).isNull();
            assertThat(relue.getDateCreation()).isEqualTo(creationAvant);
            assertThat(relue.getTypeProcessus()).isEqualTo(TypeProcessusEnum.NORMAL);
            assertThat(relue.getIdProcessusOrigine())
                    .as("l'origine ne pointe vers rien : c'est le complementaire qui la reference")
                    .isNull();

            assertThat(signaturesDe(relue))
                    .as("les visas de l'etat clos sont intacts (RG-09)")
                    .isEqualTo(signaturesAvant)
                    .isNotEmpty();
        }

        /**
         * Test 13 du guide. L'index partiel {@code ux_processus_normal_par_periode} ne
         * porte que sur les etats NORMAL : plusieurs COMPLEMENTAIRE peuvent coexister
         * sur la meme unite et la meme periode. Un bénéficiaire peut etre oublie deux
         * fois, ou deux oublis peuvent etre signales a des dates differentes.
         */
        @Test
        @DisplayName("8. Plusieurs complementaires sur la meme periode : autorises")
        void plusieursComplementairesAutorises() {
            ProcessusMensuel origine = uneOrigineCloturee(10);

            ProcessusMensuel premier = ouvertureService.ouvrir(demandePour(origine), JETON, IP);
            ProcessusMensuel second = ouvertureService.ouvrir(
                    demandePour(origine, "TCHOUMBA Alice omise le 22"), JETON, IP);
            vider();

            assertThat(premier.getId()).isNotEqualTo(second.getId());
            assertThat(complementairesDe(origine)).hasSize(2);
            assertThat(second.getMotifOuverture()).isEqualTo("TCHOUMBA Alice omise le 22");
        }

        /**
         * Test 14 du guide, l'autre bord du precedent : l'index continue d'interdire un
         * second etat NORMAL sur la meme periode. Ouvrir la regularisation n'a pas
         * relache l'unicite du cycle ordinaire.
         */
        @Test
        @DisplayName("9. Un second etat NORMAL sur la meme periode reste refuse")
        void secondEtatNormalToujoursRefuse() {
            ProcessusMensuel origine = uneOrigineCloturee(10);
            ouvertureService.ouvrir(demandePour(origine), JETON, IP);
            vider();

            // Comparaison sur l'identifiant : ProcessusMensuel ne redefinit pas equals(),
            // et l'entite relue apres vidage du contexte est une autre instance.
            assertThat(processusRepository
                    .findByCodeUniteAndMoisPaiementAndAnneePaiementAndTypeProcessus(
                            origine.getCodeUnite(), origine.getMoisPaiement(),
                            origine.getAnneePaiement(), TypeProcessusEnum.NORMAL))
                    .as("il n'y a toujours qu'un seul etat NORMAL pour ce couple, et c'est l'origine")
                    .isPresent()
                    .get()
                    .extracting(ProcessusMensuel::getId)
                    .isEqualTo(origine.getId());
        }

        /**
         * L'ouverture est tracee avec son motif, et sous une action <b>distincte</b> du
         * declenchement ordinaire : ouvrir une regularisation sur une periode deja payee
         * n'est pas le meme geste qu'ouvrir le mois courant, et un controle interne doit
         * pouvoir les compter separement.
         *
         * <p>Le delta porte l'origine et sa date de cloture : c'est ce qui permet, six
         * mois plus tard, de refaire le raisonnement du delai avec les valeurs du jour.
         */
        @Test
        @DisplayName("10. L'ouverture est tracee avec le motif, l'origine et sa date de cloture")
        void ouvertureTracee() {
            ProcessusMensuel origine = uneOrigineCloturee(10);

            ProcessusMensuel complementaire =
                    ouvertureService.ouvrir(demandePour(origine), JETON, IP);

            ArgumentCaptor<EvenementAudit> capture = ArgumentCaptor.forClass(EvenementAudit.class);
            verify(publicateurAudit).publier(capture.capture());
            EvenementAudit evenement = capture.getValue();

            assertThat(evenement.action()).isEqualTo("OUVERTURE_COMPLEMENTAIRE");
            assertThat(evenement.entiteCible()).isEqualTo("processus_mensuel");
            assertThat(evenement.idEntite()).isEqualTo(complementaire.getId());
            assertThat(evenement.adresseIp()).isEqualTo(IP);
            assertThat(evenement.detailJson())
                    .contains("COMPLEMENTAIRE")
                    .contains(MOTIF)
                    .contains(String.valueOf(origine.getId()))
                    .contains(LOGIN_AGENT)
                    .contains("dateClotureOrigine");
        }
    }

    // =====================================================================
    // Les refus d'ouverture, drapeau ouvert
    // =====================================================================

    @Nested
    @DisplayName("Refus d'ouverture")
    class Refus {

        @BeforeEach
        void ouvrirLaFonctionnalite() {
            ouvrirLeDrapeau();
        }

        /** Test 7 du guide. */
        @Test
        @DisplayName("11. Origine inexistante : 404, sans interroger le service Identite")
        void origineInexistante() {
            DeclenchementProcessusRequest demande = new DeclenchementProcessusRequest(
                    1, ANNEE, UNITE, TypeProcessusEnum.COMPLEMENTAIRE, 999_999_999L, MOTIF);

            assertThatThrownBy(() -> ouvertureService.ouvrir(demande, JETON, IP))
                    .isInstanceOf(ProcessusIntrouvableException.class);

            // On ne demande pas « avez-vous droit sur l'unite de rien » (doctrine 4.1).
            verifyNoInteractions(habilitationClient);
        }

        /**
         * Une demande complementaire sans identifiant d'origine n'a aucun sens : elle
         * produirait un second etat mensuel autonome sur une periode close.
         *
         * <p>{@code 422} et non {@code 400} : la requete est syntaxiquement valide — elle
         * est meme exactement celle d'un etat NORMAL —, et la contrainte est
         * conditionnelle au type. Un {@code @NotNull} sur le DTO refuserait tous les
         * declenchements ordinaires du module.
         */
        @Test
        @DisplayName("12. Aucun identifiant d'origine : refus, sans toucher a la base")
        void origineAbsente() {
            DeclenchementProcessusRequest sansOrigine = new DeclenchementProcessusRequest(
                    1, ANNEE, UNITE, TypeProcessusEnum.COMPLEMENTAIRE, null, MOTIF);

            assertThatThrownBy(() -> ouvertureService.ouvrir(sansOrigine, JETON, IP))
                    .isInstanceOf(OrigineRequiseException.class)
                    .hasMessageContaining("idProcessusOrigine");

            verifyNoInteractions(habilitationClient);
        }

        /**
         * Test 11 du guide. Comme le motif de retour au Sprint 4.4, le controle porte sur
         * le <b>contenu utile</b> : une suite d'espaces est un champ present et un motif
         * absent.
         *
         * <p>Le motif est la seule trace de ce qui a declenche la regularisation — ce
         * module n'a pas d'entite Reclamation, le signalement du beneficiaire lui est
         * exterieur. Sans lui, plus rien n'explique pourquoi une periode close a recu un
         * paiement complementaire.
         */
        @Test
        @DisplayName("13. Motif vide ou compose d'espaces : refus, avant tout appel reseau")
        void motifVideRefuse() {
            ProcessusMensuel origine = uneOrigineCloturee(10);

            for (String motifCreux : new String[] {null, "", "   ", "\t\n  "}) {
                assertThatThrownBy(() ->
                        ouvertureService.ouvrir(demandePour(origine, motifCreux), JETON, IP))
                        .as("« %s » n'est pas un motif", motifCreux)
                        .isInstanceOf(MotifOuvertureRequisException.class);
            }

            verifyNoInteractions(habilitationClient);
            assertThat(complementairesDe(origine)).isEmpty();
        }

        /**
         * Test 8 du guide. Un dossier encore dans le circuit n'a pas besoin d'etre
         * regularise : il peut etre retourne a l'agent, corrige et resoumis (RG-11).
         * Ouvrir un complementaire a cote de lui creerait deux dossiers vivants sur la
         * meme periode, dont les montants se cumuleraient a l'insu du valideur.
         */
        @Test
        @DisplayName("14. Origine non cloturee : refus, quel que soit son statut")
        void origineNonClotureeRefusee() {
            ProcessusMensuel enSaisie = processusRepository.save(
                    TransitionProcessus.declencher(prochainMois(), ANNEE, UNITE));
            vider();

            assertThatThrownBy(() -> ouvertureService.ouvrir(demandePour(enSaisie), JETON, IP))
                    .isInstanceOf(EtatNonClotureException.class)
                    .hasMessageContaining("EN_COURS_SAISIE")
                    // Le message dit l'action attendue : un retour, pas une regularisation.
                    .hasMessageContaining("retour");

            assertThat(complementairesDe(enSaisie)).isEmpty();
        }

        /**
         * Test 9 du guide. L'origine est close depuis plus longtemps que
         * {@code DELAI_REGULARISATION_JOURS}, dont la valeur de 90 jours reste
         * <b>provisoire</b> (point M-02).
         *
         * <p>Le message nomme le delai applique et la date de cloture : sans les deux,
         * l'agent ne peut ni comprendre le refus, ni le contester aupres de la DRH.
         */
        @Test
        @DisplayName("15. Origine hors delai de regularisation : refus motive et chiffre")
        void origineHorsDelaiRefusee() {
            ProcessusMensuel origine = uneOrigineCloturee(200);

            assertThatThrownBy(() -> ouvertureService.ouvrir(demandePour(origine), JETON, IP))
                    .isInstanceOf(DelaiRegularisationDepasseException.class)
                    .hasMessageContaining("90")
                    .hasMessageContaining("200");

            assertThat(complementairesDe(origine)).isEmpty();
        }

        /**
         * La borne exacte, eprouvee des deux cotes. Le delai est <b>franc</b> : une
         * origine close depuis exactement 90 jours reste regularisable, la 91e journee
         * non. Un test sur une valeur confortablement inferieure passerait meme avec une
         * comparaison inversee d'une unite.
         */
        @Test
        @DisplayName("16. La borne du delai : 90 jours passe, 91 refuse")
        void borneDuDelai() {
            ProcessusMensuel aLaBorne = uneOrigineCloturee(90);
            assertThat(ouvertureService.ouvrir(demandePour(aLaBorne), JETON, IP).getId())
                    .as("le 90e jour est encore dans le delai")
                    .isNotNull();

            ProcessusMensuel unJourDeTrop = uneOrigineCloturee(91);
            assertThatThrownBy(() -> ouvertureService.ouvrir(demandePour(unJourDeTrop), JETON, IP))
                    .isInstanceOf(DelaiRegularisationDepasseException.class);
        }

        /**
         * Le delai n'est pas evaluable : refus, jamais une anciennete supposee. Un etat
         * {@code CLOTURE} sans aucune etape {@code VALIDEE} est une incoherence de
         * donnees, pas un cas metier — la cloture ne s'obtient que par une validation,
         * qui ecrit toujours l'etape et le processus dans la meme transaction.
         *
         * <p>{@code 500}, comme un seuil illisible : l'agent n'a rien a corriger.
         */
        @Test
        @DisplayName("17. Date de cloture introuvable : refus, jamais une anciennete supposee")
        void dateDeClotureIntrouvable() {
            ProcessusMensuel sansEtape = uneOrigineClotureeSansEtape();

            assertThatThrownBy(() -> ouvertureService.ouvrir(demandePour(sansEtape), JETON, IP))
                    .isInstanceOf(DelaiRegularisationIndisponibleException.class)
                    .hasMessageContaining("introuvable");
        }

        /** Le delai illisible arrete l'ouverture, comme le seuil arrete la validation. */
        @Test
        @DisplayName("18. Delai illisible en base : refus, aucune ouverture")
        void delaiIllisibleRefuse() {
            ProcessusMensuel origine = uneOrigineCloturee(10);
            fixerValeur(FonctionnaliteService.CODE_DELAI_REGULARISATION_JOURS, "trois mois");

            assertThatThrownBy(() -> ouvertureService.ouvrir(demandePour(origine), JETON, IP))
                    .isInstanceOf(DelaiRegularisationIndisponibleException.class);

            assertThat(complementairesDe(origine)).isEmpty();
        }

        /**
         * Test 10 du guide. L'agent n'a pas de portee sur l'unite <b>de l'origine</b> :
         * la portee est verifiee sur l'unite reelle du dossier vise, jamais sur celle que
         * la demande declare.
         */
        @Test
        @DisplayName("19. Origine appartenant a une autre unite : refus de portee")
        void origineDUneAutreUniteRefusee() {
            ProcessusMensuel origine = uneOrigineCloturee(10, AUTRE_UNITE);

            // L'agent reste habilite sur UNITE, pas sur AUTRE_UNITE.
            assertThatThrownBy(() -> ouvertureService.ouvrir(
                    demandePour(origine), JETON, IP))
                    .isInstanceOf(AgentNonHabiliteException.class)
                    .hasMessageContaining(AUTRE_UNITE);

            assertThat(complementairesDe(origine)).isEmpty();
        }

        /**
         * L'autre bord du precedent : l'appelant est habilite sur l'unite de l'origine,
         * mais <b>declare une autre unite</b> dans sa demande.
         *
         * <p>Le code unite de l'origine fait autorite ; celui de la requete n'est qu'une
         * declaration a verifier — doctrine du Sprint 3.4, ou le service Saisie oppose
         * deja le meme refus sous le meme code. {@code 403}, et trace en audit par
         * {@code GestionnaireErreursApi} (CT-04).
         */
        @Test
        @DisplayName("20. Unite declaree differente de celle de l'origine : 403 UNITE_NON_CONCORDANTE")
        void uniteDeclareeNonConcordante() {
            ProcessusMensuel origine = uneOrigineCloturee(10);

            DeclenchementProcessusRequest demande = new DeclenchementProcessusRequest(
                    origine.getMoisPaiement(), ANNEE, AUTRE_UNITE,
                    TypeProcessusEnum.COMPLEMENTAIRE, origine.getId(), MOTIF);

            assertThatThrownBy(() -> ouvertureService.ouvrir(demande, JETON, IP))
                    .isInstanceOf(UniteNonConcordanteException.class)
                    .hasMessageContaining(AUTRE_UNITE)
                    .hasMessageContaining(UNITE);

            assertThat(complementairesDe(origine)).isEmpty();
        }

        /**
         * La periode declaree ne decrit pas le dossier vise.
         *
         * <p>{@code 422} la ou l'unite vaut {@code 403} : la periode n'ouvre aucun droit,
         * se tromper de mois est une maladresse de saisie et non un franchissement de
         * perimetre. La traiter en refus d'acces polluerait le journal d'audit avec des
         * fautes de frappe.
         */
        @Test
        @DisplayName("21. Periode declaree differente de celle de l'origine : 422 PERIODE_NON_CONCORDANTE")
        void periodeDeclareeNonConcordante() {
            ProcessusMensuel origine = uneOrigineCloturee(10);

            DeclenchementProcessusRequest demande = new DeclenchementProcessusRequest(
                    origine.getMoisPaiement(), ANNEE - 1, origine.getCodeUnite(),
                    TypeProcessusEnum.COMPLEMENTAIRE, origine.getId(), MOTIF);

            assertThatThrownBy(() -> ouvertureService.ouvrir(demande, JETON, IP))
                    .isInstanceOf(PeriodeNonConcordanteException.class)
                    .hasMessageContaining(String.valueOf(ANNEE - 1));

            assertThat(complementairesDe(origine)).isEmpty();
        }

        /**
         * Refus conservateur (doctrine Sprint 1.3) : le service Identite muet ne laisse
         * rien passer. Une regularisation touche a un paiement ; elle ne s'ouvre pas sur
         * une habilitation qu'on n'a pas pu verifier.
         */
        @Test
        @DisplayName("22. Service Identite muet : refus conservateur, rien n'est cree")
        void identiteMuetteRefusee() {
            ProcessusMensuel origine = uneOrigineCloturee(10);
            when(habilitationClient.verifier(anyString(), anyString()))
                    .thenReturn(new ServiceIdentiteIndisponible("delai de lecture depasse"));

            assertThatThrownBy(() -> ouvertureService.ouvrir(demandePour(origine), JETON, IP))
                    .isInstanceOf(ServiceIdentiteIndisponibleException.class);

            assertThat(complementairesDe(origine)).isEmpty();
            verify(publicateurAudit, never()).publier(any(EvenementAudit.class));
        }
    }

    // =====================================================================
    // La date de cloture sur un dossier a plusieurs cycles
    // =====================================================================

    /**
     * <b>Le piege que le Sprint 4.4 a deja tendu une fois</b>, avec RG-12 et le
     * decoupage en cycles.
     *
     * <p>{@code processus_mensuel} ne porte aucune colonne {@code date_cloture}
     * (decision Sprint 4.1) : l'instant de la cloture se lit sur
     * {@code etape_workflow.date_creation} de l'etape de validation qui a clos le
     * dossier. Un dossier qui a connu plusieurs cycles — valide par le chef d'unite,
     * monte au directeur reseau, retourne, corrige, resoumis, puis clos — porte
     * <b>plusieurs etapes VALIDEE</b>.
     *
     * <p>Prendre la premiere ferait courir le delai depuis un visa annule par un retour,
     * et refuserait des regularisations parfaitement legitimes. C'est le rang d'etape,
     * calcule {@code dernier + 1} depuis le Sprint 4.4, qui permet de trancher — et le
     * raisonnement ne tient que parce que {@link StatutEnum#CLOTURE} est terminal :
     * aucune etape ne peut suivre celle qui a clos le dossier.
     *
     * <p>La version fidele de ce scenario, jouee par les vrais services de bout en bout,
     * est dans {@code CircuitCompletIT}.
     */
    @Test
    @DisplayName("23. Dossier a plusieurs cycles : le delai court depuis la DERNIERE validation")
    void delaiCompteDepuisLeDernierCycle() {
        ouvrirLeDrapeau();

        ProcessusMensuel origine = unProcessusClos();

        // Cycle 1 : soumission, visa du chef d'unite, puis retour du directeur reseau.
        // Le premier visa date de 300 jours — bien au-dela du delai de 90.
        etapeValidee(origine, 1, NomEtapeEnum.SOUMISSION_AGENT, 300);
        etapeValidee(origine, 2, NomEtapeEnum.VALIDATION_DA, 300);
        etapeRetournee(origine, 3, NomEtapeEnum.VALIDATION_DR, 299);

        // Cycle 2 : l'agent corrige et resoumet, le circuit se rejoue, la cloture
        // intervient il y a 10 jours seulement.
        etapeValidee(origine, 4, NomEtapeEnum.SOUMISSION_AGENT, 12);
        etapeValidee(origine, 5, NomEtapeEnum.VALIDATION_DA, 11);
        etapeValidee(origine, 6, NomEtapeEnum.VALIDATION_DR, 10);
        vider();

        ProcessusMensuel complementaire =
                ouvertureService.ouvrir(demandePour(origine), JETON, IP);

        assertThat(complementaire.getId())
                .as("la cloture reelle date de 10 jours : la regularisation est recevable. "
                        + "Un calcul fonde sur la PREMIERE etape VALIDEE aurait refuse a tort")
                .isNotNull();
        assertThat(complementaire.getIdProcessusOrigine()).isEqualTo(origine.getId());
    }

    /**
     * L'autre bord du precedent, sans lequel le test 23 passerait meme si le service
     * prenait n'importe quelle etape recente : ici <b>toutes</b> les etapes sont
     * anciennes, et l'ouverture doit etre refusee.
     */
    @Test
    @DisplayName("24. Dossier a plusieurs cycles, tous anciens : ouverture refusee")
    void delaiRefuseQuandTousLesCyclesSontAnciens() {
        ouvrirLeDrapeau();

        ProcessusMensuel origine = unProcessusClos();
        etapeValidee(origine, 1, NomEtapeEnum.SOUMISSION_AGENT, 300);
        etapeValidee(origine, 2, NomEtapeEnum.VALIDATION_DA, 299);
        etapeRetournee(origine, 3, NomEtapeEnum.VALIDATION_DR, 298);
        etapeValidee(origine, 4, NomEtapeEnum.SOUMISSION_AGENT, 200);
        etapeValidee(origine, 5, NomEtapeEnum.VALIDATION_DA, 199);
        vider();

        assertThatThrownBy(() -> ouvertureService.ouvrir(demandePour(origine), JETON, IP))
                .isInstanceOf(DelaiRegularisationDepasseException.class)
                .hasMessageContaining("199");
    }

    // =====================================================================
    // Outillage
    // =====================================================================

    private void habiliteSur(String codeUnite) {
        when(habilitationClient.verifier(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String demandee = invocation.getArgument(0);
                    return codeUnite.equals(demandee)
                            ? new AgentHabilite(LOGIN_AGENT, RoleEnum.AGENT_UNITE.name(), demandee)
                            : new AgentNonHabilite(
                                    "role AGENT_UNITE sans portee sur l'unite " + demandee);
                });
    }

    private DeclenchementProcessusRequest demandePour(ProcessusMensuel origine) {
        return demandePour(origine, MOTIF);
    }

    private DeclenchementProcessusRequest demandePour(ProcessusMensuel origine, String motif) {
        return new DeclenchementProcessusRequest(
                origine.getMoisPaiement(), origine.getAnneePaiement(), origine.getCodeUnite(),
                TypeProcessusEnum.COMPLEMENTAIRE, origine.getId(), motif);
    }

    /** Un etat clos sur l'unite de reference, valide il y a {@code joursDepuis} jours. */
    private ProcessusMensuel uneOrigineCloturee(int joursDepuis) {
        return uneOrigineCloturee(joursDepuis, UNITE);
    }

    /**
     * Un etat clos, avec son parcours d'etapes : soumission de l'agent puis validation
     * du chef d'unite, toutes deux signees. C'est ce que le circuit laisse reellement
     * derriere lui, et c'est la seconde qui tient lieu de date de cloture.
     */
    private ProcessusMensuel uneOrigineCloturee(int joursDepuis, String codeUnite) {
        ProcessusMensuel origine = unProcessusClos(codeUnite);
        etapeValidee(origine, 1, NomEtapeEnum.SOUMISSION_AGENT, joursDepuis + 1);
        etapeValidee(origine, 2, NomEtapeEnum.VALIDATION_DA, joursDepuis);
        vider();
        return processusRepository.findById(origine.getId()).orElseThrow();
    }

    /** Un etat clos SANS aucune etape : incoherence de donnees, pour le test 17. */
    private ProcessusMensuel uneOrigineClotureeSansEtape() {
        ProcessusMensuel origine = unProcessusClos();
        vider();
        return processusRepository.findById(origine.getId()).orElseThrow();
    }

    private ProcessusMensuel unProcessusClos() {
        return unProcessusClos(UNITE);
    }

    /**
     * Mene un processus jusqu'a {@link StatutEnum#CLOTURE} par la machine a etats, et
     * non en forcant le statut : le chemin emprunte est celui de la production.
     */
    private ProcessusMensuel unProcessusClos(String codeUnite) {
        ProcessusMensuel processus =
                TransitionProcessus.declencher(prochainMois(), ANNEE, codeUnite);
        processus.reporterMontantTotal(45_000);
        TransitionProcessus.soumettre(processus);
        TransitionProcessus.transfererAuChefUnite(processus);
        TransitionProcessus.cloturerApresValidationChefUnite(processus);
        return processusRepository.save(processus);
    }

    private void etapeValidee(ProcessusMensuel processus, int ordre, NomEtapeEnum nom,
            int joursDepuis) {
        EtapeWorkflow etape = new EtapeWorkflow(processus.getId(), 7L, ordre, nom);
        etape.validerAvecSignature("SHA-256:" + "a".repeat(64));
        dater(etapeRepository.save(etape), joursDepuis);
    }

    private void etapeRetournee(ProcessusMensuel processus, int ordre, NomEtapeEnum nom,
            int joursDepuis) {
        EtapeWorkflow etape = new EtapeWorkflow(processus.getId(), 9L, ordre, nom);
        etape.retournerAvecMotif("Montant du 12 a verifier");
        dater(etapeRepository.save(etape), joursDepuis);
    }

    /**
     * Recule l'horodatage d'une etape. {@code date_creation} est pose par
     * {@code @PrePersist} et n'a pas de mutateur — c'est voulu —, d'ou la mise a jour
     * JPQL, qui reste confinee a la transaction du test.
     */
    private void dater(EtapeWorkflow etape, int joursDepuis) {
        entityManager
                .createQuery("update EtapeWorkflow e set e.dateCreation = :date where e.id = :id")
                .setParameter("date", LocalDateTime.now().minusDays(joursDepuis))
                .setParameter("id", etape.getId())
                .executeUpdate();
    }

    private List<String> signaturesDe(ProcessusMensuel processus) {
        return etapeRepository.findByIdProcessusOrderByOrdreEtape(processus.getId()).stream()
                .map(EtapeWorkflow::getSignatureNumerique)
                .toList();
    }

    private List<ProcessusMensuel> complementairesDe(ProcessusMensuel origine) {
        return entityManager
                .createQuery("select p from ProcessusMensuel p where p.idProcessusOrigine = :id",
                        ProcessusMensuel.class)
                .setParameter("id", origine.getId())
                .getResultList();
    }

    private void ouvrirLeDrapeau() {
        fixerValeur(FonctionnaliteService.CODE_RATTRAPAGE_ACTIF, "true");
    }

    private void supprimerLeDrapeau() {
        entityManager
                .createQuery("delete from ParametreSysteme p where p.code = :code")
                .setParameter("code", FonctionnaliteService.CODE_RATTRAPAGE_ACTIF)
                .executeUpdate();
        vider();
    }

    private void fixerValeur(String code, String valeur) {
        entityManager
                .createQuery("update ParametreSysteme p set p.valeur = :valeur where p.code = :code")
                .setParameter("valeur", valeur)
                .setParameter("code", code)
                .executeUpdate();
        vider();
    }

    private void vider() {
        entityManager.flush();
        entityManager.clear();
    }

    private static int prochainMois() {
        prochainMois = prochainMois % 12 + 1;
        return prochainMois;
    }

}
