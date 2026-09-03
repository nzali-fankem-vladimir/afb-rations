package cm.afrilandfirstbank.rations.workflow.infrastructure;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.StatutEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.TypeProcessusEnum;

/**
 * Acces aux processus mensuels.
 *
 * <p>{@code findById}, {@code save} et consorts viennent de {@link JpaRepository} :
 * le detail d'un processus (endpoint {@code GET /processus/{id}}) se sert
 * directement de {@code findById}.
 */
public interface ProcessusMensuelRepository extends JpaRepository<ProcessusMensuel, Long> {

    /**
     * Cherche le processus d'un type donne pour un couple unite / periode.
     *
     * <p>Sert au controle d'unicite de {@code POST /processus} : avant de creer un
     * {@link TypeProcessusEnum#NORMAL}, on verifie qu'aucun n'existe deja pour
     * cette unite et cette periode. L'index partiel {@code ux_processus_normal_par_periode}
     * (migration V1) garantit qu'il y en a au plus un — d'ou l'{@link Optional} —,
     * mais le refus, avec un message comprehensible nommant le processus existant,
     * doit venir du service (point de vigilance du guide 4.1).
     *
     * <p>Le type est un parametre : plusieurs {@link TypeProcessusEnum#COMPLEMENTAIRE}
     * peuvent partager le meme couple, ce que cette methode n'a pas a decider.
     */
    Optional<ProcessusMensuel> findByCodeUniteAndMoisPaiementAndAnneePaiementAndTypeProcessus(
            String codeUnite, Integer moisPaiement, Integer anneePaiement, TypeProcessusEnum typeProcessus);

    /**
     * Liste paginee des processus d'une unite dans un statut donne — suivi du
     * circuit cote Chef d'Unite ou Directeur Reseau.
     */
    Page<ProcessusMensuel> findByStatutAndCodeUnite(StatutEnum statut, String codeUnite, Pageable pagination);

    /**
     * Charge un processus <b>en verrouillant sa ligne</b> ({@code SELECT ... FOR UPDATE}),
     * pour les trois gestes du verrou de transmission (RG-13, Sprint 5.3).
     *
     * <h2>C'est ici, et nulle part ailleurs, que vit la resistance a la concurrence</h2>
     *
     * <p>Deux instances du service peuvent traiter la meme cloture au meme instant. Un
     * controle en deux temps — lire {@code transmis_comptabilite}, puis publier, puis
     * l'ecrire — ne les separe pas : les deux liraient {@code false} avant que l'une ait
     * pu ecrire, et la comptabilite recevrait deux fois le meme etat, donc deux jeux
     * d'ecritures pour les memes beneficiaires.
     *
     * <p>{@link LockModeType#PESSIMISTIC_WRITE} rend l'operation serielle : PostgreSQL
     * pose un verrou exclusif sur la ligne, la seconde transaction <b>attend</b> le commit
     * de la premiere, puis relit le drapeau <b>deja pose</b> et se voit refuser. Le verrou
     * ne dure que le temps d'une transaction sans appel reseau — quelques millisecondes.
     *
     * <h2>Pourquoi un verrou plutot qu'un UPDATE conditionnel</h2>
     *
     * <p>Un {@code UPDATE ... WHERE transmis_comptabilite = false} serait atomique lui
     * aussi, et plus court. Il ecrirait en revanche <b>par-dessus</b> l'entite : ni le
     * mutateur en visibilite paquet de {@link ProcessusMensuel}, ni l'arbitrage de
     * {@code VerrouTransmission} ne seraient traverses, et l'invariant « toute mutation
     * de ce drapeau passe par le domaine » deviendrait une convention au lieu d'etre
     * verifie par le compilateur. Le delta d'audit — l'avant et l'apres — ne serait pas
     * lisible non plus, un ordre SQL de masse ne rendant qu'un nombre de lignes.
     *
     * <p><b>A n'employer que dans une transaction courte et sans appel reseau.</b> Tenir
     * ce verrou pendant une publication Kafka immobiliserait la ligne pendant plusieurs
     * secondes et ferait attendre toute lecture concurrente du meme dossier.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProcessusMensuel p where p.id = :idProcessus")
    Optional<ProcessusMensuel> verrouillerPourTransmission(@Param("idProcessus") Long idProcessus);

}
