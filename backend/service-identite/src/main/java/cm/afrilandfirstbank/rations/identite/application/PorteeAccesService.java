package cm.afrilandfirstbank.rations.identite.application;

import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.identite.domaine.PorteeAcces;
import cm.afrilandfirstbank.rations.identite.domaine.RoleEnum;
import cm.afrilandfirstbank.rations.identite.domaine.Utilisateur;

/**
 * Determine la portee d'acces d'un utilisateur, document de conception
 * section 10 :
 *
 * <ul>
 *   <li>AGENT_UNITE, CHEF_UNITE_DA : leur unite uniquement</li>
 *   <li>DIRECTEUR_RESEAU_DR : portee nationale par defaut, faute de decoupage
 *       en reseaux defini par le metier (decision Sprint 1.1, voir
 *       {@code docs/decisions/2026-08-26-portee-acces-directeur-reseau.md})</li>
 *   <li>ARH, DRH, ADMIN : toutes les unites</li>
 * </ul>
 */
@Service
public class PorteeAccesService {

    public PorteeAcces determinerPortee(Utilisateur utilisateur) {
        return switch (utilisateur.getRole()) {
            case AGENT_UNITE, CHEF_UNITE_DA -> PorteeAcces.limiteeA(utilisateur.getCodeUnite());
            case DIRECTEUR_RESEAU_DR, ARH, DRH, ADMIN -> PorteeAcces.nationale();
        };
    }

    public boolean peutAccederAUnite(Utilisateur utilisateur, String codeUnite) {
        return determinerPortee(utilisateur).couvre(codeUnite);
    }

    /**
     * Un code unite est requis pour les roles a portee locale (AGENT_UNITE,
     * CHEF_UNITE_DA), facultatif pour les roles a portee nationale. Utilise par
     * {@code UtilisateurAdminService} pour verifier la coherence role/codeUnite a
     * l'attribution (sous-sprint 1.2).
     */
    public boolean exigeCodeUnite(RoleEnum role) {
        return switch (role) {
            case AGENT_UNITE, CHEF_UNITE_DA -> true;
            case DIRECTEUR_RESEAU_DR, ARH, DRH, ADMIN -> false;
        };
    }

}
