import { cn } from '../../utils/cn'
import { enumererJours, formatDateJJMMAAAA, formatJourSemaineCourt } from '../../utils/formatters'

export interface SelecteurJourProps {
  dateDebut: string
  dateFin: string
  dateSelectionnee: string
  onSelectionner: (dateJour: string) => void
}

/**
 * Selecteur de jour BORNE a la periode de l'etat (dateDebut a dateFin incluses).
 *
 * Borner ici, plutot qu'un calendrier libre, empeche a la source l'erreur que le
 * backend ne refuse qu'a la soumission (LIGNE_HORS_PERIODE) : une ligne datee
 * hors periode echappe entre-temps au controle d'unicite RG-15, qui ne porte que
 * sur sa propre journee (guide 7F.4, etape 3).
 */
export function SelecteurJour({ dateDebut, dateFin, dateSelectionnee, onSelectionner }: SelecteurJourProps) {
  const jours = enumererJours(dateDebut, dateFin)

  return (
    <div role="tablist" aria-label="Journée" className="flex gap-2 overflow-x-auto pb-1">
      {jours.map((jour) => {
        const selectionne = jour === dateSelectionnee
        return (
          <button
            key={jour}
            type="button"
            role="tab"
            aria-selected={selectionne}
            onClick={() => onSelectionner(jour)}
            className={cn(
              'flex shrink-0 flex-col items-center rounded-lg border px-3 py-2 text-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500',
              selectionne
                ? 'border-primary-500 bg-primary-50 text-primary-700'
                : 'border-neutral-200 bg-white text-neutral-700 hover:bg-neutral-50',
            )}
          >
            <span className="text-xxs font-semibold uppercase tracking-wide">
              {formatJourSemaineCourt(jour)}
            </span>
            <span className="font-medium tabular-nums">{formatDateJJMMAAAA(jour).slice(0, 5)}</span>
          </button>
        )
      })}
    </div>
  )
}
