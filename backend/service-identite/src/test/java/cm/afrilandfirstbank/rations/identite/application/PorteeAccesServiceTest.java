package cm.afrilandfirstbank.rations.identite.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cm.afrilandfirstbank.rations.identite.domaine.RoleEnum;
import cm.afrilandfirstbank.rations.identite.domaine.Utilisateur;

/**
 * Portee d'acces par role, document de conception section 10.
 *
 * <p>Le cas du Directeur Reseau (portee nationale par defaut) applique la
 * decision du Sprint 1.1 : voir
 * {@code docs/decisions/2026-08-26-portee-acces-directeur-reseau.md}.
 */
class PorteeAccesServiceTest {

    private static final String UNITE_SIEGE_YAOUNDE = "00001";
    private static final String UNITE_DOUALA_BONANJO = "00002";

    private final PorteeAccesService service = new PorteeAccesService();

    @Test
    @DisplayName("agent d'unite accedant a sa propre unite : autorise")
    void agentUniteAccedantASaPropreUniteAutorise() {
        Utilisateur eloundaEric = new Utilisateur(
                "eric_elounda", "ELOUNDA", "Eric", RoleEnum.AGENT_UNITE, UNITE_DOUALA_BONANJO);

        assertThat(service.peutAccederAUnite(eloundaEric, UNITE_DOUALA_BONANJO)).isTrue();
    }

    @Test
    @DisplayName("agent d'unite accedant a une autre unite : refuse")
    void agentUniteAccedantAUneAutreUniteRefuse() {
        Utilisateur eloundaEric = new Utilisateur(
                "eric_elounda", "ELOUNDA", "Eric", RoleEnum.AGENT_UNITE, UNITE_DOUALA_BONANJO);

        assertThat(service.peutAccederAUnite(eloundaEric, UNITE_SIEGE_YAOUNDE)).isFalse();
    }

    @Test
    @DisplayName("chef d'unite accedant a une autre unite : refuse")
    void chefUniteAccedantAUneAutreUniteRefuse() {
        Utilisateur ngonoMarie = new Utilisateur(
                "marie_ngono", "NGONO", "Marie", RoleEnum.CHEF_UNITE_DA, UNITE_DOUALA_BONANJO);

        assertThat(service.peutAccederAUnite(ngonoMarie, UNITE_SIEGE_YAOUNDE)).isFalse();
    }

    @Test
    @DisplayName("analyste RH accedant a n'importe quelle unite : autorise")
    void analysteRhAccedantAToutesLesUnitesAutorise() {
        Utilisateur fouda = new Utilisateur("paul_fouda", "FOUDA", "Paul", RoleEnum.ARH, null);

        assertThat(service.peutAccederAUnite(fouda, UNITE_SIEGE_YAOUNDE)).isTrue();
        assertThat(service.peutAccederAUnite(fouda, UNITE_DOUALA_BONANJO)).isTrue();
        assertThat(service.determinerPortee(fouda).estNationale()).isTrue();
    }

    @Test
    @DisplayName("administrateur accedant a n'importe quelle unite : autorise")
    void administrateurAccedantAToutesLesUnitesAutorise() {
        Utilisateur biya = new Utilisateur("sara_biya", "BIYA", "Sara", RoleEnum.ADMIN, null);

        assertThat(service.peutAccederAUnite(biya, UNITE_SIEGE_YAOUNDE)).isTrue();
        assertThat(service.peutAccederAUnite(biya, UNITE_DOUALA_BONANJO)).isTrue();
        assertThat(service.determinerPortee(biya).estNationale()).isTrue();
    }

    @Test
    @DisplayName("directeur reseau : portee nationale par defaut, decision Sprint 1.1")
    void directeurReseauPorteeNationaleParDefaut() {
        Utilisateur mballaHenri = new Utilisateur(
                "henri_mballa", "MBALLA", "Henri", RoleEnum.DIRECTEUR_RESEAU_DR, null);

        assertThat(service.peutAccederAUnite(mballaHenri, UNITE_SIEGE_YAOUNDE)).isTrue();
        assertThat(service.peutAccederAUnite(mballaHenri, UNITE_DOUALA_BONANJO)).isTrue();
        assertThat(service.determinerPortee(mballaHenri).estNationale()).isTrue();
    }

}
