package cm.afrilandfirstbank.rations.workflow.application;

import java.util.ArrayList;
import java.util.List;

import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.workflow.application.ResultatDemandeTransmission.Transmise;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ProcessusMensuelRepository;

/**
 * De quoi doter un {@link ValidationService} d'une chaine de transmission eprouvable, dans
 * les tests qui portent sur autre chose qu'elle.
 *
 * <h2>Executeur synchrone, deliberement</h2>
 *
 * <p>{@link DeclenchementTransmission} recoit ici {@code Runnable::run} : la transmission
 * s'execute sur le fil du test, dans sa transaction. Deux raisons.
 *
 * <p><b>Correction du test</b> : sous {@code @DataJpaTest}, la transaction du test n'est pas
 * commitee et n'est visible que de son propre fil. Un pool reel ferait lire la base par un
 * autre fil, qui ne verrait aucun des processus crees par le test — l'echec serait un
 * artefact du montage, pas un defaut du code.
 *
 * <p><b>Ce qui doit etre prouve</b> : la propriete de ce sous-sprint n'est pas le
 * parallelisme, c'est de ne jamais republier ce qui a pu partir, et de ne poser le drapeau de
 * RG-13 qu'apres un accuse. Les deux se prouvent sur un seul fil. Le dimensionnement du pool
 * reel — quatre fils, file nulle, rejet journalise puis leve — se justifie dans
 * {@code ConfigurationTransmission} et se constate a la verification manuelle.
 */
final class AppuiTransmission {

    private AppuiTransmission() {
    }

    /** Accuse par defaut : topic de developpement, premiere partition. */
    static Transmise accuse(int nombreLignes, long montantTotal) {
        return new Transmise("rations.etat.valide", 0, 12L, nombreLignes, montantTotal);
    }

    /** Adresse d'origine des gestes du verrou simules, pour les traces d'audit. */
    private static final String IP_SIMULEE = "10.0.0.1";

    /**
     * Un declencheur cable comme en production, <b>verrou de RG-13 compris</b>.
     *
     * <h2>Ce que le client de test doit simuler depuis le Sprint 5.3</h2>
     *
     * <p>Le drapeau {@code transmis_comptabilite} n'est plus pose par le service Workflow
     * apres coup : il est <b>reserve puis confirme</b> par le service Transmission, qui
     * appelle pour cela l'endpoint interne du verrou. Un client de test qui rendrait
     * simplement un accuse sans passer par le verrou laisserait donc le drapeau a faux, et
     * les tests de circuit ne prouveraient plus rien de RG-13.
     *
     * <p>Le client est donc enveloppe : quand il rend un accuse, les deux gestes du verrou
     * sont joues <b>pour de vrai</b>, sur le vrai service et la vraie base. C'est une
     * simulation fidele du service distant, pas un raccourci — et elle fait au passage
     * eprouver le refus de seconde transmission par les tests de circuit existants.
     */
    static DeclenchementTransmission declenchement(TransmissionClient client,
            ProcessusMensuelRepository processusRepository, PublicateurAudit publicateurAudit) {

        VerrouTransmissionService verrou =
                new VerrouTransmissionService(processusRepository, publicateurAudit);

        return new DeclenchementTransmission(
                avecVerrou(client, verrou), publicateurAudit, Runnable::run);
    }

    /**
     * Enveloppe un client de test des deux gestes que le service Transmission accomplit
     * reellement autour de sa publication.
     */
    private static TransmissionClient avecVerrou(TransmissionClient client,
            VerrouTransmissionService verrou) {

        return (idProcessus, enteteAutorisation) -> {
            ResultatDemandeTransmission reponse =
                    client.demanderTransmission(idProcessus, enteteAutorisation);

            if (!(reponse instanceof Transmise accuse)) {
                return reponse;
            }

            ResultatVerrouTransmission reservation = verrou.reserver(idProcessus, IP_SIMULEE);
            if (reservation.resultat() == ResultatVerrouTransmission.Resultat.DEJA_TRANSMISE) {
                return new ResultatDemandeTransmission.DejaTransmise(reservation.message());
            }

            verrou.confirmer(idProcessus, accuse.topic(), accuse.partition(), accuse.offset(),
                    accuse.nombreLignes(), accuse.montantTotal(), IP_SIMULEE);
            return accuse;
        };
    }

    /**
     * Un service Transmission de laboratoire : il rend ce qu'on lui dit de rendre et retient
     * ce qu'on lui a demande.
     *
     * <p>Ecrit a la main plutot que simule par Mockito : le nombre d'appels est <b>la</b>
     * chose a verifier — un reessai de trop sur un echec ambigu, et c'est un double paiement.
     * Un compteur qu'on lit directement se relit plus surement qu'un {@code verify} au bon
     * nombre d'invocations.
     */
    static final class ClientDeTest implements TransmissionClient {

        private final List<Long> appels = new ArrayList<>();
        private final List<ResultatDemandeTransmission> reponses = new ArrayList<>();
        private ResultatDemandeTransmission reponseParDefaut = accuse(1, 1_000L);

        /** Reponses servies dans l'ordre ; au-dela, la reponse par defaut. */
        ClientDeTest repondra(ResultatDemandeTransmission... suite) {
            reponses.addAll(List.of(suite));
            return this;
        }

        ClientDeTest repondraToujours(ResultatDemandeTransmission reponse) {
            this.reponseParDefaut = reponse;
            return this;
        }

        @Override
        public ResultatDemandeTransmission demanderTransmission(Long idProcessus,
                String enteteAutorisation) {
            appels.add(idProcessus);
            int rang = appels.size() - 1;
            return rang < reponses.size() ? reponses.get(rang) : reponseParDefaut;
        }

        int nombreAppels() {
            return appels.size();
        }

        List<Long> processusAppeles() {
            return List.copyOf(appels);
        }
    }

}
