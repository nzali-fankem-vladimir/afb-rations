import { Navigate, Route, Routes } from 'react-router-dom'

import { AppLayout } from '../components/layout/AppLayout'
import { LIENS_NAVIGATION, routeAccueil } from '../components/layout/navigation'
import { AccesInterdit } from '../pages/AccesInterdit'
import { PageIntrouvable } from '../pages/PageIntrouvable'
import { PageProvisoire } from '../pages/PageProvisoire'
import { ProcessusListPage } from '../pages/processus/ProcessusListPage'
import { SaisieProcessusPage } from '../pages/saisie/SaisieProcessusPage'
import { useAuth } from '../hooks/useAuth'
import { ProtectedRoute } from './ProtectedRoute'

/**
 * Element reel a rendre pour un lien de navigation donne. Un seul lien de la
 * sidebar (guide 7F.2) peut correspondre a un ecran deja construit -- les
 * autres restent une PageProvisoire tant que leur sous-sprint n'est pas atteint.
 */
function elementPourLien(href: string) {
  switch (href) {
    case '/processus':
      return <ProcessusListPage />
    // /saisie (sans identifiant) n'a rien a afficher par lui-meme : l'agent
    // choisit d'abord un processus depuis la liste, qui l'envoie ensuite sur
    // /saisie/:idProcessus (route de detail ci-dessous).
    case '/saisie':
      return <Navigate to="/processus" replace />
    default:
      return null
  }
}

/**
 * Routage du module.
 *
 * Toutes les routes, sans exception, sont sous un ProtectedRoute exigeant une session
 * CONNECTE : aucune page du module n'est publique. Les routes metier ajoutent leur
 * filtre de role, genere depuis LIENS_NAVIGATION (meme source que la sidebar) : un
 * role accessible dans le menu l'est necessairement ici, et inversement.
 */
export function AppRouter() {
  const { role } = useAuth()

  return (
    <Routes>
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route index element={<Navigate to={routeAccueil(role)} replace />} />

          {LIENS_NAVIGATION.map((lien) => (
            <Route key={lien.href} element={<ProtectedRoute roles={lien.roles} />}>
              <Route path={lien.href} element={elementPourLien(lien.href) ?? <PageProvisoire titre={lien.label} />} />
              {/* /saisie/:idProcessus : route de detail sans lien propre dans la
                  sidebar, declaree sous le meme ProtectedRoute que /saisie
                  (decision Sprint 7F.3, section 1) -- masquer un lien ne
                  protege rien, seul le garde-role compte. */}
              {lien.href === '/saisie' && (
                <Route path="/saisie/:idProcessus" element={<SaisieProcessusPage />} />
              )}
            </Route>
          ))}

          <Route path="/acces-interdit" element={<AccesInterdit />} />
          <Route path="*" element={<PageIntrouvable />} />
        </Route>
      </Route>
    </Routes>
  )
}
