package cm.afrilandfirstbank.rations.identite.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cm.afrilandfirstbank.rations.identite.domaine.ResultatHabilitation;
import cm.afrilandfirstbank.rations.identite.domaine.RoleEnum;
import cm.afrilandfirstbank.rations.identite.domaine.Utilisateur;

/**
 * Verdict d'habilitation pour un couple utilisateur / code unite
 * (convention {@code docs/appel-habilitation.md}).
 *
 * <p>La portee elle-meme est testee dans {@link PorteeAccesServiceTest} ; on
 * verifie ici l'assemblage du resultat et la propagation du verdict pour les
 * deux natures de portee.
 */
class HabilitationServiceTest {

    private static final String UNITE_DOUALA_BONANJO = "00002";
    private static final String UNITE_BAFOUSSAM = "00003";

    private final HabilitationService service = new HabilitationService(new PorteeAccesService());

    @Test
    @DisplayName("role a portee locale, sur sa propre unite : autorise, portee non nationale")
    void porteeLocaleSurSaPropreUniteAutorise() {
        Utilisateur mbargaJean = new Utilisateur(
                "jean_mbarga", "MBARGA", "Jean", RoleEnum.AGENT_UNITE, UNITE_DOUALA_BONANJO);

        ResultatHabilitation resultat = service.verifier(mbargaJean, UNITE_DOUALA_BONANJO);

        assertThat(resultat.autorise()).isTrue();
        assertThat(resultat.porteeNationale()).isFalse();
        assertThat(resultat.login()).isEqualTo("jean_mbarga");
        assertThat(resultat.role()).isEqualTo(RoleEnum.AGENT_UNITE);
        assertThat(resultat.codeUniteDemande()).isEqualTo(UNITE_DOUALA_BONANJO);
    }

    @Test
    @DisplayName("role a portee locale, sur une autre unite : refuse")
    void porteeLocaleSurUneAutreUniteRefuse() {
        Utilisateur essamaPaul = new Utilisateur(
                "paul_essama", "ESSAMA", "Paul", RoleEnum.CHEF_UNITE_DA, UNITE_DOUALA_BONANJO);

        ResultatHabilitation resultat = service.verifier(essamaPaul, UNITE_BAFOUSSAM);

        assertThat(resultat.autorise()).isFalse();
        assertThat(resultat.porteeNationale()).isFalse();
        assertThat(resultat.codeUniteDemande()).isEqualTo(UNITE_BAFOUSSAM);
    }

    @Test
    @DisplayName("role a portee nationale : autorise sur toute unite, portee nationale")
    void porteeNationaleAutoriseePartout() {
        Utilisateur tchindaAgnes = new Utilisateur(
                "agnes_tchinda", "TCHINDA", "Agnes", RoleEnum.DRH, null);

        ResultatHabilitation resultat = service.verifier(tchindaAgnes, UNITE_BAFOUSSAM);

        assertThat(resultat.autorise()).isTrue();
        assertThat(resultat.porteeNationale()).isTrue();
    }

    @Test
    @DisplayName("directeur reseau : portee nationale par defaut, decision Sprint 1.1")
    void directeurReseauPorteeNationaleParDefaut() {
        Utilisateur atanganaSylvie = new Utilisateur(
                "sylvie_atangana", "ATANGANA", "Sylvie", RoleEnum.DIRECTEUR_RESEAU_DR, null);

        ResultatHabilitation resultat = service.verifier(atanganaSylvie, UNITE_DOUALA_BONANJO);

        assertThat(resultat.autorise()).isTrue();
        assertThat(resultat.porteeNationale()).isTrue();
    }

}
