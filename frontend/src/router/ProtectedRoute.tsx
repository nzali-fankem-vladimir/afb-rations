import { Navigate, Outlet } from 'react-router-dom'

import { useAuth } from '../hooks/useAuth'
import { useFonctionnalites } from '../hooks/useFonctionnalites'
import { EcranSession } from '../pages/EcranSession'
import type { FonctionnaliteNavigation } from '../components/layout/navigation'
import type { RoleEnum } from '../types/enums'

interface ProtectedRouteProps {
  /** Roles autorises. Absent : une session CONNECTE suffit. */
  roles?: RoleEnum[]
  /**
   * Drapeau de fonctionnalite exige. Absent : la route ne depend d'aucun
   * drapeau (Sprint 7F.7, etape 5).
   */
  fonctionnalite?: FonctionnaliteNavigation
}

/**
 * Protection d'un groupe de routes, en trois temps et dans cet ordre.
 *
 * 1. La session. Tant qu'elle n'est pas CONNECTE, on rend l'ecran d'etat (chargement,
 *    connexion, non habilite, erreur) A LA PLACE de la route, sans changer l'adresse :
 *    une redirection prematuree pendant le chargement du profil renverrait un
 *    utilisateur authentifie vers la connexion, et l'adresse demandee serait perdue.
 * 2. Le role, lu dans le profil applicatif et jamais dans le jeton (CLAUDE.md
 *    section 10). Refus : page d'acces interdit, sans deconnexion.
 * 3. Le drapeau de fonctionnalite, quand la route en exige un. Meme precaution
 *    qu'au point 1 : tant que le drapeau n'est pas lu, on attend plutot que de
 *    rediriger -- rediriger pendant la lecture renverrait un agent legitime hors
 *    d'une fonctionnalite pourtant ouverte, sur une simple question de timing.
 *
 * Masquer un lien dans la sidebar est un confort ; ce composant est la protection.
 * Il est la seule barriere contre un acces direct par l'adresse, pour le role
 * comme pour le drapeau.
 */
export function ProtectedRoute({ roles, fonctionnalite }: ProtectedRouteProps) {
  const { etat, possedeRole } = useAuth()
  const fonctionnalites = useFonctionnalites()

  if (etat !== 'CONNECTE') {
    return <EcranSession />
  }

  if (roles && !possedeRole(...roles)) {
    return <Navigate to="/acces-interdit" replace />
  }

  if (fonctionnalite === 'rattrapage') {
    if (fonctionnalites.chargement) {
      return <EcranSession />
    }
    if (!fonctionnalites.rattrapageActif) {
      return <Navigate to="/acces-interdit" replace />
    }
  }

  return <Outlet />
}
