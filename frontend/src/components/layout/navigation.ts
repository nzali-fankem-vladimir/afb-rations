import type { ComponentType } from 'react'
import {
  BarChart3,
  CheckSquare,
  ClipboardEdit,
  FileBarChart,
  FileCheck,
  ShieldCheck,
  Users,
  Wallet,
} from 'lucide-react'

import type { RoleEnum } from '../../types/enums'

export interface LienNavigation {
  href: string
  label: string
  icon: ComponentType<{ className?: string; 'aria-hidden'?: boolean }>
  roles: RoleEnum[]
}

/**
 * Liens de la sidebar, verifies le 16 septembre 2026 contre les @PreAuthorize
 * reels des controleurs backend (guide 7F.2 section 4) : un lien affiche vers
 * une route refusee est pire qu'un lien absent.
 */
export const LIENS_NAVIGATION: LienNavigation[] = [
  { href: '/processus', label: 'Processus', icon: FileCheck, roles: ['AGENT_UNITE'] },
  { href: '/saisie', label: 'Saisie journalière', icon: ClipboardEdit, roles: ['AGENT_UNITE'] },
  {
    href: '/validation',
    label: 'Validation',
    icon: CheckSquare,
    roles: ['CHEF_UNITE_DA', 'DIRECTEUR_RESEAU_DR'],
  },
  {
    href: '/suivi',
    label: 'Suivi',
    icon: BarChart3,
    roles: ['AGENT_UNITE', 'CHEF_UNITE_DA', 'DIRECTEUR_RESEAU_DR', 'ARH'],
  },
  { href: '/grilles', label: 'Grilles tarifaires', icon: Wallet, roles: ['ARH', 'DRH'] },
  { href: '/rapports', label: 'Rapports', icon: FileBarChart, roles: ['ARH'] },
  { href: '/audit', label: "Journal d'audit", icon: ShieldCheck, roles: ['ARH', 'DRH', 'ADMIN'] },
  { href: '/admin/utilisateurs', label: 'Utilisateurs', icon: Users, roles: ['ADMIN'] },
]

/** Route d'accueil par defaut selon le role : premier lien accessible, dans l'ordre du tableau. */
export function routeAccueil(role: RoleEnum | null): string {
  if (role === null) return '/'
  const premier = LIENS_NAVIGATION.find((lien) => lien.roles.includes(role))
  return premier?.href ?? '/'
}
