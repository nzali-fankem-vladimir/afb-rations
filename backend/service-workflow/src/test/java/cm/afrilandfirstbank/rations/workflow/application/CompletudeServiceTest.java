package cm.afrilandfirstbank.rations.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import cm.afrilandfirstbank.rations.workflow.domaine.CodeManqueEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.TransitionProcessus;

/**
 * Tests du controle de completude (Sprint 4.2, US-07, CT-13).
 *
 * <p>Aucune base, aucun reseau, aucun contexte Spring : {@link CompletudeService}
 * ne lit que ses deux parametres. C'est precisement ce qui permet d'eprouver les
 * cas limites — reponse tronquee, date absente, deux cents lignes fautives — qui
 * seraient penibles a fabriquer contre un service Saisie reel.
 */
@DisplayName("Controle de completude avant soumission")
class CompletudeServiceTest {

    private static final int MOIS = 9;
    private static final int ANNEE = 2026;
    private static final String UNITE = "00002";

    private final CompletudeService service = new CompletudeService();

    private final ProcessusMensuel processus = declencherSur(MOIS, ANNEE, UNITE);

    // --- 1. Cas nominal --------------------------------------------------------

    @Test
    @DisplayName("1. un etat complet ne produit aucun manque")
    void etatCompletNeProduitAucunManque() {
        EtatConsolide etat = etat(journee(LocalDate.of(2026, 9, 3),
                ligne(101L, 2500, beneficiaire("MBARGA", "Jean", "03702009991111"))));

        ResultatCompletude resultat = service.verifier(processus, etat);

        assertThat(resultat.estComplet()).isTrue();
        assertThat(resultat.manques()).isEmpty();
    }

    // --- 2. Controle 1 : etat vide ---------------------------------------------

    @Nested
    @DisplayName("Controle 1 - etat vide")
    class EtatVide {

        @Test
        @DisplayName("2. aucune journee : refus, avec la periode et l'unite nommees")
        void aucuneJournee() {
            EtatConsolide etat = new EtatConsolide(1L, UNITE, LocalDate.of(ANNEE, MOIS, 1), LocalDate.of(ANNEE, MOIS, 1).plusMonths(1).minusDays(1), 0, 0, 0, 0L, List.of());

            ResultatCompletude resultat = service.verifier(processus, etat);

            assertThat(resultat.estComplet()).isFalse();
            assertThat(resultat.manques()).hasSize(1);
            assertThat(resultat.manques().get(0).code()).isEqualTo(CodeManqueEnum.ETAT_VIDE);
            assertThat(resultat.manques().get(0).message())
                    .contains("09/2026")
                    .contains(UNITE)
                    .contains("Saisissez au moins une prestation");
        }

        @Test
        @DisplayName("3. compteur de lignes absent de la reponse : refus, jamais un laissez-passer")
        void compteurAbsent() {
            EtatConsolide etat = new EtatConsolide(
                    1L, UNITE, LocalDate.of(ANNEE, MOIS, 1), LocalDate.of(ANNEE, MOIS, 1).plusMonths(1).minusDays(1), 0, null, 0, 0L, List.of());

            ResultatCompletude resultat = service.verifier(processus, etat);

            assertThat(codes(resultat)).containsExactly(CodeManqueEnum.ETAT_VIDE);
        }

        @Test
        @DisplayName("4. journees ouvertes mais sans aucune ligne : refus")
        void journeesSansLigne() {
            EtatConsolide etat = etat(journee(LocalDate.of(2026, 9, 3)));

            ResultatCompletude resultat = service.verifier(processus, etat);

            assertThat(codes(resultat)).containsExactly(CodeManqueEnum.ETAT_VIDE);
        }

        @Test
        @DisplayName("5. compteur non nul mais aucun detail : refus, et le message dit l'incoherence")
        void compteurEnDesaccordAvecLeDetail() {
            EtatConsolide etat = new EtatConsolide(1L, UNITE, LocalDate.of(ANNEE, MOIS, 1), LocalDate.of(ANNEE, MOIS, 1).plusMonths(1).minusDays(1), 0, 3, 1, 7500L, List.of());

            ResultatCompletude resultat = service.verifier(processus, etat);

            assertThat(codes(resultat)).containsExactly(CodeManqueEnum.ETAT_VIDE);
            assertThat(resultat.manques().get(0).message())
                    .contains("annonce 3 ligne(s) mais n'en detaille aucune")
                    // Le message n'accuse pas l'agent d'un oubli qui n'est pas le sien.
                    .doesNotContain("Saisissez au moins une prestation");
        }

        @Test
        @DisplayName("6. l'etat vide court-circuite : un seul manque, jamais quatre")
        void etatVideCourtCircuiteLesAutresControles() {
            EtatConsolide etat = new EtatConsolide(1L, UNITE, LocalDate.of(ANNEE, MOIS, 1), LocalDate.of(ANNEE, MOIS, 1).plusMonths(1).minusDays(1), 0, 0, 0, 0L, List.of());

            ResultatCompletude resultat = service.verifier(processus, etat);

            assertThat(resultat.manques()).hasSize(1);
        }
    }

    // --- 3. Controle 2 : coherence de periode ----------------------------------

    @Nested
    @DisplayName("Controle 2 - coherence de periode")
    class Periode {

        @Test
        @DisplayName("7. une ligne sur une journee d'un autre mois : refus nommant la journee")
        void ligneSurUnAutreMois() {
            EtatConsolide etat = etat(
                    journee(LocalDate.of(2026, 9, 3),
                            ligne(101L, 2500, beneficiaire("MBARGA", "Jean", "03702009991111"))),
                    journee(LocalDate.of(2026, 3, 5),
                            ligne(102L, 2500, beneficiaire("ESSAMA", "Paul", "03702009992222"))));

            ResultatCompletude resultat = service.verifier(processus, etat);

            assertThat(codes(resultat)).containsExactly(CodeManqueEnum.LIGNE_HORS_PERIODE);
            assertThat(resultat.manques().get(0).message())
                    .contains("1 ligne(s)")
                    .contains("05/03/2026")
                    .contains("09/2026")
                    // Le refus nomme le recours : sans cela l'agent reste sans issue.
                    .contains("Supprimez ces lignes");
        }

        @Test
        @DisplayName("8. meme mois, autre annee : hors periode aussi")
        void memeMoisAutreAnnee() {
            EtatConsolide etat = etat(journee(LocalDate.of(2025, 9, 3),
                    ligne(101L, 2500, beneficiaire("NKOLO", "Claire", "03702009993333"))));

            ResultatCompletude resultat = service.verifier(processus, etat);

            assertThat(codes(resultat)).containsExactly(CodeManqueEnum.LIGNE_HORS_PERIODE);
            assertThat(resultat.manques().get(0).message()).contains("03/09/2025");
        }

        @Test
        @DisplayName("9. LE PIEGE : une journee hors periode mais VIDE ne bloque pas")
        void journeeHorsPeriodeMaisVideNeBloquePas() {
            // Il n'existe aucun DELETE /saisie/fiches/{id} au contrat d'API. Bloquer
            // sur une journee vide egaree enfermerait l'agent : il ne pourrait plus
            // jamais soumettre son mois. Le controle porte donc sur les LIGNES.
            EtatConsolide etat = etat(
                    journee(LocalDate.of(2026, 9, 3),
                            ligne(101L, 2500, beneficiaire("ATANGANA", "Sylvie", "03702009994444"))),
                    journee(LocalDate.of(2026, 3, 5)));

            ResultatCompletude resultat = service.verifier(processus, etat);

            assertThat(resultat.estComplet()).isTrue();
        }

        @Test
        @DisplayName("10. une journee sans date porte des lignes : refus, avec un message distinct")
        void journeeSansDate() {
            EtatConsolide etat = etat(journee(null,
                    ligne(101L, 2500, beneficiaire("TCHINDA", "Agnes", "03702009995555"))));

            ResultatCompletude resultat = service.verifier(processus, etat);

            assertThat(codes(resultat)).containsExactly(CodeManqueEnum.LIGNE_HORS_PERIODE);
            assertThat(resultat.manques().get(0).message())
                    .contains("journee sans date")
                    // On n'accuse pas l'agent d'une anomalie de donnees.
                    .contains("signalez-la a l'administrateur")
                    .doesNotContain("Supprimez ces lignes");
        }

        @Test
        @DisplayName("11. deux lignes le meme jour fautif : la journee n'est nommee qu'une fois")
        void journeeFautiveNommeeUneSeuleFois() {
            EtatConsolide etat = etat(journee(LocalDate.of(2026, 3, 5),
                    ligne(101L, 2500, beneficiaire("FOUDA", "Marie", "03702009996666")),
                    ligne(102L, 1500, beneficiaire("FOUDA", "Marie", "03702009996666"))));

            ResultatCompletude resultat = service.verifier(processus, etat);

            String message = resultat.manques().get(0).message();
            assertThat(message).contains("2 ligne(s)");
            assertThat(message.split("05/03/2026", -1)).hasSize(2); // une seule occurrence
        }
    }

    // --- 4. Controle 3 : montant present ---------------------------------------

    @Nested
    @DisplayName("Controle 3 - montant applicable")
    class Montant {

        @Test
        @DisplayName("12. montant absent : refus nommant la ligne et sa journee")
        void montantAbsent() {
            EtatConsolide etat = etat(journee(LocalDate.of(2026, 9, 3),
                    ligne(412L, null, beneficiaire("BELINGA", "Rose", "03702009997777"))));

            ResultatCompletude resultat = service.verifier(processus, etat);

            assertThat(codes(resultat)).containsExactly(CodeManqueEnum.LIGNE_SANS_MONTANT);
            assertThat(resultat.manques().get(0).message())
                    .contains("ligne n° 412")
                    .contains("03/09/2026")
                    .contains("RG-03");
        }

        @Test
        @DisplayName("13. montant a zero : refus - zero n'est pas un tarif, c'est une resolution manquee")
        void montantAZero() {
            EtatConsolide etat = etat(journee(LocalDate.of(2026, 9, 3),
                    ligne(412L, 0, beneficiaire("ONANA", "Marthe", "03702009998888"))));

            assertThat(codes(service.verifier(processus, etat)))
                    .containsExactly(CodeManqueEnum.LIGNE_SANS_MONTANT);
        }

        @Test
        @DisplayName("14. montant negatif : refus")
        void montantNegatif() {
            EtatConsolide etat = etat(journee(LocalDate.of(2026, 9, 3),
                    ligne(412L, -2500, beneficiaire("NDONGO", "Pierre", "03702009999999"))));

            assertThat(codes(service.verifier(processus, etat)))
                    .containsExactly(CodeManqueEnum.LIGNE_SANS_MONTANT);
        }
    }

    // --- 5. Controle 4 : compte courant ----------------------------------------

    @Nested
    @DisplayName("Controle 4 - numero de compte courant")
    class CompteCourant {

        @Test
        @DisplayName("15. compte absent : refus nommant le beneficiaire")
        void compteAbsent() {
            EtatConsolide etat = etat(journee(LocalDate.of(2026, 9, 3),
                    ligne(412L, 2500, beneficiaire("EYENGA", "Louise", null))));

            ResultatCompletude resultat = service.verifier(processus, etat);

            assertThat(codes(resultat)).containsExactly(CodeManqueEnum.BENEFICIAIRE_SANS_COMPTE);
            assertThat(resultat.manques().get(0).message())
                    .contains("EYENGA Louise")
                    .contains("ligne n° 412")
                    .contains("impayable");
        }

        @Test
        @DisplayName("16. compte fait d'espaces : refus, comme un compte absent")
        void compteEnBlanc() {
            EtatConsolide etat = etat(journee(LocalDate.of(2026, 9, 3),
                    ligne(412L, 2500, beneficiaire("MBARGA", "Jean", "   "))));

            assertThat(codes(service.verifier(processus, etat)))
                    .containsExactly(CodeManqueEnum.BENEFICIAIRE_SANS_COMPTE);
        }

        @Test
        @DisplayName("17. beneficiaire entierement absent de la ligne : meme refus")
        void beneficiaireAbsent() {
            EtatConsolide etat = etat(journee(LocalDate.of(2026, 9, 3), ligne(412L, 2500, null)));

            ResultatCompletude resultat = service.verifier(processus, etat);

            assertThat(codes(resultat)).containsExactly(CodeManqueEnum.BENEFICIAIRE_SANS_COMPTE);
            assertThat(resultat.manques().get(0).message()).contains("beneficiaire absent");
        }
    }

    // --- 6. Cumul, volume, robustesse ------------------------------------------

    @Nested
    @DisplayName("Cumul et volume")
    class CumulEtVolume {

        @Test
        @DisplayName("18. trois manques cumules, dans un ordre stable")
        void troisManquesCumules() {
            EtatConsolide etat = etat(
                    journee(LocalDate.of(2026, 3, 5),
                            ligne(101L, 2500, beneficiaire("MBARGA", "Jean", "03702009991111"))),
                    journee(LocalDate.of(2026, 9, 4),
                            ligne(102L, null, beneficiaire("ESSAMA", "Paul", "03702009992222")),
                            ligne(103L, 1500, beneficiaire("NKOLO", "Claire", null))));

            ResultatCompletude resultat = service.verifier(processus, etat);

            assertThat(codes(resultat)).containsExactly(
                    CodeManqueEnum.LIGNE_HORS_PERIODE,
                    CodeManqueEnum.LIGNE_SANS_MONTANT,
                    CodeManqueEnum.BENEFICIAIRE_SANS_COMPTE);
        }

        @Test
        @DisplayName("19. deux cents lignes fautives produisent UN manque, pas deux cents")
        void unManqueParControleJamaisUnParLigne() {
            List<EtatConsolide.Ligne> lignes = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                lignes.add(ligne(1000L + i, null,
                        beneficiaire("ONANA", "Marthe", "03702009998888")));
            }
            EtatConsolide etat = etat(journee(LocalDate.of(2026, 9, 3),
                    lignes.toArray(new EtatConsolide.Ligne[0])));

            ResultatCompletude resultat = service.verifier(processus, etat);

            assertThat(resultat.manques()).hasSize(1);
            assertThat(resultat.manques().get(0).message())
                    // Le nombre total reste annonce : la troncature ne cache pas l'ampleur.
                    .contains("200 ligne(s)")
                    .contains("et 195 autre(s)");
        }

        @Test
        @DisplayName("20. journees nulles dans la reponse : refus propre, jamais une NullPointerException")
        void reponseTronqueeNeLevePas() {
            EtatConsolide etat = new EtatConsolide(1L, UNITE, LocalDate.of(ANNEE, MOIS, 1), LocalDate.of(ANNEE, MOIS, 1).plusMonths(1).minusDays(1), 2, 3, 1, 7500L, null);

            ResultatCompletude resultat = service.verifier(processus, etat);

            assertThat(codes(resultat)).containsExactly(CodeManqueEnum.ETAT_VIDE);
        }

        @Test
        @DisplayName("21. le vocabulaire des manques compte exactement les quatre controles arbitres")
        void vocabulaireFerme() {
            // Un cinquieme controle devient alors un geste visible dans ce test,
            // pas une chaine glissee au fil de l'eau. Notamment : la journee ouverte
            // sans ligne a ete ECARTEE (absence de DELETE /saisie/fiches), et le
            // statut ENREGISTREE des fiches aussi (aucune fiche ne l'atteint jamais).
            assertThat(CodeManqueEnum.values()).containsExactly(
                    CodeManqueEnum.ETAT_VIDE,
                    CodeManqueEnum.LIGNE_HORS_PERIODE,
                    CodeManqueEnum.LIGNE_SANS_MONTANT,
                    CodeManqueEnum.BENEFICIAIRE_SANS_COMPTE);
        }
    }

    // --- Fabriques de jeux d'essai ---------------------------------------------

    private static List<CodeManqueEnum> codes(ResultatCompletude resultat) {
        return resultat.manques().stream().map(ManqueCompletude::code).toList();
    }

    private EtatConsolide etat(EtatConsolide.Journee... journees) {
        List<EtatConsolide.Journee> liste = List.of(journees);
        int nombreLignes = liste.stream()
                .mapToInt(journee -> journee.lignes() == null ? 0 : journee.lignes().size())
                .sum();
        long total = liste.stream()
                .flatMap(journee -> journee.lignes() == null
                        ? java.util.stream.Stream.<EtatConsolide.Ligne>empty()
                        : journee.lignes().stream())
                .mapToLong(ligne -> ligne.montantApplique() == null ? 0 : ligne.montantApplique())
                .sum();
        return new EtatConsolide(1L, UNITE, LocalDate.of(ANNEE, MOIS, 1), LocalDate.of(ANNEE, MOIS, 1).plusMonths(1).minusDays(1), liste.size(), nombreLignes, 1, total, liste);
    }

    private EtatConsolide.Journee journee(LocalDate dateJour, EtatConsolide.Ligne... lignes) {
        List<EtatConsolide.Ligne> liste = List.of(lignes);
        long sousTotal = liste.stream()
                .mapToLong(ligne -> ligne.montantApplique() == null ? 0 : ligne.montantApplique())
                .sum();
        return new EtatConsolide.Journee(11L, dateJour, "EN_SAISIE", liste.size(), sousTotal, liste);
    }

    private EtatConsolide.Ligne ligne(Long id, Integer montant, EtatConsolide.Beneficiaire beneficiaire) {
        return new EtatConsolide.Ligne(id, 11L, beneficiaire == null ? null : beneficiaire.id(),
                beneficiaire, "RATION", "JOUR", montant, 12L,
                LocalDateTime.of(2026, 9, 3, 9, 0));
    }

    private EtatConsolide.Beneficiaire beneficiaire(String nom, String prenom, String numCompteCourant) {
        return new EtatConsolide.Beneficiaire(55L, nom, prenom, numCompteCourant, "00002");
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
