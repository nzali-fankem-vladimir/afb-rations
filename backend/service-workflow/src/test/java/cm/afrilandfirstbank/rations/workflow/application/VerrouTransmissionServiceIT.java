package cm.afrilandfirstbank.rations.workflow.application;

import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.workflow.application.ResultatVerrouTransmission.Resultat;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutIntegrationEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.TransitionProcessus;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.EtatNonClotureException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ProcessusMensuelRepository;

/**
 * Le verrou d'unicite de transmission, <b>contre la vraie base</b> (RG-13, Sprint 5.3).
 *
 * <h2>Ce que seul un test contre PostgreSQL peut etablir</h2>
 *
 * <p>Les tests du service Transmission prouvent qu'il respecte le verrou. Celui-ci prouve
 * que le verrou <b>tient</b> : que deux transactions concurrentes qui reservent le meme
 * etat sont serialisees par {@code SELECT ... FOR UPDATE}, et qu'une seule obtient le
 * droit de publier. C'est le scenario des deux instances du service traitant la meme
 * cloture, et aucune doublure en memoire ne peut en repondre.
 *
 * <h2>Sans transaction ambiante, deliberement</h2>
 *
 * <p>{@link Propagation#NOT_SUPPORTED} sur la classe : {@code @DataJpaTest} enveloppe
 * normalement chaque test dans une transaction annulee a la fin, invisible des autres
 * fils. Ici il faut au contraire de <b>vraies transactions commitees</b>, sans quoi le
 * second fil ne verrait meme pas la ligne. Chaque geste ouvre donc la sienne, par
 * {@link TransactionTemplate}, et le jeu d'essai est efface a la main.
 *
 * <p>Annee 2095 : aucune collision avec les autres suites sur l'index partiel
 * {@code ux_processus_normal_par_periode}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("Verrou de transmission — la concurrence, contre la vraie base (RG-13)")
class VerrouTransmissionServiceIT {

    private static final String UNITE = "00009";
    private static final int ANNEE = 2095;
    private static final String IP = "10.0.0.77";

    @Autowired
    private ProcessusMensuelRepository processusRepository;

    @Autowired
    private PlatformTransactionManager gestionnaireTransactions;

    private TransactionTemplate transaction;
    private VerrouTransmissionService verrou;
    private Long idProcessus;

    private static int prochainMois = 1;

    @BeforeEach
    void preparer() {
        transaction = new TransactionTemplate(gestionnaireTransactions);
        verrou = new VerrouTransmissionService(processusRepository, mock(PublicateurAudit.class));
        idProcessus = transaction.execute(statut -> processusRepository
                .save(unEtatCloture(prochainMois++))
                .getId());
    }

    @AfterEach
    void nettoyer() {
        if (idProcessus != null) {
            transaction.executeWithoutResult(statut ->
                    processusRepository.deleteById(idProcessus));
        }
    }

    // --- Le geste, sequentiellement ------------------------------------------------

    @Test
    @DisplayName("reservation puis confirmation : le drapeau, l'horodatage et le statut avancent")
    void reservationPuisConfirmation() {
        ResultatVerrouTransmission reservation =
                transaction.execute(s -> verrou.reserver(idProcessus, IP));

        assertThat(reservation.resultat()).isEqualTo(Resultat.RESERVEE);
        assertThat(reservation.transmisComptabilite()).isTrue();
        assertThat(reservation.dateReservation())
                .withFailMessage("Sans horodatage, une publication d'issue incertaine serait "
                        + "indiscernable d'un etat normalement en transit.")
                .isNotNull();
        assertThat(reservation.statutIntegration())
                .withFailMessage("Le statut d'integration ne se pose qu'a la CONFIRMATION : "
                        + "c'est lui qui distingue « publie » de « reserve ».")
                .isNull();

        ResultatVerrouTransmission confirmation = transaction.execute(s -> verrou.confirmer(
                idProcessus, "rations.etat.valide", 0, 12L, 1, 2_500L, IP));

        assertThat(confirmation.resultat()).isEqualTo(Resultat.CONFIRMEE);
        assertThat(confirmation.statutIntegration()).isEqualTo(StatutIntegrationEnum.EN_ATTENTE);
        assertThat(confirmation.dateReservation()).isNotNull();
    }

    @Test
    @DisplayName("seconde reservation : refusee, avec la date de la premiere transmission")
    void secondeReservationRefusee() {
        transaction.execute(s -> verrou.reserver(idProcessus, IP));

        ResultatVerrouTransmission seconde =
                transaction.execute(s -> verrou.reserver(idProcessus, IP));

        assertThat(seconde.resultat()).isEqualTo(Resultat.DEJA_TRANSMISE);
        assertThat(seconde.message())
                .contains("deja ete transmis")
                .contains("RG-13");
    }

    @Test
    @DisplayName("etat non cloture : aucune reservation, 422")
    void etatNonClotureRefuse() {
        Long idEnSaisie = transaction.execute(s -> processusRepository
                .save(declencherSur(prochainMois++, ANNEE, UNITE))
                .getId());

        try {
            assertThatThrownBy(() -> transaction.execute(s -> verrou.reserver(idEnSaisie, IP)))
                    .isInstanceOf(EtatNonClotureException.class)
                    .hasMessageContaining("EN_COURS_SAISIE");

            boolean transmis = Boolean.TRUE.equals(transaction.execute(s -> processusRepository
                    .findById(idEnSaisie).orElseThrow().isTransmisComptabilite()));
            assertThat(transmis).isFalse();
        } finally {
            transaction.executeWithoutResult(s -> processusRepository.deleteById(idEnSaisie));
        }
    }

    // --- La liberation --------------------------------------------------------------

    @Test
    @DisplayName("liberation : le drapeau et l'horodatage reviennent, une reprise redevient possible")
    void liberationRendLaReprisePossible() {
        transaction.execute(s -> verrou.reserver(idProcessus, IP));

        ResultatVerrouTransmission liberation = transaction.execute(s ->
                verrou.liberer(idProcessus, "broker injoignable des l'appel", IP));

        assertThat(liberation.resultat()).isEqualTo(Resultat.LIBEREE);
        assertThat(liberation.transmisComptabilite()).isFalse();
        assertThat(liberation.dateReservation()).isNull();

        // La reprise aboutit : c'est toute la raison d'etre de la liberation.
        assertThat(transaction.execute(s -> verrou.reserver(idProcessus, IP)).resultat())
                .isEqualTo(Resultat.RESERVEE);
    }

    @Test
    @DisplayName("liberation refusee quand la comptabilite a deja repondu")
    void liberationRefuseeApresAccuse() {
        transaction.execute(s -> verrou.reserver(idProcessus, IP));
        transaction.execute(s -> verrou.confirmer(
                idProcessus, "rations.etat.valide", 0, 12L, 1, 2_500L, IP));

        ResultatVerrouTransmission refus = transaction.execute(s ->
                verrou.liberer(idProcessus, "tentative tardive", IP));

        assertThat(refus.resultat()).isEqualTo(Resultat.LIBERATION_REFUSEE);
        assertThat(refus.transmisComptabilite())
                .withFailMessage("La comptabilite a repondu, donc l'evenement etait bien parti : "
                        + "liberer ferait republier un etat deja pris en charge.")
                .isTrue();
    }

    // --- La concurrence, le coeur du sous-sprint ------------------------------------

    /**
     * Huit transactions concurrentes reservent le meme etat. PostgreSQL les serialise sur
     * le verrou de ligne : la premiere ecrit, les sept autres attendent son commit puis
     * lisent le drapeau deja pose.
     *
     * <p>Un controle en deux temps — lire le drapeau, puis publier, puis l'ecrire — aurait
     * laisse passer plusieurs fils ici, et la comptabilite aurait recu autant de fois le
     * meme etat.
     */
    @Test
    @DisplayName("huit reservations simultanees : une seule aboutit, sept sont refusees")
    void huitReservationsSimultanees() throws Exception {
        int fils = 8;
        CountDownLatch depart = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(fils);

        try {
            List<Future<Resultat>> issues = new java.util.ArrayList<>();
            for (int i = 0; i < fils; i++) {
                Callable<Resultat> tache = () -> {
                    depart.await();
                    return transaction.execute(s -> verrou.reserver(idProcessus, IP)).resultat();
                };
                issues.add(pool.submit(tache));
            }

            depart.countDown();

            List<Resultat> resultats = new java.util.ArrayList<>();
            for (Future<Resultat> issue : issues) {
                resultats.add(issue.get(30, TimeUnit.SECONDS));
            }

            assertThat(resultats)
                    .withFailMessage("Deux reservations accordees, ce sont deux publications "
                            + "possibles du meme etat, donc un double paiement (RG-13).")
                    .filteredOn(r -> r == Resultat.RESERVEE)
                    .hasSize(1);
            assertThat(resultats)
                    .filteredOn(r -> r == Resultat.DEJA_TRANSMISE)
                    .hasSize(fils - 1);

        } finally {
            pool.shutdownNow();
        }
    }

    // --- Jeu d'essai ------------------------------------------------------------------

    /** Un etat mene jusqu'a CLOTURE par la machine a etats, comme en production. */
    // --- RG-13 face a la regularisation (Sprint 6bis.2, etape 6) -------------------

    @Test
    @DisplayName("etat complementaire : seconde transmission sur la MEME periode, autorisee")
    void secondeTransmissionSurLaMemePeriodeParUnAutreProcessus() {
        // Un etat complementaire cloture declenche une transmission sur une periode
        // DEJA transmise. La question que le guide 6bis.2 pose ici est celle du
        // controle interne : RG-13 va-t-elle la refuser comme un doublon ?
        //
        // Non, et c'est structurel : le verrou vit sur la LIGNE du processus, pas
        // sur le couple (unite, periode). Deux processus distincts, c'est deux
        // lignes, donc deux verrous independants. Un verrou porte par la periode
        // aurait rendu toute regularisation intransmissible — c'est-a-dire impayee.
        Long idComplementaire = transaction.execute(s -> {
            ProcessusMensuel origine = processusRepository.findById(idProcessus).orElseThrow();
            ProcessusMensuel complementaire = TransitionProcessus.ouvrirComplementaire(
                    origine, "Oubli signale par le beneficiaire apres cloture.");
            complementaire.reporterMontantTotal(1_200);
            TransitionProcessus.soumettre(complementaire);
            TransitionProcessus.transfererAuChefUnite(complementaire);
            TransitionProcessus.cloturerApresValidationChefUnite(complementaire);
            return processusRepository.save(complementaire).getId();
        });

        try {
            // La periode est bien la meme : c'est ce qui donne son sens au test.
            transaction.executeWithoutResult(s -> {
                ProcessusMensuel origine = processusRepository.findById(idProcessus).orElseThrow();
                ProcessusMensuel complementaire =
                        processusRepository.findById(idComplementaire).orElseThrow();
                assertThat(complementaire.getDateDebut()).isEqualTo(origine.getDateDebut());
                assertThat(complementaire.getDateFin()).isEqualTo(origine.getDateFin());
                assertThat(complementaire.getCodeUnite()).isEqualTo(origine.getCodeUnite());
            });

            assertThat(transaction.execute(s -> verrou.reserver(idProcessus, IP)).resultat())
                    .isEqualTo(Resultat.RESERVEE);

            assertThat(transaction.execute(s -> verrou.reserver(idComplementaire, IP)).resultat())
                    .as("la regularisation doit pouvoir partir en paiement a son tour")
                    .isEqualTo(Resultat.RESERVEE);

            // Et chacun reste unique POUR LUI-MEME : la seconde demande sur le
            // complementaire est refusee comme elle l'est sur l'etat d'origine.
            assertThat(transaction.execute(s -> verrou.reserver(idComplementaire, IP)).resultat())
                    .isEqualTo(Resultat.DEJA_TRANSMISE);

        } finally {
            transaction.executeWithoutResult(s -> processusRepository.deleteById(idComplementaire));
        }
    }

    private static ProcessusMensuel unEtatCloture(int mois) {
        ProcessusMensuel processus = declencherSur(mois, ANNEE, UNITE);
        processus.reporterMontantTotal(2_500);
        TransitionProcessus.soumettre(processus);
        TransitionProcessus.transfererAuChefUnite(processus);
        TransitionProcessus.cloturerApresValidationChefUnite(processus);
        return processus;
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
