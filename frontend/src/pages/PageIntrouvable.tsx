import { useNavigate } from 'react-router-dom'
import { Compass } from 'lucide-react'

import { PageHeader } from '../components/layout/PageHeader'
import { routeAccueil } from '../components/layout/navigation'
import { Button } from '../components/communs/Button'
import { Card, CardContent } from '../components/communs/Card'
import { useAuth } from '../hooks/useAuth'

/** Route de repli ("*") pour une adresse qui ne correspond a aucune route configuree. */
export function PageIntrouvable() {
  const { role } = useAuth()
  const navigate = useNavigate()

  return (
    <>
      <PageHeader surTitre="Navigation" titre="Page introuvable" />

      <div className="p-8">
        <Card className="max-w-2xl">
          <CardContent className="flex gap-4 p-6">
            <div
              className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-neutral-100"
              aria-hidden
            >
              <Compass className="h-5 w-5 text-neutral-600" aria-hidden />
            </div>

            <div className="flex flex-col items-start gap-4">
              <div className="flex flex-col gap-1.5">
                <p className="font-semibold text-neutral-900">Cette adresse ne correspond à aucun écran du module.</p>
                <p className="text-sm text-neutral-600">
                  Utilisez le menu de gauche pour reprendre votre navigation, ou revenez directement à votre page
                  d'accueil.
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
