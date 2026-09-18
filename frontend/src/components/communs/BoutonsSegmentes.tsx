import { cn } from '../../utils/cn'

export interface OptionSegment<T extends string> {
  valeur: T
  libelle: string
}

export interface BoutonsSegmentesProps<T extends string> {
  /** Nomme le groupe pour un lecteur d'ecran : « Nature », « Session ». */
  libelleGroupe: string
  options: OptionSegment<T>[]
  valeur: T | ''
  onChange: (valeur: T) => void
  disabled?: boolean
  erreur?: string
  compact?: boolean
  className?: string
}

/**
 * Choix entre deux ou trois valeurs, en boutons plutot qu'en liste deroulante
 * (Sprint 7F.6, proposition n°5). Une liste demande deux gestes -- ouvrir,
 * choisir --, repetes a chaque ligne de prestation ; ici, un seul clic, et les
 * valeurs possibles restent visibles sans etre depliees.
 *
 * Reserve aux enumerations courtes et fermees : RG-01 (RATION, TRANSPORT) et
 * RG-02 (JOUR, SOIR). Au-dela de trois valeurs, la liste deroulante reste le
 * bon composant.
 */
export function BoutonsSegmentes<T extends string>({
  libelleGroupe,
  options,
  valeur,
  onChange,
  disabled = false,
  erreur,
  compact = false,
  className,
}: BoutonsSegmentesProps<T>) {
  return (
    <div className={cn('flex flex-col gap-1', className)}>
      <div
        role="group"
        aria-label={libelleGroupe}
        className={cn(
          'inline-grid grid-flow-col auto-cols-fr overflow-hidden rounded border bg-white',
          erreur ? 'border-primary-500' : 'border-neutral-500',
          disabled && 'opacity-50',
        )}
      >
        {options.map((option, index) => {
          const selectionne = option.valeur === valeur
          return (
            <button
              key={option.valeur}
              type="button"
              aria-pressed={selectionne}
              disabled={disabled}
              onClick={() => onChange(option.valeur)}
              className={cn(
                'font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-primary-500 disabled:cursor-not-allowed',
                compact ? 'px-2 py-1.5 text-xs' : 'px-3 py-2 text-sm',
                index > 0 && 'border-l border-neutral-300',
                selectionne ? 'bg-neutral-900 text-white' : 'bg-white text-neutral-700 hover:bg-neutral-50',
              )}
            >
              {option.libelle}
            </button>
          )
        })}
      </div>
      {erreur && (
        <p role="alert" className="text-xs font-medium text-primary-700">
          {erreur}
        </p>
      )}
    </div>
  )
}
