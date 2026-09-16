import { AlertCircle } from 'lucide-react'

import { cn } from '../../utils/cn'
import { Label } from './Label'
import { Select } from './Select'
import type { SelectProps } from './Select'

export interface OptionListe {
  valeur: string
  libelle: string
}

export interface ChampListeProps extends Omit<SelectProps, 'children'> {
  id: string
  label: string
  options: OptionListe[]
  erreur?: string
  obligatoire?: boolean
  className?: string
  /** Libelle de l'option vide initiale (ex. "Selectionner..."). Absent : aucune option vide. */
  libellePlaceholder?: string
}

export function ChampListe({
  id,
  label,
  options,
  erreur,
  obligatoire = false,
  className,
  libellePlaceholder,
  ...props
}: ChampListeProps) {
  const idErreur = `${id}-erreur`

  return (
    <div className={cn('flex flex-col gap-1.5', className)}>
      <Label htmlFor={id} obligatoire={obligatoire}>
        {label}
      </Label>
      <Select
        id={id}
        className={cn(erreur && 'border-primary-500 focus-visible:ring-primary-500')}
        aria-invalid={erreur ? true : undefined}
        aria-describedby={erreur ? idErreur : undefined}
        {...props}
      >
        {libellePlaceholder && <option value="">{libellePlaceholder}</option>}
        {options.map((option) => (
          <option key={option.valeur} value={option.valeur}>
            {option.libelle}
          </option>
        ))}
      </Select>
      {erreur && (
        <p id={idErreur} role="alert" className="flex items-center gap-1 text-xs text-neutral-800">
          <AlertCircle className="h-3.5 w-3.5 shrink-0 text-primary-500" aria-hidden="true" />
          {erreur}
        </p>
      )}
    </div>
  )
}
