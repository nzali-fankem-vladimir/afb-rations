package cm.afrilandfirstbank.rations.transmission.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.transmission.domaine.EtatValideEvent;
import cm.afrilandfirstbank.rations.transmission.domaine.exception.PublicationEchoueeException;
import cm.afrilandfirstbank.rations.transmission.domaine.exception.ServiceWorkflowIndisponibleException;

/**
 * RG-13 : <b>un etat valide n'est transmis qu'une seule fois</b> (US-12, CT-22,
 * Sprint 5.3).
 *
 * <h2>Ce que ce fichier doit prouver, et pourquoi c'est le test le plus important du
 * sous-sprint</h2>
 *
 * <p>Une seconde transmission produit deux jeux d'ecritures comptables, donc un double
 * paiement des memes beneficiaires. La propriete a etablir n'est donc pas « le service
 * refuse poliment » mais <b>le topic ne recoit qu'un seul message</b> — le resultat
 * observable du cote de la comptabilite, pas l'etat interne du module. C'est ce que
 * compte {@link PublicateurEspion}.
 *
 * <p>Le verrou lui-meme vit cote service Workflow, dans la base : ce fichier prouve que
 * <b>ce service le respecte</b> — qu'il ne publie jamais sans l'avoir obtenu, qu'il ne le
 * confirme qu'apres un accuse, et qu'il ne le libere que sur la preuve qu'aucun evenement
 * n'est parti. La serialisation par PostgreSQL est eprouvee de l'autre cote, par
 * {@code VerrouTransmissionServiceIT}.
 */
@DisplayName("Unicite de transmission — RG-13, CT-22")
class UniciteTransmissionTest {

    private static final Long ID_PROCESSUS = 740L;
    private static final String UNITE = "00002";
    private static final int MOIS = 7;
    private static final int ANNEE = 2026;
    private static final String JETON = "Bearer jeton-de-test";
    private static final String IP = "10.0.0.12";

    private VerrouEspion verrou;
    private PublicateurEspion publicateur;
    private TransmissionService service;

    @BeforeEach
    void preparer() {
        verrou = new VerrouEspion();
        publicateur = new PublicateurEspion();
        service = new TransmissionService(
                new ProcessusFige(),
                new ConsolidationFigee(),
                new ConstructionChargeService(),
                publicateur,
                new UniciteTransmissionService(verrou, new PublicateurAuditMuet()),
                new PublicateurAuditMuet());
    }

    private ResultatTransmission transmettre() {
        return service.transmettre(ID_PROCESSUS, JETON, IP);
    }

    // --- 1, 2 et 3 : le compte des messages ---------------------------------------

    @Nested
    @DisplayName("Deux demandes, un seul message")
    class DeuxDemandes {

        @Test
        @DisplayName("1. premiere transmission : le message part, le verrou est pose puis confirme")
        void premiereTransmission() {
            ResultatTransmission resultat = transmettre();

            assertThat(resultat).isInstanceOf(ResultatTransmission.Transmise.class);
            assertThat(publicateur.nombrePublications()).isEqualTo(1);
            assertThat(verrou.reservations()).isEqualTo(1);
            assertThat(verrou.confirmations())
                    .withFailMessage("Sans confirmation, l'etat resterait signale comme une "
                            + "publication d'issue inconnue alors qu'elle a abouti.")
                    .isEqualTo(1);
            assertThat(verrou.liberations()).isZero();
        }

        @Test
        @DisplayName("2. seconde demande : aucune publication, une reponse explicite")
        void secondeDemandeRefusee() {
            transmettre();
            ResultatTransmission seconde = transmettre();

            assertThat(seconde).isInstanceOf(ResultatTransmission.DejaTransmise.class);
            assertThat(((ResultatTransmission.DejaTransmise) seconde).message())
                    .withFailMessage("Un rejeu legitime doit comprendre ce qui se passe, pas "
                            + "recevoir une erreur technique qui ferait croire a une panne.")
                    .contains("deja ete transmis")
                    .contains("RG-13");
        }

        @Test
        @DisplayName("3. le topic ne recoit qu'un seul message apres deux demandes")
        void unSeulMessageSurLeTopic() {
            transmettre();
            transmettre();

            assertThat(publicateur.nombrePublications())
                    .withFailMessage("Deux messages sur le topic, c'est deux jeux d'ecritures "
                            + "comptables et un double paiement des memes beneficiaires.")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("le refus est rendu sans meme demander le verrou, quand l'en-tete le dit deja")
        void refusSurLectureDeLEnTete() {
            // La lecture de l'en-tete est une economie, pas le controle : elle evite de
            // deranger le service Saisie pour un etat manifestement deja parti.
            TransmissionService surEtatDejaTransmis = new TransmissionService(
                    new ProcessusFige(true),
                    new ConsolidationFigee(),
                    new ConstructionChargeService(),
                    publicateur,
                    new UniciteTransmissionService(verrou, new PublicateurAuditMuet()),
                    new PublicateurAuditMuet());

            ResultatTransmission resultat =
                    surEtatDejaTransmis.transmettre(ID_PROCESSUS, JETON, IP);

            assertThat(resultat).isInstanceOf(ResultatTransmission.DejaTransmise.class);
            assertThat(publicateur.nombrePublications()).isZero();
            assertThat(verrou.reservations()).isZero();
        }
    }

    // --- 4 : la concurrence -------------------------------------------------------

    @Nested
    @DisplayName("Deux demandes concurrentes, un seul message")
    class Concurrence {

        /**
         * Huit fils demandent la transmission du meme etat au meme instant.
         *
         * <p>Le verrou est ici un {@code compareAndSet} : c'est exactement la garantie que
         * PostgreSQL apporte a l'autre bout avec {@code SELECT ... FOR UPDATE}, reduite a
         * ce qui compte pour ce service — <b>un seul appelant obtient le droit de
         * publier</b>. Ce que le test etablit, c'est que ce service n'a aucun chemin par
         * lequel une publication survient sans ce droit.
         */
        @Test
        @DisplayName("4. huit demandes simultanees : un seul message publie, sept refus explicites")
        void huitDemandesSimultanees() throws Exception {
            int fils = 8;
            CountDownLatch depart = new CountDownLatch(1);
            CountDownLatch arrivee = new CountDownLatch(fils);
            List<ResultatTransmission> resultats = new ArrayList<>();
            ExecutorService pool = Executors.newFixedThreadPool(fils);

            try {
                for (int i = 0; i < fils; i++) {
                    pool.submit(() -> {
                        try {
                            depart.await();
                            ResultatTransmission resultat = transmettre();
                            synchronized (resultats) {
                                resultats.add(resultat);
                            }
                        } catch (Exception ignoree) {
                            // Une exception serait deja un echec du test : le compte des
                            // publications le dira.
                        } finally {
                            arrivee.countDown();
                        }
                    });
                }

                depart.countDown();
                assertThat(arrivee.await(20, TimeUnit.SECONDS)).isTrue();

            } finally {
                pool.shutdownNow();
            }

            assertThat(publicateur.nombrePublications())
                    .withFailMessage("Un controle en deux temps aurait laisse passer plusieurs "
                            + "fils : c'est le scenario des deux instances traitant la meme "
                            + "cloture.")
                    .isEqualTo(1);

            assertThat(resultats)
                    .filteredOn(r -> r instanceof ResultatTransmission.Transmise)
                    .hasSize(1);
            assertThat(resultats)
                    .filteredOn(r -> r instanceof ResultatTransmission.DejaTransmise)
                    .hasSize(fils - 1);
        }
    }

    // --- 5 : l'echec de publication -----------------------------------------------

    @Nested
    @DisplayName("Echec de publication")
    class EchecDePublication {

        @Test
        @DisplayName("5. echec PROUVE sans envoi : le verrou est libere, une reprise reste possible")
        void echecAvantEnvoiLibere() {
            publicateur.echouera(new ResultatPublication.EchecAvantEnvoi(
                    "KafkaException : Topic rations.etat.valide not present in metadata"));

            assertThatThrownBy(UniciteTransmissionTest.this::transmettre)
                    .isInstanceOf(PublicationEchoueeException.class);

            assertThat(verrou.liberations())
                    .withFailMessage("Le message n'a jamais quitte la machine : y laisser le "
                            + "verrou figerait un etat cloture, repute transmis et jamais paye.")
                    .isEqualTo(1);
            assertThat(verrou.pose()).isFalse();

            // Et la reprise aboutit : c'est la moitie de la propriete qui compte.
            publicateur.reussira();
            assertThat(transmettre()).isInstanceOf(ResultatTransmission.Transmise.class);
            assertThat(publicateur.nombrePublications()).isEqualTo(1);
        }

        @Test
        @DisplayName("echec d'issue INCERTAINE : le verrou reste pose, aucune reprise possible")
        void echecIncertainConserveLeVerrou() {
            publicateur.echouera(new ResultatPublication.EchecIssueIncertaine(
                    "aucun accuse du broker en 7 s"));

            assertThatThrownBy(UniciteTransmissionTest.this::transmettre)
                    .isInstanceOf(PublicationEchoueeException.class);

            assertThat(verrou.liberations())
                    .withFailMessage("Un accuse peut se perdre APRES que le broker a ecrit le "
                            + "message : liberer ici rouvrirait la porte au double paiement.")
                    .isZero();
            assertThat(verrou.pose()).isTrue();

            // Une demande ulterieure est refusee, meme si le broker est revenu.
            publicateur.reussira();
            assertThat(transmettre()).isInstanceOf(ResultatTransmission.DejaTransmise.class);
            assertThat(publicateur.nombrePublications()).isZero();
        }

        @Test
        @DisplayName("verrou indisponible : rien n'est publie, le refus est un 503")
        void verrouIndisponibleNePubliePas() {
            verrou.tombeEnPanne();

            assertThatThrownBy(UniciteTransmissionTest.this::transmettre)
                    .isInstanceOf(ServiceWorkflowIndisponibleException.class)
                    .hasMessageContaining("verrou");

            assertThat(publicateur.nombrePublications())
                    .withFailMessage("Sans verrou, rien ne garantit qu'un autre appel ne publie "
                            + "pas le meme etat au meme instant : le refus conservateur est la "
                            + "seule issue.")
                    .isZero();
        }
    }

    // --- Doublures ----------------------------------------------------------------

    /**
     * Le verrou du service Workflow, reduit a sa garantie : <b>un seul appelant obtient le
     * droit de publier</b>.
     *
     * <p>Ecrit a la main plutot que simule par Mockito : le nombre de reservations, de
     * confirmations et de liberations est <b>la</b> chose a verifier, et un compteur qu'on
     * lit se relit plus surement qu'un {@code verify} au bon nombre d'invocations.
     */
    private static final class VerrouEspion implements VerrouTransmissionClient {

        private final AtomicBoolean pose = new AtomicBoolean(false);
        private final AtomicInteger reservations = new AtomicInteger();
        private final AtomicInteger confirmations = new AtomicInteger();
        private final AtomicInteger liberations = new AtomicInteger();
        private volatile boolean enPanne;

        void tombeEnPanne() {
            this.enPanne = true;
        }

        boolean pose() {
            return pose.get();
        }

        int reservations() {
            return reservations.get();
        }

        int confirmations() {
            return confirmations.get();
        }

        int liberations() {
            return liberations.get();
        }

        @Override
        public ResultatVerrou reserver(Long idProcessus, String enteteAutorisation) {
            if (enPanne) {
                return new ResultatVerrou.VerrouIndisponible("service Workflow injoignable");
            }
            reservations.incrementAndGet();
            return pose.compareAndSet(false, true)
                    ? new ResultatVerrou.VerrouTenu("Transmission reservee.")
                    : new ResultatVerrou.DejaTransmis(
                            "L'etat " + idProcessus + " a deja ete transmis (RG-13).");
        }

        @Override
        public ResultatVerrou confirmer(Long idProcessus, ResultatPublication.Publiee accuse,
                int nombreLignes, long montantTotal, String enteteAutorisation) {
            confirmations.incrementAndGet();
            return new ResultatVerrou.VerrouTenu("Transmission confirmee.");
        }

        @Override
        public ResultatVerrou liberer(Long idProcessus, String motif, String enteteAutorisation) {
            liberations.incrementAndGet();
            pose.set(false);
            return new ResultatVerrou.VerrouTenu("Reservation levee.");
        }
    }

    /** Le broker, reduit a un compteur : c'est le resultat observable qui compte. */
    private static final class PublicateurEspion implements PublicateurEtatValide {

        private final AtomicInteger publications = new AtomicInteger();
        private volatile ResultatPublication echec;

        int nombrePublications() {
            return publications.get();
        }

        void echouera(ResultatPublication echec) {
            this.echec = echec;
        }

        void reussira() {
            this.echec = null;
        }

        @Override
        public ResultatPublication publier(EtatValideEvent charge) {
            if (echec != null) {
                return echec;
            }
            int rang = publications.incrementAndGet();
            return new ResultatPublication.Publiee("rations.etat.valide", 0, rang);
        }
    }

    /** L'en-tete d'un etat cloture, fige. */
    private static final class ProcessusFige implements ProcessusClient {

        private final boolean dejaTransmis;

        ProcessusFige() {
            this(false);
        }

        ProcessusFige(boolean dejaTransmis) {
            this.dejaTransmis = dejaTransmis;
        }

        @Override
        public ResultatProcessus obtenir(Long idProcessus, String enteteAutorisation) {
            return new ResultatProcessus.ProcessusObtenu(new EnTeteProcessus(
                    ID_PROCESSUS, "CLOTURE", UNITE, MOIS, ANNEE, "NORMAL", 2_500, dejaTransmis));
        }
    }

    /** Une journee, une ligne, 2500 FCFA : de quoi construire une charge valide. */
    private static final class ConsolidationFigee implements ConsolidationClient {

        @Override
        public ResultatConsolidation consolider(Long idProcessus, String codeUnite,
                String enteteAutorisation) {

            EtatConsolide.Beneficiaire mbarga = new EtatConsolide.Beneficiaire(
                    1L, "Mbarga", "Jean", "00002000123456", "00002");
            EtatConsolide.Ligne ligne = new EtatConsolide.Ligne(
                    1L, mbarga.id(), mbarga, "RATION", "JOUR", 2_500);
            LocalDate jour = LocalDate.of(ANNEE, MOIS, 12);
            EtatConsolide.Journee journee = new EtatConsolide.Journee(
                    jour.toEpochDay(), jour, "ENREGISTREE", 1, 2_500L, List.of(ligne));

            return new ResultatConsolidation.EtatObtenu(new EtatConsolide(
                    ID_PROCESSUS, UNITE, MOIS, ANNEE, 1, 1, 1, 2_500L, List.of(journee)));
        }
    }

    /** L'audit ne fait jamais echouer le metier : il n'a rien a prouver ici. */
    private static final class PublicateurAuditMuet implements PublicateurAudit {

        @Override
        public void publier(EvenementAudit evenement) {
            // sans effet
        }
    }

}
