package cm.afrilandfirstbank.rations.identite.application;

import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cm.afrilandfirstbank.rations.commun.audit.DeltaAudit;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.identite.domaine.Utilisateur;
import cm.afrilandfirstbank.rations.identite.domaine.exception.UtilisateurNonHabiliteException;
import cm.afrilandfirstbank.rations.identite.infrastructure.persistence.UtilisateurRepository;

/**
 * Resout l'utilisateur courant a partir du jeton presente.
 *
 * <p>Regle retenue : pre-provisionnement puis liaison automatique.
 * L'administrateur cree le profil local (login, role, code unite) sans connaitre
 * l'identifiant technique Keycloak. A la premiere connexion, le service rapproche
 * le profil par son login et y inscrit le sub. Les connexions suivantes passent
 * directement par le sub.
 *
 * <p>Un jeton valide sans profil local correspondant est refuse : l'habilitation
 * au module reste un acte d'administration explicite, elle ne decoule pas de la
 * seule existence d'un compte a l'annuaire.
 *
 * <p>Aucun mot de passe n'est manipule ici : l'authentification a deja eu lieu
 * chez Keycloak (CLAUDE.md section 10).
 */
@Service
public class UtilisateurCourantService {

    private static final Logger log = LoggerFactory.getLogger(UtilisateurCourantService.class);

    static final String REVENDICATION_LOGIN = "preferred_username";

    private static final String ACTION_LIAISON = "LIAISON_COMPTE_KEYCLOAK";
    private static final String ENTITE_UTILISATEUR = "utilisateurs";

    private final UtilisateurRepository utilisateurRepository;
    private final PublicateurAudit publicateurAudit;

    public UtilisateurCourantService(UtilisateurRepository utilisateurRepository,
            PublicateurAudit publicateurAudit) {
        this.utilisateurRepository = utilisateurRepository;
        this.publicateurAudit = publicateurAudit;
    }

    /**
     * Retourne le profil local du porteur du jeton, en etablissant la liaison si
     * c'est sa premiere connexion.
     *
     * @throws UtilisateurNonHabiliteException si aucun profil ne lui a ete ouvert,
     *         ou si son profil est desactive
     */
    @Transactional
    public Utilisateur resoudre(Jwt jeton) {
        String subKeycloak = jeton.getSubject();
        String login = jeton.getClaimAsString(REVENDICATION_LOGIN);

        Utilisateur utilisateur = utilisateurRepository.findBySubKeycloak(subKeycloak)
                .orElseGet(() -> lierProfilPreProvisionne(subKeycloak, login));

        if (!utilisateur.estActif()) {
            log.warn("Acces refuse : le profil {} est desactive.", utilisateur.getLogin());
            throw new UtilisateurNonHabiliteException(
                    "Le profil " + utilisateur.getLogin() + " est desactive.");
        }

        utilisateur.enregistrerAcces(LocalDateTime.now());
        return utilisateur;
    }

    /**
     * Premiere connexion : on cherche un profil ouvert au meme login, encore non lie.
     * Le rapprochement se fait sur le login annuaire, seule donnee commune entre le
     * jeton et le profil cree par l'administrateur.
     */
    private Utilisateur lierProfilPreProvisionne(String subKeycloak, String login) {
        Optional<Utilisateur> profil = Optional.ofNullable(login).flatMap(utilisateurRepository::findByLogin);

        Utilisateur utilisateur = profil.orElseThrow(() -> {
            log.warn("Acces refuse : aucun profil local ouvert pour le login {} (sub {}).", login, subKeycloak);
            return new UtilisateurNonHabiliteException(
                    "Aucun profil n'a ete ouvert dans le module pour ce compte.");
        });

        // Le profil porte deja un autre sub : deux comptes Keycloak revendiquent le
        // meme login annuaire. On refuse plutot que de reattribuer une habilitation.
        if (utilisateur.estLie()) {
            log.warn("Acces refuse : le profil {} est deja lie a un autre compte Keycloak.", login);
            throw new UtilisateurNonHabiliteException(
                    "Le profil " + login + " est deja lie a un autre compte.");
        }

        utilisateur.lierAuCompteKeycloak(subKeycloak);
        log.info("Premiere connexion du profil {} : liaison au compte Keycloak {}.", login, subKeycloak);
        tracerLiaison(utilisateur, subKeycloak);
        return utilisateur;
    }

    /**
     * Trace l'établissement du lien entre un compte de l'annuaire et un profil
     * local (omission relevée au Sprint 6.3, étape 2).
     *
     * <p><b>Pourquoi c'est un fait de contrôle interne et non un détail
     * technique.</b> Cette écriture est le moment, et le seul, où une identité
     * Keycloak prend le contrôle d'une habilitation du module. Le profil portait
     * déjà un rôle et un code unité, posés par un administrateur qui ne
     * connaissait pas le {@code sub} (décision Sprint 0.4) ; c'est ici que ce
     * rôle devient exerçable, et par ce compte-là. Après coup, plus rien ne dit
     * quand ni depuis quel compte le rattachement a eu lieu : la colonne
     * {@code sub_keycloak} porte la valeur finale, pas son histoire.
     *
     * <p>Elle survient de surcroît <b>sans qu'aucun administrateur n'agisse</b>,
     * à la première connexion de l'intéressé — une écriture que personne ne
     * déclenche est exactement celle qu'un journal doit rattraper.
     *
     * <p>{@code idUtilisateur} est renseigné, l'auteur étant le porteur du jeton
     * lui-même : il se lie à son propre profil. {@code adresseIp} reste nulle,
     * cette résolution étant appelée depuis plusieurs points sans contexte HTTP
     * uniforme.
     */
    private void tracerLiaison(Utilisateur utilisateur, String subKeycloak) {
        publicateurAudit.publier(EvenementAudit.de(
                utilisateur.getId(),
                ACTION_LIAISON,
                ENTITE_UTILISATEUR,
                utilisateur.getId(),
                null,
                DeltaAudit.nouveau()
                        .champ("subKeycloak", null, subKeycloak)
                        .contexte("login", utilisateur.getLogin())
                        .contexte("role", utilisateur.getRole())
                        .contexte("codeUnite", utilisateur.getCodeUnite())
                        .contexte("origine", "premiere connexion, liaison automatique au profil pre-provisionne")
                        .enJson()));
    }

}
