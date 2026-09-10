package cm.afrilandfirstbank.rations.workflow.application;

import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.util.ReflectionTestUtils;

import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.TransitionProcessus;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.SeuilIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ParametreSystemeRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * RG-08 eprouvee a la borne exacte (Sprint 4.3, US-09, CT-14, CT-15, CT-18).
 *
 * <h2>Aucune valeur de seuil n'est ecrite dans ce fichier</h2>
 *
 * <p>C'est la propriete la plus importante de ces tests, et elle est verifiee par
 * un test dedie, {@code aucuneValeurDeSeuilEnDur}. Tous les montants d'essai sont
 * calcules <b>a partir</b> du seuil lu dans {@code parametre_systeme} :
 * {@code seuil - 1}, {@code seuil}, {@code seuil + 1}. Un test qui ecrirait
 * la valeur configuree continuerait de passer apres une modification du parametre,
 * et masquerait donc exactement la regression que CT-18 doit reveler. Le present
 * fichier ne l'ecrit donc nulle part, pas meme dans un commentaire — c'est ce que
 * son propre test de garde a d'abord releve.
 *
 * <h2>Contre la vraie base</h2>
 *
 * <p>{@link SeuilService} est reel, adosse au vrai {@code parametre_systeme}
 * alimente par la migration V2. Simuler la lecture du parametre reviendrait a
 * eprouver que le service appelle un bouchon, pas qu'il lit la configuration. Les
 * modifications de parametre faites ici sont annulees par le rollback de
 * {@code @DataJpaTest} : la ligne d'origine est intacte a la fin de chaque test.
 *
 * <h2>Aucun processus n'est persiste</h2>
 *
 * <p>L'aiguillage ne lit qu'un montant : les processus d'essai sont construits en
 * memoire par {@link TransitionProcessus#declencher}. C'est precisement ce que la
 * separation du Sprint 4.3 permet — eprouver RG-08 sur des montants precis sans
 * monter un dossier complet.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("AiguillageService — RG-08, le seuil et sa borne")
class AiguillageServiceTest {

    private static final String UNITE = "00002";
    private static final int ANNEE = 2099;

    @Autowired
    private ParametreSystemeRepository parametreSystemeRepository;

    @Autowired
    private EntityManager entityManager;

    private SeuilService seuilService;
    private AiguillageService aiguillageService;

    @BeforeEach
    void preparer() {
        seuilService = new SeuilService(parametreSystemeRepository);
        aiguillageService = new AiguillageService(seuilService);
    }

    // =====================================================================
    // Aiguillage : les cinq cas du guide, et la borne
    // =====================================================================

    @Nested
    @DisplayName("Comparaison au seuil")
    class Comparaison {

        @Test
        @DisplayName("1. Montant tres inferieur au seuil : cloture directe")
        void tresInferieurAuSeuil() {
            long seuil = seuilLu();

            ResultatAiguillage resultat = aiguiller(seuil / 4);

            assertThat(resultat.decision())
                    .isEqualTo(DecisionAiguillage.SOUS_SEUIL_CLOTURE_DIRECTE);
            assertThat(resultat.estClotureDirecte()).isTrue();
            assertThat(resultat.seuilApplique()).isEqualTo(seuil);
        }

        @Test
        @DisplayName("2. Montant egal au seuil moins un franc : cloture directe")
        void seuilMoinsUn() {
            long seuil = seuilLu();

            assertThat(aiguiller(seuil - 1).decision())
                    .isEqualTo(DecisionAiguillage.SOUS_SEUIL_CLOTURE_DIRECTE);
        }

        /**
         * Le premier des deux tests qui verrouillent la borne.
         *
         * <p>RG-08 dit « au plus le seuil » : un etat de la valeur exacte du seuil se
         * clot. Si cette assertion tombe, c'est qu'une comparaison stricte a remplace
         * la comparaison large, et un dossier qui aurait du etre clos par le chef
         * d'unite remonte au directeur reseau — un niveau d'approbation de plus que
         * ce que la banque a decide.
         */
        @Test
        @DisplayName("3. Montant EXACTEMENT egal au seuil : cloture directe (borne)")
        void seuilExactement() {
            long seuil = seuilLu();

            ResultatAiguillage resultat = aiguiller(seuil);

            assertThat(resultat.decision())
                    .as("RG-08 : au plus le seuil, donc cloture directe a la valeur exacte")
                    .isEqualTo(DecisionAiguillage.SOUS_SEUIL_CLOTURE_DIRECTE);
            assertThat(resultat.montantTotalFcfa()).isEqualTo(seuil);
        }

        /**
         * Le second garde-fou de la borne.
         *
         * <p>Un franc au-dessus du seuil suffit a exiger l'approbation du directeur
         * reseau. Si cette assertion tombe, un etat au-dela du seuil serait cloture
         * par le seul chef d'unite : la banque serait engagee sans le niveau
         * d'approbation requis, et rien dans le dossier ne le signalerait.
         */
        @Test
        @DisplayName("4. Montant egal au seuil plus un franc : transfert au DR (borne)")
        void seuilPlusUn() {
            long seuil = seuilLu();

            ResultatAiguillage resultat = aiguiller(seuil + 1);

            assertThat(resultat.decision())
                    .as("RG-08 : strictement au-dela du seuil, donc directeur reseau")
                    .isEqualTo(DecisionAiguillage.ENVOI_DIRECTEUR_RESEAU);
            assertThat(resultat.estClotureDirecte()).isFalse();
        }

        @Test
        @DisplayName("5. Montant tres superieur au seuil : transfert au DR")
        void tresSuperieurAuSeuil() {
            long seuil = seuilLu();

            assertThat(aiguiller(seuil * 3).decision())
                    .isEqualTo(DecisionAiguillage.ENVOI_DIRECTEUR_RESEAU);
        }

        /**
         * La borne prouvee par balayage, et pas seulement par trois points.
         *
         * <p>Les tests 3 et 4 verifient deux montants. Celui-ci verifie qu'il n'existe
         * <b>qu'un seul</b> point de bascule sur toute une plage encadrant le seuil,
         * et qu'il tombe exactement entre {@code seuil} et {@code seuil + 1}.
         *
         * <p>La difference compte : une inversion de l'operateur, un decalage d'une
         * unite ou une comparaison retournee deplacent ce point et font tomber ce test
         * — <b>meme si quelqu'un avait « ajuste » les cas 3 et 4 en meme temps</b> pour
         * les faire repasser. C'est ce qui distingue un test qui constate d'un test qui
         * verrouille.
         */
        @Test
        @DisplayName("6. Une seule bascule sur la plage, exactement entre seuil et seuil+1")
        void uneSeuleBasculeALaBorne() {
            long seuil = seuilLu();

            List<Long> basculesObservees = new ArrayList<>();
            DecisionAiguillage precedente = null;

            for (long montant = seuil - 5; montant <= seuil + 5; montant++) {
                DecisionAiguillage decision = aiguiller(montant).decision();
                if (precedente != null && decision != precedente) {
                    basculesObservees.add(montant);
                }
                precedente = decision;
            }

            assertThat(basculesObservees)
                    .as("La decision doit changer une fois et une seule, au franc au-dessus "
                            + "du seuil (RG-08)")
                    .containsExactly(seuil + 1);
        }
    }

    // =====================================================================
    // Le parametre : CT-18 et les configurations refusees
    // =====================================================================

    @Nested
    @DisplayName("Lecture du parametre SEUIL_AIGUILLAGE_DR")
    class Parametre {

        /**
         * CT-18. Le seuil est relu a chaque aiguillage : une modification en base prend
         * effet immediatement, sans redemarrage ni delai d'expiration de cache.
         *
         * <p>Le montant d'essai est choisi <b>sous l'ancien seuil et au-dessus du
         * nouveau</b> : c'est le seul montant qui distingue reellement les deux
         * configurations. Un montant quelconque passerait meme si le seuil avait ete
         * mis en cache.
         */
        @Test
        @DisplayName("7. Seuil modifie en base : l'aiguillage suit la nouvelle valeur (CT-18)")
        void seuilModifieEnBase() {
            long seuilInitial = seuilLu();
            long montant = seuilInitial - 1;

            assertThat(aiguiller(montant).decision())
                    .as("Avant modification, ce montant est sous le seuil")
                    .isEqualTo(DecisionAiguillage.SOUS_SEUIL_CLOTURE_DIRECTE);

            long nouveauSeuil = montant - 1;
            fixerValeurDuSeuil(String.valueOf(nouveauSeuil));

            ResultatAiguillage apres = aiguiller(montant);

            assertThat(apres.decision())
                    .as("Le meme montant passe au-dessus du nouveau seuil, sans redemarrage")
                    .isEqualTo(DecisionAiguillage.ENVOI_DIRECTEUR_RESEAU);
            assertThat(apres.seuilApplique())
                    .as("La decision porte la valeur reellement appliquee")
                    .isEqualTo(nouveauSeuil);
        }

        @Test
        @DisplayName("8. Parametre desactive : aucun aiguillage, refus")
        void parametreDesactive() {
            desactiverLeSeuil();

            assertThatThrownBy(() -> aiguiller(1))
                    .isInstanceOf(SeuilIndisponibleException.class)
                    .hasMessageContaining(SeuilService.CODE_SEUIL_AIGUILLAGE);
        }

        @Test
        @DisplayName("9. Parametre absent : aucun aiguillage, refus")
        void parametreAbsent() {
            supprimerLeSeuil();

            assertThatThrownBy(() -> aiguiller(1))
                    .isInstanceOf(SeuilIndisponibleException.class)
                    .hasMessageContaining("n'est pas configure");
        }

        /**
         * Toutes les valeurs illisibles produisent le meme refus, y compris celles
         * qu'un lecteur humain croirait comprendre.
         *
         * <p>{@code "100 000"} est le cas le plus insidieux : il se lit comme cent
         * mille, mais rien ne garantit que c'est ce qui a ete voulu, et deviner
         * reviendrait a fabriquer un seuil. Le dernier cas est un depassement de
         * capacite : il retombe sur le meme refus, jamais sur une erreur brute.
         */
        @Test
        @DisplayName("10. Valeur non numerique, negative ou hors capacite : refus uniforme")
        void valeurIllisible() {
            for (String valeur : new String[] {
                    "cent mille", "", "   ", "100 000", "100.000", "1e5", "-1",
                    "99999999999999999999999999"}) {

                fixerValeurDuSeuil(valeur);

                assertThatThrownBy(() -> aiguiller(1))
                        .as("Valeur de parametre refusee : « %s »", valeur)
                        .isInstanceOf(SeuilIndisponibleException.class);
            }
        }

        /**
         * Un seuil de zero n'est pas une erreur de configuration : c'est une regle
         * intelligible — tout etat non nul requiert l'approbation du directeur reseau.
         * Le service l'applique donc au lieu de le refuser, et la borne y reste exacte.
         */
        @Test
        @DisplayName("11. Seuil a zero : configuration valide, borne toujours exacte")
        void seuilAZero() {
            fixerValeurDuSeuil("0");

            assertThat(aiguiller(0).decision())
                    .isEqualTo(DecisionAiguillage.SOUS_SEUIL_CLOTURE_DIRECTE);
            assertThat(aiguiller(1).decision())
                    .isEqualTo(DecisionAiguillage.ENVOI_DIRECTEUR_RESEAU);
        }

        /**
         * Les espaces en bord sont tolerés : ils viennent d'une saisie manuelle en base
         * et ne changent pas la valeur voulue. C'est la seule souplesse admise.
         */
        @Test
        @DisplayName("12. Espaces en bord de valeur : toleres, la valeur est lue")
        void espacesEnBord() {
            long seuil = seuilLu();
            fixerValeurDuSeuil("  " + seuil + "  ");

            assertThat(aiguiller(seuil).seuilApplique()).isEqualTo(seuil);
        }
    }

    // =====================================================================
    // L'etat complementaire : second niveau obligatoire (Sprint 6bis.1)
    // =====================================================================

    @Nested
    @DisplayName("Etat complementaire")
    class Complementaire {

        /**
         * Un etat COMPLEMENTAIRE monte au directeur reseau <b>quel que soit son
         * montant</b>. La preuve est faite a la borne basse : un montant de zero, qui
         * cloturerait immediatement un etat normal, monte quand meme.
         */
        @Test
        @DisplayName("14. Tout complementaire monte au directeur reseau, meme a montant nul")
        void toutComplementaireMonte() {
            for (long montant : new long[] {0, 1, seuilLu() - 1, seuilLu(), seuilLu() + 1}) {
                ResultatAiguillage resultat = aiguillerComplementaire(montant);

                assertThat(resultat.decision())
                        .as("montant %d : un complementaire ne se clot jamais au premier niveau",
                                montant)
                        .isEqualTo(DecisionAiguillage.COMPLEMENTAIRE_ENVOI_DIRECTEUR_RESEAU);
                assertThat(resultat.estClotureDirecte()).isFalse();
                assertThat(resultat.montantTotalFcfa()).isEqualTo(montant);
            }
        }

        /**
         * <b>Aucune comparaison n'a eu lieu, et la reponse le dit.</b> Inscrire un seuil
         * ferait croire a un arbitrage montant/seuil qui n'a pas eu lieu — meme
         * raisonnement qu'au Sprint 4.4, ou l'audit du second niveau n'inscrit ni seuil
         * ni decision d'aiguillage.
         *
         * <p>Un {@code 0} aurait ete pire qu'un nul : il serait indiscernable d'un seuil
         * reellement configure a zero, que le Sprint 4.3 accepte explicitement.
         */
        @Test
        @DisplayName("15. Aucun seuil applique : le champ est nul, jamais zero")
        void aucunSeuilApplique() {
            assertThat(aiguillerComplementaire(1).seuilApplique()).isNull();
        }

        /**
         * <b>Le test qui prouve que le seuil n'est pas lu du tout.</b>
         *
         * <p>Le parametre est rendu illisible : un etat normal echouerait en
         * {@link SeuilIndisponibleException}. Le complementaire, lui, aboutit — donc
         * aucune lecture n'a eu lieu.
         *
         * <p>Ce n'est pas un detail de performance. Lire un parametre qui ne gouverne pas
         * la decision ferait echouer une validation sur une panne de configuration
         * etrangere au dossier, exactement le defaut que le Sprint 4.4 a ecarte en ne
         * rappelant pas l'aiguillage au second niveau.
         */
        @Test
        @DisplayName("16. Seuil illisible : un complementaire s'aiguille quand meme")
        void seuilIllisibleNEmpechePasLAiguillageDUnComplementaire() {
            fixerValeurDuSeuil("cent mille");

            assertThat(aiguillerComplementaire(1).decision())
                    .as("le seuil n'est pas lu : il ne peut donc pas bloquer")
                    .isEqualTo(DecisionAiguillage.COMPLEMENTAIRE_ENVOI_DIRECTEUR_RESEAU);

            // Et l'autre bord : un etat NORMAL echoue bien sur le meme parametre.
            assertThatThrownBy(() -> aiguiller(1))
                    .isInstanceOf(SeuilIndisponibleException.class);
        }
    }

    // =====================================================================
    // La garantie du sous-sprint : rien en dur
    // =====================================================================

    /**
     * Ce test lit le fichier source de ce test-ci et celui d'{@link AiguillageService}
     * pour verifier qu'aucun ne contient la valeur du seuil configure.
     *
     * <p><b>Il ne remplace pas la recherche manuelle demandee par le guide</b>, il la
     * rend permanente. Un seuil recopie en dur dans un mois, par commodite, ferait
     * echouer le build au lieu d'attendre qu'un relecteur le remarque.
     */
    @Test
    @DisplayName("13. Aucune valeur de seuil en dur, ni dans le service ni dans ses tests")
    void aucuneValeurDeSeuilEnDur() throws Exception {
        String seuil = String.valueOf(seuilLu());

        // Garde de bon sens : sous quatre chiffres, la valeur se retrouverait par
        // hasard dans n'importe quel fichier (un numero de ligne, une largeur de
        // colonne) et le test denoncerait des coincidences. Un seuil en FCFA reel
        // compte toujours plus de trois chiffres ; en dessous, on ne conclut rien.
        if (seuil.length() < 4) {
            return;
        }

        for (String source : new String[] {
                "src/main/java/cm/afrilandfirstbank/rations/workflow/application/AiguillageService.java",
                "src/main/java/cm/afrilandfirstbank/rations/workflow/application/SeuilService.java",
                "src/test/java/cm/afrilandfirstbank/rations/workflow/application/AiguillageServiceTest.java"}) {

            String contenu = java.nio.file.Files.readString(java.nio.file.Path.of(source));

            assertThat(contenu)
                    .as("La valeur du seuil (%s) ne doit apparaitre nulle part dans %s : "
                            + "elle se lit dans parametre_systeme (RG-08, CLAUDE.md section 15)",
                            seuil, source)
                    .doesNotContain(seuil);
        }
    }

    // =====================================================================
    // Outils
    // =====================================================================

    /** Le seuil en vigueur, lu en base. Aucun test n'en connait la valeur autrement. */
    private long seuilLu() {
        return seuilService.seuilAiguillage();
    }

    private ResultatAiguillage aiguiller(long montantTotal) {
        ProcessusMensuel processus = declencherSur(9, ANNEE, UNITE);
        processus.reporterMontantTotal(Math.toIntExact(montantTotal));
        return aiguillageService.aiguiller(processus);
    }

    /**
     * Un etat COMPLEMENTAIRE au montant voulu, ne d'une origine close (Sprint 6bis.1).
     *
     * <p>Rien n'est persiste : ce service ne touche pas a la base, il repond a une
     * question. L'identifiant de l'origine est pose par reflexion — {@code id} est
     * genere par la base, et {@code ouvrirComplementaire} exige une origine deja
     * enregistree, a juste titre : sans identifiant, le rattachement serait introuvable.
     */
    private ResultatAiguillage aiguillerComplementaire(long montantTotal) {
        ProcessusMensuel origine = declencherSur(9, ANNEE, UNITE);
        ReflectionTestUtils.setField(origine, "id", 4242L);
        TransitionProcessus.soumettre(origine);
        TransitionProcessus.transfererAuChefUnite(origine);
        TransitionProcessus.cloturerApresValidationChefUnite(origine);

        ProcessusMensuel complementaire = TransitionProcessus.ouvrirComplementaire(
                origine, "Beneficiaire omis, regularisation");
        complementaire.reporterMontantTotal(Math.toIntExact(montantTotal));

        return aiguillageService.aiguiller(complementaire);
    }

    private void fixerValeurDuSeuil(String valeur) {
        modifier("update ParametreSysteme p set p.valeur = :valeur where p.code = :code",
                "valeur", valeur);
    }

    private void desactiverLeSeuil() {
        modifier("update ParametreSysteme p set p.actif = false where p.code = :code", null, null);
    }

    private void supprimerLeSeuil() {
        modifier("delete from ParametreSysteme p where p.code = :code", null, null);
    }

    /**
     * Modification en masse suivie d'un vidage du contexte de persistance.
     *
     * <p>Le vidage n'est pas une precaution de style : sans lui, Hibernate pourrait
     * rendre l'entite telle qu'elle etait avant la mise a jour, et le test
     * verifierait la memoire au lieu de la base. La transaction de
     * {@code @DataJpaTest} est annulee en fin de test, la ligne d'origine reste donc
     * intacte pour les suivants.
     */
    private void modifier(String requete, String parametre, String valeur) {
        Query requeteJpql = entityManager.createQuery(requete)
                .setParameter("code", SeuilService.CODE_SEUIL_AIGUILLAGE);
        if (parametre != null) {
            requeteJpql.setParameter(parametre, valeur);
        }
        requeteJpql.executeUpdate();
        entityManager.flush();
        entityManager.clear();
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
