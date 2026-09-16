import { cva } from 'class-variance-authority'
import type { VariantProps } from 'class-variance-authority'
import type { HTMLAttributes } from 'react'

import { cn } from '../../utils/cn'
import type { StatutEnum, StatutGrilleEnum, StatutIntegrationEnum } from '../../types/enums'

const badgeVariants = cva('inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium', {
  variants: {
    variant: {
      neutre: 'bg-neutral-100 text-neutral-700',
      attente: 'bg-amber-50 text-amber-700',
      positif: 'bg-emerald-50 text-emerald-700',
      // Seul usage du rouge sur un badge : la charte le reserve aux accents,
      // et ce module en compte trois (retour, rejet de grille, rejet comptable).
      negatif: 'bg-primary-50 text-primary-700',
    },
  },
  defaultVariants: { variant: 'neutre' },
})

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement>, VariantProps<typeof badgeVariants> {}

export function Badge({ className, variant, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ variant }), className)} {...props} />
}

// --- Correspondances par enumeration -------------------------------------
//
// Palette sobre a 4 variantes (Sprint 7F.1) : neutre pour "en cours, rien a
// signaler", attente pour "attente d'une action ou d'une reponse", positif
// pour un etat final favorable, negatif (rouge) reserve aux trois seuls cas
// qui appellent une action corrective.

type VarianteBadge = NonNullable<BadgeProps['variant']>

const VARIANTE_PAR_STATUT: Record<StatutEnum, VarianteBadge> = {
  EN_COURS_SAISIE: 'neutre',
  SOUMIS: 'neutre',
  EN_ATTENTE_DA: 'attente',
  EN_ATTENTE_DR: 'attente',
  RETOURNE: 'negatif',
  CLOTURE: 'positif',
}

const LIBELLE_STATUT: Record<StatutEnum, string> = {
  EN_COURS_SAISIE: 'En cours de saisie',
  SOUMIS: 'Soumis',
  EN_ATTENTE_DA: "En attente (chef d'unité)",
  EN_ATTENTE_DR: 'En attente (directeur réseau)',
  RETOURNE: 'Retourné',
  CLOTURE: 'Clôturé',
}

export function BadgeStatutProcessus({ statut, className }: { statut: StatutEnum; className?: string }) {
  return (
    <Badge variant={VARIANTE_PAR_STATUT[statut]} className={className}>
      {LIBELLE_STATUT[statut]}
    </Badge>
  )
}

const VARIANTE_PAR_STATUT_GRILLE: Record<StatutGrilleEnum, VarianteBadge> = {
  BROUILLON: 'neutre',
  EN_ATTENTE_DRH: 'attente',
  ACTIVE: 'positif',
  REJETEE: 'negatif',
}

const LIBELLE_STATUT_GRILLE: Record<StatutGrilleEnum, string> = {
  BROUILLON: 'Brouillon',
  EN_ATTENTE_DRH: 'En attente DRH',
  ACTIVE: 'Active',
  REJETEE: 'Rejetée',
}

export function BadgeStatutGrille({ statut, className }: { statut: StatutGrilleEnum; className?: string }) {
  return (
    <Badge variant={VARIANTE_PAR_STATUT_GRILLE[statut]} className={className}>
      {LIBELLE_STATUT_GRILLE[statut]}
    </Badge>
  )
}

const VARIANTE_PAR_STATUT_INTEGRATION: Record<StatutIntegrationEnum, VarianteBadge> = {
  EN_ATTENTE: 'attente',
  INTEGRE: 'positif',
  REJETE: 'negatif',
}

const LIBELLE_STATUT_INTEGRATION: Record<StatutIntegrationEnum, string> = {
  EN_ATTENTE: "En attente d'accusé",
  INTEGRE: 'Intégré',
  REJETE: 'Rejeté',
}

// `statut` nul : aucune valeur de StatutIntegrationEnum ne represente "jamais
// transmis" (CLAUDE.md section 5) -- cette situation se lit sur
// `transmisComptabilite`, jamais sur une valeur sentinelle de l'enumeration.
// Le badge doit donc l'afficher explicitement plutot que de rester silencieux.
export function BadgeStatutIntegration({
  statut,
  className,
}: {
  statut: StatutIntegrationEnum | null
  className?: string
}) {
  if (statut === null) {
    return (
      <Badge variant="neutre" className={className}>
        Non transmis
      </Badge>
    )
  }

  return (
    <Badge variant={VARIANTE_PAR_STATUT_INTEGRATION[statut]} className={className}>
      {LIBELLE_STATUT_INTEGRATION[statut]}
    </Badge>
  )
}
