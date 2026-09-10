package cm.afrilandfirstbank.rations.workflow.domaine;

import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cm.afrilandfirstbank.rations.workflow.domaine.TransitionIntegration.Decision;

/**
 * La table des transitions du statut d'integration, eprouvee <b>case par case</b>
 * (Sprint 5.2, tests 8 et 9 du guide).
 *
 * <p>Cette table est la regle la plus consequente du sous-sprint : c'est elle, et non
 * l'ordre garanti par Kafka, qui protege les statuts d'un rejeu de topic — la cle de
 * partition des accuses est posee par un producteur que l'equipe ne controle pas
 * ({@code docs/points-en-attente.md}). Elle est donc verifiee ici <b>exhaustivement</b>,
 * plutot qu'a travers les quelques cas qu'un service applicatif rencontrerait.
 */
class TransitionIntegrationTest {

    private static final String REFERENCE = "CPT-2026-07-000512";
    private static final LocalDateTime DATE = LocalDateTime.of(2026, 8, 18, 2, 15);
    private static final String MOTIF = "Compte 00002000123456 clos depuis le 12/07.";

    // --- Un etat jamais transmis n'accuse rien ------------------------------------

    @Test
    @DisplayName("Un etat jamais transmis refuse tout accuse, quel qu'il soit : la comptabilite "
            + "ne peut pas avoir traite ce qu'elle n'a pas recu")
    void etatJamaisTransmisRefuseTout() {
        for (StatutIntegrationEnum recu : StatutIntegrationEnum.values()) {
            ProcessusMensuel processus = unEtatNonTransmis();

            Decision decision = TransitionIntegration.arbitrer(
                    processus, recu, REFERENCE, DATE, MOTIF);

            assertThat(decision)
                    .describedAs("accuse %s sur un etat jamais transmis", recu)
                    .isInstanceOf(Decision.NonTransmis.class);
        }
    }

    @Test
    @DisplayName("Le drapeau de transmission n'est jamais pose par un accuse : le poser ferait "
            + "refuser ensuite la vraie transmission comme un doublon (RG-13)")
    void leDrapeauDeTransmissionNEstJamaisPose() {
        ProcessusMensuel processus = unEtatNonTransmis();

        TransitionIntegration.appliquerAccuse(
                processus, StatutIntegrationEnum.INTEGRE, REFERENCE, DATE, null);

        assertThat(processus.isTransmisComptabilite()).isFalse();
        assertThat(processus.getStatutIntegration()).isNull();
    }

    // --- Les deux etats ouverts ---------------------------------------------------

    @Test
    @DisplayName("Depuis EN_ATTENTE, les trois statuts sont applicables : c'est le module "
            + "lui-meme qui a pose EN_ATTENTE a la publication, sans reference ni date")
    void depuisEnAttenteToutEstApplicable() {
        for (StatutIntegrationEnum recu : StatutIntegrationEnum.values()) {
            ProcessusMensuel processus = unEtatTransmis();

            Decision decision = TransitionIntegration.appliquerAccuse(
                    processus, recu, REFERENCE, DATE, MOTIF);

            assertThat(decision)
                    .describedAs("accuse %s depuis EN_ATTENTE", recu)
                    .isInstanceOf(Decision.Appliquer.class);
            assertThat(processus.getStatutIntegration()).isEqualTo(recu);
        }
    }

    @Test
    @DisplayName("Un premier accuse EN_ATTENTE portant une reference ENRICHIT l'etat, il ne le "
            + "contredit pas : c'est le cas normal du « bien recu, en cours de traitement »")
    void accuseEnAttenteEnrichitSansContredire() {
        ProcessusMensuel processus = unEtatTransmis();
        // A la publication, le module a pose EN_ATTENTE sans reference ni date.
        assertThat(processus.getReferenceComptable()).isNull();

        Decision decision = TransitionIntegration.appliquerAccuse(
                processus, StatutIntegrationEnum.EN_ATTENTE, "CPT-PROVISOIRE", DATE, null);

        assertThat(decision).isInstanceOf(Decision.Appliquer.class);
        assertThat(processus.getReferenceComptable()).isEqualTo("CPT-PROVISOIRE");
    }

    @Test
    @DisplayName("Les quatre valeurs de l'accuse sont ecrites ensemble : elles viennent d'un seul "
            + "accuse et n'ont aucun sens separement")
    void lesQuatreValeursSontEcritesEnsemble() {
        ProcessusMensuel processus = unEtatTransmis();

        TransitionIntegration.appliquerAccuse(
                processus, StatutIntegrationEnum.REJETE, REFERENCE, DATE, MOTIF);

        assertThat(processus.getStatutIntegration()).isEqualTo(StatutIntegrationEnum.REJETE);
        assertThat(processus.getReferenceComptable()).isEqualTo(REFERENCE);
        assertThat(processus.getDateTraitement()).isEqualTo(DATE);
        assertThat(processus.getMotifIntegration()).isEqualTo(MOTIF);
    }

    @Test
    @DisplayName("Un motif nul recu EFFACE le motif precedent : un etat integre qui garderait le "
            + "motif d'un rejet anterieur afficherait sa justification a cote")
    void unMotifNulEffaceLePrecedent() {
        ProcessusMensuel processus = unEtatTransmis();
        TransitionIntegration.appliquerAccuse(
                processus, StatutIntegrationEnum.EN_ATTENTE, null, null, MOTIF);

        TransitionIntegration.appliquerAccuse(
                processus, StatutIntegrationEnum.INTEGRE, REFERENCE, DATE, null);

        assertThat(processus.getMotifIntegration()).isNull();
    }

    // --- Test 8 du guide : le rejeu a l'identique ---------------------------------

    @Test
    @DisplayName("Test 8 — le meme accuse recu deux fois : la seconde reception ne fait rien, et "
            + "l'etat final est identique a celui d'une reception unique")
    void memeAccuseDeuxFois() {
        ProcessusMensuel processus = unEtatTransmis();

        Decision premiere = TransitionIntegration.appliquerAccuse(
                processus, StatutIntegrationEnum.INTEGRE, REFERENCE, DATE, null);
        StatutIntegrationEnum statutApresLaPremiere = processus.getStatutIntegration();
        String referenceApresLaPremiere = processus.getReferenceComptable();

        Decision seconde = TransitionIntegration.appliquerAccuse(
                processus, StatutIntegrationEnum.INTEGRE, REFERENCE, DATE, null);

        assertThat(premiere).isInstanceOf(Decision.Appliquer.class);
        assertThat(seconde).isInstanceOf(Decision.DejaApplique.class);
        assertThat(processus.getStatutIntegration()).isEqualTo(statutApresLaPremiere);
        assertThat(processus.getReferenceComptable()).isEqualTo(referenceApresLaPremiere);
    }

    @Test
    @DisplayName("Un rejeu a l'identique est reconnu meme sur un statut definitif : c'est ce qui "
            + "rend un rejeu de topic inoffensif")
    void rejeuIdentiqueSurStatutDefinitif() {
        ProcessusMensuel processus = unEtatTransmis();
        TransitionIntegration.appliquerAccuse(
                processus, StatutIntegrationEnum.REJETE, REFERENCE, DATE, MOTIF);

        for (int rejeu = 0; rejeu < 5; rejeu++) {
            assertThat(TransitionIntegration.arbitrer(
                    processus, StatutIntegrationEnum.REJETE, REFERENCE, DATE, MOTIF))
                    .isInstanceOf(Decision.DejaApplique.class);
        }
    }

    // --- Test 9 du guide : les contradictions -------------------------------------

    @Test
    @DisplayName("Test 9 — un REJETE apres un INTEGRE deja recu est refuse : le module ne peut "
            + "pas savoir lequel dit vrai, il refuse et signale")
    void rejetApresIntegrationRefuse() {
        ProcessusMensuel processus = unEtatTransmis();
        TransitionIntegration.appliquerAccuse(
                processus, StatutIntegrationEnum.INTEGRE, REFERENCE, DATE, null);

        Decision decision = TransitionIntegration.appliquerAccuse(
                processus, StatutIntegrationEnum.REJETE, null, DATE, MOTIF);

        assertThat(decision).isInstanceOf(Decision.Contradiction.class);
        // Rien n'est ecrit : l'etat garde son statut d'origine.
        assertThat(processus.getStatutIntegration()).isEqualTo(StatutIntegrationEnum.INTEGRE);
        assertThat(processus.getReferenceComptable()).isEqualTo(REFERENCE);
        // Le message nomme les DEUX statuts : sans cela, personne ne pourrait lever la
        // contradiction avec la comptabilite.
        assertThat(((Decision.Contradiction) decision).message())
                .contains("INTEGRE").contains("REJETE");
    }

    @Test
    @DisplayName("Aucun accuse ne fait REGRESSER un statut : un EN_ATTENTE arrivant apres un "
            + "verdict definitif est refuse — c'est le filet contre un rejeu desordonne")
    void aucuneRegression() {
        for (StatutIntegrationEnum definitif : new StatutIntegrationEnum[] {
                StatutIntegrationEnum.INTEGRE, StatutIntegrationEnum.REJETE }) {

            ProcessusMensuel processus = unEtatTransmis();
            TransitionIntegration.appliquerAccuse(
                    processus, definitif, REFERENCE, DATE, MOTIF);

            Decision decision = TransitionIntegration.appliquerAccuse(
                    processus, StatutIntegrationEnum.EN_ATTENTE, REFERENCE, DATE, MOTIF);

            assertThat(decision)
                    .describedAs("EN_ATTENTE recu apres %s", definitif)
                    .isInstanceOf(Decision.Contradiction.class);
            assertThat(processus.getStatutIntegration()).isEqualTo(definitif);
        }
    }

    @Test
    @DisplayName("Meme statut mais reference differente sur un etat definitif : contradiction. "
            + "Deux INTEGRE de references differentes ne sont pas le meme accuse")
    void memeStatutReferenceDifferenteRefuse() {
        ProcessusMensuel processus = unEtatTransmis();
        TransitionIntegration.appliquerAccuse(
                processus, StatutIntegrationEnum.INTEGRE, REFERENCE, DATE, null);

        Decision decision = TransitionIntegration.appliquerAccuse(
                processus, StatutIntegrationEnum.INTEGRE, "CPT-2026-07-000999", DATE, null);

        assertThat(decision).isInstanceOf(Decision.Contradiction.class);
        assertThat(processus.getReferenceComptable()).isEqualTo(REFERENCE);
    }

    @Test
    @DisplayName("Meme statut mais date differente sur un etat definitif : contradiction. "
            + "Comparer le seul statut laisserait passer un rapprochement comptable faux")
    void memeStatutDateDifferenteRefuse() {
        ProcessusMensuel processus = unEtatTransmis();
        TransitionIntegration.appliquerAccuse(
                processus, StatutIntegrationEnum.INTEGRE, REFERENCE, DATE, null);

        Decision decision = TransitionIntegration.appliquerAccuse(
                processus, StatutIntegrationEnum.INTEGRE, REFERENCE, DATE.plusDays(1), null);

        assertThat(decision).isInstanceOf(Decision.Contradiction.class);
        assertThat(processus.getDateTraitement()).isEqualTo(DATE);
    }

    @Test
    @DisplayName("La table couvre les trois statuts courants croises aux trois statuts recus, "
            + "et aucune combinaison n'echappe a une decision nommee")
    void aucuneCombinaisonSansDecision() {
        // Le statut courant NUL sur un etat transmis ne figure pas ici : il est
        // inatteignable par le domaine, constaterTransmissionComptable() posant le
        // drapeau et EN_ATTENTE dans le meme geste. La branche existe malgre tout dans
        // la table, comme garde-fou defensif -- et elle appliquerait, ce que la ligne
        // EN_ATTENTE ci-dessous couvre a l'identique.
        for (StatutIntegrationEnum courant : StatutIntegrationEnum.values()) {

            for (StatutIntegrationEnum recu : StatutIntegrationEnum.values()) {
                ProcessusMensuel processus = unEtatTransmis();
                TransitionIntegration.appliquerAccuse(
                        processus, courant, REFERENCE, DATE, MOTIF);

                Decision decision = TransitionIntegration.arbitrer(
                        processus, recu, "CPT-AUTRE", DATE, MOTIF);

                assertThat(decision)
                        .describedAs("courant %s, recu %s", courant, recu)
                        .isNotNull();
                // Un etat transmis ne rend jamais NonTransmis : ce verdict ne depend que
                // du drapeau, jamais du statut d'integration.
                assertThat(decision).isNotInstanceOf(Decision.NonTransmis.class);
            }
        }
    }

    // --- Montages -----------------------------------------------------------------

    private static ProcessusMensuel unEtatNonTransmis() {
        return declencherSur(8, 2026, "00002");
    }

    /**
     * Un etat cloture puis transmis : {@code transmis_comptabilite} a vrai et
     * {@code statut_integration} a {@code EN_ATTENTE}.
     *
     * <p>Il passe par les vrais gestes du verrou du Sprint 5.3 — reservation puis
     * confirmation — et non par un raccourci : la reservation exige un etat CLOTURE, et
     * un jeu d'essai qui contournerait cette exigence ne prouverait rien de ce que le
     * code fait en production.
     */
    private static ProcessusMensuel unEtatTransmis() {
        ProcessusMensuel processus = unEtatNonTransmis();
        TransitionProcessus.soumettre(processus);
        TransitionProcessus.transfererAuChefUnite(processus);
        TransitionProcessus.cloturerApresValidationChefUnite(processus);
        VerrouTransmission.reserver(processus, LocalDateTime.now());
        VerrouTransmission.confirmer(processus);
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
