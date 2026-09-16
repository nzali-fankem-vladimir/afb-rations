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

interface LogoProps extends Omit<ImgHTMLAttributes<HTMLImageElement>, 'src' | 'alt' | 'width' | 'height'> {
  variant?: keyof typeof SOURCES
}

/** Logo Afriland First Bank. `embleme` : motif seul, pour les espaces reduits. */
export function Logo({ variant = 'complet', className, ...props }: LogoProps) {
  return (
    <img
      src={SOURCES[variant]}
      alt="Afriland First Bank"
      width={DIMENSIONS[variant].width}
      height={DIMENSIONS[variant].height}
      className={cn('h-9 w-auto object-contain', className)}
      {...props}
    />
  )
}
