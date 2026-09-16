import { Navigate, Outlet } from 'react-router-dom'

import { useAuth } from '../hooks/useAuth'
import type { RoleEnum } from '../types/enums'

interface ProtectedRouteProps {
  roles: RoleEnum[]
}

/**
 * Filtre par role. L'authentification elle-meme est deja garantie en amont :
 * ce routeur n'est monte que lorsque useAuth().etat vaut CONNECTE (voir App.tsx).
 */
export function ProtectedRoute({ roles }: ProtectedRouteProps) {
  const { possedeRole } = useAuth()

  if (!possedeRole(...roles)) {
    return <Navigate to="/acces-interdit" replace />
  }

  return <Outlet />
}
