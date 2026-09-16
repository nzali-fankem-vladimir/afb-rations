import { Navigate, Outlet } from 'react-router-dom'

import { useAuth } from '../hooks/useAuth'
import { EcranSession } from '../pages/EcranSession'
import type { RoleEnum } from '../types/enums'

interface ProtectedRouteProps {
  /** Roles autorises. Absent : une session CONNECTE suffit. */
  roles?: RoleEnum[]
}

/**
 * Protection d'un groupe de routes, en deux temps et dans cet ordre.
 *
 * 1. La session. Tant qu'elle n'est pas CONNECTE, on rend l'ecran d'etat (chargement,
 *    connexion, non habilite, erreur) A LA PLACE de la route, sans changer l'adresse :
 *    une redirection prematuree pendant le chargement du profil renverrait un
 *    utilisateur authentifie vers la connexion, et l'adresse demandee serait perdue.
 * 2. Le role, lu dans le profil applicatif et jamais dans le jeton (CLAUDE.md
 *    section 10). Refus : page d'acces interdit, sans deconnexion.
 *
 * Masquer un lien dans la sidebar est un confort ; ce composant est la protection.
 * Il est la seule barriere contre un acces direct par l'adresse.
 */
export function ProtectedRoute({ roles }: ProtectedRouteProps) {
  const { etat, possedeRole } = useAuth()

  if (etat !== 'CONNECTE') {
    return <EcranSession />
  }

  if (roles && !possedeRole(...roles)) {
    return <Navigate to="/acces-interdit" replace />
  }

  return <Outlet />
}
