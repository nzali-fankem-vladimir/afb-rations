package cm.afrilandfirstbank.rations.workflow.application;

import java.util.List;

/**
 * Verdict du controle de completude : la liste des manques, vide si l'etat peut
 * etre soumis.
 *
 * <p><b>Ce type existe pour ne pas rendre un booleen.</b> L'etape 2 du guide 4.2
 * l'exige : « il ne retourne pas un simple booleen : il retourne la liste des
 * manques constates, pour que l'interface les affiche a l'agent. Un message
 * generique du type etat incomplet obligerait l'agent a chercher lui-meme. »
 * C'est aussi ce que verifie CT-13.
 *
 * <p>La liste est <b>non modifiable et d'ordre stable</b> : etat vide, periode,
 * montant, compte courant. Un ordre stable rend les tests lisibles et l'affichage
 * previsible d'une soumission a l'autre.
 */
public record ResultatCompletude(List<ManqueCompletude> manques) {

    public ResultatCompletude {
        manques = manques == null ? List.of() : List.copyOf(manques);
    }

    /** Aucun manque : l'etat peut etre soumis. */
    public boolean estComplet() {
        return manques.isEmpty();
    }

    /** Verdict sans aucun manque. */
    public static ResultatCompletude complet() {
        return new ResultatCompletude(List.of());
    }

}
