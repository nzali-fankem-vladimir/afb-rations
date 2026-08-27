package cm.afrilandfirstbank.rations.commun.audit;

import static org.assertj.core.api.Assertions.assertThat;

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

}
