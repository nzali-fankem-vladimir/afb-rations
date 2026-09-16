import { ChampTexte } from './ChampTexte'
import { cn } from '../../utils/cn'

export interface SelecteurPeriodeProps {
  idPrefix: string
  dateDebut: string
  dateFin: string
  onChangerDateDebut: (valeur: string) => void
  onChangerDateFin: (valeur: string) => void
  labelDateDebut?: string
  labelDateFin?: string
  erreurDateDebut?: string
  erreurDateFin?: string
  obligatoire?: boolean
  className?: string
}

// Deux bornes independantes, toutes deux INCLUSES (depuis la Maille 1) : la
// periode d'un etat n'est plus un mois, c'est un intervalle de dates. Aucune
// duree n'est imposee ici (ni minimum, ni maximum) : le cycle est hebdomadaire
// aujourd'hui, mais l'intervalle a ete choisi precisement pour ne pas figer une
// cadence -- ce composant ne doit pas la refiger cote interface.
export function SelecteurPeriode({
  idPrefix,
  dateDebut,
  dateFin,
  onChangerDateDebut,
  onChangerDateFin,
  labelDateDebut = 'Date de debut',
  labelDateFin = 'Date de fin',
  erreurDateDebut,
  erreurDateFin,
  obligatoire = false,
  className,
}: SelecteurPeriodeProps) {
  return (
    <div className={cn('flex flex-col gap-4 sm:flex-row', className)}>
      <ChampTexte
        id={`${idPrefix}-date-debut`}
        type="date"
        label={labelDateDebut}
        obligatoire={obligatoire}
        erreur={erreurDateDebut}
        value={dateDebut}
        onChange={(event) => onChangerDateDebut(event.target.value)}
        className="flex-1"
      />
      <ChampTexte
        id={`${idPrefix}-date-fin`}
        type="date"
        label={labelDateFin}
        obligatoire={obligatoire}
        erreur={erreurDateFin}
        value={dateFin}
        onChange={(event) => onChangerDateFin(event.target.value)}
        className="flex-1"
      />
    </div>
  )
}
