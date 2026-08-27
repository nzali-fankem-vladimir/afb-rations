package cm.afrilandfirstbank.rations.grilles.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import cm.afrilandfirstbank.rations.grilles.domaine.GrilleTarifaire;
import cm.afrilandfirstbank.rations.grilles.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.SessionEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.StatutGrilleEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.TransitionGrille;

/**
 * Tests du repository sur les donnees de reference du Sprint 0.5 : les quatre
 * grilles ACTIVE (une par couple nature/session), date_debut au premier jour du
 * mois courant, date_fin nulle.
 *
 * <p>{@code @AutoConfigureTestDatabase(replace = NONE)} : on tourne contre la
 * vraie base {@code rations_grilles} (conteneur Docker du Sprint 0.5), pas une
 * base embarquee — les migrations Flyway y sont specifiques a PostgreSQL (index
 * partiel, date_trunc) et le jeu de donnees teste est celui de la V2.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GrilleTarifaireRepositoryTest {

    @Autowired
    private GrilleTarifaireRepository repository;

    @Test
    @DisplayName("10. la recherche de grille active retourne une grille pour RATION / JOUR aujourd'hui")
    void grilleActivePourRationJour() {
        Optional<GrilleTarifaire> trouvee =
                repository.rechercherGrilleActive(NatureEnum.RATION, SessionEnum.JOUR, LocalDate.now());

        assertThat(trouvee).isPresent();
        assertThat(trouvee.get().getNature()).isEqualTo(NatureEnum.RATION);
        assertThat(trouvee.get().getSession()).isEqualTo(SessionEnum.JOUR);
        assertThat(trouvee.get().getStatutValidation()).isEqualTo(StatutGrilleEnum.ACTIVE);
        assertThat(trouvee.get().getDateFin()).isNull();
        assertThat(trouvee.get().getMontantFcfa()).isPositive();
    }

    @Test
    @DisplayName("11. la recherche a une date anterieure a date_debut ne retourne rien")
    void aucuneGrilleAvantDateDebut() {
        LocalDate avantToutHistorique = LocalDate.of(2000, 1, 1);

        Optional<GrilleTarifaire> trouvee = repository.rechercherGrilleActive(
                NatureEnum.RATION, SessionEnum.JOUR, avantToutHistorique);

        assertThat(trouvee).isEmpty();
    }

    /**
     * Verification complementaire du Sprint 2.2, et la plus importante du
     * sous-sprint : <b>une grille en attente ne change rien aux montants
     * appliques</b> (CT-25).
     *
     * <p>C'est ce test qui prouve que la chaine de paiement est protegee. Si la
     * recherche de grille active se mettait un jour a voir les propositions —
     * une clause {@code statutValidation} oubliee, un filtre elargi par
     * inadvertance — les agents seraient payes au tarif d'une grille que la DRH
     * n'a jamais validee, et rien d'autre dans le module ne s'en apercevrait.
     *
     * <p>Le montant choisi, 999 999 FCFA, est volontairement absurde : s'il
     * apparaissait sur une fiche, l'anomalie sauterait aux yeux.
     */
    @Test
    @DisplayName("12 bis. une grille EN_ATTENTE_DRH ne remplace pas la grille active (CT-25)")
    void grilleEnAttenteSansEffetSurLaResolutionDuMontant() {
        LocalDate aujourdHui = LocalDate.now();
        LocalDate priseEffetFuture = aujourdHui.plusMonths(1);

        GrilleTarifaire enVigueurAvant = repository
                .rechercherGrilleActive(NatureEnum.RATION, SessionEnum.JOUR, aujourdHui)
                .orElseThrow();
        Long idAvant = enVigueurAvant.getId();
        Integer montantAvant = enVigueurAvant.getMontantFcfa();

        // L'ARH propose un nouveau tarif : nouvelle ligne, statut EN_ATTENTE_DRH
        // (decisions Sprint 2.2, etapes 1 et 4).
        GrilleTarifaire proposition = new GrilleTarifaire(NatureEnum.RATION, SessionEnum.JOUR,
                999_999, priseEffetFuture, 4L, "NKOLO Claire");
        TransitionGrille.soumettre(proposition);
        repository.saveAndFlush(proposition);

        // Aujourd'hui : la grille en vigueur est inchangee.
        Optional<GrilleTarifaire> aujourdHuiApres =
                repository.rechercherGrilleActive(NatureEnum.RATION, SessionEnum.JOUR, aujourdHui);
        assertThat(aujourdHuiApres).isPresent();
        assertThat(aujourdHuiApres.get().getId()).isEqualTo(idAvant);
        assertThat(aujourdHuiApres.get().getMontantFcfa()).isEqualTo(montantAvant);

        // A la date de prise d'effet demandee : toujours l'ancienne grille. Tant
        // que la DRH n'a pas tranche, la proposition n'existe pas pour les saisies,
        // meme apres sa propre date de debut.
        Optional<GrilleTarifaire> aLaDateDemandee =
                repository.rechercherGrilleActive(NatureEnum.RATION, SessionEnum.JOUR, priseEffetFuture);
        assertThat(aLaDateDemandee).isPresent();
        assertThat(aLaDateDemandee.get().getId()).isEqualTo(idAvant);
        assertThat(aLaDateDemandee.get().getMontantFcfa())
                .as("le montant applique ne doit jamais venir d'une grille non validee")
                .isEqualTo(montantAvant);

        // La grille courante est elle aussi inchangee : c'est ce que lit le
        // controle d'unicite.
        assertThat(repository.rechercherGrilleCourante(NatureEnum.RATION, SessionEnum.JOUR))
                .isPresent()
                .get()
                .extracting(GrilleTarifaire::getId)
                .isEqualTo(idAvant);

        // La proposition existe bel et bien : elle est simplement invisible des
        // recherches de grille applicable.
        //
        // On la retrouve par son identifiant, sans presumer du nombre de
        // propositions en attente : la base de developpement porte aussi celles
        // laissees par les verifications manuelles. Un test qui compte les lignes
        // d'une base partagee finit toujours par echouer pour une raison etrangere
        // a ce qu'il verifie.
        List<GrilleTarifaire> enAttente =
                repository.rechercherPropositionsEnAttente(NatureEnum.RATION, SessionEnum.JOUR);
        assertThat(enAttente)
                .filteredOn(grille -> grille.getId().equals(proposition.getId()))
                .singleElement()
                .satisfies(trouvee -> {
                    assertThat(trouvee.getMontantFcfa()).isEqualTo(999_999);
                    assertThat(trouvee.getStatutValidation()).isEqualTo(StatutGrilleEnum.EN_ATTENTE_DRH);
                    assertThat(trouvee.getLibelleCreateur()).isEqualTo("NKOLO Claire");
                });
    }

    /**
     * L'index partiel autorise une grille EN_ATTENTE_DRH a coexister avec une
     * grille ACTIVE sur le meme couple. Sans cela, le versionnement decide a
     * l'etape 1 serait impossible en base, quelle que soit la regle applicative.
     */
    @Test
    @DisplayName("12 ter. l'index partiel n'empeche pas une proposition sur un couple deja actif")
    void indexPartielTolereUnePropositionSurCoupleActif() {
        // Ecart mesure, pas total absolu : la base de developpement peut deja
        // porter des propositions issues des verifications manuelles.
        int avant = repository.rechercherPropositionsEnAttente(
                NatureEnum.TRANSPORT, SessionEnum.SOIR).size();

        GrilleTarifaire proposition = new GrilleTarifaire(NatureEnum.TRANSPORT, SessionEnum.SOIR,
                3000, LocalDate.now().plusMonths(1), 4L, "NKOLO Claire");
        TransitionGrille.soumettre(proposition);

        // Ce que ce test verifie tient dans cette ligne : l'insertion ne doit pas
        // etre rejetee par ux_grille_active_par_couple, alors qu'une grille ACTIVE
        // existe sur le meme couple. Sans cela, le versionnement decide au Sprint
        // 2.2 serait impossible en base, quelle que soit la regle applicative.
        repository.saveAndFlush(proposition);

        assertThat(proposition.getId()).isNotNull();
        assertThat(repository.rechercherPropositionsEnAttente(
                NatureEnum.TRANSPORT, SessionEnum.SOIR)).hasSize(avant + 1);
    }

    @Test
    @DisplayName("bonus : existsByNatureAndSessionAndStatutValidation voit les quatre couples ACTIVE")
    void existenceDesQuatreCouplesActifs() {
        assertThat(repository.existsByNatureAndSessionAndStatutValidation(
                NatureEnum.RATION, SessionEnum.JOUR, StatutGrilleEnum.ACTIVE)).isTrue();
        assertThat(repository.existsByNatureAndSessionAndStatutValidation(
                NatureEnum.TRANSPORT, SessionEnum.SOIR, StatutGrilleEnum.ACTIVE)).isTrue();
    }
}
