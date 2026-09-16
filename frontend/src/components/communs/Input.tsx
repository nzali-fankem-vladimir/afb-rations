import { forwardRef } from 'react'
import type { InputHTMLAttributes } from 'react'

import { cn } from '../../utils/cn'

export type InputProps = InputHTMLAttributes<HTMLInputElement>

// autoComplete/spellCheck desactives par defaut : dans ce module, l'agent saisit
// les coordonnees d'un beneficiaire (compte, matricule), jamais les siennes --
// la saisie semi-automatique et le correcteur du navigateur n'ont rien a y faire.
export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ className, type, autoComplete = 'off', spellCheck = false, ...props }, ref) => (
    <input
      type={type}
      autoComplete={autoComplete}
      spellCheck={spellCheck}
      className={cn(
        'flex h-10 w-full rounded border border-neutral-500 bg-white px-3 py-2 text-sm text-neutral-900 placeholder:text-neutral-600 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 disabled:cursor-not-allowed disabled:opacity-50',
        className,
      )}
      ref={ref}
      {...props}
    />
  ),
)
Input.displayName = 'Input'
