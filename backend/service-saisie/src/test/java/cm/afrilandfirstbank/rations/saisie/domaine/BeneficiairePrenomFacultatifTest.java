package cm.afrilandfirstbank.rations.saisie.domaine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Le prenom d'un beneficiaire est facultatif (Sprint 8.1, retour utilisateur), et
 * enregistre comme chaine vide plutot que {@code null} : la colonne reste
 * {@code NOT NULL}, donc aucune migration, et tout ce qui lit le prenom recoit une
 * chaine. La creation et la correction passent par le meme point, ce que ces tests
 * verrouillent pour qu'aucun des deux chemins ne l'oublie.
 */
class BeneficiairePrenomFacultatifTest {

    @Test
    @DisplayName("creation : un prenom absent devient une chaine vide, jamais null")
    void creationSansPrenom() {
        assertThat(new Beneficiaire("MBARGA", null, "02000123456", "00002").getPrenom()).isEmpty();
    }

    @Test
    @DisplayName("creation : un prenom blanc devient une chaine vide")
    void creationAvecPrenomBlanc() {
        assertThat(new Beneficiaire("MBARGA", "   ", "02000123456", "00002").getPrenom()).isEmpty();
    }

    @Test
    @DisplayName("creation : un prenom renseigne est conserve tel quel")
    void creationAvecPrenom() {
        assertThat(new Beneficiaire("MBARGA", "Jean", "02000123456", "00002").getPrenom()).isEqualTo("Jean");
    }

    @Test
    @DisplayName("correction : on peut retirer un prenom, et null n'est jamais enregistre")
    void correctionRetireLePrenom() {
        Beneficiaire beneficiaire = new Beneficiaire("MBARGA", "Jean", "02000123456", "00002");

        beneficiaire.corrigerIdentite("MBARGA", "", "00002");
        assertThat(beneficiaire.getPrenom()).isEmpty();

        beneficiaire.corrigerIdentite("MBARGA", "Jean", "00002");
        beneficiaire.corrigerIdentite("MBARGA", null, "00002");
        assertThat(beneficiaire.getPrenom()).isEmpty();
    }
}
