import { useContext } from 'react'

import { FonctionnalitesContext } from '../contexts/fonctionnalitesContexte'
import type { FonctionnalitesContexte } from '../contexts/fonctionnalitesContexte'

/**
 * Les fonctionnalites pilotees par drapeau (Sprint 7F.7, etape 5).
 *
 * Leve hors du fournisseur plutot que de rendre une valeur par defaut : un
 * ecran qui lirait silencieusement « ferme » parce qu'il est monte au mauvais
 * endroit de l'arbre masquerait une fonctionnalite ouverte, sans le dire.
 */
export function useFonctionnalites(): FonctionnalitesContexte {
  const contexte = useContext(FonctionnalitesContext)
  if (contexte === null) {
    throw new Error('useFonctionnalites doit etre utilise a l\'interieur de FonctionnalitesProvider.')
  }
  return contexte
}
