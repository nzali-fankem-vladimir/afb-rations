package cm.afrilandfirstbank.rations.workflow.infrastructure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cm.afrilandfirstbank.rations.workflow.domaine.PieceJointe;

/**
 * Acces aux pieces jointes.
 *
 * <p>La recherche par processus est la seule dont le module ait besoin, et c'est
 * la contrainte {@code id_processus UNIQUE} de la migration V1 qui rend un
 * {@link Optional} suffisant : il ne peut jamais y en avoir deux.
 *
 * <p><b>{@link #existsByIdProcessus(Long)} est le controle applicatif qui evite
 * un message technique illisible.</b> La contrainte de base garantit la regle,
 * mais une seconde generation la ferait remonter en violation d'integrite. Le
 * service de soumission interroge donc cette methode avant de generer quoi que ce
 * soit — et n'ecrit ainsi aucun fichier pour une soumission qu'il va refuser.
 */
public interface PieceJointeRepository extends JpaRepository<PieceJointe, Long> {

    Optional<PieceJointe> findByIdProcessus(Long idProcessus);

    boolean existsByIdProcessus(Long idProcessus);

}
