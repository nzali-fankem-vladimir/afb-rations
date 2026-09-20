package cm.afrilandfirstbank.rations.grilles.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * grilles ACTIVE, une par couple nature/session.
 *
 * <p><b>Ce que ces tests n'affirment plus (correctif du Sprint 8.1).</b> Ils
 * decrivaient le jeu de donnees d'origine — « date_debut au premier jour du mois
 * courant, date_fin nulle » — et deux d'entre eux l'affirmaient. Or une grille
 * peut etre remplacee, et l'est : une bascule validee le 17 septembre 2026 a
 * ferme la grille RATION / JOUR au 30 septembre au profit d'une remplacante au
 * 1er octobre. Comportement <b>correct</b>, decide au Sprint 2.3 (fermeture
 * programmee) — mais les deux tests tombaient, sur un build qui n'avait rien
 * casse. C'est l'erreur que CLAUDE.md section 15 interdit : un test ne depend
 * pas de la valeur ambiante d'une donnee que l'exploitation fait legitimement
 * evoluer, il pose ou compare ce qu'il eprouve. Les tests 12 ter et 13 le
 * faisaient deja ; les tests 10 et 12 bis, plus anciens, ont ete alignes.
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

    /**
     * Grille applicable a une date, telle que la lit la resolution du montant.
     *
     * <p>Enveloppe {@code rechercherGrillesCouvrant} en verifiant au passage
     * l'invariant que la requete ne garantit pas a elle seule : <b>jamais plus
     * d'une grille ne couvre une date donnee</b>. C'est ce que le
     * {@code Optional} du Sprint 2.1 laissait croire sans jamais le verifier — il
     * ne distingue que present et absent, et aurait traduit un chevauchement en
     * exception technique opaque, ou pire, en silence.
     *
     * <p>Chaque test de date qui passe par ici verifie donc aussi, gratuitement,
     * que le partitionnement des periodes tient (Sprint 2.4).
     */
    private Optional<GrilleTarifaire> grilleActive(NatureEnum nature, SessionEnum session, LocalDate date) {
        List<GrilleTarifaire> couvrantes = repository.rechercherGrillesCouvrant(nature, session, date);

        assertThat(couvrantes)
                .as("deux grilles couvrant %s / %s au %s : les periodes se chevauchent", nature, session, date)
                .hasSizeLessThanOrEqualTo(1);

        return couvrantes.stream().findFirst();
    }

    @Test
    @DisplayName("10. la recherche de grille active retourne une grille pour RATION / JOUR aujourd'hui")
    void grilleActivePourRationJour() {
        LocalDate aujourdHui = LocalDate.now();

        Optional<GrilleTarifaire> trouvee =
                grilleActive(NatureEnum.RATION, SessionEnum.JOUR, aujourdHui);

        assertThat(trouvee).isPresent();
        assertThat(trouvee.get().getNature()).isEqualTo(NatureEnum.RATION);
        assertThat(trouvee.get().getSession()).isEqualTo(SessionEnum.JOUR);
        assertThat(trouvee.get().getStatutValidation()).isEqualTo(StatutGrilleEnum.ACTIVE);
        assertThat(trouvee.get().getMontantFcfa()).isPositive();

        // Ce que ce test doit verifier est que la grille rendue COUVRE la date
        // demandee, bornes incluses. Il affirmait auparavant que sa date_fin etait
        // nulle : c'etait affirmer qu'aucune remplacante n'a jamais ete programmee
        // sur ce couple, vrai du seul jeu de donnees d'origine. Depuis le Sprint
        // 2.3, une grille parfaitement applicable aujourd'hui peut porter une
        // fermeture programmee, et le test 13 la produit lui-meme.
        assertThat(trouvee.get().getDateDebut()).isBeforeOrEqualTo(aujourdHui);
        assertThat(trouvee.get().getDateFin())
                .as("une grille rendue pour aujourd'hui est soit sans fin, soit close apres aujourd'hui")
                .satisfiesAnyOf(
                        dateFin -> assertThat(dateFin).isNull(),
                        dateFin -> assertThat(dateFin).isAfterOrEqualTo(aujourdHui));
    }

    @Test
    @DisplayName("11. la recherche a une date anterieure a date_debut ne retourne rien")
    void aucuneGrilleAvantDateDebut() {
        LocalDate avantToutHistorique = LocalDate.of(2000, 1, 1);

        Optional<GrilleTarifaire> trouvee = grilleActive(
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

        GrilleTarifaire enVigueurAvant =
                grilleActive(NatureEnum.RATION, SessionEnum.JOUR, aujourdHui).orElseThrow();
        Long idAvant = enVigueurAvant.getId();
        Integer montantAvant = enVigueurAvant.getMontantFcfa();

        // Releve AVANT, pour comparer apres. La grille courante n'est pas
        // necessairement celle qui s'applique aujourd'hui : elles divergent des la
        // premiere bascule (meme distinction qu'au test 13). Comparer avant et
        // apres eprouve ce que la proposition change, sans rien presumer de l'etat
        // de depart.
        Long idCouranteAvant = repository
                .rechercherGrilleCourante(NatureEnum.RATION, SessionEnum.JOUR)
                .orElseThrow()
                .getId();

        // L'ARH propose un nouveau tarif : nouvelle ligne, statut EN_ATTENTE_DRH
        // (decisions Sprint 2.2, etapes 1 et 4).
        GrilleTarifaire proposition = new GrilleTarifaire(NatureEnum.RATION, SessionEnum.JOUR,
                999_999, priseEffetFuture, 4L, "NKOLO Claire");
        TransitionGrille.soumettre(proposition);
        repository.saveAndFlush(proposition);

        // Aujourd'hui : la grille en vigueur est inchangee.
        Optional<GrilleTarifaire> aujourdHuiApres =
                grilleActive(NatureEnum.RATION, SessionEnum.JOUR, aujourdHui);
        assertThat(aujourdHuiApres).isPresent();
        assertThat(aujourdHuiApres.get().getId()).isEqualTo(idAvant);
        assertThat(aujourdHuiApres.get().getMontantFcfa()).isEqualTo(montantAvant);

        // A la date de prise d'effet demandee : jamais la proposition. Tant que la
        // DRH n'a pas tranche, elle n'existe pas pour les saisies, meme apres sa
        // propre date de debut.
        //
        // Le test exigeait ici l'ancienne grille elle-meme. C'etait supposer
        // qu'aucune autre grille ne prend legitimement effet dans le mois qui
        // vient, ce qui est faux des qu'une bascule est programmee (Sprint 2.3) et
        // etranger a ce que CT-25 verifie. La formulation juste est negative : le
        // montant applique ne vient pas d'une grille non validee.
        Optional<GrilleTarifaire> aLaDateDemandee =
                grilleActive(NatureEnum.RATION, SessionEnum.JOUR, priseEffetFuture);
        assertThat(aLaDateDemandee).isPresent();
        assertThat(aLaDateDemandee.get().getId())
                .as("la proposition en attente ne doit jamais etre rendue comme grille applicable")
                .isNotEqualTo(proposition.getId());
        assertThat(aLaDateDemandee.get().getMontantFcfa())
                .as("le montant applique ne doit jamais venir d'une grille non validee")
                .isNotEqualTo(999_999);
        assertThat(aLaDateDemandee.get().getStatutValidation()).isEqualTo(StatutGrilleEnum.ACTIVE);

        // La grille courante est inchangee : c'est ce que lit le controle
        // d'unicite. Comparee a son releve d'avant, non a la grille du jour.
        assertThat(repository.rechercherGrilleCourante(NatureEnum.RATION, SessionEnum.JOUR))
                .isPresent()
                .get()
                .extracting(GrilleTarifaire::getId)
                .isEqualTo(idCouranteAvant);

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

    /**
     * Verification de bout en bout du Sprint 2.3, exigee par le guide : apres une
     * bascule, la recherche de grille active du Sprint 2.1 retourne <b>la nouvelle
     * grille, et une seule</b>.
     *
     * <p>Ce test tourne contre PostgreSQL, seul endroit ou l'index partiel
     * {@code ux_grille_active_par_couple} existe reellement. Les tests unitaires
     * de {@code DecisionGrilleServiceTest} prouvent que le service ecrit les deux
     * lignes dans le bon ordre ; celui-ci prouve que la base l'accepte, ce
     * qu'aucun mock ne peut etablir.
     *
     * <p>Il verifie aussi le bornage retenu au sous-sprint : l'ancienne grille est
     * fermee <b>a la veille</b>, et les deux periodes s'enchainent sans trou. La
     * derniere assertion est celle qui compte pour le Sprint 6bis — une
     * prestation datee du dernier jour de l'ancienne periode trouve encore son
     * montant d'epoque, et pas celui du nouveau tarif.
     */
    @Test
    @DisplayName("13. apres bascule : une seule grille active, la nouvelle, et aucun trou de periode")
    void basculeLaisseUneSeuleGrilleActive() {
        // On part de la grille COURANTE, comme le fait le service de decision, et
        // non de celle qui s'applique aujourd'hui. Les deux coincident tant que le
        // couple n'a qu'une grille, et divergent des la premiere bascule : la
        // grille du jour peut etre une grille deja close, que fermer une seconde
        // fois laisserait deux lignes sans date de fin.
        GrilleTarifaire ancienne = repository
                .rechercherGrilleCourante(NatureEnum.TRANSPORT, SessionEnum.SOIR)
                .orElseThrow();

        // La prise d'effet est calculee depuis la grille courante, pas depuis la
        // date du jour : elle doit lui etre strictement posterieure, quel que soit
        // l'historique deja accumule sur le couple.
        LocalDate priseEffet = ancienne.getDateDebut().plusMonths(1);
        LocalDate veille = priseEffet.minusDays(1);

        Integer montantAncien = ancienne.getMontantFcfa();
        int montantNouveau = montantAncien + 500;

        GrilleTarifaire remplacante = new GrilleTarifaire(NatureEnum.TRANSPORT, SessionEnum.SOIR,
                montantNouveau, priseEffet, 4L, "NKOLO Claire");
        TransitionGrille.soumettre(remplacante);
        repository.saveAndFlush(remplacante);

        // La bascule, dans l'ordre du service : fermeture puis activation. Si
        // l'ordre etait inverse, l'index partiel refuserait la premiere ecriture.
        TransitionGrille.fermer(ancienne, veille);
        repository.saveAndFlush(ancienne);
        TransitionGrille.valider(remplacante, 7L, LocalDateTime.now(), "TCHINDA Agnes");
        repository.saveAndFlush(remplacante);

        // Une seule grille COURANTE sur le couple : c'est l'invariant de RG-14,
        // et l'index partiel vient de l'accepter sans broncher.
        Optional<GrilleTarifaire> courante =
                repository.rechercherGrilleCourante(NatureEnum.TRANSPORT, SessionEnum.SOIR);
        assertThat(courante).isPresent();
        assertThat(courante.get().getId()).isEqualTo(remplacante.getId());

        // A la date de prise d'effet : la nouvelle grille, et son montant.
        Optional<GrilleTarifaire> aLaPriseEffet =
                grilleActive(NatureEnum.TRANSPORT, SessionEnum.SOIR, priseEffet);
        assertThat(aLaPriseEffet).isPresent();
        assertThat(aLaPriseEffet.get().getId()).isEqualTo(remplacante.getId());
        assertThat(aLaPriseEffet.get().getMontantFcfa()).isEqualTo(montantNouveau);

        // La veille : l'ancienne grille, encore. Aucun trou entre les deux
        // periodes, aucun chevauchement — c'est ce dont depend la resolution du
        // montant a une date passee.
        Optional<GrilleTarifaire> laVeille =
                grilleActive(NatureEnum.TRANSPORT, SessionEnum.SOIR, veille);
        assertThat(laVeille).isPresent();
        assertThat(laVeille.get().getId()).isEqualTo(ancienne.getId());
        assertThat(laVeille.get().getMontantFcfa()).isEqualTo(montantAncien);

        // L'ancienne reste ACTIVE : fermer n'est pas rejeter, c'est poser une
        // borne. Son statut porte encore la decision qui l'avait rendue applicable.
        assertThat(ancienne.getStatutValidation()).isEqualTo(StatutGrilleEnum.ACTIVE);
        assertThat(ancienne.getDateFin()).isEqualTo(veille);
    }

}
