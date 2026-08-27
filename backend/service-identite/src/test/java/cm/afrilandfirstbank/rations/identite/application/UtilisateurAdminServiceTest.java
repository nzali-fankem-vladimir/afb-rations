package cm.afrilandfirstbank.rations.identite.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cm.afrilandfirstbank.rations.identite.api.dto.AttributionRoleRequest;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.identite.domaine.RoleEnum;
import cm.afrilandfirstbank.rations.identite.domaine.Utilisateur;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.identite.domaine.exception.AutoModificationInterditeException;
import cm.afrilandfirstbank.rations.identite.domaine.exception.CodeUniteIncoherentException;
import cm.afrilandfirstbank.rations.identite.domaine.exception.DernierAdministrateurException;
import cm.afrilandfirstbank.rations.identite.domaine.exception.UtilisateurIntrouvableException;
import cm.afrilandfirstbank.rations.identite.infrastructure.persistence.UtilisateurRepository;

/**
 * Attribution du role et du code unite (sous-sprint 1.2).
 *
 * <p>Les trois decisions de l'etape 4
 * ({@code docs/decisions/2026-08-26-attribution-role-administrateur.md}) sont
 * couvertes par {@link #autoModificationRefusee()} et
 * {@link #dernierAdministrateurActifProtege()}. La troisieme decision
 * (aucune invalidation de session) n'introduit aucun mecanisme a tester :
 * {@link #attributionNominaleAvecCodeUnite()} montre deja que le nouveau role
 * est immediatement porte par l'entite retournee, sans etape supplementaire.
 */
@ExtendWith(MockitoExtension.class)
class UtilisateurAdminServiceTest {

    private static Validator validateur;

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Mock
    private PublicateurAudit publicateurAudit;

    private UtilisateurAdminService service;
    private Utilisateur appelantAdmin;

    @BeforeAll
    static void creerValidateur() {
        try (ValidatorFactory fabrique = Validation.buildDefaultValidatorFactory()) {
            validateur = fabrique.getValidator();
        }
    }

    @BeforeEach
    void initialiser() {
        service = new UtilisateurAdminService(utilisateurRepository, new PorteeAccesService(),
                publicateurAudit);
        appelantAdmin = new Utilisateur("sara_biya", "BIYA", "Sara", RoleEnum.ADMIN, null);
        ReflectionTestUtils.setField(appelantAdmin, "id", 1L);
    }

    private Utilisateur utilisateur(Long id, String login, String nom, String prenom, RoleEnum role,
            String codeUnite) {
        Utilisateur utilisateur = new Utilisateur(login, nom, prenom, role, codeUnite);
        ReflectionTestUtils.setField(utilisateur, "id", id);
        return utilisateur;
    }

    @Test
    @DisplayName("1. attribution nominale d'un role avec code unite")
    void attributionNominaleAvecCodeUnite() {
        Utilisateur ngonoMarie = utilisateur(10L, "marie_ngono", "NGONO", "Marie",
                RoleEnum.AGENT_UNITE, "00002");
        when(utilisateurRepository.findById(10L)).thenReturn(Optional.of(ngonoMarie));

        Utilisateur resultat = service.attribuerRole(10L,
                new AttributionRoleRequest(RoleEnum.CHEF_UNITE_DA, "00050"), appelantAdmin, "10.0.0.5");

        assertThat(resultat.getRole()).isEqualTo(RoleEnum.CHEF_UNITE_DA);
        assertThat(resultat.getCodeUnite()).isEqualTo("00050");
        verify(publicateurAudit).publier(any(EvenementAudit.class));
    }

    @Test
    @DisplayName("le delta publie est complet : ancien et nouveau role, ancien et nouveau code unite")
    void deltaPublieComplet() throws Exception {
        Utilisateur ngonoMarie = utilisateur(10L, "marie_ngono", "NGONO", "Marie",
                RoleEnum.AGENT_UNITE, "00002");
        when(utilisateurRepository.findById(10L)).thenReturn(Optional.of(ngonoMarie));

        service.attribuerRole(10L, new AttributionRoleRequest(RoleEnum.CHEF_UNITE_DA, "00050"),
                appelantAdmin, "10.0.0.5");

        ArgumentCaptor<EvenementAudit> capture = ArgumentCaptor.forClass(EvenementAudit.class);
        verify(publicateurAudit).publier(capture.capture());
        EvenementAudit evenement = capture.getValue();

        assertThat(evenement.action()).isEqualTo("ATTRIBUTION_ROLE");
        assertThat(evenement.entiteCible()).isEqualTo("utilisateurs");
        assertThat(evenement.idEntite()).isEqualTo(10L);
        assertThat(evenement.idUtilisateur()).isEqualTo(1L);
        assertThat(evenement.adresseIp()).isEqualTo("10.0.0.5");
        // Le service emetteur est estampille par le producteur, pas ici.
        assertThat(evenement.serviceEmetteur()).isNull();

        JsonNode delta = new ObjectMapper().readTree(evenement.detailJson());
        assertThat(delta.get("role").get("avant").asText()).isEqualTo("AGENT_UNITE");
        assertThat(delta.get("role").get("apres").asText()).isEqualTo("CHEF_UNITE_DA");
        assertThat(delta.get("codeUnite").get("avant").asText()).isEqualTo("00002");
        assertThat(delta.get("codeUnite").get("apres").asText()).isEqualTo("00050");
        assertThat(delta.get("loginCible").asText()).isEqualTo("marie_ngono");
    }

    @Test
    @DisplayName("2. attribution d'un role a portee nationale sans code unite")
    void attributionRoleNationalSansCodeUnite() {
        Utilisateur eloundaEric = utilisateur(11L, "eric_elounda", "ELOUNDA", "Eric",
                RoleEnum.AGENT_UNITE, "00002");
        when(utilisateurRepository.findById(11L)).thenReturn(Optional.of(eloundaEric));

        Utilisateur resultat = service.attribuerRole(11L,
                new AttributionRoleRequest(RoleEnum.ARH, null), appelantAdmin, "10.0.0.5");

        assertThat(resultat.getRole()).isEqualTo(RoleEnum.ARH);
        assertThat(resultat.getCodeUnite()).isNull();
    }

    @Test
    @DisplayName("3. utilisateur cible inexistant : erreur attendue")
    void utilisateurCibleIntrouvable() {
        when(utilisateurRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.attribuerRole(99L,
                new AttributionRoleRequest(RoleEnum.ARH, null), appelantAdmin, "10.0.0.5"))
                .isInstanceOf(UtilisateurIntrouvableException.class);

        verify(publicateurAudit, never()).publier(any());
    }

    @Test
    @DisplayName("4. code unite invalide, quatre chiffres : erreur de validation du DTO")
    void codeUniteAQuatreChiffresRefuseParValidation() {
        AttributionRoleRequest requete = new AttributionRoleRequest(RoleEnum.AGENT_UNITE, "0002");

        Set<ConstraintViolation<AttributionRoleRequest>> violations = validateur.validate(requete);

        assertThat(violations)
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("codeUnite"));
    }

    @Test
    @DisplayName("5. role AGENT_UNITE sans code unite : erreur attendue")
    void roleLocalSansCodeUniteRefuse() {
        Utilisateur foudaPaul = utilisateur(12L, "paul_fouda", "FOUDA", "Paul", RoleEnum.ARH, null);
        when(utilisateurRepository.findById(12L)).thenReturn(Optional.of(foudaPaul));

        assertThatThrownBy(() -> service.attribuerRole(12L,
                new AttributionRoleRequest(RoleEnum.AGENT_UNITE, null), appelantAdmin, "10.0.0.5"))
                .isInstanceOf(CodeUniteIncoherentException.class);
    }

    @Test
    @DisplayName("6a. un administrateur ne peut pas modifier son propre role sur cet endpoint")
    void autoModificationRefusee() {
        when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(appelantAdmin));

        assertThatThrownBy(() -> service.attribuerRole(1L,
                new AttributionRoleRequest(RoleEnum.DRH, null), appelantAdmin, "10.0.0.5"))
                .isInstanceOf(AutoModificationInterditeException.class);

        verify(publicateurAudit, never()).publier(any());
    }

    @Test
    @DisplayName("6b. le dernier administrateur actif ne peut pas perdre le role ADMIN")
    void dernierAdministrateurActifProtege() {
        Utilisateur biyaSara = utilisateur(20L, "sara_biya2", "BIYA", "Sara2", RoleEnum.ADMIN, null);
        when(utilisateurRepository.findById(20L)).thenReturn(Optional.of(biyaSara));
        when(utilisateurRepository.countByRoleAndActif(RoleEnum.ADMIN, true)).thenReturn(1L);

        assertThatThrownBy(() -> service.attribuerRole(20L,
                new AttributionRoleRequest(RoleEnum.DRH, null), appelantAdmin, "10.0.0.5"))
                .isInstanceOf(DernierAdministrateurException.class);

        verify(publicateurAudit, never()).publier(any());
    }

    @Test
    @DisplayName("le retrait du role ADMIN est autorise s'il reste un autre administrateur actif")
    void retraitAutoriseSiAutreAdministrateurActif() {
        Utilisateur biyaSara = utilisateur(21L, "sara_biya3", "BIYA", "Sara3", RoleEnum.ADMIN, null);
        when(utilisateurRepository.findById(21L)).thenReturn(Optional.of(biyaSara));
        when(utilisateurRepository.countByRoleAndActif(RoleEnum.ADMIN, true)).thenReturn(2L);

        Utilisateur resultat = service.attribuerRole(21L,
                new AttributionRoleRequest(RoleEnum.DRH, null), appelantAdmin, "10.0.0.5");

        assertThat(resultat.getRole()).isEqualTo(RoleEnum.DRH);
    }

}
