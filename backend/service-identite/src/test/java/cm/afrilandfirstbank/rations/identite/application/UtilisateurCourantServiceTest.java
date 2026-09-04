package cm.afrilandfirstbank.rations.identite.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.identite.domaine.RoleEnum;
import cm.afrilandfirstbank.rations.identite.domaine.Utilisateur;
import cm.afrilandfirstbank.rations.identite.domaine.exception.UtilisateurNonHabiliteException;
import cm.afrilandfirstbank.rations.identite.infrastructure.persistence.UtilisateurRepository;

/**
 * Regle retenue au sprint 0.4 : pre-provisionnement du profil par l'administrateur,
 * puis liaison automatique au compte Keycloak a la premiere connexion.
 * Un jeton valide sans profil local ouvert est refuse.
 */
@ExtendWith(MockitoExtension.class)
class UtilisateurCourantServiceTest {

    private static final String SUB_JEAN_MBARGA = "4bf9cd35-4f6b-4b62-a005-12b1481d33fa";
    private static final String LOGIN_JEAN_MBARGA = "jean_mbarga";

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Mock
    private PublicateurAudit publicateurAudit;

    @InjectMocks
    private UtilisateurCourantService service;

    private Utilisateur jeanMbarga;

    @BeforeEach
    void profilPreProvisionne() {
        jeanMbarga = new Utilisateur(LOGIN_JEAN_MBARGA, "MBARGA", "Jean", RoleEnum.AGENT_UNITE, "00002");
    }

    private Jwt jeton(String sub, String login) {
        return Jwt.withTokenValue("jeton-de-test")
                .header("alg", "RS256")
                .subject(sub)
                .claim("preferred_username", login)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    @Test
    @DisplayName("premiere connexion : le profil ouvert au meme login est lie au compte Keycloak")
    void premiereConnexionLieLeProfil() {
        when(utilisateurRepository.findBySubKeycloak(SUB_JEAN_MBARGA)).thenReturn(Optional.empty());
        when(utilisateurRepository.findByLogin(LOGIN_JEAN_MBARGA)).thenReturn(Optional.of(jeanMbarga));

        Utilisateur resolu = service.resoudre(jeton(SUB_JEAN_MBARGA, LOGIN_JEAN_MBARGA));

        assertThat(resolu.getSubKeycloak()).isEqualTo(SUB_JEAN_MBARGA);
        assertThat(resolu.getRole()).isEqualTo(RoleEnum.AGENT_UNITE);
        assertThat(resolu.getCodeUnite()).isEqualTo("00002");
        assertThat(resolu.getDateDernierAcces()).isNotNull();
    }

    @Test
    @DisplayName("Sprint 6.3 : la liaison du compte Keycloak publie LIAISON_COMPTE_KEYCLOAK")
    void premiereConnexionPublieUneTraceDeLiaison() {
        when(utilisateurRepository.findBySubKeycloak(SUB_JEAN_MBARGA)).thenReturn(Optional.empty());
        when(utilisateurRepository.findByLogin(LOGIN_JEAN_MBARGA)).thenReturn(Optional.of(jeanMbarga));

        service.resoudre(jeton(SUB_JEAN_MBARGA, LOGIN_JEAN_MBARGA));

        ArgumentCaptor<EvenementAudit> capture = ArgumentCaptor.forClass(EvenementAudit.class);
        verify(publicateurAudit).publier(capture.capture());
        EvenementAudit evenement = capture.getValue();

        assertThat(evenement.action()).isEqualTo("LIAISON_COMPTE_KEYCLOAK");
        assertThat(evenement.entiteCible()).isEqualTo("utilisateurs");
        // Le sub etabli figure au delta : c'est l'information que la colonne
        // sub_keycloak porte desormais, mais dont elle ne dit ni la date ni
        // l'anteriorite.
        assertThat(evenement.detailJson())
                .contains(SUB_JEAN_MBARGA)
                .contains(LOGIN_JEAN_MBARGA)
                .contains("AGENT_UNITE");
    }

    @Test
    @DisplayName("Sprint 6.3 : une connexion ordinaire ne publie rien (pas un evenement par requete HTTP)")
    void connexionSuivanteNePublieAucuneTrace() {
        jeanMbarga.lierAuCompteKeycloak(SUB_JEAN_MBARGA);
        when(utilisateurRepository.findBySubKeycloak(SUB_JEAN_MBARGA)).thenReturn(Optional.of(jeanMbarga));

        service.resoudre(jeton(SUB_JEAN_MBARGA, LOGIN_JEAN_MBARGA));

        // date_dernier_acces est ecrit a chaque requete : le tracer noierait le
        // journal sous un evenement par appel HTTP, decision Sprint 6.3.
        verifyNoInteractions(publicateurAudit);
    }

    @Test
    @DisplayName("connexions suivantes : le profil est retrouve par le sub, sans repasser par le login")
    void connexionSuivanteParLeSub() {
        jeanMbarga.lierAuCompteKeycloak(SUB_JEAN_MBARGA);
        when(utilisateurRepository.findBySubKeycloak(SUB_JEAN_MBARGA)).thenReturn(Optional.of(jeanMbarga));

        Utilisateur resolu = service.resoudre(jeton(SUB_JEAN_MBARGA, LOGIN_JEAN_MBARGA));

        assertThat(resolu.getLogin()).isEqualTo(LOGIN_JEAN_MBARGA);
        verify(utilisateurRepository, never()).findByLogin(anyString());
    }

    @Test
    @DisplayName("aucun profil ouvert : l'acces est refuse, aucun profil n'est cree automatiquement")
    void aucunProfilOuvertRefuse() {
        when(utilisateurRepository.findBySubKeycloak(anyString())).thenReturn(Optional.empty());
        when(utilisateurRepository.findByLogin("pierre_belinga")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resoudre(jeton("a1b2c3d4-0000-0000-0000-000000000001", "pierre_belinga")))
                .isInstanceOf(UtilisateurNonHabiliteException.class)
                .hasMessageContaining("Aucun profil");

        verify(utilisateurRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("profil desactive : l'acces est refuse meme si le jeton est valide")
    void profilDesactiveRefuse() {
        jeanMbarga.lierAuCompteKeycloak(SUB_JEAN_MBARGA);
        jeanMbarga.desactiver();
        when(utilisateurRepository.findBySubKeycloak(SUB_JEAN_MBARGA)).thenReturn(Optional.of(jeanMbarga));

        assertThatThrownBy(() -> service.resoudre(jeton(SUB_JEAN_MBARGA, LOGIN_JEAN_MBARGA)))
                .isInstanceOf(UtilisateurNonHabiliteException.class)
                .hasMessageContaining("desactive");
    }

    @Test
    @DisplayName("login deja lie a un autre compte Keycloak : l'acces est refuse")
    void loginDejaLieRefuse() {
        jeanMbarga.lierAuCompteKeycloak("00000000-1111-2222-3333-444444444444");
        when(utilisateurRepository.findBySubKeycloak(SUB_JEAN_MBARGA)).thenReturn(Optional.empty());
        when(utilisateurRepository.findByLogin(LOGIN_JEAN_MBARGA)).thenReturn(Optional.of(jeanMbarga));

        assertThatThrownBy(() -> service.resoudre(jeton(SUB_JEAN_MBARGA, LOGIN_JEAN_MBARGA)))
                .isInstanceOf(UtilisateurNonHabiliteException.class)
                .hasMessageContaining("deja lie");
    }

}
