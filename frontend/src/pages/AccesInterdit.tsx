import { useNavigate } from 'react-router-dom'
import { ShieldAlert } from 'lucide-react'

import { PageHeader } from '../components/layout/PageHeader'
import { routeAccueil } from '../components/layout/navigation'
import { Button } from '../components/communs/Button'
import { Card, CardContent } from '../components/communs/Card'
import { useAuth } from '../hooks/useAuth'

/** Cible de ProtectedRoute quand le role courant n'a pas acces a la route demandee. */
export function AccesInterdit() {
  const { role } = useAuth()
  const navigate = useNavigate()

  return (
    <>
      <PageHeader surTitre="Autorisation" titre="Accès interdit" />

      <div className="p-8">
        <Card className="max-w-2xl">
          <CardContent className="flex gap-4 p-6">
            <div
              className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary-50"
              aria-hidden
            >
              <ShieldAlert className="h-5 w-5 text-primary-500" aria-hidden />
            </div>

            <div className="flex flex-col items-start gap-4">
              <div className="flex flex-col gap-1.5">
                <p className="font-semibold text-neutral-900">Cette page n'est pas accessible avec votre profil.</p>
                <p className="text-sm text-neutral-600">
                  {role
                    ? `Votre rôle (${role}) ne donne pas accès à cet écran. Si vous pensez qu'il s'agit d'une erreur, contactez l'administrateur du module.`
                    : "Votre profil ne donne pas accès à cet écran. Si vous pensez qu'il s'agit d'une erreur, contactez l'administrateur du module."}
                </p>
              </div>

              <Button type="button" onClick={() => navigate(routeAccueil(role), { replace: true })}>
                Retour à l'accueil
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    </>
  )
}
