package cm.afrilandfirstbank.rations.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;

import cm.afrilandfirstbank.rations.reporting.api.dto.RapportResponse;
import cm.afrilandfirstbank.rations.reporting.domaine.EnTeteDemande;
import cm.afrilandfirstbank.rations.reporting.domaine.Rapport;

/**
 * Le verrou de cohérence de CT-32 — test 6 du guide du Sprint 6.2.
 *
 * <p>« Le total affiché à l'écran, celui du PDF et celui de l'Excel sont
 * identiques. » Les trois usages reçoivent la <b>même instance</b> de
 * {@link Rapport}, produite une seule fois par {@link RapportService}, puis
 * chacun est relu depuis sa propre sortie — JSON pour l'écran, texte extrait pour
 * le PDF, cellule numérique relue pour l'Excel — pour prouver que rien n'a divergé
 * en chemin.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Coherence des trois usages du rapport — verrou CT-32 (test 6 du guide 6.2)")
class CoherenceExportTest {

    private static final String JETON = "Bearer jeton-utilisateur";

    @Mock
    private AgregationService agregationService;

    private RapportService rapportService;
    private final ExportPdfService exportPdfService = new ExportPdfService();
    private final ExportExcelService exportExcelService = new ExportExcelService();

    @BeforeEach
    void preparer() {
        rapportService = new RapportService(agregationService);
    }

    private EnTeteDemande enTete(long id, String codeUnite, int montant, boolean transmis,
            String statutIntegration) {
        return new EnTeteDemande(id, 8, 2026, codeUnite, "NORMAL", montant, "CLOTURE", transmis,
                statutIntegration, LocalDateTime.of(2026, 8, 20, 14, 0));
    }

    @Test
    @DisplayName("6. total ecran = total PDF = total Excel, pour le meme rapport")
    void totalIdentique_surLesTroisUsages() throws Exception {
        when(agregationService.rechercher(any(), eq(JETON))).thenReturn(List.of(
                enTete(1, "00002", 42_000, true, "INTEGRE"),
                enTete(2, "00003", 17_500, true, "REJETE"),
                enTete(3, "00003", 8_250, false, null)));

        Rapport rapport = rapportService.produire(8, 2026, null, "claire_nkolo", JETON);
        long totalAttendu = rapport.synthese().montantTotalPeriode();
        // Le rapport n'est pas trivial : s'assurer que le total vaut vraiment quelque chose.
        assertThat(totalAttendu).isEqualTo(67_750L);

        // --- Ecran : RapportResponse, ce que le controleur rend en JSON ---
        long totalEcran = RapportResponse.depuis(rapport).synthese().montantTotalPeriode();
        assertThat(totalEcran).isEqualTo(totalAttendu);

        // --- PDF : relu par extraction de texte ---
        byte[] pdf = exportPdfService.exporter(rapport);
        String texte = extraireTexte(pdf);
        assertThat(texte).contains(formaterFcfa(totalAttendu));

        // --- Excel : relu par POI, cellule NUMERIQUE de la feuille Synthese ---
        double totalExcel = lireMontantSynthese(exportExcelService.exporter(rapport),
                "Montant total de la periode");
        assertThat(totalExcel).isEqualTo((double) totalAttendu);
    }

    private String extraireTexte(byte[] pdf) throws java.io.IOException {
        try (PdfDocument document = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            StringBuilder texte = new StringBuilder();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                texte.append(PdfTextExtractor.getTextFromPage(document.getPage(page)));
            }
            return texte.toString();
        }
    }

    private double lireMontantSynthese(byte[] excel, String libelleRecherche) throws java.io.IOException {
        try (XSSFWorkbook classeur = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            Sheet feuille = classeur.getSheet("Synthese");
            for (Row rangee : feuille) {
                Cell libelle = rangee.getCell(0);
                if (libelle != null && libelleRecherche.equals(libelle.getStringCellValue())) {
                    return rangee.getCell(1).getNumericCellValue();
                }
            }
            throw new AssertionError("Ligne \"" + libelleRecherche + "\" introuvable sur la feuille Synthese.");
        }
    }

    /** Meme regle de groupement que {@link ExportPdfService#fcfa}, dupliquee a dessein pour le test. */
    private String formaterFcfa(long montant) {
        String chiffres = Long.toString(Math.abs(montant));
        StringBuilder groupe = new StringBuilder();
        for (int i = 0; i < chiffres.length(); i++) {
            if (i > 0 && (chiffres.length() - i) % 3 == 0) {
                groupe.append(' ');
            }
            groupe.append(chiffres.charAt(i));
        }
        return (montant < 0 ? "-" : "") + groupe + " FCFA";
    }

}
