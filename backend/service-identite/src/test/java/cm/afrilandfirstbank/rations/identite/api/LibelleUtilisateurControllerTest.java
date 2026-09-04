package cm.afrilandfirstbank.rations.identite.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import cm.afrilandfirstbank.rations.identite.api.dto.LibelleUtilisateurResponse;
import cm.afrilandfirstbank.rations.identite.domaine.RoleEnum;
import cm.afrilandfirstbank.rations.identite.domaine.Utilisateur;
import cm.afrilandfirstbank.rations.identite.domaine.exception.LotTropGrandException;
import cm.afrilandfirstbank.rations.identite.infrastructure.persistence.UtilisateurRepository;

/**
 * {@code GET /identite/utilisateurs/libelles} — endpoint interne demandé par le
 * service Reporting pour nommer les acteurs d'un historique (Sprint 6.1).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LibelleUtilisateurController — traduction d'un lot d'identifiants (Sprint 6.1)")
class LibelleUtilisateurControllerTest {

    @Mock
    private UtilisateurRepository utilisateurRepository;

    private LibelleUtilisateurController controller;

    @BeforeEach
    void preparer() {
        controller = new LibelleUtilisateurController(utilisateurRepository);
    }

    private Utilisateur utilisateur(Long id, String login, String nom, String prenom) {
        Utilisateur utilisateur = new Utilisateur(login, nom, prenom, RoleEnum.CHEF_UNITE_DA, "00002");
        org.springframework.test.util.ReflectionTestUtils.setField(utilisateur, "id", id);
        return utilisateur;
    }

    @Test
    @DisplayName("Aucun identifiant : rend une liste vide sans interroger le repository")
    void aucunIdentifiant_rendListeVide() {
        ResponseEntity<List<LibelleUtilisateurResponse>> reponse = controller.traduire(List.of());

        assertThat(reponse.getBody()).isEmpty();
    }

    @Test
    @DisplayName("Un identifiant inconnu est absent de la reponse, sans erreur")
    void identifiantInconnu_absentSansErreur() {
        when(utilisateurRepository.findAllById(java.util.Set.of(1L, 2L)))
                .thenReturn(List.of(utilisateur(1L, "jean_mbarga", "Mbarga", "Jean")));

        ResponseEntity<List<LibelleUtilisateurResponse>> reponse = controller.traduire(List.of(1L, 2L));

        assertThat(reponse.getBody()).hasSize(1);
        assertThat(reponse.getBody().get(0).login()).isEqualTo("jean_mbarga");
    }

    @Test
    @DisplayName("Identifiants dupliques : dedoublonnes avant l'appel au repository")
    void identifiantsDupliques_dedoublonnesAvantAppel() {
        when(utilisateurRepository.findAllById(java.util.Set.of(1L)))
                .thenReturn(List.of(utilisateur(1L, "jean_mbarga", "Mbarga", "Jean")));

        controller.traduire(List.of(1L, 1L, 1L));

        verify(utilisateurRepository).findAllById(java.util.Set.of(1L));
    }

    @Test
    @DisplayName("Lot au-dela de la borne : refuse en LotTropGrandException")
    void lotTropGrand_refuse() {
        List<Long> lot = java.util.stream.LongStream.rangeClosed(1, 201).boxed().toList();

        assertThatThrownBy(() -> controller.traduire(lot))
                .isInstanceOf(LotTropGrandException.class)
                .hasMessageContaining("201");
    }

}
