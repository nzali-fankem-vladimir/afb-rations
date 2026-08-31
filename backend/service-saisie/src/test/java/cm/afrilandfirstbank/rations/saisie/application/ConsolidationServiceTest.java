package cm.afrilandfirstbank.rations.saisie.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import cm.afrilandfirstbank.rations.saisie.application.EtatConsolide.JourneeConsolidee;
import cm.afrilandfirstbank.rations.saisie.domaine.Beneficiaire;
import cm.afrilandfirstbank.rations.saisie.domaine.FicheJournaliere;
import cm.afrilandfirstbank.rations.saisie.domaine.LignePrestation;
import cm.afrilandfirstbank.rations.saisie.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.SessionEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.StatutFicheEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.AgentNonHabiliteException;
import cm.afrilandfirstbank.rations.saisie.domaine.exception.UniteNonConcordanteException;
import cm.afrilandfirstbank.rations.saisie.infrastructure.BeneficiaireRepository;
import cm.afrilandfirstbank.rations.saisie.infrastructure.FicheJournaliereRepository;
import cm.afrilandfirstbank.rations.saisie.infrastructure.LignePrestationRepository;

/**
 * <b>RG-06</b> — consolidation mensuelle cote service Saisie, Sprint 3.4.
 *
 * <p><b>Contre la vraie base {@code rations_saisie}</b>, comme
 * {@code ControleDoublonServiceTest} (Sprint 3.2) et pour la meme raison, plus
 * forte encore ici : ce qu'il faut prouver, c'est qu'aucune ligne n'est <i>oubliee
 * ni comptee deux fois</i> par la requete d'agregation. Un test a base de mocks
 * prouverait que le service additionne ce qu'on lui donne — c'est-a-dire rien du
 * tout sur le risque reel. Seul {@code EtatModifiableService} est simule : il
 * porte un appel reseau vers le service Identite.
 *
 * <h2>Le jeu de donnees et son total, calcules a la main</h2>
 *
 * <pre>
 *   Processus 9400001, unite 00002, aout 2026
 *
 *   10 aout   MBALLA    RATION    JOUR    2 500
 *             MBALLA    TRANSPORT JOUR    1 500
 *             NKOULOU   RATION    JOUR    2 500
 *                                       --------
 *                             sous-total   6 500
 *
 *   11 aout   MBALLA    RATION    SOIR    3 000
 *             NKOULOU   RATION    SOIR    3 000
 *             ATANGANA  TRANSPORT SOIR    2 000
 *             ATANGANA  RATION    SOIR    3 000
 *                                       --------
 *                             sous-total  11 000
 *
 *   12 aout   ATANGANA  RATION    JOUR    2 500
 *                                       --------
 *                             sous-total   2 500
 *
 *   13 aout   (fiche ouverte, aucune ligne)
 *                                       --------
 *                             sous-total       0
 *                                       ========
 *                            TOTAL MOIS  20 000
 * </pre>
 *
 * <p>Un second processus voisin (9400002, unite 00002 egalement) porte une ligne
 * a 99 999 FCFA qui ne doit <b>jamais</b> entrer dans ce total. Le montant est
 * choisi pour etre impossible a confondre : s'il fuit, le total ne ressemble plus
 * a rien.
 *
 * <p><b>Pourquoi ce test n'est pas une formalite.</b> Le total conditionne
 * l'aiguillage du Sprint 4 : au plus 100 000 XAF, cloture directe ; au-dela,
 * passage au Directeur Reseau. Une erreur d'un franc autour du seuil envoie un
 * dossier au mauvais niveau de validation — un defaut de controle interne, pas
 * une gene d'exploitation.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("ConsolidationService — consolidation mensuelle (RG-06)")
class ConsolidationServiceTest {

    private static final Long PROCESSUS = 9_400_001L;
    private static final Long PROCESSUS_VOISIN = 9_400_002L;
    private static final Long PROCESSUS_SANS_FICHE = 9_400_003L;
    private static final Long PROCESSUS_ISOLE = 9_400_004L;

    private static final String UNITE = "00002";
    private static final String JETON = "Bearer jeton-de-test";

    private static final LocalDate LE_10 = LocalDate.of(2026, 8, 10);
    private static final LocalDate LE_11 = LocalDate.of(2026, 8, 11);
    private static final LocalDate LE_12 = LocalDate.of(2026, 8, 12);
    private static final LocalDate LE_13 = LocalDate.of(2026, 8, 13);

    /** Total du mois, additionne a la main a partir du tableau ci-dessus. */
    private static final long TOTAL_ATTENDU = 6_500L + 11_000L + 2_500L + 0L;

    @Autowired
    private FicheJournaliereRepository ficheRepository;
    @Autowired
    private LignePrestationRepository ligneRepository;
    @Autowired
    private BeneficiaireRepository beneficiaireRepository;

    private EtatModifiableService etatModifiableService;
    private ConsolidationService consolidationService;

    private Long idMballa;
    private Long idNkoulou;
    private Long idAtangana;

    @BeforeEach
    void preparer() {
        // @DataJpaTest ne charge pas les beans @Service : le service est instancie
        // a la main, avec ses vrais repositories derriere.
        etatModifiableService = mock(EtatModifiableService.class);
        consolidationService = new ConsolidationService(
                ficheRepository, ligneRepository, beneficiaireRepository, etatModifiableService);

        idMballa = beneficiaireRepository.saveAndFlush(
                new Beneficiaire("MBALLA", "Paul", "03702009991111", UNITE)).getId();
        idNkoulou = beneficiaireRepository.saveAndFlush(
                new Beneficiaire("NKOULOU", "Estelle", "03702009992222", "00001")).getId();
        idAtangana = beneficiaireRepository.saveAndFlush(
                new Beneficiaire("ATANGANA", "Serge", "03702009993333", UNITE)).getId();

        Long fiche10 = ouvrirFiche(PROCESSUS, LE_10);
        Long fiche11 = ouvrirFiche(PROCESSUS, LE_11);
        Long fiche12 = ouvrirFiche(PROCESSUS, LE_12);
        ouvrirFiche(PROCESSUS, LE_13); // ouverte, aucune ligne

        saisir(fiche10, idMballa, NatureEnum.RATION, SessionEnum.JOUR, 2_500);
        saisir(fiche10, idMballa, NatureEnum.TRANSPORT, SessionEnum.JOUR, 1_500);
        saisir(fiche10, idNkoulou, NatureEnum.RATION, SessionEnum.JOUR, 2_500);

        saisir(fiche11, idMballa, NatureEnum.RATION, SessionEnum.SOIR, 3_000);
        saisir(fiche11, idNkoulou, NatureEnum.RATION, SessionEnum.SOIR, 3_000);
        saisir(fiche11, idAtangana, NatureEnum.TRANSPORT, SessionEnum.SOIR, 2_000);
        saisir(fiche11, idAtangana, NatureEnum.RATION, SessionEnum.SOIR, 3_000);

        saisir(fiche12, idAtangana, NatureEnum.RATION, SessionEnum.JOUR, 2_500);

        // Le voisin, qui ne doit jamais entrer dans le total du processus 9400001.
        Long ficheVoisine = ouvrirFiche(PROCESSUS_VOISIN, LE_10);
        saisir(ficheVoisine, idMballa, NatureEnum.RATION, SessionEnum.JOUR, 99_999);
    }

    private Long ouvrirFiche(Long idProcessus, LocalDate jour) {
        return ficheRepository.saveAndFlush(
                new FicheJournaliere(idProcessus, jour, UNITE, 8, 2026)).getId();
    }

    private void saisir(Long idFiche, Long idBeneficiaire, NatureEnum nature, SessionEnum session,
            int montant) {
        ligneRepository.saveAndFlush(
                new LignePrestation(idFiche, idBeneficiaire, nature, session, montant, 12L));
    }

    // === Le montant total : le test central du sous-sprint ===================

    @Test
    @DisplayName("1. le total du mois est egal au total calcule a la main")
    void totalDuMois_egalAuTotalCalculeALaMain() {
        EtatConsolide etat = consolidationService.consolider(PROCESSUS, UNITE, JETON);

        assertThat(etat.montantTotalFcfa())
                .as("total du mois : 6 500 + 11 000 + 2 500 + 0")
                .isEqualTo(TOTAL_ATTENDU)
                .isEqualTo(20_000L);
    }

    @Test
    @DisplayName("2. le total est exactement la somme des sous-totaux journaliers")
    void totalDuMois_estLaSommeDesSousTotaux() {
        EtatConsolide etat = consolidationService.consolider(PROCESSUS, UNITE, JETON);

        long sommeDesSousTotaux = etat.journees().stream()
                .mapToLong(JourneeConsolidee::sousTotalFcfa)
                .sum();

        assertThat(etat.montantTotalFcfa())
                .as("le total et le detail affiche ne peuvent pas diverger")
                .isEqualTo(sommeDesSousTotaux);
    }

    @Test
    @DisplayName("3. chaque sous-total journalier est exact")
    void sousTotauxJournaliers_exacts() {
        EtatConsolide etat = consolidationService.consolider(PROCESSUS, UNITE, JETON);

        assertThat(etat.journees())
                .extracting(JourneeConsolidee::dateJour, JourneeConsolidee::sousTotalFcfa)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(LE_10, 6_500L),
                        org.assertj.core.groups.Tuple.tuple(LE_11, 11_000L),
                        org.assertj.core.groups.Tuple.tuple(LE_12, 2_500L),
                        org.assertj.core.groups.Tuple.tuple(LE_13, 0L));
    }

    @Test
    @DisplayName("4. aucune ligne d'un autre processus n'entre dans le total")
    void ligneDUnAutreProcessus_jamaisComptee() {
        EtatConsolide etat = consolidationService.consolider(PROCESSUS, UNITE, JETON);

        assertThat(etat.montantTotalFcfa())
                .as("la ligne a 99 999 du processus voisin ne doit pas fuir")
                .isEqualTo(20_000L);

        assertThat(etat.journees())
                .flatExtracting(JourneeConsolidee::lignes)
                .extracting(ligne -> ligne.ligne().getMontantApplique())
                .doesNotContain(99_999);
    }

    @Test
    @DisplayName("5. aucune ligne n'est comptee deux fois : 8 lignes saisies, 8 lignes rendues")
    void aucuneLigneCompteeDeuxFois() {
        EtatConsolide etat = consolidationService.consolider(PROCESSUS, UNITE, JETON);

        assertThat(etat.nombreLignes())
                .as("le lot de fiches est lu par un IN, jamais par une jointure")
                .isEqualTo(8);

        assertThat(etat.journees())
                .flatExtracting(JourneeConsolidee::lignes)
                .extracting(ligne -> ligne.ligne().getId())
                .doesNotHaveDuplicates();
    }

    // === Conformite a US-06 et CT-11 =========================================

    @Test
    @DisplayName("6. l'etat presente les journees triees, avec jour, statut et compte de lignes")
    void journees_trieesEtRenseignees() {
        EtatConsolide etat = consolidationService.consolider(PROCESSUS, UNITE, JETON);

        assertThat(etat.nombreJournees()).isEqualTo(4);
        assertThat(etat.journees())
                .extracting(JourneeConsolidee::dateJour)
                .as("US-06 : l'etat presente le jour, dans l'ordre du calendrier")
                .containsExactly(LE_10, LE_11, LE_12, LE_13);

        assertThat(etat.journees())
                .extracting(JourneeConsolidee::statut)
                .containsOnly(StatutFicheEnum.EN_SAISIE);

        assertThat(etat.journees())
                .extracting(JourneeConsolidee::nombreLignes)
                .containsExactly(3, 4, 1, 0);
    }

    @Test
    @DisplayName("7. chaque ligne porte son beneficiaire, sa nature, sa session et son montant")
    void lignes_portentBeneficiaireNatureSessionMontant() {
        EtatConsolide etat = consolidationService.consolider(PROCESSUS, UNITE, JETON);

        JourneeConsolidee le10 = etat.journees().getFirst();

        assertThat(le10.lignes())
                .as("CT-11 : jours, beneficiaires, montants, sessions")
                .extracting(
                        ligne -> ligne.beneficiaire().getNom(),
                        ligne -> ligne.ligne().getNature(),
                        ligne -> ligne.ligne().getSession(),
                        ligne -> ligne.ligne().getMontantApplique())
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("MBALLA", NatureEnum.RATION, SessionEnum.JOUR, 2_500),
                        org.assertj.core.groups.Tuple.tuple("MBALLA", NatureEnum.TRANSPORT, SessionEnum.JOUR, 1_500),
                        org.assertj.core.groups.Tuple.tuple("NKOULOU", NatureEnum.RATION, SessionEnum.JOUR, 2_500));

        assertThat(le10.lignes())
                .allSatisfy(ligne -> assertThat(ligne.beneficiaire().getNumCompteCourant())
                        .as("le compte courant est indispensable a la charge Kafka §7.1")
                        .isNotBlank());
    }

    @Test
    @DisplayName("8. les beneficiaires distincts sont comptes une fois, pas une fois par ligne")
    void beneficiairesDistincts_comptesUneFois() {
        EtatConsolide etat = consolidationService.consolider(PROCESSUS, UNITE, JETON);

        assertThat(etat.nombreBeneficiaires())
                .as("3 agents servis sur 8 lignes")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("9. une journee ouverte sans ligne figure dans l'etat, sous-total 0")
    void journeeSansLigne_presenteAvecSousTotalZero() {
        EtatConsolide etat = consolidationService.consolider(PROCESSUS, UNITE, JETON);

        JourneeConsolidee le13 = etat.journees().getLast();

        assertThat(le13.dateJour()).isEqualTo(LE_13);
        assertThat(le13.lignes()).isEmpty();
        assertThat(le13.sousTotalFcfa()).isZero();
    }

    @Test
    @DisplayName("10. l'unite et la periode sont celles recopiees sur les fiches")
    void uniteEtPeriode_reprisesDesFiches() {
        EtatConsolide etat = consolidationService.consolider(PROCESSUS, UNITE, JETON);

        assertThat(etat.idProcessus()).isEqualTo(PROCESSUS);
        assertThat(etat.codeUnite()).isEqualTo(UNITE);
        assertThat(etat.moisPaiement()).isEqualTo(8);
        assertThat(etat.anneePaiement()).isEqualTo(2026);
    }

    // === Montants figes, jamais recalcules ===================================

    @Test
    @DisplayName("11. le montant rendu est celui fige a la saisie, quel qu'il soit")
    void montantRendu_estCeluiFigeALaSaisie() {
        Long ficheIsolee = ouvrirFiche(PROCESSUS_ISOLE, LE_10);
        // Un montant qu'aucune grille tarifaire ne produirait jamais : s'il est
        // rendu tel quel, c'est qu'il n'a pas ete recalcule.
        saisir(ficheIsolee, idMballa, NatureEnum.RATION, SessionEnum.JOUR, 7_777);

        EtatConsolide etat = consolidationService.consolider(PROCESSUS_ISOLE, UNITE, JETON);

        assertThat(etat.montantTotalFcfa()).isEqualTo(7_777L);
    }

    @Test
    @DisplayName("12. la consolidation ne depend d'aucun client de resolution de montant")
    void consolidation_neDependDAucunClientDeGrille() {
        List<Class<?>> dependances = Arrays.stream(ConsolidationService.class.getDeclaredConstructors())
                .flatMap(constructeur -> Arrays.stream(constructeur.getParameterTypes()))
                .toList();

        assertThat(dependances)
                .as("RG-03 : le total porte sur les montants figes, la grille n'est jamais reinterrogee")
                .doesNotContain(ResolutionMontantClient.class);
    }

    // === Aucun flottant dans la chaine de calcul =============================

    @Test
    @DisplayName("13. aucun montant de l'etat consolide n'est porte par un flottant")
    void aucunMontant_nEstUnFlottant() {
        assertThat(typesDesMontants(EtatConsolide.class))
                .as("les montants sont des entiers en FCFA : un flottant produirait un ecart d'arrondi")
                .isNotEmpty()
                .allMatch(type -> type == long.class || type == int.class);

        assertThat(typesDesMontants(JourneeConsolidee.class))
                .isNotEmpty()
                .allMatch(type -> type == long.class || type == int.class);
    }

    @Test
    @DisplayName("14. aucun composant de l'etat consolide n'est un double ni un BigDecimal")
    void aucunComposant_nEstDoubleOuBigDecimal() {
        assertThat(tousLesTypes(EtatConsolide.class))
                .doesNotContain(double.class, float.class, Double.class, Float.class, BigDecimal.class);
        assertThat(tousLesTypes(JourneeConsolidee.class))
                .doesNotContain(double.class, float.class, Double.class, Float.class, BigDecimal.class);
    }

    private static List<Class<?>> typesDesMontants(Class<?> record) {
        return Arrays.stream(record.getRecordComponents())
                .filter(composant -> composant.getName().toLowerCase().contains("fcfa"))
                .map(RecordComponent::getType)
                .toList();
    }

    private static List<Class<?>> tousLesTypes(Class<?> record) {
        return Arrays.stream(record.getRecordComponents())
                .map(RecordComponent::getType)
                .toList();
    }

    // === Etat vide et portee d'acces =========================================

    @Test
    @DisplayName("15. processus sans aucune fiche : etat vide, total 0, jamais une erreur")
    void processusSansFiche_rendUnEtatVide() {
        EtatConsolide etat = consolidationService.consolider(PROCESSUS_SANS_FICHE, UNITE, JETON);

        assertThat(etat.idProcessus()).isEqualTo(PROCESSUS_SANS_FICHE);
        assertThat(etat.codeUnite())
                .as("echo du code unite declare : c'est l'unite sur laquelle la question porte")
                .isEqualTo(UNITE);
        assertThat(etat.moisPaiement()).isNull();
        assertThat(etat.anneePaiement()).isNull();
        assertThat(etat.journees()).isEmpty();
        assertThat(etat.nombreJournees()).isZero();
        assertThat(etat.nombreLignes()).isZero();
        assertThat(etat.nombreBeneficiaires()).isZero();
        assertThat(etat.montantTotalFcfa()).isZero();
    }

    @Test
    @DisplayName("16. la portee d'acces est verifiee sur le code unite declare, meme etat vide")
    void porteeDAcces_verifieeMemeSurEtatVide() {
        consolidationService.consolider(PROCESSUS_SANS_FICHE, UNITE, JETON);

        verify(etatModifiableService).exigerHabilitationSurUnite(UNITE, JETON);
    }

    @Test
    @DisplayName("17. utilisateur hors portee : refus, aucune donnee lue")
    void utilisateurHorsPortee_refuse() {
        doThrow(new AgentNonHabiliteException("Vous n'avez pas de droit sur l'unite 00002."))
                .when(etatModifiableService).exigerHabilitationSurUnite(anyString(), anyString());

        assertThatThrownBy(() -> consolidationService.consolider(PROCESSUS, UNITE, JETON))
                .isInstanceOf(AgentNonHabiliteException.class);
    }

    @Test
    @DisplayName("18. code unite declare different de celui des fiches : refus")
    void uniteDeclareeNonConcordante_refusee() {
        assertThatThrownBy(() -> consolidationService.consolider(PROCESSUS, "00007", JETON))
                .as("un parametre fourni par l'appelant ne se croit pas sur parole")
                .isInstanceOf(UniteNonConcordanteException.class)
                .hasMessageContaining("00007")
                .hasMessageContaining(UNITE);
    }

}
