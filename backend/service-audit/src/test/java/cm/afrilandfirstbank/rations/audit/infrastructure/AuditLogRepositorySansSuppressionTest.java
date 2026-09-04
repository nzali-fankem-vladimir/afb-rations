package cm.afrilandfirstbank.rations.audit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Garde de l'immuabilite du journal d'audit (CLAUDE.md sections 3 et 4, guide
 * de ce sprint, etape 4). Sur le modele de {@code PerimetreDuModuleTest}
 * (Sprint 1.3) et {@code CompteDeControleDuRefusTest} (Sprint 6.3) : une regle
 * verifiee au build, jamais confiee a une convention ecrite seule.
 *
 * <p>Deux angles, deliberement redondants :
 * <ul>
 *   <li>{@link #aucuneMethodeDeSuppressionSurLInterface()} inspecte
 *       l'interface <b>compilee</b> par reflexion. Elle protege contre toute
 *       source d'exposition, y compris une future interface Spring Data
 *       (par exemple {@code PagingAndSortingRepository}) qui porterait une
 *       suppression sans jamais s'appeler {@code JpaRepository}.</li>
 *   <li>{@link #aucunImportDeJpaRepositoryDansLeModule()} relit les
 *       <b>sources</b> : elle interdit le geste le plus direct — importer
 *       {@code JpaRepository} ou {@code CrudRepository} quelque part dans
 *       {@code service-audit} — avant meme que le code ne compile dans un
 *       sens qui exposerait une suppression.</li>
 * </ul>
 */
class AuditLogRepositorySansSuppressionTest {

    /**
     * Fragments de nom de methode qui trahissent une suppression, quelle que
     * soit l'interface Spring Data qui les aurait introduits.
     */
    private static final List<String> FRAGMENTS_SUPPRESSION = List.of("delete", "remove", "erase");

    @Test
    @DisplayName("aucune methode de suppression accessible sur AuditLogRepository, y compris heritee")
    void aucuneMethodeDeSuppressionSurLInterface() {
        List<Method> methodesDeSuppression = Arrays.stream(AuditLogRepository.class.getMethods())
                .filter(methode -> FRAGMENTS_SUPPRESSION.stream()
                        .anyMatch(fragment -> methode.getName().toLowerCase().contains(fragment)))
                .toList();

        assertThat(methodesDeSuppression)
                .as("""
                        AuditLogRepository expose une methode de suppression : %s.
                        Le journal d'audit doit rester immuable par construction (CLAUDE.md
                        section 3) : aucune methode d'alteration d'une ligne existante ne doit
                        etre accessible, ni directement ni par heritage d'une interface Spring
                        Data qui en porterait une (JpaRepository, CrudRepository,
                        PagingAndSortingRepository, ListCrudRepository...).""",
                        methodesDeSuppression)
                .isEmpty();
    }

    @Test
    @DisplayName("service-audit n'importe JpaRepository ni CrudRepository nulle part")
    void aucunImportDeJpaRepositoryDansLeModule() throws java.io.IOException {
        java.nio.file.Path sources = java.nio.file.Path.of("src", "main", "java");
        List<String> importsInterdits =
                List.of("org.springframework.data.jpa.repository.JpaRepository",
                        "org.springframework.data.repository.CrudRepository",
                        "org.springframework.data.repository.ListCrudRepository",
                        "org.springframework.data.repository.PagingAndSortingRepository",
                        // Ecarte a l'etape 4 : les versions recentes de Spring Data JPA y ont
                        // ajoute delete(PredicateSpecification) et delete(DeleteSpecification),
                        // constate par ce test lui-meme avant d'etre retire du repository.
                        "org.springframework.data.jpa.repository.JpaSpecificationExecutor");

        try (var fichiers = java.nio.file.Files.walk(sources)) {
            for (java.nio.file.Path fichier : fichiers.filter(java.nio.file.Files::isRegularFile)
                    .filter(f -> f.toString().endsWith(".java")).toList()) {
                String contenu = java.nio.file.Files.readString(fichier);
                for (String interdit : importsInterdits) {
                    assertThat(contenu)
                            .as("""
                                    %s importe %s. Le seul repository de ce service doit rester
                                    RepositoryEcritureSeule + JpaSpecificationExecutor (ni l'un ni
                                    l'autre ne porte de suppression) : importer une interface Spring
                                    Data plus large ouvrirait a nouveau la porte a un delete accessible
                                    par accident.""", fichier, interdit)
                            .doesNotContain(interdit);
                }
            }
        }
    }

}
