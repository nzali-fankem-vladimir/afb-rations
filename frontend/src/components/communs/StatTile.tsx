import type { ReactNode } from 'react'

export interface StatTileProps {
  valeur: ReactNode
  libelle: string
}

/**
 * Un indicateur chiffré en tête d'écran (compteur d'états, montant total,
 * avancement...), retour utilisateur sur la maquette de refonte du Sprint
 * 7F.6 : « des chiffres qui commandent une action, pas de la décoration ».
 */
export function StatTile({ valeur, libelle }: StatTileProps) {
  return (
    <div className="rounded-lg border border-neutral-200 bg-white p-4">
      <p className="text-xl font-bold tabular-nums text-neutral-900">{valeur}</p>
      <p className="text-xs text-neutral-600">{libelle}</p>
    </div>
  )
}
