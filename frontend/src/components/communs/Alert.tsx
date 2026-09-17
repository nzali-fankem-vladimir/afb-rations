import { forwardRef } from 'react'
import type { HTMLAttributes } from 'react'
import { cva } from 'class-variance-authority'
import type { VariantProps } from 'class-variance-authority'

import { cn } from '../../utils/cn'

const alertVariants = cva(
  'relative w-full rounded border px-4 py-3 text-sm [&>svg]:absolute [&>svg]:left-4 [&>svg]:top-3.5 [&>svg~*]:pl-7',
  {
    variants: {
      variant: {
        default: 'border-neutral-200 bg-neutral-50 text-neutral-900',
        destructive: 'border-primary-500 bg-primary-50 text-primary-700 [&>svg]:text-primary-500',
        warning: 'border-amber-400 bg-amber-50 text-amber-800 [&>svg]:text-amber-500',
      },
    },
    defaultVariants: { variant: 'default' },
  },
)

// "alert" est ASSERTIF : il coupe la parole au lecteur d'ecran pour lire son
// contenu d'un bloc. Reserve a un echec (destructive), qui doit interrompre.
// "status" annonce la meme chose poliment, sans casser la navigation en cours --
// adapte a un avertissement ou une simple information.
const ROLE_PAR_VARIANTE: Record<'default' | 'destructive' | 'warning', 'status' | 'alert'> = {
  default: 'status',
  destructive: 'alert',
  warning: 'status',
}

export interface AlertProps extends HTMLAttributes<HTMLDivElement>, VariantProps<typeof alertVariants> {}

export const Alert = forwardRef<HTMLDivElement, AlertProps>(({ className, variant, role, ...props }, ref) => (
  <div
    ref={ref}
    role={role ?? ROLE_PAR_VARIANTE[variant ?? 'default']}
    className={cn(alertVariants({ variant }), className)}
    {...props}
  />
))
Alert.displayName = 'Alert'

// <div>, pas <p> : le contenu porte le plus souvent plusieurs blocs (un titre,
// un detail, une liste de manques -- AffichageErreur, ResultatValidation...),
// et un <p> imbrique dans un <p> est un HTML invalide qui declenche une erreur
// d'hydratation (Sprint 7F.5, constate en reel sur l'ecran de reprise agent).
export const AlertDescription = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(
  ({ className, ...props }, ref) => <div ref={ref} className={cn('leading-relaxed', className)} {...props} />,
)
AlertDescription.displayName = 'AlertDescription'
