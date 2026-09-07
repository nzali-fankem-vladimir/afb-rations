package cm.afrilandfirstbank.rations.workflow.application;

/**
 * La decision d'aiguillage, <b>et de quoi la justifier</b> (RG-08).
 *
 * <p>Rendre la seule {@link DecisionAiguillage} aurait suffi a faire fonctionner le
 * circuit. Ce record porte en plus les deux nombres qui l'ont produite, pour deux
 * raisons concretes :
 *
 * <ul>
 *   <li>le contrat d'API section 5 exige que la reponse rende le
 *       {@code seuilApplique} — le chef d'unite doit voir sur quelle regle son
 *       dossier a ete aiguille, sans avoir a interroger un administrateur ;</li>
 *   <li>le journal d'audit enregistre les deux valeurs : un controle interne qui
 *       relit la trace six mois plus tard doit pouvoir refaire la comparaison,
 *       meme si le parametre a change depuis.</li>
 * </ul>
 *
 * @param decision l'issue retenue
 * @param montantTotalFcfa le montant <b>enregistre a la soumission</b>, jamais un
 *        montant recalcule a l'instant de la validation
 * @param seuilApplique la valeur du seuil telle qu'elle a ete lue dans
 *        {@code parametre_systeme} pour cette decision precise. <b>Nul quand aucune
 *        comparaison n'a eu lieu</b> — cas d'un etat COMPLEMENTAIRE, qui monte au
 *        Directeur Reseau quel que soit son montant (Sprint 6bis.1). Un {@code 0}
 *        aurait ete une valeur sentinelle, indiscernable d'un seuil reellement
 *        configure a zero, que le Sprint 4.3 accepte explicitement
 */
public record ResultatAiguillage(
        DecisionAiguillage decision,
        long montantTotalFcfa,
        Long seuilApplique) {

    /** Vrai si l'etat se clot sur la seule validation du Chef d'Unite. */
    public boolean estClotureDirecte() {
        return decision == DecisionAiguillage.SOUS_SEUIL_CLOTURE_DIRECTE;
    }

}
