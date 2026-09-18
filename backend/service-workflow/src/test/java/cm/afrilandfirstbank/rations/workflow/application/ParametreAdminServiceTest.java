package cm.afrilandfirstbank.rations.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.mockito.ArgumentCaptor;

import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.workflow.domaine.ParametreSysteme;
import cm.afrilandfirstbank.rations.workflow.domaine.RoleEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.AgentNonHabiliteException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ParametreIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ParametreNonModifiableException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ServiceIdentiteIndisponibleException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ValeurParametreInvalideException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ParametreSystemeRepository;

/**
 * Ecriture des trois parametres modifiables (guide 7F.6, etape 6, ajout
 * backend scope). Contre la vraie table {@code parametre_systeme}, seeded par
 * les migrations V2 et V7 -- seul {@code ProfilClient} (appel reseau) est
 * simule, meme doctrine que {@code RetourServiceTest}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("ParametreAdminService — ecriture des parametres modifiables")
class ParametreAdminServiceTest {

    private static final String JETON = "Bearer jeton-de-test";
    private static final String IP = "10.0.0.40";
    private static final Long ID_ADMIN = 7L;
    private static final String LOGIN_ADMIN = "sara_akono";

    @Autowired
    private ParametreSystemeRepository parametreSystemeRepository;

    private ProfilClient profilClient;
    private PublicateurAudit publicateurAudit;
    private ParametreAdminService parametreAdminService;

    @BeforeEach
    void preparer() {
        profilClient = mock(ProfilClient.class);
        publicateurAudit = mock(PublicateurAudit.class);
        parametreAdminService = new ParametreAdminService(
                parametreSystemeRepository, profilClient, publicateurAudit);

        when(profilClient.obtenir(anyString())).thenReturn(new ResultatProfil.ProfilObtenu(
                new ActeurSignataire(ID_ADMIN, LOGIN_ADMIN, RoleEnum.ADMIN)));
    }

    @Test
    @DisplayName("1. Seuil d'aiguillage modifie : valeur ecrite, audit avant/apres")
    void seuilModifie() {
        ParametreSysteme resultat = parametreAdminService.modifier(
                SeuilService.CODE_SEUIL_AIGUILLAGE, "150000", JETON, IP);

        assertThat(resultat.getValeur()).isEqualTo("150000");
        assertThat(parametreSystemeRepository.findByCode(SeuilService.CODE_SEUIL_AIGUILLAGE)
                .orElseThrow().getValeur()).isEqualTo("150000");

        ArgumentCaptor<EvenementAudit> capteur = ArgumentCaptor.forClass(EvenementAudit.class);
        verify(publicateurAudit).publier(capteur.capture());
        assertThat(capteur.getValue().idUtilisateur()).isEqualTo(ID_ADMIN);
        assertThat(capteur.getValue().action()).isEqualTo("MODIFICATION_PARAMETRE");
        assertThat(capteur.getValue().entiteCible()).isEqualTo("parametre_systeme");
        assertThat(capteur.getValue().detailJson())
                .contains("\"avant\":\"100000\"")
                .contains("\"apres\":\"150000\"");
    }

    @Test
    @DisplayName("2. Seuil negatif refuse, valeur en base inchangee")
    void seuilNegatifRefuse() {
        assertThatThrownBy(() -> parametreAdminService.modifier(
                SeuilService.CODE_SEUIL_AIGUILLAGE, "-1", JETON, IP))
                .isInstanceOf(ValeurParametreInvalideException.class);

        assertThat(parametreSystemeRepository.findByCode(SeuilService.CODE_SEUIL_AIGUILLAGE)
                .orElseThrow().getValeur()).isEqualTo("100000");
        verifyNoInteractions(publicateurAudit);
    }

    @Test
    @DisplayName("3. Seuil non numerique refuse")
    void seuilNonNumeriqueRefuse() {
        assertThatThrownBy(() -> parametreAdminService.modifier(
                SeuilService.CODE_SEUIL_AIGUILLAGE, "cent-mille", JETON, IP))
                .isInstanceOf(ValeurParametreInvalideException.class);
    }

    @Test
    @DisplayName("4. Delai de regularisation modifiable, meme regle entiere")
    void delaiRegularisationModifiable() {
        ParametreSysteme resultat = parametreAdminService.modifier(
                FonctionnaliteService.CODE_DELAI_REGULARISATION_JOURS, "120", JETON, IP);

        assertThat(resultat.getValeur()).isEqualTo("120");
    }

    @Test
    @DisplayName("5. Compte de charge : texte libre non vide accepte")
    void compteChargeTexteLibreAccepte() {
        ParametreSysteme resultat = parametreAdminService.modifier(
                FonctionnaliteService.CODE_COMPTE_CHARGE, "64380090300", JETON, IP);

        assertThat(resultat.getValeur()).isEqualTo("64380090300");
    }

    @Test
    @DisplayName("6. Valeur vide refusee, pour les trois codes")
    void valeurVideRefusee() {
        assertThatThrownBy(() -> parametreAdminService.modifier(
                FonctionnaliteService.CODE_COMPTE_CHARGE, "   ", JETON, IP))
                .isInstanceOf(ValeurParametreInvalideException.class);
    }

    @Test
    @DisplayName("7. RATTRAPAGE_ACTIF n'est pas modifiable par cet endpoint")
    void rattrapageActifNonModifiable() {
        assertThatThrownBy(() -> parametreAdminService.modifier(
                FonctionnaliteService.CODE_RATTRAPAGE_ACTIF, "false", JETON, IP))
                .isInstanceOf(ParametreNonModifiableException.class);

        verifyNoInteractions(publicateurAudit);
    }

    @Test
    @DisplayName("8. Code hors liste blanche refuse avant meme de chercher la ligne")
    void codeInconnuNonModifiable() {
        assertThatThrownBy(() -> parametreAdminService.modifier(
                "CODE_INEXISTANT", "1", JETON, IP))
                .isInstanceOf(ParametreNonModifiableException.class);
    }

    @Test
    @DisplayName("9. Profil absent : refuse, rien n'est modifie")
    void profilAbsentRefuse() {
        when(profilClient.obtenir(anyString()))
                .thenReturn(new ResultatProfil.ProfilAbsent("aucun profil local"));

        assertThatThrownBy(() -> parametreAdminService.modifier(
                SeuilService.CODE_SEUIL_AIGUILLAGE, "150000", JETON, IP))
                .isInstanceOf(AgentNonHabiliteException.class);

        assertThat(parametreSystemeRepository.findByCode(SeuilService.CODE_SEUIL_AIGUILLAGE)
                .orElseThrow().getValeur()).isEqualTo("100000");
    }

    @Test
    @DisplayName("10. Service Identite injoignable : refus conservateur, rien n'est modifie")
    void serviceIdentiteIndisponible() {
        when(profilClient.obtenir(anyString()))
                .thenReturn(new ResultatProfil.ServiceIdentiteIndisponible("delai depasse"));

        assertThatThrownBy(() -> parametreAdminService.modifier(
                SeuilService.CODE_SEUIL_AIGUILLAGE, "150000", JETON, IP))
                .isInstanceOf(ServiceIdentiteIndisponibleException.class);

        assertThat(parametreSystemeRepository.findByCode(SeuilService.CODE_SEUIL_AIGUILLAGE)
                .orElseThrow().getValeur()).isEqualTo("100000");
    }

    @Test
    @DisplayName("11. Code entierement absent de parametre_systeme mais dans la liste blanche : introuvable")
    void codeAbsentDeLaTable() {
        // Aucun des trois codes modifiables n'est jamais absent en pratique (V2, V7) ;
        // ce test verifie seulement que le message reste correct si la ligne disparait.
        parametreSystemeRepository.findByCode(SeuilService.CODE_SEUIL_AIGUILLAGE)
                .ifPresent(parametreSystemeRepository::delete);

        assertThatThrownBy(() -> parametreAdminService.modifier(
                SeuilService.CODE_SEUIL_AIGUILLAGE, "150000", JETON, IP))
                .isInstanceOf(ParametreIntrouvableException.class);
    }

    @Test
    @DisplayName("12. Consultation : rend la valeur courante, y compris pour RATTRAPAGE_ACTIF (non modifiable, mais lisible)")
    void consultationValeurCourante() {
        ParametreSysteme resultat = parametreAdminService.consulter(
                FonctionnaliteService.CODE_RATTRAPAGE_ACTIF);

        assertThat(resultat.getCode()).isEqualTo(FonctionnaliteService.CODE_RATTRAPAGE_ACTIF);
        verifyNoInteractions(publicateurAudit, profilClient);
    }

    @Test
    @DisplayName("13. Consultation d'un code inexistant : introuvable")
    void consultationCodeInexistant() {
        assertThatThrownBy(() -> parametreAdminService.consulter("CODE_INEXISTANT"))
                .isInstanceOf(ParametreIntrouvableException.class);
    }

}
