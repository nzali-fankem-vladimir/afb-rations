import { cn } from '../../utils/cn'
import { enumererJours, formatDateJJMMAAAA, formatJourSemaineCourt, formatMontantFcfa } from '../../utils/formatters'

/** Ce qui a deja ete saisi sur une journee, pour l'afficher sur son bouton. */
export interface ResumeJournee {
  nombreLignes: number
  sousTotalFcfa: number
}

export interface SelecteurJourProps {
  dateDebut: string
  dateFin: string
  dateSelectionnee: string
  /** Indexe par date ISO. Une journee absente n'a aucune ligne. */
  resumes: Record<string, ResumeJournee>
  onSelectionner: (dateJour: string) => void
}

/**
 * Selecteur de jour BORNE a la periode de l'etat (dateDebut a dateFin incluses).
 *
 * Borner ici, plutot qu'un calendrier libre, empeche a la source l'erreur que le
 * backend ne refuse qu'a la soumission (LIGNE_HORS_PERIODE) : une ligne datee
 * hors periode echappe entre-temps au controle d'unicite RG-15, qui ne porte que
 * sur sa propre journee (guide 7F.4, etape 3).
 *
 * Chaque jour porte son nombre de lignes et son sous-total (Sprint 7F.6,
 * proposition n°5) : l'agent voit ou il en est sans ouvrir l'onglet de
 * consultation, et repere d'un coup d'oeil un jour oublie. Les chiffres
 * viennent de l'etat consolide (RG-06), jamais d'une somme refaite ici.
 */
export function SelecteurJour({
  dateDebut,
  dateFin,
  dateSelectionnee,
  resumes,
  onSelectionner,
}: SelecteurJourProps) {
  const jours = enumererJours(dateDebut, dateFin)
  const aujourdhui = new Date().toISOString().slice(0, 10)

  return (
    <div role="tablist" aria-label="Journée" className="flex gap-2 overflow-x-auto pb-1">
      {jours.map((jour) => {
        const selectionne = jour === dateSelectionnee
        const resume = resumes[jour]
        const vide = !resume || resume.nombreLignes === 0

        return (
          <button
            key={jour}
            type="button"
            role="tab"
            aria-selected={selectionne}
            onClick={() => onSelectionner(jour)}
            className={cn(
              'flex min-w-36 flex-1 shrink-0 flex-col items-start rounded-lg border px-3 py-2 text-left transition-colors motion-reduce:transition-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500',
              selectionne
                ? 'border-primary-500 bg-primary-50 text-primary-700'
                : 'border-neutral-200 bg-white text-neutral-700 hover:bg-neutral-50',
            )}
          >
            <span className="flex w-full items-baseline justify-between gap-2">
              <span className="text-xxs font-semibold uppercase tracking-wide">
                {formatJourSemaineCourt(jour)}
              </span>
              {jour === aujourdhui && (
                <span className="text-xxs font-semibold text-emerald-700">aujourd'hui</span>
              )}
            </span>
            <span className="font-semibold tabular-nums">{formatDateJJMMAAAA(jour).slice(0, 5)}</span>
            <span className={cn('text-xs tabular-nums', selectionne ? 'text-primary-700' : 'text-neutral-600')}>
              {vide ? 'aucune ligne' : `${resume.nombreLignes} ligne${resume.nombreLignes > 1 ? 's' : ''}`}
            </span>
            <span
              className={cn(
                'text-sm tabular-nums',
                vide
                  ? 'text-neutral-500'
                  : selectionne
                    ? 'font-bold text-primary-700'
                    : 'font-bold text-neutral-900',
              )}
            >
              {vide ? formatMontantFcfa(0) : formatMontantFcfa(resume.sousTotalFcfa)}
            </span>
          </button>
        )
      })}
    </div>
  )
}
