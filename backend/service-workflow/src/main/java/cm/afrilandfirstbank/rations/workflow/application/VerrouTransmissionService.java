package cm.afrilandfirstbank.rations.workflow.application;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import cm.afrilandfirstbank.rations.commun.audit.DeltaAudit;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.workflow.application.ResultatVerrouTransmission.Resultat;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.VerrouTransmission;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.EtatNonClotureException;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.ProcessusIntrouvableException;
import cm.afrilandfirstbank.rations.workflow.infrastructure.ProcessusMensuelRepository;

/**
 * Tient le verrou d'unicite de transmission (RG-13, Sprint 5.3) : reservation avant
 * publication, confirmation apres accuse du broker, liberation sur echec prouve.
 *
 * <h2>Pourquoi ce verrou vit ici et non dans le service Transmission</h2>
 *
 * <p>Le service Transmission publie, mais il <b>n'a pas de base</b> (CLAUDE.md section 3).
 * L'unique source de verite est {@code processus_mensuel}, qui vit ici. Un verrou porte
 * par une memoire du service Transmission — cache, table locale — disparaitrait au premier
 * redemarrage, c'est-a-dire au moment precis ou un rejeu est le plus probable. C'est le
 * meme raisonnement qu'au Sprint 5.2 pour l'idempotence de l'accuse comptable.
 *
 * <h2>Trois transactions courtes, jamais une longue</h2>
 *
 * <p>Chaque geste ouvre sa propre transaction ({@link Propagation#REQUIRES_NEW}) et la
 * referme aussitot : le verrou de ligne ne dure que quelques millisecondes. Tenir la ligne
 * verrouillee pendant la publication Kafka — sept secondes au pire — bloquerait toute
 * lecture concurrente du meme dossier, et immobiliserait une connexion de la reserve
 * pendant un appel reseau, ce que les doctrines des Sprints 2.3, 3.4 et 4.1 ecartent
 * partout ailleurs.
 *
 * <p>{@code REQUIRES_NEW} et non {@code REQUIRED} : ces ecritures constatent des faits
 * exterieurs et deja acquis — un verrou pose, un evenement sur le broker. Une transaction
 * englobante qui echouerait ensuite effacerait le constat, et laisserait un etat publie
 * mais repute non transmis : la reprise republierait, et la comptabilite produirait deux
 * jeux d'ecritures.
 *
 * <h2>Ce que ce service ne fait pas</h2>
 *
 * <p><b>Il ne verifie pas la portee d'acces.</b> Le role est filtre par la couche api, et
 * la portee sur l'unite a deja ete etablie deux fois dans la meme chaine : par la
 * validation qui a produit la cloture, puis par {@code GET /processus/{id}} que le service
 * Transmission interroge sur le jeton relaye. Une troisieme interrogation du service
 * Identite ajouterait cinq secondes au pire cas d'un fil HTTP qui attend, pour reposer une
 * question deja tranchee.
 *
 * <p><b>Il ne produit aucune ecriture comptable</b> et ne parle jamais a Kafka : il tient
 * un drapeau, rien de plus.
 */
@Service
public class VerrouTransmissionService {

    private static final Logger journal = LoggerFactory.getLogger(VerrouTransmissionService.class);

    private static final String ENTITE_CIBLE = "processus_mensuel";
    private static final String ACTION_TRANSMISSION = "TRANSMISSION_COMPTABLE";
    private static final String ACTION_DOUBLON_REFUSE = "TRANSMISSION_DOUBLON_REFUSEE";
    private static final String ACTION_LIBERATION = "TRANSMISSION_RESERVATION_LIBEREE";

    /**
     * Prefixe reperable en supervision : quelqu'un a demande une seconde transmission d'un
     * etat deja parti. Le refus a fonctionne — c'est une information, pas une alerte —,
     * mais une repetition signale un rejeu en boucle qu'il faut comprendre.
     */
    public static final String PREFIXE_DOUBLON = "TRANSMISSION DOUBLON REFUSEE";

    private final ProcessusMensuelRepository processusMensuelRepository;
    private final PublicateurAudit publicateurAudit;

    public VerrouTransmissionService(ProcessusMensuelRepository processusMensuelRepository,
            PublicateurAudit publicateurAudit) {
        this.processusMensuelRepository = processusMensuelRepository;
        this.publicateurAudit = publicateurAudit;
    }

    /**
     * Reserve la transmission de l'etat, ou dit qu'elle a deja eu lieu.
     *
     * <p>C'est <b>le</b> point de serialisation de RG-13 : tous les chemins d'une seconde
     * transmission — rejeu de cloture, appel manuel de l'endpoint interne, deux instances,
     * reprise apres incident — passent par cette methode, et la ligne y est chargee sous
     * verrou exclusif.
     *
     * @param adresseIp origine de la demande, pour la trace d'audit
     * @return {@code RESERVEE} : l'appelant peut publier, et lui seul.
     *         {@code DEJA_TRANSMISE} : il ne doit rien publier
     * @throws ProcessusIntrouvableException identifiant inconnu ({@code 404})
     * @throws EtatNonClotureException l'etat n'est pas cloture ({@code 422})
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResultatVerrouTransmission reserver(Long idProcessus, String adresseIp) {
        ProcessusMensuel processus = verrouiller(idProcessus);
        LocalDateTime avantReservation = processus.getDateReservationTransmission();

        return switch (VerrouTransmission.reserver(processus, LocalDateTime.now())) {

            case VerrouTransmission.Reservation.Reservee reservee -> {
                ProcessusMensuel enregistre = processusMensuelRepository.save(processus);
                journal.info("Transmission de l'etat {} (unite {}) reservee a {} : la publication "
                                + "peut avoir lieu, et une seule fois (RG-13).",
                        idProcessus, enregistre.getCodeUnite(), reservee.instant());
                yield ResultatVerrouTransmission.de(Resultat.RESERVEE,
                        "Transmission reservee : la publication peut avoir lieu.", enregistre);
            }

            case VerrouTransmission.Reservation.DejaTransmise deja -> {
                journal.warn("{} : {}", PREFIXE_DOUBLON, deja.message());
                publierRefusDeDoublon(processus, avantReservation, adresseIp);
                yield ResultatVerrouTransmission.de(Resultat.DEJA_TRANSMISE, deja.message(),
                        processus);
            }

            case VerrouTransmission.Reservation.EtatNonCloture refus ->
                    throw new EtatNonClotureException(refus.message());
        };
    }

    /**
     * Confirme que l'evenement est sur le broker : le statut d'integration passe a
     * {@code EN_ATTENTE}, et l'etat cesse d'etre « reserve, issue inconnue ».
     *
     * <p>C'est ici, et seulement ici, qu'est publiee la trace d'audit
     * {@code TRANSMISSION_COMPTABLE} : elle atteste d'un fait — un evenement accepte par le
     * broker — et non d'une intention. Une trace posee a la reservation affirmerait une
     * transmission dont on ignore encore l'issue.
     *
     * <p><b>Idempotente</b> : une confirmation rejouee ne reecrit rien et ne trace rien de
     * plus. Meme discipline qu'au Sprint 5.2, ou un accuse deja applique ne publie aucun
     * evenement — une seconde ligne ferait croire a un second traitement comptable.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResultatVerrouTransmission confirmer(Long idProcessus, String topic, Integer partition,
            Long offset, Integer nombreLignes, Long montantTotal, String adresseIp) {

        ProcessusMensuel processus = verrouiller(idProcessus);
        boolean dejaConfirmee = processus.getStatutIntegration() != null;

        VerrouTransmission.confirmer(processus);
        ProcessusMensuel enregistre = processusMensuelRepository.save(processus);

        if (dejaConfirmee) {
            // Aucune trace d'audit sur une confirmation rejouee : une seconde ligne ferait
            // croire a un second traitement comptable du meme etat (doctrine Sprint 5.2).
            journal.info("Transmission de l'etat {} deja confirmee (statut {}) : rejeu sans effet.",
                    idProcessus, enregistre.getStatutIntegration());
            return ResultatVerrouTransmission.de(Resultat.CONFIRMEE,
                    "Transmission deja confirmee : rejeu sans effet.", enregistre);
        }

        journal.info("Etat {} (unite {}) transmis a la comptabilite : {} ligne(s), {} FCFA, "
                        + "topic {} partition {} offset {}.",
                idProcessus, enregistre.getCodeUnite(), nombreLignes, montantTotal, topic,
                partition, offset);

        publierTraceDeTransmission(enregistre, topic, partition, offset, nombreLignes,
                montantTotal, adresseIp);

        return ResultatVerrouTransmission.de(Resultat.CONFIRMEE,
                "Transmission confirmee : l'accuse comptable est attendu.", enregistre);
    }

    /**
     * Leve une reservation dont l'appelant atteste qu'aucun evenement n'est parti.
     *
     * <p><b>La preuve appartient a l'appelant.</b> Le service Transmission ne demande une
     * liberation que sur un echec anterieur a tout envoi — serialisation impossible,
     * broker injoignable des l'appel. Sur un echec ambigu — delai d'accuse depasse — la
     * reservation <b>reste posee</b>, et l'etat devient reperable par son anciennete : on
     * ne rejoue jamais une transmission qui a pu partir (doctrine Sprint 5.1).
     *
     * <p>Refusee si un accuse comptable est deja arrive : la comptabilite ayant repondu,
     * l'evenement etait bien parti.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResultatVerrouTransmission liberer(Long idProcessus, String motif, String adresseIp) {
        ProcessusMensuel processus = verrouiller(idProcessus);
        LocalDateTime reservationLevee = processus.getDateReservationTransmission();

        return switch (VerrouTransmission.liberer(processus)) {

            case VerrouTransmission.Liberation.Liberee ignoree -> {
                ProcessusMensuel enregistre = processusMensuelRepository.save(processus);
                journal.warn("Reservation de transmission de l'etat {} levee : aucun evenement "
                                + "n'est parti ({}). Une reprise reste possible.",
                        idProcessus, motif);
                publierLiberation(enregistre, reservationLevee, motif, adresseIp);
                yield ResultatVerrouTransmission.de(Resultat.LIBEREE,
                        "Reservation levee : une reprise reste possible.", enregistre);
            }

            case VerrouTransmission.Liberation.Refusee refus -> {
                journal.error("Liberation refusee pour l'etat {} : {}", idProcessus,
                        refus.message());
                yield ResultatVerrouTransmission.de(Resultat.LIBERATION_REFUSEE, refus.message(),
                        processus);
            }
        };
    }

    // --- Outillage -----------------------------------------------------------------

    /**
     * Charge le processus <b>sous verrou exclusif de ligne</b>. Toute la resistance a la
     * concurrence de ce sous-sprint tient dans cet appel : deux instances qui reservent le
     * meme etat y sont serialisees par PostgreSQL.
     */
    private ProcessusMensuel verrouiller(Long idProcessus) {
        return processusMensuelRepository.verrouillerPourTransmission(idProcessus)
                .orElseThrow(() -> new ProcessusIntrouvableException(idProcessus));
    }

    /**
     * Trace la transmission effective (CLAUDE.md section 12), reprise de
     * {@code EnregistrementTransmission} du Sprint 5.1.
     *
     * <p>La partition et l'offset y figurent : ils permettent de retrouver l'evenement
     * exact sur le broker longtemps apres l'expiration de la retention du topic.
     *
     * <p><b>{@code idUtilisateur} est nul</b> : ce service n'appelle pas
     * {@code GET /identite/moi}, et l'auteur reel de la cloture est deja porte par l'etape
     * {@code VALIDATION_DA} ou {@code VALIDATION_DR}.
     */
    private void publierTraceDeTransmission(ProcessusMensuel processus, String topic,
            Integer partition, Long offset, Integer nombreLignes, Long montantTotal,
            String adresseIp) {

        publicateurAudit.publier(EvenementAudit.de(
                null,
                ACTION_TRANSMISSION,
                ENTITE_CIBLE,
                processus.getId(),
                adresseIp,
                DeltaAudit.nouveau()
                        .champ("statutIntegration", null, processus.getStatutIntegration())
                        .contexte("dateReservation", processus.getDateReservationTransmission())
                        .contexte("topic", topic)
                        .contexte("partition", partition)
                        .contexte("offset", offset)
                        .contexte("codeUnite", processus.getCodeUnite())
                        .contexte("dateDebut", processus.getDateDebut())
                        .contexte("dateFin", processus.getDateFin())
                        .contexte("montantTotal", montantTotal)
                        .contexte("nombreLignes", nombreLignes)
                        .enJson()));
    }

    /**
     * Trace un refus de seconde transmission.
     *
     * <p><b>Trace, contrairement au rejeu d'accuse du Sprint 5.2</b>, et la difference
     * n'est pas un oubli. Un accuse rejoue est une operation d'exploitation ordinaire dont
     * une seconde ligne ferait croire a un second traitement comptable. Ici, rien de tel :
     * la trace dit qu'une <b>demande</b> a ete refusee, pas qu'un traitement a eu lieu.
     * C'est exactement ce qu'un controle interne doit pouvoir constater — quelqu'un, ou
     * quelque chose, a tente de payer deux fois le meme etat.
     */
    private void publierRefusDeDoublon(ProcessusMensuel processus, LocalDateTime dateReservation,
            String adresseIp) {

        publicateurAudit.publier(EvenementAudit.de(
                null,
                ACTION_DOUBLON_REFUSE,
                ENTITE_CIBLE,
                processus.getId(),
                adresseIp,
                DeltaAudit.nouveau()
                        .contexte("motif", "ETAT_DEJA_TRANSMIS")
                        .contexte("datePremiereTransmission", dateReservation)
                        .contexte("statutIntegration", processus.getStatutIntegration())
                        .contexte("referenceComptable", processus.getReferenceComptable())
                        .contexte("codeUnite", processus.getCodeUnite())
                        .contexte("dateDebut", processus.getDateDebut())
                        .contexte("dateFin", processus.getDateFin())
                        .contexte("montantTotal", processus.getMontantTotal())
                        .enJson()));
    }

    /**
     * Trace la levee d'une reservation.
     *
     * <p>Un drapeau de RG-13 qui revient a faux est un fait sensible : il rouvre la porte a
     * une publication. Le journal d'audit doit en garder la trace avec son motif, dans une
     * base qu'aucun service metier ne peut reecrire.
     */
    private void publierLiberation(ProcessusMensuel processus, LocalDateTime reservationLevee,
            String motif, String adresseIp) {

        publicateurAudit.publier(EvenementAudit.de(
                null,
                ACTION_LIBERATION,
                ENTITE_CIBLE,
                processus.getId(),
                adresseIp,
                DeltaAudit.nouveau()
                        .champ("transmisComptabilite", true, false)
                        .champ("dateReservationTransmission", reservationLevee, null)
                        .contexte("motif", motif)
                        .contexte("codeUnite", processus.getCodeUnite())
                        .contexte("dateDebut", processus.getDateDebut())
                        .contexte("dateFin", processus.getDateFin())
                        .enJson()));
    }

}
