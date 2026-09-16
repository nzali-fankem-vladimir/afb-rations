import { AlertCircle } from 'lucide-react'

import { cn } from '../../utils/cn'
import { Label } from './Label'
import { Input } from './Input'
import type { InputProps } from './Input'

export interface ChampTexteProps extends InputProps {
  id: string
  label: string
  erreur?: string
  obligatoire?: boolean
  className?: string
}

// Rattache le message d'erreur au champ par aria-describedby (annonce a la prise
// de focus) et marque le champ invalide par aria-invalid : sans les deux, un
// lecteur d'ecran annonce le libelle du champ et s'arrete la, sans jamais dire
// pourquoi la valeur est refusee.
export function ChampTexte({ id, label, erreur, obligatoire = false, className, ...props }: ChampTexteProps) {
  const idErreur = `${id}-erreur`

  return (
    <div className={cn('flex flex-col gap-1.5', className)}>
      <Label htmlFor={id} obligatoire={obligatoire}>
        {label}
      </Label>
      <Input
        id={id}
        className={cn(erreur && 'border-primary-500 focus-visible:ring-primary-500')}
        aria-invalid={erreur ? true : undefined}
        aria-describedby={erreur ? idErreur : undefined}
        {...props}
      />
      {erreur && (
        <p id={idErreur} role="alert" className="flex items-center gap-1 text-xs text-neutral-800">
          <AlertCircle className="h-3.5 w-3.5 shrink-0 text-primary-500" aria-hidden="true" />
          {erreur}
        </p>
      )}
    </div>
  )
}
