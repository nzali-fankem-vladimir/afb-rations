package cm.afrilandfirstbank.rations.identite.infrastructure.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cm.afrilandfirstbank.rations.identite.domaine.Utilisateur;

/**
 * Acces a la projection locale des comptes annuaire.
 *
 * <p>Deux cles de recherche, correspondant aux deux temps de la resolution :
 * le sub Keycloak pour un profil deja lie, le login pour un profil pre-provisionne
 * qui se connecte pour la premiere fois.
 */
@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    Optional<Utilisateur> findBySubKeycloak(String subKeycloak);

    Optional<Utilisateur> findByLogin(String login);

}
