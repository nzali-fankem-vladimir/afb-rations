package cm.afrilandfirstbank.rations.workflow.domaine;

import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import cm.afrilandfirstbank.rations.workflow.domaine.exception.MotifRetourRequisException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.TransitionProcessusInterditeException;

/**
 * Tests de la machine a etats du processus mensuel (diagramme ET01, RG-07).
 *
 * <p><b>Neuf transitions valides</b> (tests 1 a 9) et <b>six transitions
 * interdites</b> (tests 10 a 15), plus quatre tests de verrouillage. Chaque
 * transition refusee doit lever une erreur explicite et <b>laisser le statut
 * inchange</b> : un refus qui abimerait l'etat au passage serait pire que pas de
 * refus du tout.
 *
 * <p>Les tests 13 et 14 protegent le caractere definitif de la cloture, dont
 * depend l'unicite de la transmission comptable au Sprint 5 (RG-13). Le test 16
 * les generalise : aucune sortie de CLOTURE, quelle que soit la cible.
 *
 * <p><b>Les etats de depart sont construits en parcourant la machine</b>, jamais
 * en forcant un statut. Un processus EN_ATTENTE_DR est donc obtenu par
 * declenchement, soumission, transfert puis aiguillage : le montage du test
 * prouve au passage que le chemin est reellement praticable.
 */
class TransitionProcessusTest {

    private static final int MOIS = 8;
    private static final int ANNEE = 2026;
    private static final String CODE_UNITE = "00002";
    private static final String MOTIF = "Journee du 12 aout saisie deux fois.";

    /**
     * Amene un processus neuf jusqu'au statut demande, en n'empruntant que des
     * transitions legales.
     */
    private static ProcessusMensuel processusAu(StatutEnum statutVoulu) {
        ProcessusMensuel processus = declencherSur(MOIS, ANNEE, CODE_UNITE);
        if (statutVoulu == StatutEnum.EN_COURS_SAISIE) {
            return processus;
        }

        TransitionProcessus.soumettre(processus);
        if (statutVoulu == StatutEnum.SOUMIS) {
            return processus;
        }

        TransitionProcessus.transfererAuChefUnite(processus);
        if (statutVoulu == StatutEnum.EN_ATTENTE_DA) {
            return processus;
        }

        return switch (statutVoulu) {
            case EN_ATTENTE_DR -> {
                TransitionProcessus.aiguillerVersDirecteurReseau(processus);
                yield processus;
            }
            case RETOURNE -> {
                TransitionProcessus.retournerParChefUnite(processus, MOTIF);
                yield processus;
            }
            case CLOTURE -> {
                TransitionProcessus.cloturerApresValidationChefUnite(processus);
                yield processus;
            }
            default -> throw new IllegalArgumentException("Statut non montable : " + statutVoulu);
        };
    }

    // =========================================================================
    // Les neuf transitions d'ET01
    // =========================================================================

    @Nested
    @DisplayName("Transitions valides (ET01)")
    class Valides {

        @Test
        @DisplayName("1. (creation) -> EN_COURS_SAISIE : declenchement par l'agent")
        void creationVersEnCoursSaisie() {
            ProcessusMensuel processus = declencherSur(MOIS, ANNEE, CODE_UNITE);

            assertThat(processus.getStatut()).isEqualTo(StatutEnum.EN_COURS_SAISIE);
            assertThat(processus.getTypeProcessus()).isEqualTo(TypeProcessusEnum.NORMAL);
            assertThat(processus.getDateDebut()).isEqualTo(LocalDate.of(ANNEE, MOIS, 1));
            assertThat(processus.getDateFin())
                    .as("borne de fin INCLUSE : le dernier jour du mois, pas le premier du suivant")
                    .isEqualTo(LocalDate.of(ANNEE, MOIS, 1).plusMonths(1).minusDays(1));
            assertThat(processus.getCodeUnite()).isEqualTo(CODE_UNITE);
            // Un etat normal ne regularise rien, n'a rien consolide, n'a rien transmis.
            assertThat(processus.getIdProcessusOrigine()).isNull();
            assertThat(processus.getMotifOuverture()).isNull();
            assertThat(processus.getMontantTotal()).isZero();
            assertThat(processus.isTransmisComptabilite()).isFalse();
        }

        @Test
        @DisplayName("2. EN_COURS_SAISIE -> SOUMIS : soumission, informations completes")
        void enCoursSaisieVersSoumis() {
            ProcessusMensuel processus = processusAu(StatutEnum.EN_COURS_SAISIE);

            TransitionProcessus.soumettre(processus);

            assertThat(processus.getStatut()).isEqualTo(StatutEnum.SOUMIS);
            assertThat(TransitionProcessus.estAutorisee(StatutEnum.EN_COURS_SAISIE, StatutEnum.SOUMIS))
                    .isTrue();
        }

        @Test
        @DisplayName("3. SOUMIS -> EN_ATTENTE_DA : signature agent apposee, transfert au DA")
        void soumisVersEnAttenteDa() {
            ProcessusMensuel processus = processusAu(StatutEnum.SOUMIS);

            TransitionProcessus.transfererAuChefUnite(processus);

            assertThat(processus.getStatut()).isEqualTo(StatutEnum.EN_ATTENTE_DA);
        }

        @Test
        @DisplayName("4. EN_ATTENTE_DA -> CLOTURE : validation DA, montant au plus le seuil")
        void enAttenteDaVersCloture() {
            ProcessusMensuel processus = processusAu(StatutEnum.EN_ATTENTE_DA);

            TransitionProcessus.cloturerApresValidationChefUnite(processus);

            assertThat(processus.getStatut()).isEqualTo(StatutEnum.CLOTURE);
        }

        @Test
        @DisplayName("5. EN_ATTENTE_DA -> EN_ATTENTE_DR : validation DA, montant au-dela du seuil")
        void enAttenteDaVersEnAttenteDr() {
            ProcessusMensuel processus = processusAu(StatutEnum.EN_ATTENTE_DA);

            TransitionProcessus.aiguillerVersDirecteurReseau(processus);

            assertThat(processus.getStatut()).isEqualTo(StatutEnum.EN_ATTENTE_DR);
        }

        @Test
        @DisplayName("6. EN_ATTENTE_DA -> RETOURNE : retour DA avec motif")
        void enAttenteDaVersRetourne() {
            ProcessusMensuel processus = processusAu(StatutEnum.EN_ATTENTE_DA);

            TransitionProcessus.retournerParChefUnite(processus, MOTIF);

            assertThat(processus.getStatut()).isEqualTo(StatutEnum.RETOURNE);
        }

        @Test
        @DisplayName("7. EN_ATTENTE_DR -> CLOTURE : validation DR")
        void enAttenteDrVersCloture() {
            ProcessusMensuel processus = processusAu(StatutEnum.EN_ATTENTE_DR);

            TransitionProcessus.cloturerApresValidationDirecteurReseau(processus);

            assertThat(processus.getStatut()).isEqualTo(StatutEnum.CLOTURE);
        }

        @Test
        @DisplayName("8. EN_ATTENTE_DR -> RETOURNE : retour DR avec motif, vers l'agent (RG-11)")
        void enAttenteDrVersRetourne() {
            ProcessusMensuel processus = processusAu(StatutEnum.EN_ATTENTE_DR);

            TransitionProcessus.retournerParDirecteurReseau(processus, MOTIF);

            // RG-11 : le retour ramene a l'agent, PAS au Chef d'Unite, bien que le
            // dossier soit passe par lui. Aucune transition ne vise EN_ATTENTE_DA
            // depuis EN_ATTENTE_DR.
            assertThat(processus.getStatut()).isEqualTo(StatutEnum.RETOURNE);
            assertThat(TransitionProcessus.estAutorisee(StatutEnum.EN_ATTENTE_DR, StatutEnum.EN_ATTENTE_DA))
                    .isFalse();
        }

        @Test
        @DisplayName("9. RETOURNE -> EN_COURS_SAISIE : reprise par l'agent")
        void retourneVersEnCoursSaisie() {
            ProcessusMensuel processus = processusAu(StatutEnum.RETOURNE);

            TransitionProcessus.reprendreParAgent(processus);

            assertThat(processus.getStatut()).isEqualTo(StatutEnum.EN_COURS_SAISIE);
        }

    }

    // =========================================================================
    // Les six transitions interdites du plan de test
    // =========================================================================

    @Nested
    @DisplayName("Transitions interdites")
    class Interdites {

        @Test
        @DisplayName("10. EN_COURS_SAISIE -> EN_ATTENTE_DA : saut de la soumission (RG-07)")
        void sautDeLaSoumission() {
            ProcessusMensuel processus = processusAu(StatutEnum.EN_COURS_SAISIE);

            assertThatThrownBy(() -> TransitionProcessus.transfererAuChefUnite(processus))
                    .isInstanceOf(TransitionProcessusInterditeException.class)
                    .hasMessageContaining("EN_COURS_SAISIE -> EN_ATTENTE_DA")
                    .hasMessageContaining("SOUMIS");

            assertThat(processus.getStatut()).isEqualTo(StatutEnum.EN_COURS_SAISIE);
            assertThat(TransitionProcessus.estAutorisee(
                    StatutEnum.EN_COURS_SAISIE, StatutEnum.EN_ATTENTE_DA)).isFalse();
        }

        @Test
        @DisplayName("11. SOUMIS -> CLOTURE : saut des validations (RG-07)")
        void sautDesValidations() {
            ProcessusMensuel processus = processusAu(StatutEnum.SOUMIS);

            assertThatThrownBy(() -> TransitionProcessus.cloturerApresValidationChefUnite(processus))
                    .isInstanceOf(TransitionProcessusInterditeException.class)
                    .hasMessageContaining("SOUMIS -> CLOTURE");

            assertThat(processus.getStatut()).isEqualTo(StatutEnum.SOUMIS);
            assertThat(TransitionProcessus.estAutorisee(StatutEnum.SOUMIS, StatutEnum.CLOTURE)).isFalse();
        }

        @Test
        @DisplayName("12. EN_ATTENTE_DA -> EN_ATTENTE_DA : un etat ne se transfere pas a lui-meme")
        void transfertSurPlace() {
            ProcessusMensuel processus = processusAu(StatutEnum.EN_ATTENTE_DA);

            assertThatThrownBy(() -> TransitionProcessus.transfererAuChefUnite(processus))
                    .isInstanceOf(TransitionProcessusInterditeException.class)
                    .hasMessageContaining("EN_ATTENTE_DA -> EN_ATTENTE_DA");

            assertThat(processus.getStatut()).isEqualTo(StatutEnum.EN_ATTENTE_DA);
            assertThat(TransitionProcessus.estAutorisee(
                    StatutEnum.EN_ATTENTE_DA, StatutEnum.EN_ATTENTE_DA)).isFalse();
        }

        @Test
        @DisplayName("13. CLOTURE -> RETOURNE : la cloture est definitive (RG-13)")
        void clotureNeSeRetournePas() {
            ProcessusMensuel processus = processusAu(StatutEnum.CLOTURE);

            // Le motif est volontairement RENSEIGNE : sans lui, l'echec viendrait de
            // MotifRetourRequisException et le test passerait pour la mauvaise raison,
            // sans jamais eprouver le caractere terminal de la cloture.
            assertThatThrownBy(() -> TransitionProcessus.retournerParChefUnite(processus, MOTIF))
                    .isInstanceOf(TransitionProcessusInterditeException.class)
                    .hasMessageContaining("CLOTURE -> RETOURNE")
                    .hasMessageContaining("definitif")
                    .hasMessageContaining("COMPLEMENTAIRE");

            assertThatThrownBy(() -> TransitionProcessus.retournerParDirecteurReseau(processus, MOTIF))
                    .isInstanceOf(TransitionProcessusInterditeException.class);

            assertThat(processus.getStatut()).isEqualTo(StatutEnum.CLOTURE);
        }

        @Test
        @DisplayName("14. CLOTURE -> EN_COURS_SAISIE : un etat clos ne se rouvre pas (RG-13)")
        void clotureNeSeRouvrePas() {
            ProcessusMensuel processus = processusAu(StatutEnum.CLOTURE);

            assertThatThrownBy(() -> TransitionProcessus.reprendreParAgent(processus))
                    .isInstanceOf(TransitionProcessusInterditeException.class)
                    .hasMessageContaining("CLOTURE -> EN_COURS_SAISIE")
                    .hasMessageContaining("definitif");

            assertThat(processus.getStatut()).isEqualTo(StatutEnum.CLOTURE);
            assertThat(TransitionProcessus.estAutorisee(
                    StatutEnum.CLOTURE, StatutEnum.EN_COURS_SAISIE)).isFalse();
        }

        @Test
        @DisplayName("15. RETOURNE -> EN_ATTENTE_DA : saut de la resoumission (RG-07)")
        void sautDeLaResoumission() {
            ProcessusMensuel processus = processusAu(StatutEnum.RETOURNE);

            assertThatThrownBy(() -> TransitionProcessus.transfererAuChefUnite(processus))
                    .isInstanceOf(TransitionProcessusInterditeException.class)
                    .hasMessageContaining("RETOURNE -> EN_ATTENTE_DA")
                    .hasMessageContaining("EN_COURS_SAISIE");

            assertThat(processus.getStatut()).isEqualTo(StatutEnum.RETOURNE);
            assertThat(TransitionProcessus.estAutorisee(
                    StatutEnum.RETOURNE, StatutEnum.EN_ATTENTE_DA)).isFalse();
        }

    }

    // =========================================================================
    // Verrouillages : ce que la machine ne doit jamais devenir
    // =========================================================================

    @Nested
    @DisplayName("Verrouillages de la machine")
    class Verrouillages {

        @Test
        @DisplayName("16. CLOTURE est terminal : aucune sortie, quelle que soit la cible")
        void clotureNaAucuneSortie() {
            for (StatutEnum cible : StatutEnum.values()) {
                assertThat(TransitionProcessus.estAutorisee(StatutEnum.CLOTURE, cible))
                        .as("CLOTURE -> %s doit rester interdit (RG-13)", cible)
                        .isFalse();
            }
        }

        /**
         * Le tableau ET01 en entier, confronte a la machine. Ajouter ou retirer une
         * arete fait echouer ce test : c'est le garde-fou qui empeche qu'une
         * transition de commodite se glisse dans le circuit sans decision.
         */
        @Test
        @DisplayName("17. La table des transitions est exactement celle d'ET01, ni plus ni moins")
        void tableConformeAEt01() {
            Set<String> attendues = new HashSet<>(Set.of(
                    "EN_COURS_SAISIE->SOUMIS",
                    "SOUMIS->EN_ATTENTE_DA",
                    "EN_ATTENTE_DA->CLOTURE",
                    "EN_ATTENTE_DA->EN_ATTENTE_DR",
                    "EN_ATTENTE_DA->RETOURNE",
                    "EN_ATTENTE_DR->CLOTURE",
                    "EN_ATTENTE_DR->RETOURNE",
                    "RETOURNE->EN_COURS_SAISIE"));

            Set<String> reelles = new HashSet<>();
            for (StatutEnum source : StatutEnum.values()) {
                for (StatutEnum cible : StatutEnum.values()) {
                    if (TransitionProcessus.estAutorisee(source, cible)) {
                        reelles.add(source + "->" + cible);
                    }
                }
            }

            // Huit aretes d'etat a etat ; la neuvieme transition d'ET01 est la
            // creation, qui n'a pas d'etat source (voir declencher()).
            assertThat(reelles).containsExactlyInAnyOrderElementsOf(attendues);
            assertThat(reelles).hasSize(8);
        }

        @Test
        @DisplayName("18. RG-10 : aucun retour sans motif, ni cote DA ni cote DR")
        void retourSansMotifRefuse() {
            for (String motifVide : new String[] { null, "", "   " }) {
                ProcessusMensuel versDa = processusAu(StatutEnum.EN_ATTENTE_DA);
                assertThatThrownBy(() -> TransitionProcessus.retournerParChefUnite(versDa, motifVide))
                        .isInstanceOf(MotifRetourRequisException.class)
                        .hasMessageContaining("RG-10");
                assertThat(versDa.getStatut()).isEqualTo(StatutEnum.EN_ATTENTE_DA);

                ProcessusMensuel versDr = processusAu(StatutEnum.EN_ATTENTE_DR);
                assertThatThrownBy(
                        () -> TransitionProcessus.retournerParDirecteurReseau(versDr, motifVide))
                        .isInstanceOf(MotifRetourRequisException.class);
                assertThat(versDr.getStatut()).isEqualTo(StatutEnum.EN_ATTENTE_DR);
            }
        }

        /**
         * La machine n'arbitre pas le montant (point de vigilance du guide 4.1).
         * L'aiguillage au seuil est le sous-sprint 4.3 : si une methode d'ici
         * recevait un montant ou un seuil, c'est que la decision aurait migre dans
         * le domaine et que la machine serait devenue dependante d'un parametre de
         * configuration.
         */
        @Test
        @DisplayName("19. Les deux issues depuis EN_ATTENTE_DA ne recoivent aucun montant")
        void machineIndependanteDuMontant() {
            // Aucun nom de methode ne parle de montant ni de seuil.
            for (Method methode : TransitionProcessus.class.getDeclaredMethods()) {
                assertThat(methode.getName().toLowerCase(Locale.ROOT))
                        .as("La machine a etats ne doit rien savoir du montant (RG-08 releve du 4.3)")
                        .doesNotContain("montant")
                        .doesNotContain("seuil");
            }

            // Les deux transitions concurrentes depuis EN_ATTENTE_DA ne prennent que
            // le processus : elles n'ont rien a comparer, donc rien a arbitrer. Le
            // choix entre elles se fait dans le service d'aiguillage du 4.3.
            assertThat(methodePublique("cloturerApresValidationChefUnite").getParameterTypes())
                    .containsExactly(ProcessusMensuel.class);
            assertThat(methodePublique("aiguillerVersDirecteurReseau").getParameterTypes())
                    .containsExactly(ProcessusMensuel.class);
        }

        private Method methodePublique(String nom) {
            for (Method methode : TransitionProcessus.class.getDeclaredMethods()) {
                if (methode.getName().equals(nom)) {
                    return methode;
                }
            }
            throw new AssertionError("Methode absente de la machine a etats : " + nom);
        }

        @Test
        @DisplayName("20. estAutorisee refuse un statut source nul plutot que d'y voir une creation")
        void statutSourceNulRefuse() {
            assertThat(TransitionProcessus.estAutorisee(null, StatutEnum.EN_COURS_SAISIE)).isFalse();
            assertThat(TransitionProcessus.estAutorisee(StatutEnum.EN_COURS_SAISIE, null)).isFalse();
            assertThat(TransitionProcessus.estAutorisee(null, null)).isFalse();
        }

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
