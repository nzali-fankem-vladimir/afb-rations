package cm.afrilandfirstbank.rations.workflow.infrastructure.stockage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cm.afrilandfirstbank.rations.workflow.application.DocumentEcrit;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.DocumentNonProduitException;

/**
 * Tests du stockage des pieces jointes (Sprint 4.2).
 *
 * <p>Ce que ces tests protegent est la <b>garantie</b> du port : quand une
 * ecriture rend un {@link DocumentEcrit}, le contenu est reellement sur disque,
 * complet, et n'a rien ecrase par accident. C'est cette garantie qui autorise a
 * incrementer {@code piece_jointe.nombre_signatures} — sans elle, le compteur ne
 * mesurerait qu'une intention.
 */
@DisplayName("Stockage des pieces jointes")
class StockageDocumentsFichierTest {

    private static final String CHEMIN = "2026/09/etat-rations-00002-20260901-p109.pdf";

    @TempDir
    Path racine;

    private StockageDocumentsFichier stockage;

    @BeforeEach
    void preparer() {
        stockage = new StockageDocumentsFichier(racine.toString());
    }

    // --- Ecriture nominale -----------------------------------------------------

    @Test
    @DisplayName("1. ecriture nominale : le fichier existe, avec exactement le contenu demande")
    void ecritureNominale() throws IOException {
        byte[] contenu = "contenu du document".getBytes(StandardCharsets.UTF_8);

        DocumentEcrit ecrit = stockage.ecrireNouveau(CHEMIN, contenu);

        assertThat(ecrit.cheminRelatif()).isEqualTo(CHEMIN);
        assertThat(ecrit.octetsEcrits()).isEqualTo(contenu.length);
        assertThat(ecrit.horodatage()).isNotNull();
        assertThat(Files.readAllBytes(racine.resolve(CHEMIN))).isEqualTo(contenu);
    }

    @Test
    @DisplayName("2. les sous-dossiers annee/mois sont crees au besoin")
    void sousDossiersCrees() {
        stockage.ecrireNouveau(CHEMIN, octets());

        assertThat(racine.resolve("2026").resolve("09")).isDirectory();
    }

    @Test
    @DisplayName("3. aucun fichier temporaire ne subsiste apres une ecriture reussie")
    void aucunTemporaireResiduel() throws IOException {
        stockage.ecrireNouveau(CHEMIN, octets());

        try (Stream<Path> contenu = Files.walk(racine)) {
            assertThat(contenu.filter(Files::isRegularFile).map(Path::toString))
                    .allSatisfy(nom -> assertThat(nom).doesNotContain(".partiel"));
        }
    }

    @Test
    @DisplayName("4. la taille rendue est celle constatee sur disque, pas celle demandee")
    void tailleConstateeSurDisque() throws IOException {
        DocumentEcrit ecrit = stockage.ecrireNouveau(CHEMIN, octets());

        // La distinction n'est pas rhetorique : un disque plein peut accepter une
        // ecriture partielle. C'est le fichier qui fait foi, pas le tableau.
        assertThat(ecrit.octetsEcrits()).isEqualTo(Files.size(racine.resolve(CHEMIN)));
    }

    // --- Le refus d'ecraser ----------------------------------------------------

    @Test
    @DisplayName("5. LE POINT CRITIQUE : ecrireNouveau refuse d'ecraser un document existant")
    void refusDEcraserUnDocumentExistant() throws IOException {
        byte[] premier = "PDF signe par l'agent".getBytes(StandardCharsets.UTF_8);
        stockage.ecrireNouveau(CHEMIN, premier);

        assertThatThrownBy(() -> stockage.ecrireNouveau(CHEMIN, "autre chose".getBytes()))
                .isInstanceOf(DocumentNonProduitException.class)
                .hasMessageContaining("occupe deja le chemin")
                .hasMessageContaining("signatures deja apposees");

        // Sans ce refus, une seconde soumission concurrente ecraserait le PDF
        // signe de la premiere AVANT que la contrainte id_processus UNIQUE de
        // piece_jointe ne s'exprime.
        assertThat(Files.readAllBytes(racine.resolve(CHEMIN))).isEqualTo(premier);
    }

    @Test
    @DisplayName("6. une tentative refusee ne laisse aucun fichier temporaire derriere elle")
    void refusSansResidu() throws IOException {
        stockage.ecrireNouveau(CHEMIN, octets());
        try {
            stockage.ecrireNouveau(CHEMIN, octets());
        } catch (DocumentNonProduitException attendu) {
            // refus attendu
        }

        try (Stream<Path> contenu = Files.walk(racine)) {
            assertThat(contenu.filter(Files::isRegularFile)).hasSize(1);
        }
    }

    // --- Le remplacement, pour l'enrichissement des sprints 4.3 et 4.4 ---------

    @Test
    @DisplayName("7. remplacer() ecrase, et le contenu final est integralement le nouveau")
    void remplacementComplet() throws IOException {
        stockage.ecrireNouveau(CHEMIN, "version a une signature, plus longue".getBytes(StandardCharsets.UTF_8));

        byte[] enrichi = "version a deux".getBytes(StandardCharsets.UTF_8);
        DocumentEcrit ecrit = stockage.remplacer(CHEMIN, enrichi);

        // Aucun residu de l'ancienne version, plus longue : le renommage remplace
        // le fichier entier. Une ecriture en place aurait laisse la fin du
        // document precedent, donc un PDF mutile.
        assertThat(Files.readAllBytes(racine.resolve(CHEMIN))).isEqualTo(enrichi);
        assertThat(ecrit.octetsEcrits()).isEqualTo(enrichi.length);
    }

    @Test
    @DisplayName("8. remplacer() cree le fichier s'il n'existe pas encore")
    void remplacementSurFichierAbsent() {
        assertThat(stockage.remplacer(CHEMIN, octets()).octetsEcrits()).isPositive();
    }

    // --- Refus de principe -----------------------------------------------------

    @Test
    @DisplayName("9. un document vide est refuse : ce n'est pas un document")
    void documentVideRefuse() {
        assertThatThrownBy(() -> stockage.ecrireNouveau(CHEMIN, new byte[0]))
                .isInstanceOf(DocumentNonProduitException.class)
                .hasMessageContaining("document vide");

        assertThatThrownBy(() -> stockage.ecrireNouveau(CHEMIN, null))
                .isInstanceOf(DocumentNonProduitException.class);
    }

    @Test
    @DisplayName("10. un chemin qui sortirait de la racine est refuse")
    void chemineHorsRacineRefuse() {
        assertThatThrownBy(() -> stockage.ecrireNouveau("../evasion.pdf", octets()))
                .isInstanceOf(DocumentNonProduitException.class)
                .hasMessageContaining("hors du repertoire de stockage");

        assertThatThrownBy(() -> stockage.ecrireNouveau("2026/../../evasion.pdf", octets()))
                .isInstanceOf(DocumentNonProduitException.class);
    }

    @Test
    @DisplayName("11. un chemin absent ou vide est refuse")
    void cheminVideRefuse() {
        assertThatThrownBy(() -> stockage.ecrireNouveau(null, octets()))
                .isInstanceOf(DocumentNonProduitException.class);
        assertThatThrownBy(() -> stockage.ecrireNouveau("   ", octets()))
                .isInstanceOf(DocumentNonProduitException.class);
    }

    // --- Relecture -------------------------------------------------------------

    @Test
    @DisplayName("12. lire() rend le contenu ecrit ; un document absent leve plutot que de rendre du vide")
    void relecture() {
        byte[] contenu = octets();
        stockage.ecrireNouveau(CHEMIN, contenu);

        assertThat(stockage.lire(CHEMIN)).isEqualTo(contenu);

        assertThatThrownBy(() -> stockage.lire("2026/09/inexistant.pdf"))
                .isInstanceOf(DocumentNonProduitException.class)
                .hasMessageContaining("n'a pas pu etre relu");
    }

    @Test
    @DisplayName("13. existe() distingue un document present d'un document absent")
    void existence() {
        assertThat(stockage.existe(CHEMIN)).isFalse();
        stockage.ecrireNouveau(CHEMIN, octets());
        assertThat(stockage.existe(CHEMIN)).isTrue();
    }

    private byte[] octets() {
        return "contenu".getBytes(StandardCharsets.UTF_8);
    }

}
