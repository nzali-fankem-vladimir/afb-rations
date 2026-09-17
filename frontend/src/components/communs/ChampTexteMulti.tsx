import { AlertCircle } from 'lucide-react'

import { cn } from '../../utils/cn'
import { Label } from './Label'
import { Textarea } from './Textarea'
import type { TextareaProps } from './Textarea'

export interface ChampTexteMultiProps extends TextareaProps {
  id: string
  label: string
  erreur?: string
  obligatoire?: boolean
  className?: string
}

// Meme accessibilite que ChampTexte (Sprint 7F.1) : message d'erreur rattache par
// aria-describedby, champ marque invalide par aria-invalid.
export function ChampTexteMulti({
  id,
  label,
  erreur,
  obligatoire = false,
  className,
  ...props
}: ChampTexteMultiProps) {
  const idErreur = `${id}-erreur`

  return (
    <div className={cn('flex flex-col gap-1.5', className)}>
      <Label htmlFor={id} obligatoire={obligatoire}>
        {label}
      </Label>
      <Textarea
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
