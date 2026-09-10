package cm.afrilandfirstbank.rations.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;

import cm.afrilandfirstbank.rations.reporting.domaine.Rapport;
import cm.afrilandfirstbank.rations.reporting.domaine.Rapport.LigneRapport;
import cm.afrilandfirstbank.rations.reporting.domaine.Rapport.Synthese;
import cm.afrilandfirstbank.rations.reporting.domaine.SituationIntegration;

/**
 * Export PDF — tests 8 et 11 (partie PDF) du guide du Sprint 6.2.
 *
 * <p>Le rapport est construit directement, sans passer par {@link RapportService} :
 * le calcul est déjà éprouvé par {@code RapportServiceTest}, ici on éprouve
 * uniquement la mise en page.
 */
@DisplayName("ExportPdfService — production du PDF (Sprint 6.2, US-16, charte 8.2)")
class ExportPdfServiceTest {

    private final ExportPdfService service = new ExportPdfService();

    private Rapport rapportAvecDonnees() {
        LigneRapport ligne = new LigneRapport(1740L, "00002", "NORMAL", "CLOTURE", 42_000L, true,
                SituationIntegration.INTEGRE, LocalDateTime.of(2026, 8, 20, 14, 0));
        Synthese synthese = new Synthese(1, 42_000L, 42_000L, 0L, 0L,
                Map.of("CLOTURE", 1), Map.of(SituationIntegration.INTEGRE, 1));
        return new Rapport(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1).plusMonths(1).minusDays(1), "00002", LocalDateTime.of(2026, 9, 4, 10, 0), "claire_nkolo",
                List.of(ligne), List.of(), synthese, false);
    }

    private Rapport rapportSansDonnees() {
        return new Rapport(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1).plusMonths(1).minusDays(1), null, LocalDateTime.of(2026, 9, 4, 10, 0), "claire_nkolo",
                List.of(), List.of(), Synthese.vide(), true);
    }

    @Test
    @DisplayName("8. rapport avec donnees : PDF genere, non vide, ouvrable")
    void rapportAvecDonnees_pdfOuvrable() throws IOException {
        byte[] pdf = service.exporter(rapportAvecDonnees());

        assertThat(pdf).isNotEmpty();

        try (PdfDocument document = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
            String texte = PdfTextExtractor.getTextFromPage(document.getPage(1));
            assertThat(texte).contains("RAPPORT D'ACTIVITE").contains("00002");
        }
    }

    @Test
    @DisplayName("11. periode sans donnees : PDF genere avec la mention appropriee, pas d'erreur")
    void periodeSansDonnees_pdfAvecMention() throws IOException {
        byte[] pdf = service.exporter(rapportSansDonnees());

        assertThat(pdf).isNotEmpty();

        try (PdfDocument document = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            String texte = PdfTextExtractor.getTextFromPage(document.getPage(1));
            assertThat(texte).contains("Aucune activite enregistree pour cette periode.");
        }
    }

}
