import type { ComponentType } from 'react'
import {
  BarChart3,
  CheckSquare,
  ClipboardEdit,
  FileBarChart,
  FileCheck,
  Settings,
  ShieldCheck,
  Users,
  Wallet,
} from 'lucide-react'

import type { RoleEnum } from '../../types/enums'

/**
 * Trois groupes, dans l'ordre d'affichage : ce que je fais tous les jours, ce
 * que je consulte comme referentiel, ce que j'administre. Decision retenue au
 * Sprint 7F.6 (onglet "Barre laterale" de la maquette de refonte), en remplacement
 * de la liste a plat.
 */
export type GroupeNavigation = 'Mon travail' | 'Référentiel' | 'Administration'

export interface LienNavigation {
  href: string
  label: string
  icon: ComponentType<{ className?: string; 'aria-hidden'?: boolean }>
  roles: RoleEnum[]
  groupe: GroupeNavigation
  /**
   * Marque le lien qui porte le compteur de dossiers en attente (Sidebar.tsx,
   * useCompteurValidation). Un seul lien le porte aujourd'hui ; le champ reste
   * generique pour ne pas devoir retoucher ce fichier si un second compteur
   * s'ajoute un jour (ex. grilles en attente de la DRH).
   */
  compteur?: 'validation'
}

/**
 * Liens de la sidebar, verifies le 16 septembre 2026 contre les @PreAuthorize
 * reels des controleurs backend (guide 7F.2 section 4) : un lien affiche vers
 * une route refusee est pire qu'un lien absent.
 */
export const LIENS_NAVIGATION: LienNavigation[] = [
  { href: '/processus', label: 'Processus', icon: FileCheck, roles: ['AGENT_UNITE'], groupe: 'Mon travail' },
  {
    href: '/saisie',
    label: 'Saisie journalière',
    icon: ClipboardEdit,
    roles: ['AGENT_UNITE'],
    groupe: 'Mon travail',
  },
  {
    href: '/validation',
    label: 'Validation',
    icon: CheckSquare,
    roles: ['CHEF_UNITE_DA', 'DIRECTEUR_RESEAU_DR'],
    groupe: 'Mon travail',
    compteur: 'validation',
  },
  {
    href: '/suivi',
    label: 'Suivi',
    icon: BarChart3,
    roles: ['AGENT_UNITE', 'CHEF_UNITE_DA', 'DIRECTEUR_RESEAU_DR', 'ARH'],
    groupe: 'Mon travail',
  },
  { href: '/grilles', label: 'Grilles tarifaires', icon: Wallet, roles: ['ARH', 'DRH'], groupe: 'Référentiel' },
  { href: '/rapports', label: 'Rapports', icon: FileBarChart, roles: ['ARH'], groupe: 'Référentiel' },
  {
    href: '/audit',
    label: "Journal d'audit",
    icon: ShieldCheck,
    roles: ['ARH', 'DRH', 'ADMIN'],
    groupe: 'Administration',
  },
  { href: '/admin/utilisateurs', label: 'Utilisateurs', icon: Users, roles: ['ADMIN'], groupe: 'Administration' },
  // Route reintroduite au Sprint 7F.6 (retiree au 7F.2, faute d'endpoint
  // d'ecriture a l'epoque) : un petit ajout backend scope (PUT /parametres/{code})
  // a comble le manque. Voir docs/decisions/2026-09-17-endpoint-ecriture-parametres-systeme.md.
  {
    href: '/admin/parametres',
    label: 'Paramètres système',
    icon: Settings,
    roles: ['ADMIN'],
    groupe: 'Administration',
  },
]

/**
 * Route d'accueil par defaut selon le role : premier lien accessible, dans l'ordre du tableau.
 *
 * Repli sur /acces-interdit, jamais sur '/' : la route index redirige vers cette valeur,
 * et un role prive de tout lien ferait rediriger '/' vers '/' sans fin.
 */
export function routeAccueil(role: RoleEnum | null): string {
  if (role === null) return '/acces-interdit'
  const premier = LIENS_NAVIGATION.find((lien) => lien.roles.includes(role))
  return premier?.href ?? '/acces-interdit'
}
