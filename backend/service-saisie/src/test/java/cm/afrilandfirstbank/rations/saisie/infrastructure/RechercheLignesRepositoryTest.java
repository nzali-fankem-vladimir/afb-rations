package cm.afrilandfirstbank.rations.saisie.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import cm.afrilandfirstbank.rations.saisie.domaine.Beneficiaire;
import cm.afrilandfirstbank.rations.saisie.domaine.FicheJournaliere;
import cm.afrilandfirstbank.rations.saisie.domaine.LignePrestation;
import cm.afrilandfirstbank.rations.saisie.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.saisie.domaine.SessionEnum;

/**
 * La recherche d'états par critère de ligne, contre la vraie base
 * {@code rations_saisie} (Sprint 6.1, appui du test 3 et 4 du guide côté
 * agrégation).
 *
 * <p>Les entités de ce service n'ont pas d'association JPA (identifiants plats,
 * décision Sprint 3.1) : {@link RechercheLignesRepository} assemble du JPQL à la
 * main, et seule une base réelle prouve que la requête assemblée produit
 * effectivement les bonnes lignes.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RechercheLignesRepository.class)
@DisplayName("RechercheLignesRepository — critères de ligne (Sprint 6.1)")
class RechercheLignesRepositoryTest {

    // Période et unité exclusives à ce test, pour ne pas croiser les données
    // accumulées par d'autres exécutions dans la base de développement partagée.
    private static final Integer MOIS = 3;
    private static final Integer ANNEE = 2099;

    /** Les bornes de la periode d'essai — mars 2099, un mois entier. */
    private static final LocalDate DEBUT_PERIODE = LocalDate.of(ANNEE, MOIS, 1);
    private static final LocalDate FIN_PERIODE = LocalDate.of(ANNEE, MOIS, 31);
    private static final String UNITE = "09101";
    private static final String AUTRE_UNITE = "09102";

    @Autowired
    private RechercheLignesRepository repository;

    @Autowired
    private FicheJournaliereRepository ficheRepository;

    @Autowired
    private LignePrestationRepository ligneRepository;

    @Autowired
    private BeneficiaireRepository beneficiaireRepository;

    private Long ficheAvecLigne(Long idProcessus, String codeUnite, NatureEnum nature,
            SessionEnum session, String numeroCompte) {
        FicheJournaliere fiche = ficheRepository.saveAndFlush(
                new FicheJournaliere(idProcessus, LocalDate.of(ANNEE, MOIS, 10), codeUnite, DEBUT_PERIODE, FIN_PERIODE));
        Beneficiaire beneficiaire = beneficiaireRepository.saveAndFlush(
                new Beneficiaire("Mbarga", "Jean", numeroCompte, "00002"));
        ligneRepository.saveAndFlush(
                new LignePrestation(fiche.getId(), beneficiaire.getId(), nature, session, 1000, null));
        return idProcessus;
    }

    @Test
    @DisplayName("Filtre sur la nature seule : retrouve l'état porteur de la ligne")
    void filtreNatureSeule_retrouveLEtatPorteur() {
        ficheAvecLigne(910_001L, UNITE, NatureEnum.RATION, SessionEnum.JOUR, "10001111111");
        ficheAvecLigne(910_002L, UNITE, NatureEnum.TRANSPORT, SessionEnum.JOUR, "10002222222");

        List<Long> resultat = repository.identifiantsProcessusAvecLigne(
                DEBUT_PERIODE, FIN_PERIODE, NatureEnum.RATION, null, null, null);

        assertThat(resultat).containsExactly(910_001L);
    }

    @Test
    @DisplayName("Filtre par numéro de compte exact : ne retrouve que le bénéficiaire visé")
    void filtreBeneficiaireExact_neRetrouveQueLeBonCompte() {
        ficheAvecLigne(910_003L, UNITE, NatureEnum.RATION, SessionEnum.JOUR, "10003333333");
        ficheAvecLigne(910_004L, UNITE, NatureEnum.RATION, SessionEnum.JOUR, "10004444444");

        List<Long> resultat = repository.identifiantsProcessusAvecLigne(
                DEBUT_PERIODE, FIN_PERIODE, null, null, "10003333333", null);

        assertThat(resultat).containsExactly(910_003L);
    }

    @Test
    @DisplayName("Portée locale : ne retrouve que les états des unités visibles")
    void porteeLocale_neRetrouveQueLesUnitesVisibles() {
        ficheAvecLigne(910_005L, UNITE, NatureEnum.RATION, SessionEnum.JOUR, "10005555555");
        ficheAvecLigne(910_006L, AUTRE_UNITE, NatureEnum.RATION, SessionEnum.JOUR, "10006666666");

        List<Long> resultat = repository.identifiantsProcessusAvecLigne(
                DEBUT_PERIODE, FIN_PERIODE, NatureEnum.RATION, null, null, Set.of(UNITE));

        assertThat(resultat).containsExactly(910_005L);
    }

    @Test
    @DisplayName("Portée vide (aucune unité) : ne rend jamais rien, sans erreur SQL")
    void porteeVide_neRendRien() {
        ficheAvecLigne(910_007L, UNITE, NatureEnum.RATION, SessionEnum.JOUR, "10007777777");

        List<Long> resultat = repository.identifiantsProcessusAvecLigne(
                DEBUT_PERIODE, FIN_PERIODE, NatureEnum.RATION, null, null, Set.of());

        assertThat(resultat).isEmpty();
    }

    @Test
    @DisplayName("Aucune ligne correspondante : liste vide, jamais une erreur")
    void aucuneLigneCorrespondante_listeVide() {
        ficheAvecLigne(910_008L, UNITE, NatureEnum.TRANSPORT, SessionEnum.SOIR, "10008888888");

        List<Long> resultat = repository.identifiantsProcessusAvecLigne(
                DEBUT_PERIODE, FIN_PERIODE, NatureEnum.RATION, null, null, null);

        assertThat(resultat).isEmpty();
    }

}
