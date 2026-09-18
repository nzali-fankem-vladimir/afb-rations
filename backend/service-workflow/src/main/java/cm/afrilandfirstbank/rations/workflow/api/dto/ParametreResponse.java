package cm.afrilandfirstbank.rations.workflow.api.dto;

import cm.afrilandfirstbank.rations.workflow.domaine.ParametreSysteme;

/**
 * Vue de sortie d'un parametre systeme, rendue par {@code PUT /parametres/{code}}
 * (guide 7F.6, etape 6, ajout backend scope). L'entite JPA n'est jamais
 * exposee directement (CLAUDE.md section 12).
 */
public record ParametreResponse(String code, String libelle, String valeur, boolean actif) {

    public static ParametreResponse depuis(ParametreSysteme parametre) {
        return new ParametreResponse(
                parametre.getCode(), parametre.getLibelle(), parametre.getValeur(), parametre.isActif());
    }

}
