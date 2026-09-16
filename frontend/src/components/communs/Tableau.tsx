import type { ReactNode } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'

import { cn } from '../../utils/cn'

const TAILLE_SQUELETTE = 5

export interface Colonne<T> {
  cle: string
  entete: string
  rendu?: (ligne: T) => ReactNode
  className?: string
}

export interface ParametresPagination {
  page: number
  totalPages: number
  totalElements: number
  dernierePage: boolean
  onChangerPage: (page: number) => void
}

export interface TableauProps<T> {
  colonnes: Colonne<T>[]
  donnees: T[]
  cleLigne: (ligne: T) => string | number
  chargement?: boolean
  pagination?: ParametresPagination
  actions?: (ligne: T) => ReactNode
  onLigneClick?: (ligne: T) => void
  messageVide?: ReactNode
}

export function Tableau<T>({
  colonnes,
  donnees,
  cleLigne,
  chargement = false,
  pagination,
  actions,
  onLigneClick,
  messageVide = 'Aucun résultat.',
}: TableauProps<T>) {
  const nombreColonnes = colonnes.length + (actions ? 1 : 0)

  return (
    <div className="overflow-hidden rounded-lg border border-neutral-200 bg-white">
      {/* aria-busy pendant le chargement : les lignes de squelette sont un signal
          purement visuel. Sans lui, un lecteur d'ecran annoncerait un tableau
          deja rempli de lignes vides. La zone est polie (pas d'interruption) et
          annonce le resultat une fois les donnees arrivees. */}
      <div className="overflow-x-auto" aria-busy={chargement} aria-live="polite">
        <table className="w-full text-left text-sm">
          <thead className="bg-neutral-100 text-xs uppercase text-neutral-700">
            <tr>
              {colonnes.map((colonne) => (
                <th key={colonne.cle} scope="col" className={cn('px-4 py-3 font-medium', colonne.className)}>
                  {colonne.entete}
                </th>
              ))}
              {actions && (
                <th scope="col" className="px-4 py-3 font-medium">
                  Actions
                </th>
              )}
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-200">
            {chargement &&
              Array.from({ length: TAILLE_SQUELETTE }).map((_, index) => (
                <tr key={`squelette-${index}`}>
                  {colonnes.map((colonne) => (
                    <td key={colonne.cle} className="px-4 py-3">
                      <div className="h-4 w-3/4 animate-pulse rounded bg-neutral-200" />
                    </td>
                  ))}
                  {actions && (
                    <td className="px-4 py-3">
                      <div className="h-4 w-16 animate-pulse rounded bg-neutral-200" />
                    </td>
                  )}
                </tr>
              ))}

            {!chargement && donnees.length === 0 && (
              <tr>
                <td colSpan={nombreColonnes} className="px-4 py-8 text-center text-neutral-600">
                  {messageVide}
                </td>
              </tr>
            )}

            {!chargement &&
              donnees.map((ligne) => (
                <tr
                  key={cleLigne(ligne)}
                  onClick={onLigneClick ? () => onLigneClick(ligne) : undefined}
                  onKeyDown={
                    onLigneClick
                      ? (event) => {
                          if (event.key === 'Enter' || event.key === ' ') {
                            event.preventDefault()
                            onLigneClick(ligne)
                          }
                        }
                      : undefined
                  }
                  tabIndex={onLigneClick ? 0 : undefined}
                  // Une <tr> focalisable qui reagit a Entree est un bouton pour
                  // l'utilisateur au clavier, mais reste une simple ligne pour le
                  // lecteur d'ecran, qui n'annonce donc aucune action possible.
                  // role="button" le dit, sans faire perdre la semantique de
                  // rangee (le tableau reste lisible en navigation par tableau).
                  role={onLigneClick ? 'button' : undefined}
                  className={cn(
                    'transition-colors motion-reduce:transition-none hover:bg-neutral-100',
                    onLigneClick &&
                      'cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-primary-500',
                  )}
                >
                  {colonnes.map((colonne) => (
                    <td key={colonne.cle} className={cn('px-4 py-3 text-neutral-700', colonne.className)}>
                      {colonne.rendu ? colonne.rendu(ligne) : String((ligne as Record<string, unknown>)[colonne.cle] ?? '')}
                    </td>
                  ))}
                  {actions && (
                    <td className="px-4 py-3" onClick={(event) => event.stopPropagation()}>
                      {actions(ligne)}
                    </td>
                  )}
                </tr>
              ))}
          </tbody>
        </table>
      </div>

      {pagination && (
        <div className="flex items-center justify-between border-t border-neutral-200 px-4 py-3 text-sm text-neutral-600">
          <span>
            {pagination.totalElements} résultat{pagination.totalElements > 1 ? 's' : ''}
          </span>
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={() => pagination.onChangerPage(pagination.page - 1)}
              disabled={pagination.page <= 0}
              aria-label="Page précédente"
              className="rounded p-1.5 text-neutral-600 hover:bg-neutral-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 disabled:pointer-events-none disabled:opacity-40"
            >
              <ChevronLeft className="h-4 w-4" aria-hidden="true" />
            </button>
            <span>
              Page {pagination.page + 1} sur {Math.max(1, pagination.totalPages)}
            </span>
            <button
              type="button"
              onClick={() => pagination.onChangerPage(pagination.page + 1)}
              disabled={pagination.dernierePage}
              aria-label="Page suivante"
              className="rounded p-1.5 text-neutral-600 hover:bg-neutral-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 disabled:pointer-events-none disabled:opacity-40"
            >
              <ChevronRight className="h-4 w-4" aria-hidden="true" />
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
