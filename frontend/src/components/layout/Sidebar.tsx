import { useEffect, useRef, useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import { ChevronUp, LogOut } from 'lucide-react'

import { useAuth } from '../../hooks/useAuth'
import { cn } from '../../utils/cn'
import { Logo } from './Logo'
import { LIENS_NAVIGATION } from './navigation'

/**
 * Le href actif est le plus SPECIFIQUE (le plus long) parmi les entrees dont
 * le chemin courant est une sous-route. Gere par exemple le cas d'une page de
 * detail (/validation/42) sans entree dediee : seul le prefixe /validation
 * correspond.
 */
function trouverHrefActif(pathname: string, hrefs: string[]): string | null {
  const correspondances = hrefs.filter((href) => pathname === href || pathname.startsWith(`${href}/`))
  if (correspondances.length === 0) return null
  return correspondances.reduce((plusSpecifique, actuel) =>
    actuel.length > plusSpecifique.length ? actuel : plusSpecifique,
  )
}

function initiales(prenom?: string, nom?: string): string {
  const p = prenom?.[0] ?? ''
  const n = nom?.[0] ?? ''
  return (p + n).toUpperCase() || '?'
}

export function Sidebar() {
  const { utilisateur, role, possedeRole, deconnecter } = useAuth()
  const location = useLocation()
  const [menuOuvert, setMenuOuvert] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)
  const boutonCompteRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    function surClicExterieur(event: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setMenuOuvert(false)
      }
    }
    document.addEventListener('mousedown', surClicExterieur)
    return () => document.removeEventListener('mousedown', surClicExterieur)
  }, [])

  useEffect(() => {
    if (!menuOuvert) return undefined
    function surEchap(event: KeyboardEvent) {
      if (event.key !== 'Escape') return
      setMenuOuvert(false)
      boutonCompteRef.current?.focus()
    }
    document.addEventListener('keydown', surEchap)
    return () => document.removeEventListener('keydown', surEchap)
  }, [menuOuvert])

  const liensVisibles = LIENS_NAVIGATION.filter((lien) => possedeRole(...lien.roles))
  const hrefActif = trouverHrefActif(
    location.pathname,
    liensVisibles.map((lien) => lien.href),
  )

  return (
    <aside className="flex h-screen w-64 shrink-0 flex-col bg-neutral-950 text-white">
      <div className="flex items-center gap-2 px-5 py-6">
        <Logo className="brightness-0 invert" />
      </div>

      <nav aria-label="Navigation principale" className="flex-1 space-y-1 overflow-y-auto px-3">
        {liensVisibles.map((lien) => (
          <NavLink
            key={lien.href}
            to={lien.href}
            className={cn(
              'flex items-center gap-3 rounded px-3 py-2.5 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white focus-visible:ring-offset-2 focus-visible:ring-offset-neutral-950',
              lien.href === hrefActif
                ? 'bg-primary-500 text-white'
                : 'text-neutral-300 hover:bg-neutral-900 hover:text-white',
            )}
          >
            <lien.icon className="h-4 w-4 shrink-0" aria-hidden />
            <span>{lien.label}</span>
          </NavLink>
        ))}
      </nav>

      <div className="border-t border-neutral-800 px-3 py-3" ref={menuRef}>
        <div className="relative">
          <button
            type="button"
            ref={boutonCompteRef}
            onClick={() => setMenuOuvert((ouvert) => !ouvert)}
            aria-expanded={menuOuvert}
            aria-haspopup="menu"
            aria-label={`Compte de ${utilisateur?.prenom ?? ''} ${utilisateur?.nom ?? 'utilisateur'}${role ? `, rôle ${role}` : ''} — ouvrir le menu`}
            className="flex w-full items-center gap-3 rounded px-2 py-2 text-left hover:bg-neutral-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white focus-visible:ring-offset-2 focus-visible:ring-offset-neutral-950"
          >
            <span
              aria-hidden
              className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-neutral-800 text-xs font-semibold text-white"
            >
              {initiales(utilisateur?.prenom, utilisateur?.nom)}
            </span>
            <div className="flex min-w-0 flex-1 flex-col">
              <span className="truncate text-sm font-semibold text-white">
                {utilisateur ? `${utilisateur.prenom} ${utilisateur.nom}` : 'Utilisateur'}
              </span>
              <span className="text-xxs uppercase tracking-wider text-neutral-400">{role}</span>
            </div>
            <ChevronUp
              aria-hidden
              className={cn(
                'h-4 w-4 shrink-0 text-neutral-400 transition-transform motion-reduce:transition-none',
                !menuOuvert && 'rotate-180',
              )}
            />
          </button>

          {menuOuvert && (
            <div
              role="menu"
              className="absolute bottom-full left-0 mb-2 w-full overflow-hidden rounded-md border border-neutral-800 bg-neutral-900 py-1 shadow-xl"
            >
              <button
                type="button"
                role="menuitem"
                onClick={() => void deconnecter()}
                className="flex w-full items-center gap-3 px-3 py-2.5 text-sm font-medium text-primary-300 hover:bg-neutral-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-white"
              >
                <LogOut className="h-4 w-4" aria-hidden />
                Déconnexion
              </button>
            </div>
          )}
        </div>
      </div>
    </aside>
  )
}
