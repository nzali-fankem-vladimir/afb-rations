package cm.afrilandfirstbank.rations.workflow.domaine;

/**
 * Nature d'une etape du circuit de validation (CLAUDE.md section 5).
 *
 * <p>Les trois etapes possibles du parcours d'un etat : la soumission par
 * l'agent, la validation du Chef d'Unite (DA), la validation du Directeur Reseau
 * (DR). L'ordre reel de survenue est porte par {@code etape_workflow.ordre_etape} ;
 * la presence d'une {@link #VALIDATION_DR} depend de l'aiguillage au seuil
 * (RG-08), traite au sous-sprint 4.3.
 */
public enum NomEtapeEnum {

    SOUMISSION_AGENT,
    VALIDATION_DA,
    VALIDATION_DR

}
