package cm.afrilandfirstbank.rations.identite.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import cm.afrilandfirstbank.rations.identite.domaine.RoleEnum;
import cm.afrilandfirstbank.rations.identite.domaine.Utilisateur;

/**
 * Acces a la projection locale des comptes annuaire.
 *
 * <p>Deux cles de recherche, correspondant aux deux temps de la resolution :
 * le sub Keycloak pour un profil deja lie, le login pour un profil pre-provisionne
 * qui se connecte pour la premiere fois.
 *
 * <p>{@link JpaSpecificationExecutor} porte la recherche paginee a filtres
 * optionnels des sous-sprints 1.2 et 1.3 : les filtres (role, code unite, statut
 * actif) se combinent librement, chacun omis quand non renseigne, ce qu'une
 * Specification exprime sans multiplier les methodes derivees.
 */
@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long>,
        JpaSpecificationExecutor<Utilisateur> {

    Optional<Utilisateur> findBySubKeycloak(String subKeycloak);

    Optional<Utilisateur> findByLogin(String login);

    List<Utilisateur> findByRole(RoleEnum role);

    List<Utilisateur> findByCodeUnite(String codeUnite);

}
