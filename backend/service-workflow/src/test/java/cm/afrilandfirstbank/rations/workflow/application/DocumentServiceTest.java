package cm.afrilandfirstbank.rations.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;

import cm.afrilandfirstbank.rations.workflow.domaine.NomEtapeEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.TransitionProcessus;

/**
 * Tests de la generation du document mensuel (Sprint 4.2, US-06).
 *
 * <p>Le document est verifie par <b>extraction de texte du PDF produit</b>, pas
 * par inspection des appels a iText. Un test qui verifierait qu'on a bien appele
 * {@code document.add(...)} passerait sur un PDF illisible ; celui-ci relit le
 * fichier et constate ce qu'il contient reellement.
 *
 * <p>Le test 1 depose en outre un exemplaire dans {@code target/} pour la
 * verification visuelle de la charte, qu'aucune assertion ne peut remplacer.
 */
@DisplayName("Generation du document mensuel")
class DocumentServiceTest {

    private static final String UNITE = "00002";

    private final DocumentService service = new DocumentService();

    private final ProcessusMensuel processus = processusAvecIdentifiant(109L);

    // --- Structure du document -------------------------------------------------

    @Test
    @DisplayName("1. le document produit est un PDF valide, et un exemplaire est depose pour relecture")
    void documentPdfValide() throws IOException {
        byte[] document = service.genererEtatMensuel(processus, etatDeReference(),
                List.of(signatureAgent()));

        assertThat(document).isNotEmpty();
        assertThat(new String(document, 0, 5)).isEqualTo("%PDF-");

        // Depose pour la verification visuelle de la charte (section 8.2).
        Path echantillon = Path.of("target", "echantillon-etat-mensuel.pdf");
        Files.createDirectories(echantillon.getParent());
        Files.write(echantillon, document);
        assertThat(echantillon).exists();
    }

    @Test
    @DisplayName("2. le document compte au moins deux pages : le detail, puis la page des visas")
    void pageDesVisasToujoursPresente() throws IOException {
        byte[] document = service.genererEtatMensuel(processus, etatDeReference(),
                List.of(signatureAgent()));

        try (PdfDocument pdf = ouvrir(document)) {
            assertThat(pdf.getNumberOfPages()).isGreaterThanOrEqualTo(2);
            assertThat(PdfTextExtractor.getTextFromPage(pdf.getLastPage()))
                    .contains("VISAS ET SIGNATURES");
        }
    }

    // --- Contenu attendu par US-06 ---------------------------------------------

    @Test
    @DisplayName("3. en-tete : la periode et l'unite figurent au document")
    void enTetePeriodeEtUnite() throws IOException {
        String texte = texteDe(service.genererEtatMensuel(processus, etatDeReference(),
                List.of(signatureAgent())));

        assertThat(texte)
                .contains("ETAT MENSUEL DE PAIEMENT")
                .contains("Rations et transport de la garde armee")
                .contains("septembre 2026")
                .contains(UNITE)
                .contains("Numero de dossier")
                .contains("109");
    }

    @Test
    @DisplayName("4. detail par journee : beneficiaire, nature, session et montant")
    void detailParJournee() throws IOException {
        String texte = texteDe(service.genererEtatMensuel(processus, etatDeReference(),
                List.of(signatureAgent())));

        assertThat(texte)
                .contains("Journee du 03/09/2026")
                .contains("MBARGA Jean")
                .contains("03702009991111")
                .contains("Ration")
                .contains("Jour")
                .contains("2 500 FCFA");
    }

    @Test
    @DisplayName("5. sous-totaux journaliers et total du mois, recopies de l'etat consolide")
    void sousTotauxEtTotal() throws IOException {
        String texte = texteDe(service.genererEtatMensuel(processus, etatDeReference(),
                List.of(signatureAgent())));

        // 2500 + 1500 le 03/09, 4000 le 04/09, total 8000.
        assertThat(texte)
                .contains("Sous-total du 03/09/2026 : 4 000 FCFA")
                .contains("Sous-total du 04/09/2026 : 4 000 FCFA")
                .contains("TOTAL DU MOIS")
                .contains("8 000 FCFA");
    }

    @Test
    @DisplayName("6. le total imprime est celui de l'etat consolide, jamais un recalcul")
    void totalRecopieJamaisRecalcule() throws IOException {
        // Le total annonce (99 000) est volontairement en desaccord avec la somme
        // des lignes (8 000). Le document doit imprimer le total ANNONCE : c'est
        // celui qui commandera l'aiguillage au seuil (RG-08). Recalculer ici
        // creerait un second chemin de calcul, que la decision du Sprint 3.4
        // interdit -- et le document afficherait alors un montant different de
        // celui que porte le processus.
        EtatConsolide etat = new EtatConsolide(109L, UNITE, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1).plusMonths(1).minusDays(1), 2, 3, 2, 99_000L,
                etatDeReference().journees());

        String texte = texteDe(service.genererEtatMensuel(processus, etat,
                List.of(signatureAgent())));

        assertThat(texte).contains("99 000 FCFA");
    }

    @Test
    @DisplayName("7. une journee ouverte sans ligne est imprimee, pas escamotee")
    void journeeSansLigneImprimee() throws IOException {
        EtatConsolide etat = etat(
                journee(LocalDate.of(2026, 9, 3),
                        ligne(101L, 2500, beneficiaire("ATANGANA", "Sylvie", "03702009994444"))),
                journee(LocalDate.of(2026, 9, 4)));

        String texte = texteDe(service.genererEtatMensuel(processus, etat,
                List.of(signatureAgent())));

        assertThat(texte)
                .contains("Journee du 04/09/2026")
                .contains("Aucune prestation saisie pour cette journee");
    }

    // --- Page des visas --------------------------------------------------------
    @Test
    @DisplayName("8. a la soumission, seul le cadre de l'agent porte une mention ; les deux autres sont vides")
    void unSeulVisaALaSoumission() throws IOException {
        byte[] document = service.genererEtatMensuel(processus, etatDeReference(),
                List.of(signatureAgent()));

        try (PdfDocument pdf = ouvrir(document)) {
            String visas = PdfTextExtractor.getTextFromPage(pdf.getLastPage());

            // Les trois cadres existent, avec leur intitule.
            assertThat(visas)
                    .contains("Agent d'unite")
                    .contains("Chef d'Unite (DA)")
                    .contains("Directeur Reseau (DR)");

            // Un seul porte une mention : login, role fige, horodatage (RG-09).
            assertThat(visas)
                    .contains("jean_mbarga")
                    .contains("Role : AGENT_UNITE")
                    .contains("Signe le 01/09/2026 a 10:24");

            // Compter les horodatages est la mesure fiable du nombre de signatures :
            // c'est la ligne que RG-09 exige et que l'elargissement du cadre au
            // Sprint 4.2 a rendue possible.
            assertThat(occurrences(visas, "Signe le ")).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("9. les trois cadres existent meme sans aucune signature : la geometrie ne depend pas du circuit")
    void troisCadresMemeSansSignature() throws IOException {
        byte[] document = service.genererEtatMensuel(processus, etatDeReference(), List.of());

        try (PdfDocument pdf = ouvrir(document)) {
            String visas = PdfTextExtractor.getTextFromPage(pdf.getLastPage());

            assertThat(visas)
                    .contains("Agent d'unite")
                    .contains("Chef d'Unite (DA)")
                    .contains("Directeur Reseau (DR)");
            assertThat(occurrences(visas, "Signe le ")).isZero();
        }
    }

    @Test
    @DisplayName("10. un cadre non signe ne porte AUCUN texte que l'estampage viendrait recouvrir")
    void cadreVideReellementVide() throws IOException {
        // L'estampage d'un PDF ajoute du contenu sans jamais en retirer. Une
        // mention « en attente » gravee ici resterait lisible SOUS la signature du
        // Chef d'Unite au sous-sprint 4.3, et les deux se liraient ensemble.
        byte[] document = service.genererEtatMensuel(processus, etatDeReference(),
                List.of(signatureAgent()));

        try (PdfDocument pdf = ouvrir(document)) {
            assertThat(PdfTextExtractor.getTextFromPage(pdf.getLastPage()))
                    .doesNotContain("En attente");
        }
    }

    @Test
    @DisplayName("11. une liste de signatures nulle ne fait pas echouer la generation")
    void signaturesNullesTolerees() throws IOException {
        byte[] document = service.genererEtatMensuel(processus, etatDeReference(), null);

        assertThat(texteDe(document)).contains("VISAS ET SIGNATURES");
    }

    // --- Geometrie des cadres, partagee avec l'estampage des sprints 4.3 et 4.4 --

    @Test
    @DisplayName("12. chaque etape a son cadre, et les trois ne se chevauchent jamais")
    void cadresDisjoints() {
        Rectangle agent = GabaritDocument.cadreVisa(NomEtapeEnum.SOUMISSION_AGENT);
        Rectangle chef = GabaritDocument.cadreVisa(NomEtapeEnum.VALIDATION_DA);
        Rectangle directeur = GabaritDocument.cadreVisa(NomEtapeEnum.VALIDATION_DR);

        // Un chevauchement ferait ecrire la signature du chef d'unite par-dessus
        // celle de l'agent au sous-sprint 4.3, sans qu'aucun test ne le voie.
        assertThat(agent.getY()).isGreaterThan(chef.getY() + chef.getHeight());
        assertThat(chef.getY()).isGreaterThan(directeur.getY() + directeur.getHeight());

        // Tous restent dans la page, au-dessus du pied de page.
        assertThat(directeur.getY()).isGreaterThan(GabaritDocument.MARGE);
        assertThat(agent.getY() + agent.getHeight())
                .isLessThan(GabaritDocument.FORMAT.getHeight() - GabaritDocument.MARGE);
    }

    @Test
    @DisplayName("13. l'ordre des cadres suit l'ordre du circuit")
    void ordreDesCadresConformeAuCircuit() {
        // Si NomEtapeEnum etait reorganise, les signatures changeraient de cadre
        // en silence : la validation du DR s'inscrirait a la place de l'agent.
        assertThat(NomEtapeEnum.values()).containsExactly(
                NomEtapeEnum.SOUMISSION_AGENT,
                NomEtapeEnum.VALIDATION_DA,
                NomEtapeEnum.VALIDATION_DR);

        assertThat(GabaritDocument.intituleVisa(NomEtapeEnum.SOUMISSION_AGENT))
                .isEqualTo("Agent d'unite");
    }

    // --- Convention de nommage --------------------------------------------------

    @Test
    @DisplayName("14. le nom du fichier porte l'unite, la periode triable et l'identifiant du processus")
    void conventionDeNommage() {
        assertThat(NommageDocument.cheminRelatif(processus))
                .isEqualTo("2026/09/etat-rations-00002-20260901-p109.pdf");
        assertThat(NommageDocument.nomFichier(processus))
                .isEqualTo("etat-rations-00002-20260901-p109.pdf");
    }

    @Test
    @DisplayName("15. deux etats de la meme unite et periode ne partagent JAMAIS un nom de fichier")
    void aucuneCollisionEntreEtatsDeLaMemePeriode() {
        // CLAUDE.md section 4 n'impose l'unicite que sur le processus NORMAL :
        // plusieurs COMPLEMENTAIRE sont possibles sur le meme couple unite /
        // periode (Sprint 6bis). Sans l'identifiant dans le nom, le second
        // ecraserait le PDF signe du premier.
        String normal = NommageDocument.cheminRelatif(processusAvecIdentifiant(109L));
        String complementaire = NommageDocument.cheminRelatif(processusAvecIdentifiant(240L));

        assertThat(normal).isNotEqualTo(complementaire);
    }

    @Test
    @DisplayName("16. le mois est sur deux chiffres, pour que le tri alphabetique soit chronologique")
    void triChronologiqueParLeNom() {
        ProcessusMensuel janvier = processus(1, 2027, 240L);
        ProcessusMensuel septembre = processus(9, 2026, 109L);

        assertThat(NommageDocument.cheminRelatif(septembre))
                .isLessThan(NommageDocument.cheminRelatif(janvier));
    }

    // --- Fabriques ---------------------------------------------------------------

    /** Nombre d'occurrences d'un fragment, pour compter des signatures. */
    private int occurrences(String texte, String fragment) {
        return texte.split(java.util.regex.Pattern.quote(fragment), -1).length - 1;
    }

    private PdfDocument ouvrir(byte[] document) throws IOException {
        return new PdfDocument(new PdfReader(new ByteArrayInputStream(document)));
    }

    private String texteDe(byte[] document) throws IOException {
        StringBuilder texte = new StringBuilder();
        try (PdfDocument pdf = ouvrir(document)) {
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                texte.append(PdfTextExtractor.getTextFromPage(pdf.getPage(page))).append('\n');
            }
        }
        return texte.toString();
    }

    private MentionSignature signatureAgent() {
        return new MentionSignature(NomEtapeEnum.SOUMISSION_AGENT, "jean_mbarga",
                "AGENT_UNITE", LocalDateTime.of(2026, 9, 1, 10, 24));
    }

    private ProcessusMensuel processusAvecIdentifiant(Long id) {
        return processus(9, 2026, id);
    }

    /**
     * Un processus avec un identifiant, que seule la persistance attribue
     * normalement. La reflexion evite d'ouvrir un mutateur d'identifiant sur
     * l'entite pour les seuls besoins du test.
     */
    private ProcessusMensuel processus(int mois, int annee, Long id) {
        ProcessusMensuel cree = declencherSur(mois, annee, UNITE);
        try {
            var champ = ProcessusMensuel.class.getDeclaredField("id");
            champ.setAccessible(true);
            champ.set(cree, id);
        } catch (ReflectiveOperationException impossible) {
            throw new IllegalStateException("Le champ id de ProcessusMensuel a change de nom.",
                    impossible);
        }
        return cree;
    }

    private EtatConsolide etatDeReference() {
        return etat(
                journee(LocalDate.of(2026, 9, 3),
                        ligne(101L, 2500, beneficiaire("MBARGA", "Jean", "03702009991111")),
                        ligne(102L, 1500, beneficiaire("ESSAMA", "Paul", "03702009992222"))),
                journee(LocalDate.of(2026, 9, 4),
                        ligne(103L, 4000, beneficiaire("MBARGA", "Jean", "03702009991111"))));
    }

    private EtatConsolide etat(EtatConsolide.Journee... journees) {
        List<EtatConsolide.Journee> liste = List.of(journees);
        int nombreLignes = liste.stream()
                .mapToInt(journee -> journee.lignes().size()).sum();
        long total = liste.stream()
                .mapToLong(journee -> journee.sousTotalFcfa()).sum();
        return new EtatConsolide(109L, UNITE, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1).plusMonths(1).minusDays(1), liste.size(), nombreLignes, 2, total, liste);
    }

    private EtatConsolide.Journee journee(LocalDate dateJour, EtatConsolide.Ligne... lignes) {
        List<EtatConsolide.Ligne> liste = List.of(lignes);
        long sousTotal = liste.stream().mapToLong(EtatConsolide.Ligne::montantApplique).sum();
        return new EtatConsolide.Journee(11L, dateJour, "EN_SAISIE", liste.size(), sousTotal, liste);
    }

    private EtatConsolide.Ligne ligne(Long id, Integer montant,
            EtatConsolide.Beneficiaire beneficiaire) {
        return new EtatConsolide.Ligne(id, 11L, beneficiaire.id(), beneficiaire,
                "RATION", "JOUR", montant, 12L, LocalDateTime.of(2026, 9, 3, 9, 0));
    }

    private EtatConsolide.Beneficiaire beneficiaire(String nom, String prenom, String compte) {
        return new EtatConsolide.Beneficiaire(55L, nom, prenom, compte, "00002");
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
