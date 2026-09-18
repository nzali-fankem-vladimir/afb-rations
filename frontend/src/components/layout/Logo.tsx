import type { ImgHTMLAttributes } from 'react'

import logoComplet from '../../assets/logo/logo-afriland.png'
import logoEmbleme from '../../assets/logo/logo-afriland-embleme.png'
import { cn } from '../../utils/cn'

const SOURCES = {
  complet: logoComplet,
  embleme: logoEmbleme,
} as const

const DIMENSIONS = {
  complet: { width: 832, height: 249 },
  embleme: { width: 178, height: 161 },
} as const

// ATTENTION -- l'echelle n'est PAS croissante : `sm` (h-15) est plus grand que
// `lg` (h-12). Ce n'est pas un oubli. `sm` n'a qu'un seul appelant, le
// logotype complet de la sidebar (Sidebar.tsx) -- retour utilisateur du
// rattrapage post-7F.6, demandant la taille retenue dans le projet DOTTEL
// pour ce meme emplacement (afb-dottel-mm/frontend/src/components/ui/Logo.jsx).
// Ramener `sm` sous `md`/`lg` par souci de coherence de nommage retrecirait
// le logo de marque dans la navigation.
const TAILLES = {
  sm: 'h-15',
  md: 'h-9',
  lg: 'h-12',
} as const

interface LogoProps extends Omit<ImgHTMLAttributes<HTMLImageElement>, 'src' | 'alt' | 'width' | 'height'> {
  variant?: keyof typeof SOURCES
  taille?: keyof typeof TAILLES
}

/** Logo Afriland First Bank. `embleme` : motif seul, pour les espaces reduits. */
export function Logo({ variant = 'complet', taille = 'md', className, ...props }: LogoProps) {
  return (
    <img
      src={SOURCES[variant]}
      alt="Afriland First Bank"
      width={DIMENSIONS[variant].width}
      height={DIMENSIONS[variant].height}
      className={cn(TAILLES[taille], 'w-auto object-contain', className)}
      {...props}
    />
  )
}
