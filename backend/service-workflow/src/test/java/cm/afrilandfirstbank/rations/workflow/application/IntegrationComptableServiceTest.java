package cm.afrilandfirstbank.rations.workflow.application;

import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutIntegrationEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.TransitionProcessus;
import cm.afrilandfirstbank.rations.workflow.domaine.VerrouTransmission;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.AccuseContradictoireException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusNonTransmisException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ProcessusMensuelRepository;

/**
 * L'application d'un accuse comptable au processus (Sprint 5.2, guide etapes 4 et 5).
 *
 * <p>Ce que {@code TransitionIntegrationTest} ne voit pas et que ces tests couvrent :
 * l'ecriture effective, la conversion de la date, la traduction des refus en exceptions,
 * et surtout <b>la publication d'audit — une seule pour deux receptions du meme accuse</b>.
 */
class IntegrationComptableServiceTest {

    private static final Long ID = 740L;
    private static final String IP = "rations.etat.accuse";
    private static final String REFERENCE = "CPT-2026-07-000512";
    private static final String DATE_ISO = "2026-08-18T02:15:00Z";
    private static final String MOTIF = "Compte 00002000123456 clos depuis le 12/07.";

    private final ProcessusMensuelRepository processusMensuelRepository =
            mock(ProcessusMensuelRepository.class);
    private final PublicateurAudit publicateurAudit = mock(PublicateurAudit.class);

    private final IntegrationComptableService service =
            new IntegrationComptableService(processusMensuelRepository, publicateurAudit);

    // --- Nominal ------------------------------------------------------------------

    @Test
    @DisplayName("Un accuse d'integration est ecrit sur le processus, et la date ISO est "
            + "convertie a l'instant equivalent")
    void accuseIntegreEcrit() {
        ProcessusMensuel processus = unEtatTransmis();
        when(processusMensuelRepository.findById(ID)).thenReturn(Optional.of(processus));
        when(processusMensuelRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ResultatIntegrationComptable resultat = service.appliquerAccuse(
                ID, StatutIntegrationEnum.INTEGRE, REFERENCE, DATE_ISO, null, IP);

        assertThat(resultat.aEcrit()).isTrue();
        assertThat(processus.getStatutIntegration()).isEqualTo(StatutIntegrationEnum.INTEGRE);
        assertThat(processus.getReferenceComptable()).isEqualTo(REFERENCE);
        assertThat(processus.getDateTraitement()).isEqualTo(
                OffsetDateTime.parse(DATE_ISO).atZoneSameInstant(ZoneId.systemDefault())
                        .toLocalDateTime());
        verify(processusMensuelRepository).save(processus);
    }

    @Test
    @DisplayName("Un accuse de rejet conserve son motif, visible dans le suivi (US-15)")
    void accuseRejeteConserveLeMotif() {
        ProcessusMensuel processus = unEtatTransmis();
        when(processusMensuelRepository.findById(ID)).thenReturn(Optional.of(processus));
        when(processusMensuelRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ResultatIntegrationComptable resultat = service.appliquerAccuse(
                ID, StatutIntegrationEnum.REJETE, null, DATE_ISO, MOTIF, IP);

        assertThat(resultat.statutIntegration()).isEqualTo(StatutIntegrationEnum.REJETE);
        assertThat(resultat.motifIntegration()).isEqualTo(MOTIF);
        assertThat(processus.getMotifIntegration()).isEqualTo(MOTIF);
    }

    @Test
    @DisplayName("L'ecriture est tracee avec l'avant et l'apres : un controle interne doit "
            + "pouvoir refaire l'histoire d'un paiement")
    void ecritureTracee() {
        ProcessusMensuel processus = unEtatTransmis();
        when(processusMensuelRepository.findById(ID)).thenReturn(Optional.of(processus));
        when(processusMensuelRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.appliquerAccuse(ID, StatutIntegrationEnum.INTEGRE, REFERENCE, DATE_ISO, null, IP);

        ArgumentCaptor<EvenementAudit> capture = ArgumentCaptor.forClass(EvenementAudit.class);
        verify(publicateurAudit, times(1)).publier(capture.capture());
        EvenementAudit trace = capture.getValue();

        assertThat(trace.action()).isEqualTo("INTEGRATION_COMPTABLE");
        assertThat(trace.entiteCible()).isEqualTo("processus_mensuel");
        // Aucun utilisateur derriere un accuse comptable : l'auteur est un module externe.
        assertThat(trace.idUtilisateur()).isNull();
        assertThat(trace.detailJson())
                .contains("EN_ATTENTE")
                .contains("INTEGRE")
                .contains(REFERENCE);
    }

    // --- Test 8 du guide : idempotence --------------------------------------------

    @Test
    @DisplayName("Test 8 — le meme accuse recu deux fois : la seconde ne provoque ni ecriture "
            + "ni trace d'audit, et le resultat est identique")
    void memeAccuseDeuxFoisUneSeuleEcritureUneSeuleTrace() {
        ProcessusMensuel processus = unEtatTransmis();
        when(processusMensuelRepository.findById(ID)).thenReturn(Optional.of(processus));
        when(processusMensuelRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ResultatIntegrationComptable premiere = service.appliquerAccuse(
                ID, StatutIntegrationEnum.INTEGRE, REFERENCE, DATE_ISO, null, IP);
        ResultatIntegrationComptable seconde = service.appliquerAccuse(
                ID, StatutIntegrationEnum.INTEGRE, REFERENCE, DATE_ISO, null, IP);

        assertThat(premiere.aEcrit()).isTrue();
        assertThat(seconde.aEcrit()).isFalse();
        assertThat(seconde.statutIntegration()).isEqualTo(premiere.statutIntegration());
        assertThat(seconde.referenceComptable()).isEqualTo(premiere.referenceComptable());
        assertThat(seconde.dateTraitement()).isEqualTo(premiere.dateTraitement());

        verify(processusMensuelRepository, times(1)).save(any());
        verify(publicateurAudit, times(1)).publier(any());
    }

    @Test
    @DisplayName("Dix rejeux du meme accuse : toujours une seule ecriture et une seule trace")
    void dixRejeuxUneSeuleEcriture() {
        ProcessusMensuel processus = unEtatTransmis();
        when(processusMensuelRepository.findById(ID)).thenReturn(Optional.of(processus));
        when(processusMensuelRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        for (int rejeu = 0; rejeu < 10; rejeu++) {
            service.appliquerAccuse(
                    ID, StatutIntegrationEnum.REJETE, REFERENCE, DATE_ISO, MOTIF, IP);
        }

        verify(processusMensuelRepository, times(1)).save(any());
        verify(publicateurAudit, times(1)).publier(any());
    }

    // --- Refus --------------------------------------------------------------------

    @Test
    @DisplayName("Test 4 — un identifiant inconnu rend 404, sans rien ecrire")
    void processusInconnu() {
        when(processusMensuelRepository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.appliquerAccuse(
                ID, StatutIntegrationEnum.INTEGRE, REFERENCE, DATE_ISO, null, IP))
                .isInstanceOf(ProcessusIntrouvableException.class);

        verify(processusMensuelRepository, never()).save(any());
        verifyNoInteractions(publicateurAudit);
    }

    @Test
    @DisplayName("Test 5 — un etat jamais transmis rend 422, et le drapeau de transmission n'est "
            + "pas pose pour autant")
    void processusJamaisTransmis() {
        ProcessusMensuel processus = unEtatNonTransmis();
        when(processusMensuelRepository.findById(ID)).thenReturn(Optional.of(processus));

        assertThatThrownBy(() -> service.appliquerAccuse(
                ID, StatutIntegrationEnum.INTEGRE, REFERENCE, DATE_ISO, null, IP))
                .isInstanceOf(ProcessusNonTransmisException.class)
                .hasMessageContaining("jamais ete transmis");

        assertThat(processus.isTransmisComptabilite()).isFalse();
        assertThat(processus.getStatutIntegration()).isNull();
        verify(processusMensuelRepository, never()).save(any());
    }

    @Test
    @DisplayName("Test 9 — un accuse contredisant un statut definitif rend 409, sans rien ecrire "
            + "ni tracer d'ecriture")
    void accuseContradictoire() {
        ProcessusMensuel processus = unEtatTransmis();
        when(processusMensuelRepository.findById(ID)).thenReturn(Optional.of(processus));
        when(processusMensuelRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.appliquerAccuse(ID, StatutIntegrationEnum.INTEGRE, REFERENCE, DATE_ISO, null, IP);

        assertThatThrownBy(() -> service.appliquerAccuse(
                ID, StatutIntegrationEnum.REJETE, null, DATE_ISO, MOTIF, IP))
                .isInstanceOf(AccuseContradictoireException.class)
                .hasMessageContaining("INTEGRE")
                .hasMessageContaining("REJETE");

        assertThat(processus.getStatutIntegration()).isEqualTo(StatutIntegrationEnum.INTEGRE);
        // Une seule ecriture, une seule trace : celles du premier accuse.
        verify(processusMensuelRepository, times(1)).save(any());
        verify(publicateurAudit, times(1)).publier(any());
    }

    // --- Date ---------------------------------------------------------------------

    @Test
    @DisplayName("Une date absente laisse la colonne nulle : un manque n'est pas une "
            + "contradiction, et perdre le statut serait un mauvais echange")
    void dateAbsenteAcceptee() {
        ProcessusMensuel processus = unEtatTransmis();
        when(processusMensuelRepository.findById(ID)).thenReturn(Optional.of(processus));
        when(processusMensuelRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ResultatIntegrationComptable resultat = service.appliquerAccuse(
                ID, StatutIntegrationEnum.INTEGRE, REFERENCE, null, null, IP);

        assertThat(resultat.aEcrit()).isTrue();
        assertThat(processus.getDateTraitement()).isNull();
    }

    @Test
    @DisplayName("La conversion de la date est deterministe : le meme accuse produit la meme "
            + "valeur, sans quoi l'idempotence ne reconnaitrait pas un rejeu")
    void conversionDeterministe() {
        ProcessusMensuel processus = unEtatTransmis();
        when(processusMensuelRepository.findById(ID)).thenReturn(Optional.of(processus));
        when(processusMensuelRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.appliquerAccuse(ID, StatutIntegrationEnum.INTEGRE, REFERENCE, DATE_ISO, null, IP);
        LocalDateTime premiere = processus.getDateTraitement();

        ResultatIntegrationComptable seconde = service.appliquerAccuse(
                ID, StatutIntegrationEnum.INTEGRE, REFERENCE, DATE_ISO, null, IP);

        assertThat(seconde.aEcrit()).isFalse();
        assertThat(processus.getDateTraitement()).isEqualTo(premiere);
    }

    @Test
    @DisplayName("Le meme instant exprime dans un autre fuseau est reconnu comme le meme accuse")
    void memeInstantAutreFuseau() {
        ProcessusMensuel processus = unEtatTransmis();
        when(processusMensuelRepository.findById(ID)).thenReturn(Optional.of(processus));
        when(processusMensuelRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.appliquerAccuse(ID, StatutIntegrationEnum.INTEGRE, REFERENCE,
                "2026-08-18T02:15:00Z", null, IP);
        ResultatIntegrationComptable seconde = service.appliquerAccuse(
                ID, StatutIntegrationEnum.INTEGRE, REFERENCE, "2026-08-18T03:15:00+01:00",
                null, IP);

        assertThat(seconde.aEcrit()).isFalse();
    }

    // --- Montages -----------------------------------------------------------------

    private static ProcessusMensuel unEtatNonTransmis() {
        return declencherSur(8, 2026, "00002");
    }

    /**
     * Un etat cloture puis transmis : {@code transmis_comptabilite} a vrai et
     * {@code statut_integration} a {@code EN_ATTENTE}.
     *
     * <p>Il passe par les vrais gestes du verrou du Sprint 5.3 — reservation puis
     * confirmation — et non par un raccourci : la reservation exige un etat CLOTURE, et
     * un jeu d'essai qui contournerait cette exigence ne prouverait rien de ce que le
     * code fait en production.
     */
    private static ProcessusMensuel unEtatTransmis() {
        ProcessusMensuel processus = unEtatNonTransmis();
        TransitionProcessus.soumettre(processus);
        TransitionProcessus.transfererAuChefUnite(processus);
        TransitionProcessus.cloturerApresValidationChefUnite(processus);
        VerrouTransmission.reserver(processus, LocalDateTime.now());
        VerrouTransmission.confirmer(processus);
        return processus;
    }


    /**
     * Un etat declenche sur le mois indique, borne du premier au dernier jour.
     *
     * <p><b>Le mois n'est evalue qu'une fois</b>, ce qui compte : les jeux d'essai
     * l'obtiennent souvent d'un compteur {@code prochainMois()} a effet de bord, et
     * l'inliner deux fois pour composer les deux bornes produirait une periode a
     * cheval sur deux mois differents.
     *
     * <p>Les periodes mensuelles restent DISJOINTES entre elles, ce qui est
     * desormais indispensable : la contrainte d'exclusion
     * {@code ex_processus_normal_sans_chevauchement} refuse deux etats NORMAL dont
     * les periodes se recouvrent, meme partiellement (Maille 1).
     */
    private static ProcessusMensuel declencherSur(int mois, int annee, String codeUnite) {
        LocalDate debut = LocalDate.of(annee, mois, 1);
        return TransitionProcessus.declencher(debut, debut.plusMonths(1).minusDays(1), codeUnite);
    }

}
