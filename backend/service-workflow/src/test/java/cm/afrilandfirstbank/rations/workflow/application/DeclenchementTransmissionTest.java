package cm.afrilandfirstbank.rations.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.workflow.application.ResultatDemandeTransmission.DejaTransmise;
import cm.afrilandfirstbank.rations.workflow.application.ResultatDemandeTransmission.EchecApresTentative;
import cm.afrilandfirstbank.rations.workflow.application.ResultatDemandeTransmission.EchecAvantPublication;
import cm.afrilandfirstbank.rations.workflow.application.ResultatDemandeTransmission.Transmise;

import org.mockito.ArgumentCaptor;

/**
 * La regle qui empeche un double paiement.
 *
 * <p>Ce fichier eprouve une seule propriete, mais c'est la plus lourde de consequences du
 * sous-sprint : <b>on ne rejoue jamais une transmission qui a pu partir.</b>
 *
 * <p>L'idempotence du producteur Kafka ne couvre que les reessais internes d'un meme
 * {@code send()}. Un second {@code send()} applicatif est un message neuf : rien ne le
 * rattache au premier. Or un echec de publication est ambigu par nature — un accuse peut se
 * perdre apres que le broker a ecrit le message. Reessayer alors ecrirait deux fois le meme
 * etat sur le topic, la comptabilite produirait deux jeux d'ecritures, et les memes
 * beneficiaires seraient payes deux fois. C'est ce que RG-13 interdit, et l'une des erreurs
 * interdites de CLAUDE.md section 15.
 *
 * <p>Aucune base ici : la propriete se prouve sur le nombre d'appels et sur le fait que le
 * drapeau n'est pose qu'apres un accuse. Executeur synchrone, pour la meme raison — ce n'est
 * pas le parallelisme qu'il faut prouver.
 */
@DisplayName("DeclenchementTransmission — la regle de reessai (RG-13)")
class DeclenchementTransmissionTest {

    private static final Long ID_PROCESSUS = 740L;
    private static final String UNITE = "00002";
    private static final String JETON = "Bearer jeton-de-test";
    private static final String IP = "10.0.0.12";

    /** Executeur synchrone : la tache tourne sur le fil du test. */
    private static final Executor SUR_PLACE = Runnable::run;

    private AppuiTransmission.ClientDeTest client;
    private PublicateurAudit publicateurAudit;
    private DeclenchementTransmission declenchement;

    @BeforeEach
    void preparer() {
        client = new AppuiTransmission.ClientDeTest();
        publicateurAudit = mock(PublicateurAudit.class);
        declenchement = new DeclenchementTransmission(client, publicateurAudit, SUR_PLACE);
    }

    private ResultatTransmissionCloture transmettre() {
        return declenchement.transmettre(ID_PROCESSUS, UNITE, JETON, IP);
    }

    // --- Le cas nominal -----------------------------------------------------------

    @Nested
    @DisplayName("Transmission aboutie")
    class Aboutie {

        @Test
        @DisplayName("un seul appel, le resultat le dit")
        void nominal() {
            client.repondraToujours(AppuiTransmission.accuse(4, 7_500L));

            ResultatTransmissionCloture resultat = transmettre();

            assertThat(resultat.transmis()).isTrue();
            assertThat(resultat.tentatives()).isEqualTo(1);
            assertThat(resultat.motif()).isNull();
            assertThat(client.nombreAppels()).isEqualTo(1);
        }

        @Test
        @DisplayName("etat deja transmis : un seul appel, aucun reessai, le valideur l'apprend")
        void dejaTransmisNeSeRejouePas() {
            client.repondraToujours(new DejaTransmise(
                    "L'etat 740 a deja ete transmis a la comptabilite le 2026-09-03T10:00."));

            ResultatTransmissionCloture resultat = transmettre();

            // Vrai, et c'est la seule lecture honnete : l'etat EST a la comptabilite.
            // Le rendre faux enverrait quelqu'un le retransmettre (RG-13).
            assertThat(resultat.transmis()).isTrue();
            assertThat(resultat.tentatives()).isEqualTo(1);
            assertThat(resultat.motif()).contains("deja ete transmis");
            assertThat(client.nombreAppels()).isEqualTo(1);
            verify(publicateurAudit, never()).publier(any(EvenementAudit.class));
        }

        @Test
        @DisplayName("un echec anterieur a la publication, puis un succes : deux appels")
        void reessaiQuiAboutit() {
            client.repondra(new EchecAvantPublication("service Transmission injoignable"),
                    AppuiTransmission.accuse(4, 7_500L));

            ResultatTransmissionCloture resultat = transmettre();

            assertThat(resultat.transmis()).isTrue();
            assertThat(resultat.tentatives()).isEqualTo(2);
            assertThat(client.nombreAppels()).isEqualTo(2);
        }
    }

    // --- La regle de reessai ------------------------------------------------------

    @Nested
    @DisplayName("On ne rejoue jamais ce qui a pu partir")
    class RegleDeReessai {

        @Test
        @DisplayName("echec APRES tentative : un seul appel, jamais deux")
        void echecAmbiguNeSeRejouePas() {
            client.repondraToujours(new EchecApresTentative(
                    "reponse 503 : PUBLICATION_ECHOUEE, aucun accuse du broker en 7 s"));

            ResultatTransmissionCloture resultat = transmettre();

            assertThat(client.nombreAppels())
                    .withFailMessage("Un echec de publication est AMBIGU : le message a pu etre "
                            + "ecrit et l'accuse se perdre. Le rejouer produirait deux jeux "
                            + "d'ecritures comptables pour les memes beneficiaires (RG-13).")
                    .isEqualTo(1);
            assertThat(resultat.transmis()).isFalse();
            assertThat(resultat.tentatives()).isEqualTo(1);
        }

        @Test
        @DisplayName("echec AVANT publication : reessaye, mais au plus deux tentatives")
        void echecReessayableSarreteADeuxTentatives() {
            client.repondraToujours(new EchecAvantPublication("service Saisie indisponible"));

            ResultatTransmissionCloture resultat = transmettre();

            assertThat(client.nombreAppels())
                    .isEqualTo(DeclenchementTransmission.TENTATIVES_MAXIMUM)
                    .isEqualTo(2);
            assertThat(resultat.transmis()).isFalse();
            assertThat(resultat.tentatives()).isEqualTo(2);
        }

        @Test
        @DisplayName("un echec reessayable suivi d'un echec ambigu s'arrete la")
        void unAmbiguArreteLaBoucle() {
            client.repondra(new EchecAvantPublication("connexion refusee"),
                    new EchecApresTentative("PUBLICATION_ECHOUEE"));

            transmettre();

            assertThat(client.nombreAppels()).isEqualTo(2);
        }

        @Test
        @DisplayName("un echec ambigu ne demande jamais une seconde publication")
        void echecAmbiguNeRedemandeRien() {
            client.repondraToujours(new EchecApresTentative("PUBLICATION_ECHOUEE"));

            transmettre();

            // Depuis le Sprint 5.3, le drapeau de RG-13 est tenu par le verrou, cote
            // service Transmission : ce declencheur n'ecrit plus rien. Ce qu'il doit
            // prouver ici est donc le seul geste qui lui reste dangereux — redemander.
            assertThat(client.nombreAppels()).isEqualTo(1);
        }
    }

    // --- Le signalement -----------------------------------------------------------

    @Nested
    @DisplayName("Un echec ne passe jamais inapercu")
    class Signalement {

        @Test
        @DisplayName("l'echec est trace en audit, avec son motif et l'unite")
        void echecTraceEnAudit() {
            client.repondraToujours(new EchecApresTentative("PUBLICATION_ECHOUEE : broker muet"));

            transmettre();

            ArgumentCaptor<EvenementAudit> capture = ArgumentCaptor.forClass(EvenementAudit.class);
            verify(publicateurAudit).publier(capture.capture());

            EvenementAudit evenement = capture.getValue();
            assertThat(evenement.action()).isEqualTo("TRANSMISSION_MANQUEE");
            assertThat(evenement.entiteCible()).isEqualTo("processus_mensuel");
            assertThat(evenement.idEntite()).isEqualTo(ID_PROCESSUS);
            assertThat(evenement.detailJson()).contains(UNITE).contains("broker muet");
        }

        @Test
        @DisplayName("le motif remonte dans le resultat, pour etre montre au valideur")
        void motifRemonteAuValideur() {
            client.repondraToujours(new EchecApresTentative("le broker n'a rien accuse"));

            assertThat(transmettre().motif()).contains("le broker n'a rien accuse");
        }

        @Test
        @DisplayName("une transmission reussie ne produit aucune trace d'echec")
        void succesSansTraceDEchec() {
            client.repondraToujours(AppuiTransmission.accuse(2, 4_000L));

            transmettre();

            // La trace de la transmission reussie est publiee par le verrou, cote service
            // Workflow, a la confirmation : ce declencheur ne doit rien publier de plus.
            verify(publicateurAudit, never()).publier(any(EvenementAudit.class));
        }
    }

    // --- Robustesse ---------------------------------------------------------------

    @Nested
    @DisplayName("La cloture n'est jamais mise en cause")
    class Robustesse {

        @Test
        @DisplayName("un incident imprevu ne remonte pas en exception")
        void incidentAvale() {
            TransmissionClient clientFautif = (id, entete) -> {
                throw new IllegalStateException("base indisponible");
            };

            DeclenchementTransmission fragile = new DeclenchementTransmission(
                    clientFautif, publicateurAudit, SUR_PLACE);

            ResultatTransmissionCloture resultat =
                    fragile.transmettre(ID_PROCESSUS, UNITE, JETON, IP);

            // La validation est deja commitee : aucun incident de transmission ne doit la
            // transformer en erreur pour le valideur, qui a bien valide.
            assertThat(resultat.transmis()).isFalse();
            assertThat(resultat.motif()).contains("base indisponible");
        }

        @Test
        @DisplayName("un pool sature rend la main tout de suite, sans attendre")
        void poolSatureNeBloquePas() {
            Executor refusant = tache -> {
                throw new java.util.concurrent.RejectedExecutionException("pool sature");
            };
            DeclenchementTransmission sature = new DeclenchementTransmission(
                    client, publicateurAudit, refusant);

            ResultatTransmissionCloture resultat =
                    sature.transmettre(ID_PROCESSUS, UNITE, JETON, IP);

            assertThat(resultat.transmis()).isFalse();
            assertThat(resultat.motif()).contains("fils de transmission");
            assertThat(client.nombreAppels())
                    .withFailMessage("Rien ne doit avoir ete demande au service Transmission : "
                            + "la tache n'a jamais demarre.")
                    .isZero();
        }
    }

    // --- Outillage ----------------------------------------------------------------

    private static Long eqId() {
        return org.mockito.ArgumentMatchers.eq(ID_PROCESSUS);
    }

    private static String eqIp() {
        return org.mockito.ArgumentMatchers.eq(IP);
    }

}
