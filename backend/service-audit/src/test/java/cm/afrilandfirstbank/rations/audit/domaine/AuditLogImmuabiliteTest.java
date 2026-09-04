package cm.afrilandfirstbank.rations.audit.domaine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import cm.afrilandfirstbank.rations.audit.infrastructure.AuditLogRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Preuve d'immuabilite, en trois angles, contre la vraie base
 * {@code rations_audit} (guide de rattrapage du service Audit, etape 7,
 * premiere des trois preuves attendues).
 *
 * <p>{@code @AutoConfigureTestDatabase(replace = NONE)} : meme convention que
 * {@code GrilleTarifaireRepositoryTest} (Sprint 2.3) — tourner contre une base
 * embarquee ne prouverait rien sur le comportement reel de PostgreSQL et du
 * callback JPA. {@code @DataJpaTest} enveloppe chaque test dans une
 * transaction annulee a la fin : aucune trace de test ne reste en base.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuditLogImmuabiliteTest {

    @Autowired
    private AuditLogRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    private AuditLog inserer() {
        AuditLog trace = new AuditLog(1L, "service-test", "ACTION_DE_TEST", "entite_test", 1L,
                LocalDateTime.now(), "127.0.0.1", "{\"cle\":\"valeur\"}");
        return repository.save(trace);
    }

    @Test
    @DisplayName("aucune methode de suppression n'est accessible sur le repository (rappel de l'etape 4)")
    void repositorySansMethodeDeSuppression() {
        List<Method> methodesDeSuppression = Arrays.stream(AuditLogRepository.class.getMethods())
                .filter(methode -> methode.getName().toLowerCase().contains("delete")
                        || methode.getName().toLowerCase().contains("remove")
                        || methode.getName().toLowerCase().contains("erase"))
                .toList();

        assertThat(methodesDeSuppression).isEmpty();
    }

    @Test
    @DisplayName("un appel DIRECT a EntityManager.remove(), contournant le repository, "
            + "echoue quand meme : l'immuabilite est une propriete de l'entite, pas "
            + "seulement de l'interface qui la sert")
    void suppressionDirecteParEntityManagerEchoue() {
        AuditLog trace = inserer();
        entityManager.flush();

        assertThatThrownBy(() -> {
            entityManager.remove(trace);
            entityManager.flush();
        }).isInstanceOf(UnsupportedOperationException.class);

        // La ligne existe toujours : le callback a bloque la suppression avant
        // qu'elle n'atteigne la base, pas seulement leve une exception cote Java.
        entityManager.clear();
        assertThat(repository.findById(trace.getId())).isPresent();
    }

    @Test
    @DisplayName("l'entite ne porte aucun setter au-dela du constructeur : "
            + "aucun champ deja ecrit ne peut etre modifie")
    void aucunSetterSurLEntite() {
        List<Method> setters = Arrays.stream(AuditLog.class.getDeclaredMethods())
                .filter(methode -> methode.getName().startsWith("set"))
                .toList();

        assertThat(setters)
                .as("AuditLog expose un setter : %s. Une ligne d'audit se cree une fois et "
                        + "ne change plus jamais (guide de ce sprint, etape 3).", setters)
                .isEmpty();
    }

}
