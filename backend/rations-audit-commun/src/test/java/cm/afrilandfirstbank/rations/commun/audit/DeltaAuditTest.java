package cm.afrilandfirstbank.rations.commun.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Construction du delta avant/apres. Le cas d'echappement est la raison d'etre
 * de cette classe : le Sprint 1.2 formait ce JSON par concatenation, motif qui
 * ne doit pas se propager aux six services.
 */
class DeltaAuditTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("delta complet : les deux champs, avant et apres, meme inchanges")
    void deltaComplet() throws Exception {
        String json = DeltaAudit.nouveau()
                .champ("role", "AGENT_UNITE", "CHEF_UNITE_DA")
                .champ("codeUnite", "00002", "00002")
                .enJson();

        JsonNode arbre = MAPPER.readTree(json);

        assertThat(arbre.get("role").get("avant").asText()).isEqualTo("AGENT_UNITE");
        assertThat(arbre.get("role").get("apres").asText()).isEqualTo("CHEF_UNITE_DA");
        // Champ inchange : present quand meme. Pour une piece de controle interne,
        // « inchange » et « pas considere » ne doivent pas se confondre.
        assertThat(arbre.get("codeUnite").get("avant").asText()).isEqualTo("00002");
        assertThat(arbre.get("codeUnite").get("apres").asText()).isEqualTo("00002");
    }

    @Test
    @DisplayName("valeurs nulles conservees : « passe a rien » est une information")
    void valeursNulles() throws Exception {
        String json = DeltaAudit.nouveau().champ("codeUnite", "00002", null).enJson();

        JsonNode arbre = MAPPER.readTree(json);

        assertThat(arbre.get("codeUnite").get("avant").asText()).isEqualTo("00002");
        assertThat(arbre.get("codeUnite").get("apres").isNull()).isTrue();
    }

    @Test
    @DisplayName("guillemets, antislash et sauts de ligne : le JSON reste valide")
    void echappementDesCaracteresSpeciaux() throws Exception {
        String valeurHostile = "MBARGA \"Jean\"\\ retour\nligne";

        String json = DeltaAudit.nouveau().champ("motif", null, valeurHostile).enJson();

        // Le point du test : readTree echouerait sur un JSON mal forme, ce que
        // produirait une concatenation de chaines.
        JsonNode arbre = MAPPER.readTree(json);
        assertThat(arbre.get("motif").get("apres").asText()).isEqualTo(valeurHostile);
    }

    @Test
    @DisplayName("enum et nombre : serialises selon leur type, pas en chaine forcee")
    void typesNonTextuels() throws Exception {
        String json = DeltaAudit.nouveau()
                .champ("actif", true, false)
                .champ("montant", 2500, 3000)
                .enJson();

        JsonNode arbre = MAPPER.readTree(json);

        assertThat(arbre.get("actif").get("avant").isBoolean()).isTrue();
        assertThat(arbre.get("montant").get("apres").asInt()).isEqualTo(3000);
    }

    @Test
    @DisplayName("aucun champ : null plutot qu'un objet vide")
    void deltaVide() {
        assertThat(DeltaAudit.nouveau().enJson()).isNull();
    }

    /**
     * Non-regression du correctif releve au Sprint 2.2 : Jackson serialisait par
     * defaut une {@code LocalDate} en tableau de composants ({@code [2026,9,1]}),
     * contre l'ISO 8601 exige par CLAUDE.md section 11.
     *
     * <p>Le journal restait lisible, ce qui rendait le defaut d'autant plus facile
     * a laisser passer : il ne cassait rien, il rendait seulement chaque date
     * illisible sans effort. Il valait pour les six services.
     */
    @Test
    @DisplayName("les dates sortent en ISO 8601, jamais en tableau de composants")
    void datesEnIso8601() throws Exception {
        String json = DeltaAudit.nouveau()
                .champ("dateDebut", null, LocalDate.of(2026, 9, 1))
                .champ("dateValidation", null, LocalDateTime.of(2026, 8, 27, 17, 50, 1))
                .contexte("dateAction", LocalDate.of(2026, 12, 31))
                .enJson();

        JsonNode arbre = MAPPER.readTree(json);

        assertThat(arbre.get("dateDebut").get("apres").isTextual())
                .as("une date serialisee en tableau force le lecteur du journal a la reconstituer")
                .isTrue();
        assertThat(arbre.get("dateDebut").get("apres").asText()).isEqualTo("2026-09-01");
        assertThat(arbre.get("dateValidation").get("apres").asText()).startsWith("2026-08-27T17:50:01");
        assertThat(arbre.get("dateAction").asText()).isEqualTo("2026-12-31");
    }

}
