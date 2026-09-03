package cm.afrilandfirstbank.rations.transmission.infrastructure.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Garde-fou, versant emetteur : <b>la valeur du secret partage ne doit fuir par aucun
 * canal</b> du service qui le presente (exigence posee a l'arbitrage du Sprint 5.2).
 *
 * <p>Le pendant existe cote service Workflow, sur {@code FiltreCleInterne}. Les deux sont
 * necessaires : le secret traverse deux services, et le proteger d'un seul cote laisserait
 * l'autre libre de le journaliser.
 *
 * <p>Le risque est concret ici : ce client journalise deja les reponses en erreur, corps
 * compris, pour que le diagnostic survive dans le journal d'audit. Il suffirait d'y
 * ajouter « et l'en-tete envoye » pour que le secret parte dans chaque trace de panne.
 */
class CleInterneJamaisJournaliseeTest {

    private static final Path CLIENT = Path.of(
            "src/main/java/cm/afrilandfirstbank/rations/transmission/infrastructure/workflow",
            "StatutIntegrationHttpClient.java");

    @Test
    @DisplayName("Aucun appel de journalisation ne porte la cle, ni sa longueur, ni un prefixe")
    void aucunJournalNePorteLaCle() throws IOException {
        String source = lireLeClient();

        for (String ligne : source.lines().toList()) {
            if (!ligne.contains("journal.")) {
                continue;
            }
            assertThat(ligne)
                    .describedAs("ligne de journal : %s", ligne.trim())
                    .doesNotContain("cleInterne")
                    .doesNotContain("this.cleInterne");
        }
    }

    @Test
    @DisplayName("La cle n'apparait dans aucun message construit : ni exception, ni motif "
            + "technique remonte a l'appelant, ni contexte d'audit")
    void laCleNApparaitDansAucunMessage() throws IOException {
        String source = lireLeClient();

        assertThat(source).doesNotContain("+ cleInterne");
        assertThat(source).doesNotContain("cleInterne +");
        assertThat(source).doesNotContain("{}\", cleInterne");
        assertThat(source).doesNotContain("cleInterne.length");
        assertThat(source).doesNotContain("cleInterne.substring");
    }

    @Test
    @DisplayName("Le seul usage de la cle est de la poser en en-tete : une seule occurrence "
            + "d'ecriture, et elle est celle-la")
    void leSeulUsageEstDePoserLEnTete() throws IOException {
        String source = lireLeClient();

        assertThat(source).contains(".header(EN_TETE_CLE_INTERNE, cleInterne)");
        // Cinq occurrences legitimes, et pas une de plus : la declaration du champ, le
        // parametre du constructeur, les deux cotes de son affectation, et la pose de
        // l'en-tete. Une sixieme serait a justifier -- c'est la garde.
        assertThat(source.split("cleInterne", -1).length - 1)
                .describedAs("occurrences du champ cleInterne dans le fichier")
                .isEqualTo(5);
    }

    @Test
    @DisplayName("Le nom de l'en-tete est une constante nommee : il figure dans les journaux, "
            + "sa valeur jamais")
    void leNomDeLEnTeteEstUneConstante() throws IOException {
        String source = lireLeClient();

        assertThat(source).contains("EN_TETE_CLE_INTERNE = \"X-Cle-Interne\"");
    }

    private static String lireLeClient() throws IOException {
        assertThat(CLIENT)
                .describedAs("le client de remontee du statut doit exister a cet emplacement")
                .exists();
        return Files.readString(CLIENT, StandardCharsets.UTF_8);
    }

}
