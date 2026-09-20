package cm.afrilandfirstbank.rations.identite.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cm.afrilandfirstbank.rations.commun.audit.DeltaAudit;
import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.identite.api.dto.AttributionRoleRequest;
import cm.afrilandfirstbank.rations.identite.domaine.RoleEnum;
import cm.afrilandfirstbank.rations.identite.domaine.Utilisateur;
import cm.afrilandfirstbank.rations.identite.domaine.exception.AutoModificationInterditeException;
import cm.afrilandfirstbank.rations.identite.domaine.exception.CodeUniteIncoherentException;
import cm.afrilandfirstbank.rations.identite.domaine.exception.DernierAdministrateurException;
import cm.afrilandfirstbank.rations.identite.domaine.exception.UtilisateurIntrouvableException;
import cm.afrilandfirstbank.rations.identite.infrastructure.persistence.UtilisateurRepository;
import cm.afrilandfirstbank.rations.identite.infrastructure.persistence.UtilisateurSpecifications;

/**
 * Administration des profils locaux (sous-sprint 1.2) : liste paginee et
 * attribution du role applicatif et du code unite.
 *
 * <p>Reserve au role ADMIN au niveau des controleurs. Ce service ne l'impose
 * pas lui-meme : c'est une decision d'exposition, portee par
 * {@code @PreAuthorize} sur {@code UtilisateurAdminController}.
 */
@Service
public class UtilisateurAdminService {

    // Le service emetteur n'est plus declare ici : le producteur l'estampille
    // depuis spring.application.name, pour qu'aucun service ne puisse l'oublier.
    private static final String ACTION_ATTRIBUTION_ROLE = "ATTRIBUTION_ROLE";
    private static final String ENTITE_UTILISATEUR = "utilisateurs";

    private final UtilisateurRepository utilisateurRepository;
    private final PorteeAccesService porteeAccesService;
    private final PublicateurAudit publicateurAudit;

    public UtilisateurAdminService(UtilisateurRepository utilisateurRepository,
            PorteeAccesService porteeAccesService, PublicateurAudit publicateurAudit) {
        this.utilisateurRepository = utilisateurRepository;
        this.porteeAccesService = porteeAccesService;
        this.publicateurAudit = publicateurAudit;
    }

    /** Liste paginee, filtrable et combinable par role, code unite et statut actif. */
    public Page<Utilisateur> lister(RoleEnum role, String codeUnite, Boolean actif, Pageable pageable) {
        return utilisateurRepository.findAll(
                UtilisateurSpecifications.avecFiltres(role, codeUnite, actif), pageable);
    }

    /**
     * Attribue un role applicatif et un code unite a un profil existant, et
     * l'active ou le desactive si {@code actif} est renseigne.
     *
     * <p>Trois controles de fond avant toute ecriture, decision
     * {@code docs/decisions/2026-08-26-attribution-role-administrateur.md} :
     * un administrateur ne peut pas se modifier lui-meme sur cet endpoint, le
     * dernier administrateur actif ne peut pas perdre le role ADMIN, et aucune
     * invalidation de session n'est necessaire (le role est relu en base a
     * chaque requete).
     *
     * @throws UtilisateurIntrouvableException si l'identifiant cible n'existe pas
     * @throws AutoModificationInterditeException si l'appelant se cible lui-meme
     * @throws CodeUniteIncoherentException si le code unite est absent pour un
     *         role a portee locale
     * @throws DernierAdministrateurException si le retrait viderait le systeme
     *         de tout administrateur actif
     */
    @Transactional
    public Utilisateur attribuerRole(Long id, AttributionRoleRequest requete, Utilisateur appelant,
            String adresseIp) {
        Utilisateur cible = utilisateurRepository.findById(id)
                .orElseThrow(() -> new UtilisateurIntrouvableException(id));

        if (cible.getId().equals(appelant.getId())) {
            throw new AutoModificationInterditeException(
                    "Un administrateur ne peut pas modifier son propre role sur cet endpoint.");
        }

        if (porteeAccesService.exigeCodeUnite(requete.role()) && requete.codeUnite() == null) {
            throw new CodeUniteIncoherentException(
                    "Le code unite est obligatoire pour le role " + requete.role() + ".");
        }

        boolean perdLeRoleAdmin = cible.getRole() == RoleEnum.ADMIN && requete.role() != RoleEnum.ADMIN;
        if (perdLeRoleAdmin && cible.estActif()
                && utilisateurRepository.countByRoleAndActif(RoleEnum.ADMIN, true) <= 1) {
            throw new DernierAdministrateurException(
                    "Impossible de retirer le role ADMIN au dernier administrateur actif du systeme.");
        }

        RoleEnum roleAvant = cible.getRole();
        String codeUniteAvant = cible.getCodeUnite();
        boolean actifAvant = cible.estActif();

        // 4. Modification de l'etat, PUIS 5. journalisation (document maitre
        // section 7.3). L'envoi reel sur le topic n'a lieu qu'apres le commit de
        // cette transaction : un rollback ne laisse donc pas derriere lui la
        // trace d'une attribution qui n'a pas eu lieu.
        cible.attribuerRoleEtCodeUnite(requete.role(), requete.codeUnite());

        // Statut : absent, inchange. L'appelant est toujours un administrateur actif
        // et distinct de la cible (controle d'auto-modification plus haut) : il en
        // reste donc au moins un apres une desactivation, aucun garde supplementaire
        // n'est necessaire.
        if (requete.actif() != null && requete.actif() != actifAvant) {
            if (requete.actif()) {
                cible.reactiver();
            } else {
                cible.desactiver();
            }
        }

        publicateurAudit.publier(EvenementAudit.de(
                appelant.getId(),
                ACTION_ATTRIBUTION_ROLE,
                ENTITE_UTILISATEUR,
                cible.getId(),
                adresseIp,
                DeltaAudit.nouveau()
                        .champ("role", roleAvant, requete.role())
                        .champ("codeUnite", codeUniteAvant, requete.codeUnite())
                        .champ("actif", actifAvant, cible.estActif())
                        .contexte("loginCible", cible.getLogin())
                        .enJson()));

        return cible;
    }

}
