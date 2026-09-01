package cm.afrilandfirstbank.rations.workflow.application;

import cm.afrilandfirstbank.rations.workflow.domaine.CodeManqueEnum;

/**
 * Un manque constate a la verification de completude, tel qu'il sera rendu a
 * l'agent dans le refus de soumission.
 *
 * <p><b>Un manque par controle, jamais un par ligne fautive.</b> Chacun des
 * quatre controles produit au plus un element, qui agrege ce qu'il a trouve et
 * en enumere quelques exemples. Un etat de deux cents lignes toutes
 * defectueuses produirait sinon deux cents entrees illisibles ; la liste rendue
 * en compte au plus quatre.
 *
 * @param code qualifie le controle en echec, pour que l'interface puisse
 *        aiguiller l'agent vers le bon ecran sans analyser de la prose
 * @param message phrase complete, lisible telle quelle par l'agent : elle nomme
 *        ce qui manque, l'endroit ou le trouver, et <b>le geste attendu</b> — un
 *        manque qui ne dit pas quoi faire oblige l'agent a chercher, ce que
 *        l'etape 2 du guide et CT-13 refusent explicitement
 */
public record ManqueCompletude(CodeManqueEnum code, String message) {
}
