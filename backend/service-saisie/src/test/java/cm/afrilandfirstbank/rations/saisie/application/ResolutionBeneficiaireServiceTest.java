package cm.afrilandfirstbank.rations.saisie.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.saisie.domaine.Beneficiaire;
import cm.afrilandfirstbank.rations.saisie.infrastructure.BeneficiaireRepository;

/**
 * Résolution du bénéficiaire au fil de la saisie (US-03, étape 4 du Sprint 3.1).
 *
 * <p>Le repository et le publicateur d'audit sont simulés : ce qui est vérifié
 * ici, c'est la <b>règle de résolution</b> et le respect des deux décisions
 * prises avec l'utilisateur
 * ({@code docs/decisions/2026-08-28-resolution-beneficiaire-et-incoherence-nom.md}) :
 *
 * <ol>
 *   <li>l'identité tient au <b>seul numéro de compte courant</b> ;</li>
 *   <li>compte connu, nom divergent → on conserve l'existant intact et on trace.</li>
 * </ol>
 *
 * <p>Données camerounaises, code guichet réel {@code 00002} (Douala Bonanjo).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Résolution du bénéficiaire (US-03)")
class ResolutionBeneficiaireServiceTest {

    private static final String COMPTE = "03702001234567";
    private static final String AGENCE = "00002"; // Douala Bonanjo

    @Mock
    private BeneficiaireRepository beneficiaireRepository;

    @Mock
    private PublicateurAudit publicateurAudit;

    @InjectMocks
    private ResolutionBeneficiaireService resolutionBeneficiaireService;

    /** Bénéficiaire déjà en base, avec un identifiant technique attribué. */
    private static Beneficiaire beneficiaireExistant(String nom, String prenom, Long id) {
        Beneficiaire beneficiaire = new Beneficiaire(nom, prenom, COMPTE, AGENCE);
        ReflectionTestUtils.setField(beneficiaire, "id", id);
        return beneficiaire;
    }

    @Test
    @DisplayName("1. bénéficiaire inconnu : il est créé et retourné")
    void beneficiaireInconnu_estCreeEtRetourne() {
        when(beneficiaireRepository.findByNumCompteCourant(COMPTE)).thenReturn(Optional.empty());
        when(beneficiaireRepository.save(any(Beneficiaire.class))).thenAnswer(appel -> appel.getArgument(0));

        Beneficiaire resolu = resolutionBeneficiaireService.resoudre("NGUEMA", "Jean-Pierre", COMPTE, AGENCE);

        assertThat(resolu.getNom()).isEqualTo("NGUEMA");
        assertThat(resolu.getPrenom()).isEqualTo("Jean-Pierre");
        assertThat(resolu.getNumCompteCourant()).isEqualTo(COMPTE);
        assertThat(resolu.getCodeAgence()).isEqualTo(AGENCE);
        verify(beneficiaireRepository).save(any(Beneficiaire.class));
        verifyNoInteractions(publicateurAudit);
    }

    @Test
    @DisplayName("2. bénéficiaire déjà connu, identité identique : retourne l'existant, sans doublon ni trace")
    void compteDejaConnuIdentiteIdentique_retourneLExistantSansRienCreerNiTracer() {
        Beneficiaire existant = beneficiaireExistant("NGUEMA", "Jean-Pierre", 88L);
        when(beneficiaireRepository.findByNumCompteCourant(COMPTE)).thenReturn(Optional.of(existant));

        Beneficiaire resolu = resolutionBeneficiaireService.resoudre("NGUEMA", "Jean-Pierre", COMPTE, AGENCE);

        assertThat(resolu).isSameAs(existant);
        verify(beneficiaireRepository, never()).save(any(Beneficiaire.class));
        verifyNoInteractions(publicateurAudit);
    }

    @Test
    @DisplayName("3a. décision 1 & 2 : compte connu, nom réellement différent → existant intact + audit INCOHERENCE_BENEFICIAIRE")
    void compteConnuNomDivergent_identifieSurLeCompteSeul_conserveIntactEtTrace() {
        Beneficiaire existant = beneficiaireExistant("NGUEMA", "Jean-Pierre", 88L);
        when(beneficiaireRepository.findByNumCompteCourant(COMPTE)).thenReturn(Optional.of(existant));

        // Même compte, nom franchement autre : erreur de compte possible.
        Beneficiaire resolu = resolutionBeneficiaireService.resoudre("MBALLA", "Paul", COMPTE, AGENCE);

        // Décision 1 : identifié sur le seul compte → pas de doublon créé.
        assertThat(resolu).isSameAs(existant);
        verify(beneficiaireRepository, never()).save(any(Beneficiaire.class));

        // Décision 2 : l'existant n'est pas modifié.
        assertThat(existant.getNom()).isEqualTo("NGUEMA");
        assertThat(existant.getPrenom()).isEqualTo("Jean-Pierre");

        // Décision 2 : la trace d'audit est émise, sur la bonne entité.
        ArgumentCaptor<EvenementAudit> capture = ArgumentCaptor.forClass(EvenementAudit.class);
        verify(publicateurAudit).publier(capture.capture());
        EvenementAudit evenement = capture.getValue();
        assertThat(evenement.action()).isEqualTo("INCOHERENCE_BENEFICIAIRE");
        assertThat(evenement.entiteCible()).isEqualTo("beneficiaires");
        assertThat(evenement.idEntite()).isEqualTo(88L);
        assertThat(evenement.detailJson())
                .contains("NGUEMA").contains("MBALLA")
                .contains("Jean-Pierre").contains("Paul");
    }

    @Test
    @DisplayName("3b. condition de normalisation : écart de casse, d'espaces et d'accents seul → aucune trace")
    void ecartPurementDeFormeSurLeNom_normalise_neTracePas() {
        Beneficiaire existant = beneficiaireExistant("NGUÉMA", "Jean-Pierre", 88L);
        when(beneficiaireRepository.findByNumCompteCourant(COMPTE)).thenReturn(Optional.of(existant));

        // "  nguema " vs "NGUÉMA" et "JEAN-PIERRE" vs "Jean-Pierre" :
        // espaces, casse, accent — rien de significatif après normalisation.
        Beneficiaire resolu = resolutionBeneficiaireService.resoudre("  nguema ", "JEAN-PIERRE", COMPTE, AGENCE);

        assertThat(resolu).isSameAs(existant);
        verify(beneficiaireRepository, never()).save(any(Beneficiaire.class));
        verifyNoInteractions(publicateurAudit);
    }

}
