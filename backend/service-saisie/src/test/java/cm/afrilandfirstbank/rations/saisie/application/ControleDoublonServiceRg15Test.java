package cm.afrilandfirstbank.rations.saisie.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import cm.afrilandfirstbank.rations.saisie.domaine.Beneficiaire;
import cm.afrilandfirstbank.rations.saisie.domaine.FicheJournaliere;
import cm.afrilandfirstbank.rations.saisie.domaine.LignePrestation;
import cm.afrilandfirstbank.rations.saisie.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.SessionEnum;
import cm.afrilandfirstbank.rations.saisie.infrastructure.BeneficiaireRepository;
import cm.afrilandfirstbank.rations.saisie.infrastructure.FicheJournaliereRepository;
import cm.afrilandfirstbank.rations.saisie.infrastructure.LignePrestationRepository;

/**
 * <b>RG-15</b> — unicite inter-etats, tests 1 a 9 du sous-sprint 6bis.2.
 *
 * <p><b>Contre la vraie base</b>, pour la meme raison qu'au Sprint 3.2 : ce qui
 * est en jeu ici n'est pas que le service delegue au repository, mais que la
 * requete distingue reellement l'unite, la journee, l'etat, le beneficiaire, la
 * nature et la session. Cela ne se verifie que sur une base qui execute le SQL.
 *
 * <h2>Situation de depart</h2>
 *
 * <p>L'etat NORMAL {@code 7 810 001} de l'unite 00002, periode du 1er au 31 aout,
 * a paye a MBALLA une RATION de JOUR le 15 aout. Un etat COMPLEMENTAIRE
 * {@code 7 810 002} est ouvert sur la <b>meme periode</b> et la meme unite : sa
 * fiche du 15 aout est vierge, et c'est de la que tous les tests regardent.
 *
 * <p><b>Les tests 3, 4 et 5 pesent autant que le test 2.</b> Le second prouve que
 * le rempart existe ; les trois autres prouvent qu'il ne bloque pas les
 * regularisations legitimes, qui sont la raison d'etre du sprint. Une regle
 * d'unicite trop large ne produit pas de double paiement — elle empeche un agent
 * d'etre paye pour une prestation reellement effectuee, et personne ne s'en
 * plaint dans les journaux.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("ControleDoublonService — unicite inter-etats (RG-15)")
class ControleDoublonServiceRg15Test {

    private static final Long ETAT_ORIGINE = 7_810_001L;
    private static final Long ETAT_COMPLEMENTAIRE = 7_810_002L;
    private static final Long AUTRE_COMPLEMENTAIRE = 7_810_003L;
    private static final Long ETAT_AUTRE_UNITE = 7_810_004L;
    private static final Long ETAT_AUTRE_PERIODE = 7_810_005L;

    private static final String UNITE = "00002";       // Douala Bonanjo
    private static final String AUTRE_UNITE = "00003"; // Bafoussam

    private static final LocalDate LE_15_AOUT = LocalDate.of(2026, 8, 15);
    private static final LocalDate LE_16_AOUT = LocalDate.of(2026, 8, 16);
    private static final LocalDate DEBUT_AOUT = LocalDate.of(2026, 8, 1);
    private static final LocalDate FIN_AOUT = LocalDate.of(2026, 8, 31);
    private static final LocalDate LE_12_SEPTEMBRE = LocalDate.of(2026, 9, 12);
    private static final LocalDate DEBUT_SEPTEMBRE = LocalDate.of(2026, 9, 1);
    private static final LocalDate FIN_SEPTEMBRE = LocalDate.of(2026, 9, 30);

    @Autowired
    private LignePrestationRepository ligneRepository;
    @Autowired
    private FicheJournaliereRepository ficheRepository;
    @Autowired
    private BeneficiaireRepository beneficiaireRepository;

    private ControleDoublonService controleDoublonService;

    private Long idMballa;
    private Long idNkoulou;

    /** Fiche du 15 aout dans l'etat complementaire : le point de vue des tests. */
    private FicheJournaliere ficheComplementaireDu15;

    @BeforeEach
    void preparer() {
        // @DataJpaTest ne charge pas les beans @Service : le service est
        // instancie a la main, avec son vrai repository derriere lui.
        controleDoublonService = new ControleDoublonService(ligneRepository);

        idMballa = beneficiaireRepository.saveAndFlush(
                new Beneficiaire("MBALLA", "Paul", "03702009998888", UNITE)).getId();
        idNkoulou = beneficiaireRepository.saveAndFlush(
                new Beneficiaire("NKOULOU", "Estelle", "03702007776666", UNITE)).getId();

        // L'etat d'origine, paye : MBALLA, RATION, JOUR, le 15 aout.
        FicheJournaliere origineDu15 = enregistrerFiche(
                ETAT_ORIGINE, LE_15_AOUT, UNITE, DEBUT_AOUT, FIN_AOUT);
        enregistrerLigne(origineDu15, idMballa, NatureEnum.RATION, SessionEnum.JOUR);

        // Le complementaire ouvert sur la meme periode, encore vierge.
        ficheComplementaireDu15 = enregistrerFiche(
                ETAT_COMPLEMENTAIRE, LE_15_AOUT, UNITE, DEBUT_AOUT, FIN_AOUT);
    }

    @Test
    @DisplayName("1. beneficiaire non servi sur la journee : aucun conflit")
    void beneficiaireNonServi() {
        assertThat(conflitPour(idNkoulou, NatureEnum.RATION, SessionEnum.JOUR)).isEmpty();
    }

    @Test
    @DisplayName("2. combinaison exacte deja presente dans l'etat d'origine : conflit nomme")
    void combinaisonDejaPresenteDansLOrigine() {
        assertThat(conflitPour(idMballa, NatureEnum.RATION, SessionEnum.JOUR))
                .contains(ETAT_ORIGINE);
    }

    @Test
    @DisplayName("3. meme beneficiaire, meme journee, NATURE differente : aucun conflit")
    void natureDifferente() {
        assertThat(conflitPour(idMballa, NatureEnum.TRANSPORT, SessionEnum.JOUR)).isEmpty();
    }

    @Test
    @DisplayName("4. meme beneficiaire, meme journee, SESSION differente : aucun conflit")
    void sessionDifferente() {
        assertThat(conflitPour(idMballa, NatureEnum.RATION, SessionEnum.SOIR)).isEmpty();
    }

    @Test
    @DisplayName("5. meme beneficiaire, meme combinaison, JOURNEE differente : aucun conflit")
    void journeeDifferente() {
        FicheJournaliere du16 = enregistrerFiche(
                ETAT_COMPLEMENTAIRE, LE_16_AOUT, UNITE, DEBUT_AOUT, FIN_AOUT);

        assertThat(controleDoublonService.etatDeLaPeriodePortantDeja(
                du16, idMballa, NatureEnum.RATION, SessionEnum.JOUR)).isEmpty();
    }

    @Test
    @DisplayName("6. combinaison presente dans un AUTRE complementaire de la periode : conflit")
    void presenteDansUnAutreComplementaire() {
        // Le controle ne regarde pas seulement l'etat d'origine. Sans cela, un
        // second complementaire repaierait ce qu'un premier a deja servi.
        FicheJournaliere autreDu16 = enregistrerFiche(
                AUTRE_COMPLEMENTAIRE, LE_16_AOUT, UNITE, DEBUT_AOUT, FIN_AOUT);
        enregistrerLigne(autreDu16, idNkoulou, NatureEnum.TRANSPORT, SessionEnum.SOIR);

        FicheJournaliere complementaireDu16 = enregistrerFiche(
                ETAT_COMPLEMENTAIRE, LE_16_AOUT, UNITE, DEBUT_AOUT, FIN_AOUT);

        assertThat(controleDoublonService.etatDeLaPeriodePortantDeja(
                complementaireDu16, idNkoulou, NatureEnum.TRANSPORT, SessionEnum.SOIR))
                .contains(AUTRE_COMPLEMENTAIRE);
    }

    @Test
    @DisplayName("7. combinaison presente dans un etat d'une AUTRE unite : aucun conflit")
    void autreUnite() {
        // La charge est supportee par une autre unite : ce n'est pas la meme
        // depense, meme si le beneficiaire et la journee coincident.
        FicheJournaliere ailleurs = enregistrerFiche(
                ETAT_AUTRE_UNITE, LE_15_AOUT, AUTRE_UNITE, DEBUT_AOUT, FIN_AOUT);
        enregistrerLigne(ailleurs, idNkoulou, NatureEnum.RATION, SessionEnum.SOIR);

        assertThat(conflitPour(idNkoulou, NatureEnum.RATION, SessionEnum.SOIR)).isEmpty();
    }

    @Test
    @DisplayName("8. combinaison presente dans un etat d'une AUTRE periode : aucun conflit")
    void autrePeriode() {
        // Dans une base bien formee, « autre periode » implique « autre journee » :
        // la contrainte d'exclusion de la Maille 1 interdit a deux etats NORMAL
        // d'une meme unite de se chevaucher, et un COMPLEMENTAIRE recopie les
        // bornes de son origine. C'est ce cas reel qui est joue ici.
        FicheJournaliere enSeptembre = enregistrerFiche(
                ETAT_AUTRE_PERIODE, LE_12_SEPTEMBRE, UNITE, DEBUT_SEPTEMBRE, FIN_SEPTEMBRE);
        enregistrerLigne(enSeptembre, idMballa, NatureEnum.RATION, SessionEnum.JOUR);

        // L'assertion porte sur la LISTE complete des etats en conflit, et non sur
        // le seul premier : c'est la seule forme qui prouve que l'etat de
        // septembre n'y figure pas.
        assertThat(ligneRepository.etatsPortantDejaLaPrestation(
                UNITE, LE_15_AOUT, ETAT_COMPLEMENTAIRE, idMballa,
                NatureEnum.RATION, SessionEnum.JOUR))
                .containsExactly(ETAT_ORIGINE);
    }

    @Test
    @DisplayName("9. le statut de l'etat n'entre pas dans le verdict")
    void statutSansIncidence() {
        // Trace du seul arbitrage du sous-sprint : les etats EN_COURS_SAISIE et
        // RETOURNE sont INCLUS dans le controle. Une ligne saisie dans un etat
        // encore ouvert n'est pas payee, mais elle le sera si cet etat aboutit --
        // l'exclure autoriserait deux saisies concurrentes de la meme prestation,
        // et aucun des deux etats ne verrait l'autre.
        //
        // L'inclusion n'est pas un filtre a ecrire : c'est l'ABSENCE de filtre.
        // C'est precisement ce qui rend RG-15 realisable sans interroger le
        // service Workflow, seul a connaitre le statut d'un processus. Le versant
        // structurel de cette propriete est verrouille par Rg15SansAppelReseauTest.
        //
        // Ici, la preuve porte sur la donnee : la fiche d'origine est EN_SAISIE
        // — son etat n'est donc pas cloture — et le conflit est rendu quand meme.
        FicheJournaliere origineDu15 = ficheRepository
                .findByIdProcessusAndDateJour(ETAT_ORIGINE, LE_15_AOUT).orElseThrow();

        assertThat(origineDu15.getStatut()).isNotNull();
        assertThat(conflitPour(idMballa, NatureEnum.RATION, SessionEnum.JOUR))
                .contains(ETAT_ORIGINE);
    }

    @Test
    @DisplayName("Bonus — une ligne hors des bornes de son propre etat reste vue")
    void ligneHorsDesBornesDeSonEtat() {
        // Cas que la forme « chercher par periode » du guide aurait manque. Une
        // fiche peut porter une date_jour situee hors des bornes de son etat :
        // c'est le defaut que le controle de completude refuse a la SOUMISSION
        // (LIGNE_HORS_PERIODE, Sprint 4.2), donc il existe tant que l'etat n'est
        // pas soumis — la base de developpement en porte un exemplaire.
        //
        // Interroge par periode, RG-15 ne verrait pas cette ligne et la laisserait
        // ressaisir ailleurs. Interroge par journee, il la voit.
        FicheJournaliere egaree = enregistrerFiche(
                AUTRE_COMPLEMENTAIRE, LE_15_AOUT, UNITE, DEBUT_SEPTEMBRE, FIN_SEPTEMBRE);
        enregistrerLigne(egaree, idNkoulou, NatureEnum.TRANSPORT, SessionEnum.JOUR);

        assertThat(conflitPour(idNkoulou, NatureEnum.TRANSPORT, SessionEnum.JOUR))
                .contains(AUTRE_COMPLEMENTAIRE);
    }

    // ---------------------------------------------------------------- outils

    /** Le verdict vu depuis la fiche du 15 aout de l'etat complementaire. */
    private Optional<Long> conflitPour(Long idBeneficiaire, NatureEnum nature, SessionEnum session) {
        return controleDoublonService.etatDeLaPeriodePortantDeja(
                ficheComplementaireDu15, idBeneficiaire, nature, session);
    }

    private FicheJournaliere enregistrerFiche(Long idProcessus, LocalDate jour, String codeUnite,
                                              LocalDate debut, LocalDate fin) {
        return ficheRepository.saveAndFlush(
                new FicheJournaliere(idProcessus, jour, codeUnite, debut, fin));
    }

    private void enregistrerLigne(FicheJournaliere fiche, Long idBeneficiaire,
                                  NatureEnum nature, SessionEnum session) {
        ligneRepository.saveAndFlush(
                new LignePrestation(fiche.getId(), idBeneficiaire, nature, session, 2500, null));
    }

}
