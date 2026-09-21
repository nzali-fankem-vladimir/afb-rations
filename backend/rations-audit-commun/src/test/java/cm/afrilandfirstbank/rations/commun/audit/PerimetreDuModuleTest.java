package cm.afrilandfirstbank.rations.commun.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Barriere de perimetre du module, decision Sprint 1.3.
 *
 * <p>Un module « commun » qui grossit devient un monolithe distribue. La regle
 * « ce module ne contient que la publication d'audit » est donc verifiee au
 * build, et non confiee a un README : une regle ecrite est une discipline de
 * code, et ce projet a explicitement refuse ce mode de garantie ailleurs (le
 * service Audit existe pour que l'immuabilite soit une propriete de
 * l'architecture, CLAUDE.md section 3).
 *
 * <p><b>Portee reelle de cette garantie, sans exageration :</b> elle rend la
 * derive impossible par accident, pas impossible tout court. Qui veut ajouter un
 * type metier peut editer ce test. Elle est strictement plus forte qu'une
 * convention ecrite, strictement plus faible que l'isolation du service Audit,
 * ou l'absence d'identifiants de connexion rend l'ecriture physiquement
 * impossible. Elle transforme une derive silencieuse en acte delibere et
 * visible en revue.
 */
class PerimetreDuModuleTest {

    private static final Path SOURCES = Path.of("src", "main", "java");

    private static final String PAQUET_AUTORISE =
            Path.of("cm", "afrilandfirstbank", "rations", "commun", "audit").toString();

    /**
     * Types attendus dans le module. Un type absent de cette liste fait echouer
     * le build : l'ajout doit etre un geste conscient, pas un glissement.
     */
    private static final Set<String> TYPES_AUTORISES = Set.of(
            "EvenementAudit",
            "DeltaAudit",
            "PublicateurAudit",
            "PublicateurAuditKafka",
            // Sprint 8.2 : prechauffage du producteur d'audit au demarrage. Fait
            // partie de la publication d'audit (il ferme la perte du premier
            // evenement apres chaque demarrage), sans aucun type metier.
            "PrechauffageProducteurAudit",
            "AuditCommunAutoConfiguration",
            "AuditProprietes");

    /**
     * Technologies qui n'ont rien a faire ici. Leur apparition signale que le
     * module a commence a absorber du metier, du web ou de la persistance.
     */
    private static final List<String> IMPORTS_INTERDITS = List.of(
            "jakarta.persistence",
            "jakarta.servlet",
            "org.springframework.web",
            "org.springframework.security",
            "org.springframework.data",
            "org.flywaydb",
            "cm.afrilandfirstbank.rations.identite",
            "cm.afrilandfirstbank.rations.saisie",
            "cm.afrilandfirstbank.rations.grilles",
            "cm.afrilandfirstbank.rations.workflow",
            "cm.afrilandfirstbank.rations.reporting",
            "cm.afrilandfirstbank.rations.transmission",
            "cm.afrilandfirstbank.rations.audit");

    private static List<Path> fichiersSource() throws IOException {
        try (Stream<Path> fichiers = Files.walk(SOURCES)) {
            return fichiers.filter(Files::isRegularFile)
                    .filter(fichier -> fichier.toString().endsWith(".java"))
                    .toList();
        }
    }

    @Test
    @DisplayName("aucune classe hors du paquet commun.audit")
    void aucuneClasseHorsDuPaquetAudit() throws IOException {
        for (Path source : fichiersSource()) {
            assertThat(source.getParent().toString())
                    .as("%s est hors du perimetre du module : seul %s est autorise",
                            source, PAQUET_AUTORISE)
                    .endsWith(PAQUET_AUTORISE);
        }
    }

    @Test
    @DisplayName("aucun type inattendu : l'ajout doit etre un geste conscient")
    void aucunTypeInattendu() throws IOException {
        for (Path source : fichiersSource()) {
            String nomDuType = source.getFileName().toString().replace(".java", "");
            assertThat(nomDuType)
                    .as("%s n'est pas un type attendu de ce module. Si son ajout est "
                            + "delibere, inscrivez-le dans TYPES_AUTORISES et justifiez-le "
                            + "en revue : ce module ne porte que la publication d'audit.", nomDuType)
                    .isIn(TYPES_AUTORISES);
        }
    }

    @Test
    @DisplayName("aucun import de web, de persistance, de securite ni d'un service du reacteur")
    void aucunImportInterdit() throws IOException {
        for (Path source : fichiersSource()) {
            List<String> imports = Files.readAllLines(source).stream()
                    .map(String::trim)
                    .filter(ligne -> ligne.startsWith("import "))
                    .toList();

            for (String ligne : imports) {
                assertThat(IMPORTS_INTERDITS.stream().anyMatch(ligne::contains))
                        .as("%s importe une technologie hors perimetre : %s", source, ligne)
                        .isFalse();
            }
        }
    }

    @Test
    @DisplayName("le module ne depend d'aucun service du reacteur")
    void aucuneDependanceVersUnService() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));

        // Le nom du module lui-meme contient "rations-audit-commun" ; on cible
        // les artefacts service-*, seuls interdits ici.
        assertThat(pom)
                .as("rations-audit-commun ne doit dependre d'aucun service du reacteur")
                .doesNotContain("<artifactId>service-");
    }

}
