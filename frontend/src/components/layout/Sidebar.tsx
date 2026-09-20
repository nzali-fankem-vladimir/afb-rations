import { useEffect, useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import { LogOut, PanelLeftClose, PanelLeftOpen } from 'lucide-react'

import { useAuth } from '../../hooks/useAuth'
import { useCompteurValidation } from '../../hooks/useCompteurValidation'
import { useFonctionnalites } from '../../hooks/useFonctionnalites'
import { cn } from '../../utils/cn'
import { LIBELLE_ROLE } from '../../utils/libelleRole'
import { Logo } from './Logo'
import { LIENS_NAVIGATION, type GroupeNavigation } from './navigation'

/** Ordre d'affichage des groupes, independant de l'ordre des liens dans navigation.ts. */
const ORDRE_GROUPES: GroupeNavigation[] = ['Mon travail', 'Référentiel', 'Administration']

const CLE_SIDEBAR_REDUITE = 'rations.sidebar.reduite'

/**
 * Lit l'etat replie/deplie depuis localStorage. Enveloppe defensive : un
 * navigateur en navigation privee, avec le stockage bloque, ou l'acces refuse
 * par une politique de securite doit degrader vers l'etat par defaut
 * (deplie), jamais faire echouer le rendu de la sidebar.
 */
function lireSidebarReduite(): boolean {
  try {
    return localStorage.getItem(CLE_SIDEBAR_REDUITE) === '1'
  } catch {
    return false
  }
}

function ecrireSidebarReduite(reduite: boolean): void {
  try {
    localStorage.setItem(CLE_SIDEBAR_REDUITE, reduite ? '1' : '0')
  } catch {
    // Stockage indisponible : la preference ne survivra pas au rechargement,
    // ce qui est un moindre mal plutot qu'une exception qui casse la sidebar.
  }
}

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
  const { utilisateur, role, codeUnite, possedeRole, deconnecter } = useAuth()
  const location = useLocation()
  const [reduite, setReduite] = useState<boolean>(lireSidebarReduite)
  const compteValidation = useCompteurValidation(role, location.pathname)
  const fonctionnalites = useFonctionnalites()

  useEffect(() => {
    ecrireSidebarReduite(reduite)
  }, [reduite])

  // Deux filtres, dans cet ordre : le role, puis le drapeau de fonctionnalite.
  // Un lien conditionnel reste masque tant que le drapeau n'est pas lu -- le
  // faire apparaitre puis disparaitre serait pire que d'attendre une requete.
  const liensVisibles = LIENS_NAVIGATION.filter((lien) => {
    if (!possedeRole(...lien.roles)) return false
    if (lien.fonctionnalite === 'rattrapage') {
      return !fonctionnalites.chargement && fonctionnalites.rattrapageActif
    }
    return true
  })
  const hrefActif = trouverHrefActif(
    location.pathname,
    liensVisibles.map((lien) => lien.href),
  )
  const groupesVisibles = ORDRE_GROUPES.filter((groupe) => liensVisibles.some((lien) => lien.groupe === groupe))

  return (
    <aside
      className={cn(
        'flex h-screen shrink-0 flex-col bg-neutral-950 text-white transition-[width] duration-150 motion-reduce:transition-none',
        reduite ? 'w-[76px]' : 'w-64',
      )}
    >
      <div className={cn('flex flex-col items-center gap-1 py-6', reduite ? 'px-2' : 'px-5')}>
        {reduite ? (
          <Logo variant="embleme" className="h-8 w-auto brightness-0 invert" />
        ) : (
          <>
            <Logo taille="sm" className="brightness-0 invert" />
            {/* Sous-titre du module, sous le logo de la banque : distingue ce
                module (Rations & Transport) des autres applications liées au
                portail INTRA, qui partagent le meme logo Afriland. */}
            <p className="text-center text-[11px] font-bold uppercase tracking-widest text-primary-500">
              Rations &amp; Transport
            </p>
          </>
        )}
      </div>

      <div className={cn('px-3 pb-2', reduite && 'flex justify-center px-0')}>
        <button
          type="button"
          onClick={() => setReduite((valeur) => !valeur)}
          aria-pressed={reduite}
          title={reduite ? 'Étendre la barre latérale' : 'Réduire la barre latérale'}
          className={cn(
            'flex items-center gap-2 rounded px-2 py-1.5 text-xs font-medium text-neutral-400 hover:bg-neutral-900 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white focus-visible:ring-offset-2 focus-visible:ring-offset-neutral-950',
            reduite ? 'justify-center' : 'w-full justify-end',
          )}
        >
          {reduite ? (
            <PanelLeftOpen className="h-4 w-4" aria-hidden />
          ) : (
            <>
              <PanelLeftClose className="h-4 w-4" aria-hidden />
              Réduire
            </>
          )}
          <span className="sr-only">{reduite ? 'Étendre la barre latérale' : 'Réduire la barre latérale'}</span>
        </button>
      </div>

      <nav aria-label="Navigation principale" className="flex-1 space-y-1 overflow-y-auto px-3">
        {groupesVisibles.map((groupe) => (
          <div key={groupe} className="pb-1">
            {!reduite && (
              <p className="px-3 pb-1 pt-3 text-[11px] font-semibold uppercase tracking-wider text-neutral-500">
                {groupe}
              </p>
            )}
            {liensVisibles
              .filter((lien) => lien.groupe === groupe)
              .map((lien) => {
                const compte = lien.compteur === 'validation' ? compteValidation : null
                const actif = lien.href === hrefActif
                return (
                  <NavLink
                    key={lien.href}
                    to={lien.href}
                    title={reduite ? lien.label : undefined}
                    className={cn(
                      // Liseré plutôt qu'un aplat rouge (Sprint 7F.6, onglet "Barre
                      // latérale") : le rouge reste réservé aux actions et aux alertes,
                      // un aplat permanent le banaliserait. Bordure transparente par
                      // défaut, jamais retirée, pour que l'état actif ne décale rien.
                      'relative flex items-center gap-3 rounded border-l-2 px-3 py-2.5 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white focus-visible:ring-offset-2 focus-visible:ring-offset-neutral-950',
                      reduite && 'justify-center border-l-0 px-0',
                      actif
                        ? 'border-primary-500 bg-neutral-900 text-white'
                        : 'border-transparent text-neutral-300 hover:bg-neutral-900 hover:text-white',
                    )}
                  >
                    <lien.icon className="h-4 w-4 shrink-0" aria-hidden />
                    {reduite ? (
                      <span className="sr-only">{lien.label}</span>
                    ) : (
                      <span className="flex-1 truncate">{lien.label}</span>
                    )}
                    {compte !== null && compte > 0 && (
                      <span
                        aria-hidden={reduite}
                        className={cn(
                          'flex shrink-0 items-center justify-center rounded-full bg-primary-500 text-xxs font-bold text-white',
                          reduite
                            ? 'absolute right-1 top-1 h-4 min-w-4 px-1'
                            : 'min-w-[1.375rem] px-1.5 py-0.5',
                        )}
                      >
                        {compte}
                      </span>
                    )}
                    {!reduite && compte !== null && compte > 0 && (
                      <span className="sr-only">, {compte} en attente</span>
                    )}
                  </NavLink>
                )
              })}
          </div>
        ))}
      </nav>

      <div className="border-t border-neutral-800 px-3 py-3">
        <div className={cn('flex items-center gap-2', reduite && 'flex-col')}>
          <div
            className={cn('flex min-w-0 flex-1 items-center gap-3 px-2 py-1', reduite && 'flex-none px-0')}
            title={reduite && utilisateur ? `${utilisateur.prenom} ${utilisateur.nom}` : undefined}
          >
            <span
              aria-hidden
              className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-neutral-800 text-xs font-semibold text-white"
            >
              {initiales(utilisateur?.prenom, utilisateur?.nom)}
            </span>
            {!reduite && (
              <div className="flex min-w-0 flex-1 flex-col">
                <span className="truncate text-sm font-semibold text-white">
                  {utilisateur ? `${utilisateur.prenom} ${utilisateur.nom}` : 'Utilisateur'}
                </span>
                {/* Rôle en toutes lettres, avec l'unité (Sprint 7F.6, onglet "Barre
                    latérale") : "Agent d'unité · 00002", jamais le nom technique de
                    l'énumération. Sans unité pour les rôles à portée nationale
                    (ARH/DRH/ADMIN, Sprint 1.1). */}
                <span className="truncate text-xxs text-neutral-400">
                  {role ? LIBELLE_ROLE[role] : ''}
                  {codeUnite ? ` · ${codeUnite}` : ''}
                </span>
              </div>
            )}
          </div>
          {/* Déconnexion en un geste, à la place du menu déroulant : le chevron ne
              disait pas qu'il menait à la déconnexion. L'icône de sortie, nommée
              par l'infobulle et le lecteur d'écran, dit ce qu'elle fait. */}
          <button
            type="button"
            onClick={() => void deconnecter()}
            title="Se déconnecter"
            aria-label="Se déconnecter"
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded text-neutral-300 transition-colors hover:bg-primary-600 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white focus-visible:ring-offset-2 focus-visible:ring-offset-neutral-950"
          >
            <LogOut className="h-4 w-4" aria-hidden />
          </button>
        </div>
      </div>
    </aside>
  )
}
