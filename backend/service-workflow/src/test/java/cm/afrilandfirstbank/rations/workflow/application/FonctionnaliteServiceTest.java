package cm.afrilandfirstbank.rations.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import cm.afrilandfirstbank.rations.workflow.domaine.exception.DelaiRegularisationIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ParametreSystemeRepository;

import jakarta.persistence.EntityManager;

/**
 * Lecture du drapeau de fonctionnalite et du delai de regularisation (Sprint 6bis.1,
 * {@code docs/dispositifs_provisoires.md} section 1).
 *
 * <h2>Contre la vraie base, et c'est indispensable ici</h2>
 *
 * <p>Ce service ne fait qu'une chose : lire {@code parametre_systeme}. Un test a base
 * de mocks prouverait que le service rend ce qu'on lui dit de rendre, c'est-a-dire
 * rien sur le risque reel — qui est que la <b>ligne posee par la migration V2</b> ne
 * soit pas celle qu'on croit. Les valeurs de depart lues ici sont donc celles que V2
 * a reellement inscrites.
 *
 * <p>{@code @DataJpaTest} est transactionnel et annule tout a la fin de chaque test :
 * les modifications de parametre faites ci-dessous ne survivent pas au test qui les
 * fait, et n'affectent aucune autre suite.
 *
 * <h2>Deux comportements opposes, une seule regle</h2>
 *
 * <p>Le drapeau absent rend {@code false} sans lever ; le delai absent <b>leve</b>.
 * Ce n'est pas une incoherence : dans les deux cas on choisit l'issue qui ne peut
 * rien casser. Fermer une fonctionnalite est toujours sur ; deviner un delai ne
 * l'est jamais.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("FonctionnaliteService — drapeau de rattrapage et delai de regularisation")
class FonctionnaliteServiceTest {

    @Autowired
    private ParametreSystemeRepository parametreSystemeRepository;

    @Autowired
    private EntityManager entityManager;

    private FonctionnaliteService fonctionnaliteService;

    @BeforeEach
    void preparer() {
        // @DataJpaTest ne charge pas les beans @Service : le service est instancie a la
        // main, avec son vrai repository derriere.
        fonctionnaliteService = new FonctionnaliteService(parametreSystemeRepository);
    }

    // =====================================================================
    // Le drapeau RATTRAPAGE_ACTIF
    // =====================================================================

    @Nested
    @DisplayName("Drapeau de rattrapage")
    class Drapeau {

        /**
         * <b>Le test le plus important du fichier.</b> Il lit la valeur que la migration
         * V2 a reellement posee, sans rien modifier : la fonctionnalite est fermee par
         * defaut, et c'est ce qui protege le module tant que RG-15 n'existe pas.
         */
        @Test
        @DisplayName("1. Valeur de la migration V2 : la fonctionnalite est FERMEE")
        void fermeParDefaut() {
            assertThat(fonctionnaliteService.rattrapageActif())
                    .as("RATTRAPAGE_ACTIF vaut false depuis la migration V2 : "
                            + "la regularisation ne doit pas s'ouvrir toute seule")
                    .isFalse();
        }

        @Test
        @DisplayName("2. Valeur 'true' : la fonctionnalite est ouverte")
        void ouvertQuandVraie() {
            fixerDrapeau("true");

            assertThat(fonctionnaliteService.rattrapageActif()).isTrue();
        }

        /**
         * Le cas que le guide 6bis.1 fait trancher a l'etape 2, et que les points de
         * vigilance redisent : <b>une absence ne s'interprete jamais en faveur de
         * l'execution</b>.
         *
         * <p>Aucune exception n'est levee pour autant : « ferme » est une reponse
         * normale, pas une panne. Le service journalise en revanche un {@code WARN}
         * distinct, parce qu'une ligne disparue de la base eteindrait la fonctionnalite
         * pour toujours — le jour de la confirmation metier, l'{@code UPDATE} prevu
         * n'ouvrirait rien du tout.
         */
        @Test
        @DisplayName("3. Parametre absent de la base : FERME par defaut, sans lever")
        void fermeQuandParametreAbsent() {
            supprimerDrapeau();

            assertThat(fonctionnaliteService.rattrapageActif())
                    .as("une absence ne doit jamais ouvrir une fonctionnalite sensible")
                    .isFalse();
        }

        /**
         * Desactiver la ligne ({@code actif = false}) est l'autre facon de faire
         * disparaitre un parametre. Elle ferme aussi, et pour la meme raison.
         */
        @Test
        @DisplayName("4. Parametre desactive : FERME, meme si la valeur dit 'true'")
        void fermeQuandParametreDesactive() {
            fixerDrapeau("true");
            desactiverDrapeau();

            assertThat(fonctionnaliteService.rattrapageActif())
                    .as("actif = false retire le reglage, quelle que soit sa valeur")
                    .isFalse();
        }

        /**
         * Toute valeur qui n'est pas un {@code 'true'} franc ferme. On ne devine pas
         * l'intention derriere {@code 'oui'}, {@code '1'} ou {@code 'vrai'} : une
         * fonctionnalite qui touche au paiement ne s'ouvre pas sur une interpretation.
         */
        @Test
        @DisplayName("5. Valeur illisible : FERME, jamais une interpretation genereuse")
        void fermeQuandValeurIllisible() {
            for (String valeur : new String[] {"oui", "1", "VRAI", "", "   ", "tru"}) {
                fixerDrapeau(valeur);

                assertThat(fonctionnaliteService.rattrapageActif())
                        .as("la valeur « %s » ne doit pas ouvrir la regularisation", valeur)
                        .isFalse();
            }
        }

        /**
         * La casse est en revanche toleree : {@code 'TRUE'} est sans ambiguite la meme
         * intention que {@code 'true'}. Refuser sur une majuscule serait un piege sans
         * aucun gain de securite — la valeur est ecrite a la main par un administrateur.
         */
        @Test
        @DisplayName("6. La casse ne compte pas : 'TRUE' ouvre comme 'true'")
        void casseToleree() {
            fixerDrapeau("TRUE");

            assertThat(fonctionnaliteService.rattrapageActif()).isTrue();
        }

        /** Les espaces autour de la valeur sont ebarbes, comme pour le seuil (4.3). */
        @Test
        @DisplayName("7. Les espaces autour de la valeur sont ignores")
        void espacesEbarbes() {
            fixerDrapeau("  true  ");

            assertThat(fonctionnaliteService.rattrapageActif()).isTrue();
        }
    }

    // =====================================================================
    // Le delai DELAI_REGULARISATION_JOURS
    // =====================================================================

    @Nested
    @DisplayName("Delai de regularisation")
    class Delai {

        @Test
        @DisplayName("8. Valeur de la migration V2 : 90 jours, marques provisoires")
        void valeurDeLaMigration() {
            assertThat(fonctionnaliteService.delaiRegularisationJours()).isEqualTo(90L);

            // Le libelle porte la mention qui rend la valeur reperable par une simple
            // requete avant mise en production (dispositifs_provisoires.md section 1.6).
            assertThat(parametreSystemeRepository
                    .findByCodeAndActifTrue(FonctionnaliteService.CODE_DELAI_REGULARISATION_JOURS)
                    .orElseThrow().getLibelle())
                    .containsIgnoringCase("PROVISOIRE");
        }

        /**
         * <b>Ici, contrairement au drapeau, l'absence leve.</b> Aucune valeur par defaut
         * n'est sure : un delai devine trop long ouvrirait des regularisations sur des
         * periodes que le metier voulait figees, un delai trop court les refuserait
         * toutes. Meme parti qu'au Sprint 4.3 pour le seuil d'aiguillage.
         */
        @Test
        @DisplayName("9. Parametre absent : refus, jamais un delai suppose")
        void absenceRefusee() {
            supprimerDelai();

            assertThatThrownBy(() -> fonctionnaliteService.delaiRegularisationJours())
                    .isInstanceOf(DelaiRegularisationIndisponibleException.class)
                    .hasMessageContaining(FonctionnaliteService.CODE_DELAI_REGULARISATION_JOURS)
                    .hasMessageContaining("refusee");
        }

        @Test
        @DisplayName("10. Parametre desactive : meme refus")
        void desactivationRefusee() {
            desactiverDelai();

            assertThatThrownBy(() -> fonctionnaliteService.delaiRegularisationJours())
                    .isInstanceOf(DelaiRegularisationIndisponibleException.class);
        }

        /**
         * Un seul chemin d'echec pour toutes les valeurs inexploitables, comme
         * {@code SeuilService} : la meme capture couvre le texte, la chaine vide, le
         * separateur de milliers, la decimale et le depassement de capacite.
         */
        @Test
        @DisplayName("11. Valeur illisible : refus, quel que soit le defaut")
        void valeurIllisibleRefusee() {
            for (String valeur : new String[] {
                    "quatre-vingt-dix", "", "   ", "90 jours", "9 0", "90.5",
                    "99999999999999999999"}) {

                fixerDelai(valeur);

                assertThatThrownBy(() -> fonctionnaliteService.delaiRegularisationJours())
                        .as("la valeur « %s » n'est pas un nombre entier de jours", valeur)
                        .isInstanceOf(DelaiRegularisationIndisponibleException.class);
            }
        }

        /**
         * Un delai negatif refuserait <b>toute</b> regularisation sans qu'aucune erreur
         * ne le signale — exactement comme un seuil negatif ferait monter tout au
         * directeur reseau (Sprint 4.3). Il est donc refuse explicitement.
         */
        @Test
        @DisplayName("12. Delai negatif : refuse, il fermerait tout en silence")
        void delaiNegatifRefuse() {
            fixerDelai("-1");

            assertThatThrownBy(() -> fonctionnaliteService.delaiRegularisationJours())
                    .isInstanceOf(DelaiRegularisationIndisponibleException.class)
                    .hasMessageContaining("negatif");
        }

        /**
         * Zero est en revanche <b>accepte</b> : c'est une configuration intelligible —
         * seule une periode close le jour meme reste regularisable. Meme distinction
         * qu'au Sprint 4.3, ou un seuil de zero est accepte et un seuil negatif refuse.
         */
        @Test
        @DisplayName("13. Delai de zero : accepte, c'est une configuration intelligible")
        void delaiZeroAccepte() {
            fixerDelai("0");

            assertThat(fonctionnaliteService.delaiRegularisationJours()).isZero();
        }

        @Test
        @DisplayName("14. Les espaces autour de la valeur sont ignores")
        void espacesEbarbes() {
            fixerDelai("  120  ");

            assertThat(fonctionnaliteService.delaiRegularisationJours()).isEqualTo(120L);
        }
    }

    // =====================================================================
    // Outillage
    // =====================================================================

    private void fixerDrapeau(String valeur) {
        fixerValeur(FonctionnaliteService.CODE_RATTRAPAGE_ACTIF, valeur);
    }

    private void fixerDelai(String valeur) {
        fixerValeur(FonctionnaliteService.CODE_DELAI_REGULARISATION_JOURS, valeur);
    }

    private void fixerValeur(String code, String valeur) {
        entityManager
                .createQuery("update ParametreSysteme p set p.valeur = :valeur where p.code = :code")
                .setParameter("valeur", valeur)
                .setParameter("code", code)
                .executeUpdate();
        vider();
    }

    private void desactiverDrapeau() {
        desactiver(FonctionnaliteService.CODE_RATTRAPAGE_ACTIF);
    }

    private void desactiverDelai() {
        desactiver(FonctionnaliteService.CODE_DELAI_REGULARISATION_JOURS);
    }

    private void desactiver(String code) {
        entityManager
                .createQuery("update ParametreSysteme p set p.actif = false where p.code = :code")
                .setParameter("code", code)
                .executeUpdate();
        vider();
    }

    private void supprimerDrapeau() {
        supprimer(FonctionnaliteService.CODE_RATTRAPAGE_ACTIF);
    }

    private void supprimerDelai() {
        supprimer(FonctionnaliteService.CODE_DELAI_REGULARISATION_JOURS);
    }

    /**
     * Supprime la ligne pour de bon — pas seulement sa valeur. C'est le seul moyen
     * d'eprouver le cas « la ligne a disparu de la base », qui est distinct de « la
     * ligne est la mais desactivee ». La transaction de test l'annule ensuite.
     */
    private void supprimer(String code) {
        entityManager
                .createQuery("delete from ParametreSysteme p where p.code = :code")
                .setParameter("code", code)
                .executeUpdate();
        vider();
    }

    private void vider() {
        entityManager.flush();
        entityManager.clear();
    }

}
