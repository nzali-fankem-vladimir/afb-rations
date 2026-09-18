import type { ReactNode } from 'react'

export interface LigneRecapitulatif {
  libelle: string
  valeur: ReactNode
  /** Valeur actuelle, affichee "avant -> apres" quand elle differe. */
  avant?: ReactNode
}

/**
 * Recapitulatif d'une action sensible, affiche avant confirmation (Sprint 7F.6,
 * proposition n°1). L'utilisateur relit ce qui va reellement partir, pas ce
 * qu'il croit avoir saisi.
 */
export function Recapitulatif({ lignes }: { lignes: LigneRecapitulatif[] }) {
  return (
    <dl className="divide-y divide-neutral-200 rounded border border-neutral-200 bg-neutral-50 text-sm">
      {lignes.map((ligne) => {
        const change = ligne.avant !== undefined && ligne.avant !== ligne.valeur
        return (
          <div key={ligne.libelle} className="flex items-baseline justify-between gap-4 px-3 py-2">
            <dt className="text-neutral-600">{ligne.libelle}</dt>
            <dd className="text-right font-medium text-neutral-900">
              {change ? (
                <>
                  <span className="text-neutral-500 line-through">{ligne.avant}</span>
                  <span aria-hidden="true"> → </span>
                  <span className="sr-only"> devient </span>
                  {ligne.valeur}
                </>
              ) : (
                ligne.valeur
              )}
            </dd>
          </div>
        )
      })}
    </dl>
  )
}
