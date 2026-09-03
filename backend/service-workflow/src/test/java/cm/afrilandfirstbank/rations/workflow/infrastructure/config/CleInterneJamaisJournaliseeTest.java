package cm.afrilandfirstbank.rations.workflow.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Garde-fou : <b>la valeur du secret partage ne doit fuir par aucun canal</b>, et surtout
 * pas par les journaux (exigence posee a l'arbitrage du Sprint 5.2).
 *
 * <h2>Pourquoi ce test relit les sources</h2>
 *
 * <p>Le module a pris l'habitude de journaliser des motifs detailles —
 * {@code AUDIT PERDU}, {@code TRANSMISSION MANQUEE}, {@code SEUIL INDISPONIBLE},
 * {@code INCOHERENCE GRILLE}. C'est une bonne habitude, et c'est precisement ce qui rend
 * la fuite probable : il suffit qu'un jour quelqu'un ajoute la cle recue au message pour
 * « faciliter le diagnostic ». Un garde-fou qui fuirait par le canal meme cense le
 * surveiller ne serait pas un garde-fou.
 *
 * <p>Un test de comportement ne suffirait pas : il ne verifierait que les chemins qu'il
 * emprunte. Celui-ci relit le fichier, comme le test de garde du Sprint 4.3 qui interdit
 * qu'un test ecrive la valeur du seuil.
 */
class CleInterneJamaisJournaliseeTest {

    private static final Path FILTRE = Path.of(
            "src/main/java/cm/afrilandfirstbank/rations/workflow/infrastructure/config",
            "FiltreCleInterne.java");

    /**
     * Les seules facons de manipuler la valeur du secret dans ce fichier : la lire de
     * l'en-tete, la convertir en octets, la comparer. Toute autre apparition dans une
     * chaine construite serait suspecte.
     */
    private static final List<String> EXPRESSIONS_INTERDITES = List.of(
            "clePresentee +",
            "+ clePresentee",
            "{}\", clePresentee",
            "cleAttendue +",
            "+ cleAttendue",
            "clePresentee)",
            "cleAttendue)");

    @Test
    @DisplayName("La valeur de la cle n'est concatenee dans aucun message du filtre")
    void aucuneConcatenationDeLaCle() throws IOException {
        String source = lireLeFiltre();

        for (String interdite : EXPRESSIONS_INTERDITES) {
            // Deux usages legitimes existent et sont exclus : la lecture de l'en-tete et
            // la comparaison a temps constant.
            String sansLesUsagesLegitimes = source
                    .replace("clePresentee.getBytes(StandardCharsets.UTF_8), cleAttendue)", "")
                    .replace("String clePresentee = requete.getHeader(EN_TETE_CLE_INTERNE);", "");

            assertThat(sansLesUsagesLegitimes)
                    .describedAs("expression interdite dans FiltreCleInterne : %s", interdite)
                    .doesNotContain(interdite);
        }
    }

    @Test
    @DisplayName("Aucun appel de journalisation du filtre ne porte la cle, ni sa longueur, ni un "
            + "prefixe — un prefixe est deja une fuite")
    void aucunJournalNePorteLaCle() throws IOException {
        String source = lireLeFiltre();

        for (String ligne : source.lines().toList()) {
            if (!ligne.contains("journal.")) {
                continue;
            }
            assertThat(ligne)
                    .describedAs("ligne de journal du filtre : %s", ligne.trim())
                    .doesNotContain("clePresentee")
                    .doesNotContain("cleAttendue")
                    .doesNotContain(".length")
                    .doesNotContain("substring");
        }
    }

    @Test
    @DisplayName("Le corps de la reponse de refus ne porte que le NOM de l'en-tete, jamais une "
            + "valeur")
    void leCorpsDeRefusNePorteQueLeNom() throws IOException {
        String source = lireLeFiltre();

        // Le message rendu est une constante litterale : rien n'y est interpole hormis le
        // chemin de la requete, qui ne porte aucun secret.
        assertThat(source).contains("CLE_INTERNE_INVALIDE");
        assertThat(source).doesNotContain("clePresentee\"");
        assertThat(source).doesNotContain("+ clePresentee");
    }

    @Test
    @DisplayName("La comparaison reste a temps constant : String.equals s'arrete au premier "
            + "octet different et laisse reconstituer le secret a la repetition")
    void comparaisonATempsConstant() throws IOException {
        String source = lireLeFiltre();

        assertThat(source).contains("MessageDigest.isEqual");
        assertThat(source).doesNotContain("cleAttendue.equals");
        assertThat(source).doesNotContain("equals(cleAttendue)");
    }

    private static String lireLeFiltre() throws IOException {
        assertThat(FILTRE)
                .describedAs("le filtre du secret partage doit exister a cet emplacement")
                .exists();
        return Files.readString(FILTRE, StandardCharsets.UTF_8);
    }

}
