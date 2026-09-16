import { Navigate, Route, Routes } from 'react-router-dom'

import { AppLayout } from '../components/layout/AppLayout'
import { LIENS_NAVIGATION, routeAccueil } from '../components/layout/navigation'
import { AccesInterdit } from '../pages/AccesInterdit'
import { PageIntrouvable } from '../pages/PageIntrouvable'
import { PageProvisoire } from '../pages/PageProvisoire'
import { useAuth } from '../hooks/useAuth'
import { ProtectedRoute } from './ProtectedRoute'

/**
 * Routage du module, monte uniquement une fois la session CONNECTE (voir App.tsx) : la
 * protection par role s'appuie donc sur une authentification deja acquise.
 *
 * Les routes sont generees depuis LIENS_NAVIGATION (meme source que la sidebar) : un
 * role accessible dans le menu l'est necessairement ici, et inversement.
 */
export function AppRouter() {
  const { role } = useAuth()

  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<Navigate to={routeAccueil(role)} replace />} />

        {LIENS_NAVIGATION.map((lien) => (
          <Route key={lien.href} element={<ProtectedRoute roles={lien.roles} />}>
            <Route path={lien.href} element={<PageProvisoire titre={lien.label} />} />
          </Route>
        ))}

        <Route path="/acces-interdit" element={<AccesInterdit />} />
        <Route path="*" element={<PageIntrouvable />} />
      </Route>
    </Routes>
  )
}
