import { useEffect, useState } from 'react'

import { rechercherDemandes } from '../api/reportingApi'
import type { RoleEnum } from '../types/enums'

/**
 * Nombre de dossiers en attente DU NIVEAU DE VALIDATION DE L'UTILISATEUR
 * COURANT, pour le compteur de la sidebar (proposition d'interface validee
 * par l'utilisateur au Sprint 7F.6, onglet "Barre laterale").
 *
 * Un seul appel, size=1 : seul totalElements interesse ici, jamais le
 * contenu de la page. Renvoie null hors circuit de validation (l'appelant
 * n'affiche alors aucun badge) et aussi en cas d'echec reseau -- un
 * compteur de confort ne doit jamais faire echouer ni clignoter le reste de
 * la sidebar : une pastille absente est un moindre mal qu'une pastille
 * fausse ou qu'une exception qui casse la navigation.
 */
export function useCompteurValidation(role: RoleEnum | null, cleActualisation: string): number | null {
  const [compte, setCompte] = useState<number | null>(null)

  const statutAttendu = role === 'CHEF_UNITE_DA' ? 'EN_ATTENTE_DA' : role === 'DIRECTEUR_RESEAU_DR' ? 'EN_ATTENTE_DR' : null

  useEffect(() => {
    if (statutAttendu === null) {
      setCompte(null)
      return
    }
    let annule = false
    rechercherDemandes({ statut: statutAttendu, page: 0, size: 1 })
      .then((page) => {
        if (!annule) setCompte(page.totalElements)
      })
      .catch(() => {
        if (!annule) setCompte(null)
      })
    return () => {
      annule = true
    }
    // cleActualisation (le chemin courant) declenche une nouvelle lecture a
    // chaque navigation : c'est ce qui fait retomber le compteur juste apres
    // qu'un dossier vient d'etre valide ou retourne depuis l'ecran d'examen.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statutAttendu, cleActualisation])

  return compte
}
