package cm.afrilandfirstbank.rations.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cm.afrilandfirstbank.rations.reporting.domaine.Rapport;
import cm.afrilandfirstbank.rations.reporting.domaine.Rapport.LigneRapport;
import cm.afrilandfirstbank.rations.reporting.domaine.Rapport.Synthese;
import cm.afrilandfirstbank.rations.reporting.domaine.SituationIntegration;

/**
 * Export Excel — tests 9 et 11 (partie Excel) du guide du Sprint 6.2.
 *
 * <p>Le point du test 9 n'est pas la valeur des montants (déjà éprouvée par
 * {@code RapportServiceTest}) mais leur <b>type de cellule</b> : le fichier est
 * relu avec POI, comme le guide le demande, et chaque cellule de montant doit être
 * {@link CellType#NUMERIC} — jamais {@link CellType#STRING}, qui empêcherait toute
 * somme dans le tableur.
 */
@DisplayName("ExportExcelService — production de l'Excel (Sprint 6.2, US-16)")
class ExportExcelServiceTest {

    private final ExportExcelService service = new ExportExcelService();

    private Rapport rapportAvecDonnees() {
        LigneRapport ligneA = new LigneRapport(1L, "00002", "NORMAL", "CLOTURE", 42_000L, true,
                SituationIntegration.INTEGRE, LocalDateTime.of(2026, 8, 20, 14, 0));
        LigneRapport ligneB = new LigneRapport(2L, "00003", "NORMAL", "SOUMIS", 8_000L, false,
                SituationIntegration.NON_TRANSMIS, LocalDateTime.of(2026, 8, 21, 9, 0));
        Synthese synthese = new Synthese(2, 50_000L, 42_000L, 8_000L, 0L,
                Map.of("CLOTURE", 1, "SOUMIS", 1),
                Map.of(SituationIntegration.INTEGRE, 1, SituationIntegration.NON_TRANSMIS, 1));
        List<Rapport.SousTotalAgence> sousTotaux = List.of(
                new Rapport.SousTotalAgence("00002", 1, 42_000L),
                new Rapport.SousTotalAgence("00003", 1, 8_000L));
        return new Rapport(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1).plusMonths(1).minusDays(1), null, LocalDateTime.of(2026, 9, 4, 10, 0), "claire_nkolo",
                List.of(ligneA, ligneB), sousTotaux, synthese, false);
    }

    private Rapport rapportSansDonnees() {
        return new Rapport(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1).plusMonths(1).minusDays(1), null, LocalDateTime.of(2026, 9, 4, 10, 0), "claire_nkolo",
                List.of(), List.of(), Synthese.vide(), true);
    }

    @Test
    @DisplayName("9. rapport avec donnees : fichier genere, montants NUMERIQUES sur les deux feuilles")
    void rapportAvecDonnees_montantsNumeriques() throws IOException {
        byte[] excel = service.exporter(rapportAvecDonnees());
        assertThat(excel).isNotEmpty();

        try (XSSFWorkbook classeur = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            Sheet detail = classeur.getSheet("Detail");
            assertThat(detail).isNotNull();

            Row entetes = detail.getRow(5); // 4 lignes d'identification + 1 vide avant l'entete
            assertThat(entetes.getCell(4).getStringCellValue()).isEqualTo("Montant FCFA");

            Cell montantLigne1 = detail.getRow(6).getCell(4);
            assertThat(montantLigne1.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(montantLigne1.getNumericCellValue()).isEqualTo(42_000d);

            Cell montantLigne2 = detail.getRow(7).getCell(4);
            assertThat(montantLigne2.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(montantLigne2.getNumericCellValue()).isEqualTo(8_000d);

            Sheet synthese = classeur.getSheet("Synthese");
            assertThat(synthese).isNotNull();
            for (Row rangee : synthese) {
                Cell libelle = rangee.getCell(0);
                if (libelle != null && libelle.getCellType() == CellType.STRING
                        && libelle.getStringCellValue().startsWith("Montant")) {
                    Cell valeur = rangee.getCell(1);
                    assertThat(valeur.getCellType())
                            .as("la cellule de \"%s\" doit rester numerique", libelle.getStringCellValue())
                            .isEqualTo(CellType.NUMERIC);
                }
            }
        }
    }

    @Test
    @DisplayName("11. periode sans donnees : classeur genere avec la mention appropriee, pas d'erreur")
    void periodeSansDonnees_classeurAvecMention() throws IOException {
        byte[] excel = service.exporter(rapportSansDonnees());
        assertThat(excel).isNotEmpty();

        try (XSSFWorkbook classeur = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            Sheet detail = classeur.getSheet("Detail");
            Row ligneMention = detail.getRow(5); // 4 lignes d'identification + 1 vide
            assertThat(ligneMention.getCell(0).getStringCellValue())
                    .isEqualTo("Aucune activite enregistree pour cette periode.");

            Sheet synthese = classeur.getSheet("Synthese");
            Row totalPeriode = synthese.getRow(6); // identification (4) + vide (1) + nombre d'etats (1)
            assertThat(totalPeriode.getCell(0).getStringCellValue())
                    .isEqualTo("Montant total de la periode");
            assertThat(totalPeriode.getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(totalPeriode.getCell(1).getNumericCellValue()).isZero();
        }
    }

}
