package cm.afrilandfirstbank.rations.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;

import cm.afrilandfirstbank.rations.workflow.domaine.NomEtapeEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.PieceJointe;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.RoleEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.TransitionProcessus;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.DocumentNonProduitException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.stockage.StockageDocumentsFichier;

/**
 * Tests du mecanisme de signature (Sprint 4.2, etape 5, RG-09).
 *
 * <p><b>Le stockage est reel</b>, sur un repertoire temporaire : c'est la seule
 * facon d'eprouver la chaine complete — composition, ecriture confirmee,
 * relecture, estampage, remplacement. Un stockage simule prouverait qu'on l'a
 * appele, pas que le document survit a l'operation.
 *
 * <p>Le coeur de ce fichier est la serie 2 : <b>l'enrichissement ne doit jamais
 * faire perdre une signature deja apposee</b>. C'est le point de vigilance
 * section 10 du guide, et rien d'autre qu'un test qui relit le PDF ne peut le
 * garantir.
 */
@DisplayName("Signature et enrichissement du document")
class SignatureServiceTest {

    private static final String UNITE = "00002";
    private static final String CHEMIN = "2026/09/etat-rations-00002-20260901-p109.pdf";

    private static final LocalDateTime T_AGENT = LocalDateTime.of(2026, 9, 1, 10, 24);
    private static final LocalDateTime T_CHEF = LocalDateTime.of(2026, 9, 2, 8, 5);
    private static final LocalDateTime T_DIRECTEUR = LocalDateTime.of(2026, 9, 3, 16, 40);

    @TempDir
    Path racine;

    private StockageDocumentsFichier stockage;
    private SignatureService service;

    private final ProcessusMensuel processus = processus(109L);

    @BeforeEach
    void preparer() {
        stockage = new StockageDocumentsFichier(racine.toString());
        service = new SignatureService(new DocumentService(), stockage);
    }

    // --- 1. Premiere signature -------------------------------------------------

    @Test
    @DisplayName("1. la premiere signature cree le document, l'ecrit, et rend sa preuve d'ecriture")
    void premiereSignature() {
        ResultatSignature resultat = service.creerEtSigner(
                processus, etatDeReference(), agent(), T_AGENT);

        assertThat(resultat.document().cheminRelatif()).isEqualTo(CHEMIN);
        assertThat(resultat.document().octetsEcrits()).isPositive();
        assertThat(racine.resolve(CHEMIN)).exists();

        assertThat(resultat.mention().etape()).isEqualTo(NomEtapeEnum.SOUMISSION_AGENT);
        assertThat(resultat.mention().login()).isEqualTo("jean_mbarga");
        assertThat(resultat.mention().role()).isEqualTo("AGENT_UNITE");
        assertThat(resultat.mention().horodatage()).isEqualTo(T_AGENT);
    }

    @Test
    @DisplayName("2. la mention est reellement imprimee sur le document, pas seulement rendue")
    void mentionImprimeeSurLeDocument() throws IOException {
        service.creerEtSigner(processus, etatDeReference(), agent(), T_AGENT);

        assertThat(visasDuFichier())
                .contains("jean_mbarga")
                .contains("Rôle : AGENT_UNITE")
                .contains("Signé le 01/09/2026 à 10:24");
    }

    @Test
    @DisplayName("3. l'empreinte est bien celle du fichier ecrit sur le disque")
    void empreinteCorrespondAuFichier() throws IOException {
        ResultatSignature resultat = service.creerEtSigner(
                processus, etatDeReference(), agent(), T_AGENT);

        // C'est tout l'interet de l'empreinte : elle doit pouvoir etre recalculee
        // sur le fichier archive, sans rien d'autre que lui.
        assertThat(resultat.empreinte())
                .startsWith("SHA-256:")
                .isEqualTo(SignatureService.empreinte(Files.readAllBytes(racine.resolve(CHEMIN))));
        assertThat(resultat.empreinte()).hasSize("SHA-256:".length() + 64);
    }

    @Test
    @DisplayName("4. une seconde creation sur le meme processus est refusee, le document signe est intact")
    void secondeCreationRefusee() throws IOException {
        service.creerEtSigner(processus, etatDeReference(), agent(), T_AGENT);
        byte[] premier = Files.readAllBytes(racine.resolve(CHEMIN));

        assertThatThrownBy(() -> service.creerEtSigner(
                processus, etatDeReference(), agent(), T_AGENT))
                .isInstanceOf(DocumentNonProduitException.class)
                .hasMessageContaining("occupe deja le chemin");

        assertThat(Files.readAllBytes(racine.resolve(CHEMIN))).isEqualTo(premier);
    }

    // --- 2. Enrichissement : le coeur du sous-sprint ----------------------------

    @Test
    @DisplayName("5. LE POINT CENTRAL : enrichir CONSERVE la signature precedente")
    void enrichirConserveLaSignaturePrecedente() throws IOException {
        service.creerEtSigner(processus, etatDeReference(), agent(), T_AGENT);

        service.enrichirEtSigner(pieceJointe(), chefUnite(), NomEtapeEnum.VALIDATION_DA, T_CHEF, false);

        String visas = visasDuFichier();
        assertThat(visas)
                // celle de l'agent, toujours la
                .contains("jean_mbarga")
                .contains("Signé le 01/09/2026 à 10:24")
                // et celle du chef d'unite, ajoutee
                .contains("paul_essama")
                .contains("Rôle : CHEF_UNITE_DA")
                .contains("Signé le 02/09/2026 à 08:05");

        assertThat(occurrences(visas, "Signé le ")).isEqualTo(2);
    }

    @Test
    @DisplayName("6. les trois signatures du circuit coexistent sur le meme document")
    void troisSignaturesCoexistent() throws IOException {
        service.creerEtSigner(processus, etatDeReference(), agent(), T_AGENT);
        // Le chef d'unite aiguille vers le directeur reseau : son cadre est trace.
        service.enrichirEtSigner(pieceJointe(), chefUnite(), NomEtapeEnum.VALIDATION_DA, T_CHEF, true);
        service.enrichirEtSigner(pieceJointe(), directeurReseau(), NomEtapeEnum.VALIDATION_DR,
                T_DIRECTEUR, false);

        String visas = visasDuFichier();
        assertThat(visas)
                .contains("jean_mbarga")
                .contains("paul_essama")
                .contains("sylvie_atangana");
        assertThat(occurrences(visas, "Signé le ")).isEqualTo(3);
    }

    @Test
    @DisplayName("6 bis. le cadre du directeur reseau n'apparait QUE si l'etat est aiguille vers lui")
    void cadreDirecteurReseauConditionnel() throws IOException {
        service.creerEtSigner(processus, etatDeReference(), agent(), T_AGENT);
        assertThat(visasDuFichier()).doesNotContain("Directeur réseau");

        // Cloture au premier niveau (sous le seuil) : le cadre n'est jamais trace.
        service.enrichirEtSigner(pieceJointe(), chefUnite(), NomEtapeEnum.VALIDATION_DA, T_CHEF, false);
        assertThat(visasDuFichier()).doesNotContain("Directeur réseau");
    }

    @Test
    @DisplayName("6 ter. aiguille vers le DR, le cadre apparait des la validation du chef d'unite, vide jusqu'a son visa")
    void cadreDirecteurReseauTraceALAiguillage() throws IOException {
        service.creerEtSigner(processus, etatDeReference(), agent(), T_AGENT);

        service.enrichirEtSigner(pieceJointe(), chefUnite(), NomEtapeEnum.VALIDATION_DA, T_CHEF, true);

        String visas = visasDuFichier();
        assertThat(visas).contains("Directeur réseau");
        // Deux mentions seulement : le cadre du DR est trace mais reste vide.
        assertThat(occurrences(visas, "Signé le ")).isEqualTo(2);
    }

    @Test
    @DisplayName("7. le detail de l'etat survit intact a l'enrichissement")
    void detailIntactApresEnrichissement() throws IOException {
        service.creerEtSigner(processus, etatDeReference(), agent(), T_AGENT);
        service.enrichirEtSigner(pieceJointe(), chefUnite(), NomEtapeEnum.VALIDATION_DA, T_CHEF, false);

        // Une regeneration aurait pu produire un document coherent en apparence :
        // on verifie donc aussi que le contenu metier n'a pas bouge d'un chiffre.
        String texte = texteDuFichier();
        assertThat(texte)
                .contains("03/09/2026")
                .contains("MBARGA Jean")
                .contains("Sous-total du 03/09/2026")
                .contains("8 000 FCFA");
    }

    @Test
    @DisplayName("8. l'enrichissement n'ajoute aucune page : il estampe, il ne recompose pas")
    void aucunePageAjoutee() throws IOException {
        service.creerEtSigner(processus, etatDeReference(), agent(), T_AGENT);
        int avant = nombreDePages();

        service.enrichirEtSigner(pieceJointe(), chefUnite(), NomEtapeEnum.VALIDATION_DA, T_CHEF, false);

        assertThat(nombreDePages()).isEqualTo(avant);
    }

    // --- 3. Ce que l'empreinte devient au fil des signatures --------------------

    @Test
    @DisplayName("9. l'empreinte change a chaque signature, et suit toujours le fichier courant")
    void empreinteSuitLeFichierCourant() throws IOException {
        ResultatSignature premiere = service.creerEtSigner(
                processus, etatDeReference(), agent(), T_AGENT);

        ResultatSignature seconde = service.enrichirEtSigner(
                pieceJointe(), chefUnite(), NomEtapeEnum.VALIDATION_DA, T_CHEF, false);

        assertThat(seconde.empreinte()).isNotEqualTo(premiere.empreinte());
        assertThat(seconde.empreinte())
                .isEqualTo(SignatureService.empreinte(Files.readAllBytes(racine.resolve(CHEMIN))));
    }

    @Test
    @DisplayName("10. CONSEQUENCE ASSUMEE : une empreinte anterieure ne vaut plus pour le fichier enrichi")
    void empreinteAnterieureNePlusVerifiable() throws IOException {
        ResultatSignature premiere = service.creerEtSigner(
                processus, etatDeReference(), agent(), T_AGENT);

        service.enrichirEtSigner(pieceJointe(), chefUnite(), NomEtapeEnum.VALIDATION_DA, T_CHEF, false);

        // Ce n'est pas un defaut, c'est la nature d'un document enrichi : l'etat
        // intermediaire n'existe plus. Seule la DERNIERE empreinte est verifiable,
        // et c'est l'empreinte finale, apres cloture, qui scelle le justificatif.
        // Le test existe pour que personne ne construise un controle sur les
        // empreintes intermediaires en croyant qu'elles restent opposables.
        assertThat(SignatureService.empreinte(Files.readAllBytes(racine.resolve(CHEMIN))))
                .isNotEqualTo(premiere.empreinte());
    }

    // --- 4. Refus ---------------------------------------------------------------

    @Test
    @DisplayName("11. enrichir un document absent echoue, sans rien ecrire")
    void enrichirUnDocumentAbsent() {
        assertThatThrownBy(() -> service.enrichirEtSigner(
                pieceJointe(), chefUnite(), NomEtapeEnum.VALIDATION_DA, T_CHEF, false))
                .isInstanceOf(DocumentNonProduitException.class)
                .hasMessageContaining("n'a pas pu etre relu");

        assertThat(racine.resolve(CHEMIN)).doesNotExist();
    }

    @Test
    @DisplayName("12. au-dela de trois signatures, la piece jointe refuse plutot que de compter faux")
    void auDelaDeTroisSignatures() {
        PieceJointe piece = pieceJointe(); // nait a une signature
        piece.enregistrerSignatureSupplementaire();
        piece.enregistrerSignatureSupplementaire();
        assertThat(piece.getNombreSignatures()).isEqualTo(3);

        assertThatThrownBy(piece::enregistrerSignatureSupplementaire)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("porte deja 3 signatures");
    }

    @Test
    @DisplayName("13. la piece jointe nait a une signature, et date sa derniere modification a l'enrichissement")
    void compteurEtDateDeModification() {
        PieceJointe piece = pieceJointe();

        assertThat(piece.getNombreSignatures()).isEqualTo(1);
        assertThat(piece.getDateDerniereModification()).isNull();

        piece.enregistrerSignatureSupplementaire();

        assertThat(piece.getNombreSignatures()).isEqualTo(2);
        assertThat(piece.getDateDerniereModification()).isNotNull();
    }

    // --- Fabriques ---------------------------------------------------------------

    private PieceJointe pieceJointe() {
        return new PieceJointe(109L, CHEMIN);
    }

    private ActeurSignataire agent() {
        return new ActeurSignataire(7L, "jean_mbarga", RoleEnum.AGENT_UNITE);
    }

    private ActeurSignataire chefUnite() {
        return new ActeurSignataire(8L, "paul_essama", RoleEnum.CHEF_UNITE_DA);
    }

    private ActeurSignataire directeurReseau() {
        return new ActeurSignataire(9L, "sylvie_atangana", RoleEnum.DIRECTEUR_RESEAU_DR);
    }

    private String visasDuFichier() throws IOException {
        try (PdfDocument pdf = ouvrir()) {
            return PdfTextExtractor.getTextFromPage(pdf.getLastPage());
        }
    }

    private String texteDuFichier() throws IOException {
        StringBuilder texte = new StringBuilder();
        try (PdfDocument pdf = ouvrir()) {
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                texte.append(PdfTextExtractor.getTextFromPage(pdf.getPage(page))).append('\n');
            }
        }
        return texte.toString();
    }

    private int nombreDePages() throws IOException {
        try (PdfDocument pdf = ouvrir()) {
            return pdf.getNumberOfPages();
        }
    }

    private PdfDocument ouvrir() throws IOException {
        return new PdfDocument(new PdfReader(
                new ByteArrayInputStream(Files.readAllBytes(racine.resolve(CHEMIN)))));
    }

    private int occurrences(String texte, String fragment) {
        return texte.split(java.util.regex.Pattern.quote(fragment), -1).length - 1;
    }

    private ProcessusMensuel processus(Long id) {
        ProcessusMensuel cree = declencherSur(9, 2026, UNITE);
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
        var beneficiaire = new EtatConsolide.Beneficiaire(55L, "MBARGA", "Jean",
                "03702009991111", "00002");
        var autre = new EtatConsolide.Beneficiaire(56L, "ESSAMA", "Paul",
                "03702009992222", "00002");

        var jour3 = new EtatConsolide.Journee(11L, LocalDate.of(2026, 9, 3), "EN_SAISIE", 2, 4000L,
                List.of(
                        ligne(101L, beneficiaire, 2500),
                        ligne(102L, autre, 1500)));
        var jour4 = new EtatConsolide.Journee(12L, LocalDate.of(2026, 9, 4), "EN_SAISIE", 1, 4000L,
                List.of(ligne(103L, beneficiaire, 4000)));

        return new EtatConsolide(109L, UNITE, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1).plusMonths(1).minusDays(1), 2, 3, 2, 8000L, List.of(jour3, jour4));
    }

    private EtatConsolide.Ligne ligne(Long id, EtatConsolide.Beneficiaire beneficiaire, int montant) {
        return new EtatConsolide.Ligne(id, 11L, beneficiaire.id(), beneficiaire,
                "RATION", "JOUR", montant, 12L, LocalDateTime.of(2026, 9, 3, 9, 0));
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
