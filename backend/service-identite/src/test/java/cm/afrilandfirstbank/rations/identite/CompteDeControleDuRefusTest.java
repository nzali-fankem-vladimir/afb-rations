package cm.afrilandfirstbank.rations.identite;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Garde du compte de contrôle du refus 403, décision Sprint 6.3.
 *
 * <h2>Ce que ce test protège</h2>
 *
 * <p>Un compte du realm de développement est <b>volontairement absent</b> du
 * pré-provisionnement : c'est le seul cas qui prouve qu'un jeton Keycloak
 * parfaitement valide — bon realm, bonne audience, rôle applicatif reconnu — est
 * malgré tout refusé faute d'habilitation ouverte dans le module. C'est
 * l'invariant central de la décision du Sprint 0.4 : <i>l'habilitation au module
 * est un acte d'administration explicite, elle ne découle pas de la seule
 * existence d'un compte à l'annuaire.</i>
 *
 * <p>Lui ouvrir un profil ne casse rien de visible. Le build passe, les tests
 * passent, l'application fonctionne — et la checklist d'environnement rend
 * <b>{@code 200} là où elle attend {@code 403}</b>, sur un test de sécurité, sans
 * un mot d'explication. Même famille que le compteur de signatures du Sprint 4.2
 * ou {@code @EnableKafka} manquant au Sprint 5.2 : un résultat plausible au lieu
 * d'une erreur.
 *
 * <h2>Pourquoi un test et pas un commentaire</h2>
 *
 * <p>C'est arrivé. Au Sprint 6.3, {@code pierre_belinga} — alors compte de
 * contrôle — a reçu un profil local pour éprouver la trace
 * {@code LIAISON_COMPTE_KEYCLOAK}. L'avertissement était pourtant écrit à
 * <b>trois</b> endroits : {@code infra/keycloak/README.md}, la décision du
 * Sprint 0.4, et le commentaire de l'utilisateur dans l'export du realm. Un
 * quatrième texte n'aurait pas plus d'effet ; une garde au build, si.
 *
 * <h2>Portée réelle, sans exagération</h2>
 *
 * <p>Elle couvre la migration, pas un {@code INSERT} manuel en base — et c'est
 * précisément par là que l'erreur est passée. Elle rend la dérive impossible
 * <b>par accident</b>, pas impossible tout court : qui veut ajouter ce compte
 * peut éditer ce test, ce qui devient alors un acte délibéré et visible en revue.
 * Même discipline que {@code PerimetreDuModuleTest} pour le périmètre de
 * {@code rations-audit-commun} et {@code CleInterneJamaisJournaliseeTest} pour le
 * secret partagé.
 *
 * @see <a href="file:../../../../../../../../docs/decisions/2026-09-04-compte-de-controle-du-refus-403.md">
 *      docs/decisions/2026-09-04-compte-de-controle-du-refus-403.md</a>
 */
class CompteDeControleDuRefusTest {

    /**
     * Le compte de contrôle en vigueur. Si le métier du compte change un jour,
     * cette constante et {@code infra/keycloak/README.md} changent ensemble — et
     * la décision est mise à jour, pas contournée.
     */
    private static final String COMPTE_DE_CONTROLE = "thomas_ndzana";

    /**
     * Ancien compte de contrôle, habilité depuis le Sprint 6.3. Il n'est plus
     * interdit de pré-provisionnement — il l'est déjà en base —, mais il reste
     * nommé ici pour qu'une relecture de ce test raconte l'histoire complète.
     */
    private static final String ANCIEN_COMPTE_DE_CONTROLE = "pierre_belinga";

    private static final Path MIGRATION_PROFILS =
            Path.of("src", "main", "resources", "db", "dev", "V1000__profils_de_test_developpement.sql");

    /**
     * Les instructions de la migration, commentaires SQL retirés.
     *
     * <p>Indispensable, et la première version de ce test l'avait manqué : l'en-tête
     * de {@code V1000} <b>nomme</b> les deux comptes pour avertir de ne pas les
     * ajouter. Un test qui lirait le fichier entier échouerait donc sur
     * l'avertissement lui-même — il serait rouge en permanence, donc désactivé ou
     * ignoré sous quinze jours, c'est-à-dire inutile. Ce qui est interdit, ce sont
     * les {@code INSERT}, pas le fait de nommer le risque.
     */
    private static String instructionsSql() throws IOException {
        return Files.readAllLines(MIGRATION_PROFILS, StandardCharsets.UTF_8).stream()
                .filter(ligne -> !ligne.stripLeading().startsWith("--"))
                .reduce("", (a, b) -> a + "\n" + b);
    }

    @Test
    @DisplayName("le compte de controle du refus 403 n'est jamais pre-provisionne par V1000")
    void compteDeControleAbsentDuPreProvisionnement() throws IOException {
        assertThat(MIGRATION_PROFILS)
                .as("la migration de pre-provisionnement doit exister a cet emplacement")
                .exists();

        assertThat(instructionsSql())
                .as("""
                        %s est le compte de controle du refus 403 : il doit rester SANS profil local.
                        Lui en ouvrir un ne casse rien de visible, mais la checklist d'environnement
                        rendra 200 la ou elle attend 403 (UTILISATEUR_NON_HABILITE), sur un test de
                        securite, sans aucun message d'explication.
                        Besoin d'un profil pre-provisionne non lie, pour eprouver une liaison ?
                        Creer un compte jetable, jamais celui-ci.
                        Voir docs/decisions/2026-09-04-compte-de-controle-du-refus-403.md"""
                        .formatted(COMPTE_DE_CONTROLE))
                .doesNotContain(COMPTE_DE_CONTROLE);
    }

    @Test
    @DisplayName("l'ancien compte de controle n'est pas revenu dans la migration par inadvertance")
    void ancienCompteDeControleToujoursAbsentDeLaMigration() throws IOException {
        // pierre_belinga a recu un profil local EN BASE au Sprint 6.3, pas dans la
        // migration. Le remettre ici ferait diverger un environnement neuf d'un
        // environnement existant : deux postes, deux comportements, aucun signal.
        assertThat(instructionsSql())
                .as("le profil de %s a ete ouvert en base au Sprint 6.3, pas dans la migration ; "
                        + "l'ajouter ici ferait diverger un environnement neuf d'un environnement "
                        + "existant", ANCIEN_COMPTE_DE_CONTROLE)
                .doesNotContain(ANCIEN_COMPTE_DE_CONTROLE);
    }

}
