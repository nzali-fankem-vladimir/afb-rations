import { createContext } from 'react'

/**
 * Les fonctionnalites pilotees par drapeau, lues une fois au chargement de
 * l'application (Sprint 7F.7, etape 5 ; docs/dispositifs_provisoires.md
 * section 1.4).
 *
 * Trois champs plutot qu'un booleen nu, parce que trois situations doivent se
 * distinguer et qu'elles n'appellent pas le meme rendu :
 *
 * - lecture en cours : on ne sait pas encore, il ne faut ni afficher ni
 *   rediriger -- les deux seraient faux la moitie du temps ;
 * - lecture aboutie : `rattrapageActif` porte la valeur reelle du drapeau ;
 * - lecture echouee : `rattrapageActif` porte une valeur de REPLI, et
 *   `lectureEchouee` le dit, pour qu'un ecran puisse un jour le signaler
 *   plutot que de faire passer un repli pour une lecture.
 */
export interface FonctionnalitesContexte {
  /** Vrai tant que la lecture n'a ni abouti ni echoue. */
  chargement: boolean
  /** Le drapeau tel que lu, ou la valeur de repli quand `lectureEchouee` est vrai. */
  rattrapageActif: boolean
  /** La valeur ci-dessus est un repli, pas une lecture. */
  lectureEchouee: boolean
}

export const FonctionnalitesContext = createContext<FonctionnalitesContexte | null>(null)
