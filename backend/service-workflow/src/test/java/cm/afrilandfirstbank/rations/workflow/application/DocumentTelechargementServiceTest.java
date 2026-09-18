package cm.afrilandfirstbank.rations.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.workflow.application.DocumentTelechargementService.DocumentTelecharge;
import cm.afrilandfirstbank.rations.workflow.application.ResultatHabilitationUnite.AgentHabilite;
import cm.afrilandfirstbank.rations.workflow.domaine.PieceJointe;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.TransitionProcessus;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.DocumentNonProduitException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.PieceJointeIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.PieceJointeRepository;

/**
 * Telechargement du document signe d'un processus (rattrapage post-7F.6, demande n°8 de
 * la verification visuelle du Sprint 7F.6, point ferme au Sprint 7F.5 --
 * {@code docs/points-en-attente.md} section « PDF signe »).
 *
 * <p>Ce que {@code ProcessusControllerIT} ne peut pas voir : que la portee
 * d'acces est bien reutilisee depuis {@link ProcessusService#consulter}
 * (jamais un second appel au service Identite), que l'evenement d'audit n'est
 * publie qu'une fois le fichier reellement relu, et que le nom de fichier
 * extrait du chemin est bien le dernier segment.
 */
class DocumentTelechargementServiceTest {

    private static final Long ID = 740L;
    private static final String JETON = "Bearer jeton-de-test";
    private static final String IP = "10.0.0.5";
    private static final String CHEMIN = "2026/09/etat-rations-00002-20260907-p740.pdf";
    private static final byte[] CONTENU = { 1, 2, 3, 4 };
    private static final String LOGIN = "jean_mbarga";
    private static final String UNITE = "00002";

    private final ProcessusService processusService = mock(ProcessusService.class);
    private final PieceJointeRepository pieceJointeRepository = mock(PieceJointeRepository.class);
    private final StockageDocuments stockageDocuments = mock(StockageDocuments.class);
    private final PublicateurAudit publicateurAudit = mock(PublicateurAudit.class);

    private final DocumentTelechargementService service = new DocumentTelechargementService(
            processusService, pieceJointeRepository, stockageDocuments, publicateurAudit);

    private static PieceJointe unePieceJointe() {
        return new PieceJointe(ID, CHEMIN);
    }

    /**
     * Detail rendu par {@code ProcessusService.consulter}, tel que le code de
     * production le dereference desormais des l'entree de la methode --
     * necessaire meme quand le test ne porte que sur la piece jointe ou le
     * stockage.
     */
    private static ProcessusService.DetailProcessus unDetail() {
        ProcessusMensuel processus = TransitionProcessus.declencher(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), UNITE);
        return new ProcessusService.DetailProcessus(processus, null, new AgentHabilite(LOGIN, "AGENT_UNITE", UNITE));
    }

    // --- Nominal ------------------------------------------------------------------

    @Test
    @DisplayName("Le document est relu, le nom de fichier est le dernier segment du chemin, "
            + "et un seul evenement TELECHARGEMENT_DOCUMENT est publie")
    void telechargementNominal() {
        when(processusService.consulter(ID, JETON)).thenReturn(unDetail());
        when(pieceJointeRepository.findByIdProcessus(ID)).thenReturn(Optional.of(unePieceJointe()));
        when(stockageDocuments.lire(CHEMIN)).thenReturn(CONTENU);

        DocumentTelecharge document = service.telecharger(ID, JETON, IP);

        assertThat(document.contenu()).isEqualTo(CONTENU);
        assertThat(document.nomFichier()).isEqualTo("etat-rations-00002-20260907-p740.pdf");
        assertThat(document.typeMime()).isEqualTo("application/pdf");

        verify(processusService).consulter(ID, JETON);

        ArgumentCaptor<EvenementAudit> capture = ArgumentCaptor.forClass(EvenementAudit.class);
        verify(publicateurAudit).publier(capture.capture());
        EvenementAudit evenement = capture.getValue();
        assertThat(evenement.action()).isEqualTo("TELECHARGEMENT_DOCUMENT");
        assertThat(evenement.entiteCible()).isEqualTo("processus_mensuel");
        assertThat(evenement.idEntite()).isEqualTo(ID);
        // idUtilisateur reste nul (identifiant local non resolu sans appel
        // supplementaire), mais le login est desormais repris en contexte --
        // c'est lui que la page d'audit affiche a la place de "Non renseigne".
        assertThat(evenement.idUtilisateur()).isNull();
        assertThat(evenement.adresseIp()).isEqualTo(IP);
        assertThat(evenement.detailJson())
                .contains(LOGIN)
                .contains(UNITE)
                .contains("etat-rations-00002-20260907-p740.pdf")
                .contains(String.valueOf(CONTENU.length));
    }

    // --- Refus ----------------------------------------------------------------

    @Test
    @DisplayName("Processus introuvable : le refus de ProcessusService remonte tel quel, "
            + "rien n'est lu ni trace")
    void processusIntrouvable() {
        when(processusService.consulter(ID, JETON)).thenThrow(new ProcessusIntrouvableException(ID));

        assertThatThrownBy(() -> service.telecharger(ID, JETON, IP))
                .isInstanceOf(ProcessusIntrouvableException.class);

        verifyNoInteractions(pieceJointeRepository);
        verifyNoInteractions(stockageDocuments);
        verifyNoInteractions(publicateurAudit);
    }

    @Test
    @DisplayName("Hors portee : le refus de ProcessusService remonte tel quel -- la portee "
            + "n'est jamais revalidee ici")
    void horsPortee() {
        RuntimeException refus = new RuntimeException("hors portee");
        when(processusService.consulter(ID, JETON)).thenThrow(refus);

        assertThatThrownBy(() -> service.telecharger(ID, JETON, IP)).isSameAs(refus);

        verifyNoInteractions(pieceJointeRepository);
        verifyNoInteractions(stockageDocuments);
        verifyNoInteractions(publicateurAudit);
    }

    @Test
    @DisplayName("Aucune piece jointe (etat jamais soumis) : 404 PIECE_JOINTE_INTROUVABLE, "
            + "le stockage n'est jamais interroge")
    void aucunePieceJointe() {
        when(processusService.consulter(ID, JETON)).thenReturn(unDetail());
        when(pieceJointeRepository.findByIdProcessus(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.telecharger(ID, JETON, IP))
                .isInstanceOf(PieceJointeIntrouvableException.class);

        verifyNoInteractions(stockageDocuments);
        verifyNoInteractions(publicateurAudit);
    }

    @Test
    @DisplayName("Fichier illisible malgre une base qui l'atteste : le refus du stockage "
            + "remonte tel quel, aucune trace d'audit d'un telechargement qui n'a pas eu lieu")
    void fichierIllisible() {
        when(processusService.consulter(ID, JETON)).thenReturn(unDetail());
        when(pieceJointeRepository.findByIdProcessus(ID)).thenReturn(Optional.of(unePieceJointe()));
        when(stockageDocuments.lire(CHEMIN)).thenThrow(new DocumentNonProduitException("panne disque"));

        assertThatThrownBy(() -> service.telecharger(ID, JETON, IP))
                .isInstanceOf(DocumentNonProduitException.class);

        verify(publicateurAudit, never()).publier(any());
    }

}
