import { useEffect, useState } from 'react'

import { rechercherDemandes } from '../../api/reportingApi'
import type { DemandeResponse } from '../../api/reportingApi'

export interface StatistiquesProcessus {
  enCoursSaisie: number
  enAttenteValidation: number
  retourne: number
  clotureCetteAnnee: number
  /** Le dossier retourné le plus récent, pour le bandeau d'alerte -- null si aucun n'attend de correction. */
  premierRetourne: DemandeResponse | null
}

const VIDE: StatistiquesProcessus = {
  enCoursSaisie: 0,
  enAttenteValidation: 0,
  retourne: 0,
  clotureCetteAnnee: 0,
  premierRetourne: null,
}

/**
 * Quatre compteurs pour l'écran "Mes états" de l'agent (retour utilisateur sur
 * la maquette de refonte, Sprint 7F.6). Chacun vient d'un appel séparé à
 * `GET /reporting/demandes` avec `size=1`, seul `totalElements` étant retenu :
 * le filtre serveur compare `statut` par égalité stricte, donc "en attente de
 * validation" (trois statuts : SOUMIS, EN_ATTENTE_DA, EN_ATTENTE_DR) demande
 * trois appels sommés côté client, jamais une estimation depuis la seule page
 * affichée -- un total partiel présenté comme le total réel serait un chiffre
 * plausible et faux.
 */
export function useStatistiquesProcessus(cleActualisation: string): StatistiquesProcessus | null {
  const [statistiques, setStatistiques] = useState<StatistiquesProcessus | null>(null)

  useEffect(() => {
    let annule = false
    const debutAnnee = `${new Date().getFullYear()}-01-01`

    Promise.all([
      rechercherDemandes({ statut: 'EN_COURS_SAISIE', page: 0, size: 1 }),
      rechercherDemandes({ statut: 'SOUMIS', page: 0, size: 1 }),
      rechercherDemandes({ statut: 'EN_ATTENTE_DA', page: 0, size: 1 }),
      rechercherDemandes({ statut: 'EN_ATTENTE_DR', page: 0, size: 1 }),
      rechercherDemandes({ statut: 'RETOURNE', page: 0, size: 1 }),
      rechercherDemandes({ statut: 'CLOTURE', dateDebut: debutAnnee, page: 0, size: 1 }),
    ])
      .then(([enCours, soumis, attenteDa, attenteDr, retourne, cloture]) => {
        if (annule) return
        setStatistiques({
          enCoursSaisie: enCours.totalElements,
          enAttenteValidation: soumis.totalElements + attenteDa.totalElements + attenteDr.totalElements,
          retourne: retourne.totalElements,
          clotureCetteAnnee: cloture.totalElements,
          premierRetourne: retourne.content[0] ?? null,
        })
      })
      .catch(() => {
        // Compteurs de confort : une panne les laisse absents, elle ne doit
        // jamais faire echouer ni figer le reste de l'ecran (doctrine des
        // compteurs du Sprint 7F.6, useCompteurValidation).
        if (!annule) setStatistiques(VIDE)
      })
    return () => {
      annule = true
    }
  }, [cleActualisation])

  return statistiques
}
