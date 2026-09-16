import { forwardRef } from 'react'
import type { LabelHTMLAttributes } from 'react'

import { cn } from '../../utils/cn'

export interface LabelProps extends LabelHTMLAttributes<HTMLLabelElement> {
  /** Affiche l'asterisque rouge des champs obligatoires (repere visuel, `required` porte deja l'annonce pour le lecteur d'ecran). */
  obligatoire?: boolean
}

export const Label = forwardRef<HTMLLabelElement, LabelProps>(
  ({ className, obligatoire = false, children, ...props }, ref) => (
    <label
      ref={ref}
      className={cn('text-sm font-medium text-neutral-900 leading-none peer-disabled:opacity-70', className)}
      {...props}
    >
      {children}
      {obligatoire && (
        <span className="ml-0.5 text-primary-500" aria-hidden="true">
          *
        </span>
      )}
    </label>
  ),
)
Label.displayName = 'Label'
