import type { ReactNode } from 'react'
import { AlertCircle, Loader2 } from 'lucide-react'

import { Alert, AlertDescription } from '../components/communs/Alert'
import { Button } from '../components/communs/Button'
import { Card, CardContent, CardHeader, CardTitle } from '../components/communs/Card'
import { Logo } from '../components/layout/Logo'
import type { CauseErreurSession } from '../contexts/authContexte'
import { useAuth } from '../hooks/useAuth'

const MESSAGES_ERREUR: Record<CauseErreurSession, string> = {
  FOURNISSEUR_INJOIGNABLE:
    "Le service de connexion de la banque est injoignable. Réessayez dans un instant ou signalez-le au support.",
  PROFIL_INDISPONIBLE:
    "Votre profil n'a pas pu être chargé : le service d'identité du module ne répond pas. Réessayez dans un instant ou signalez-le au support.",
  JETON_REFUSE:
    "Votre connexion n'est pas reconnue par le module. Il s'agit d'un problème de configuration, pas de votre compte : signalez-le au support.",
}

/** Cadre commun des ecrans affiches hors de la coquille applicative (pas de sidebar sans profil). */
function CadreSession({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-neutral-100 px-4">
      <Card className="w-full max-w-md">
        <CardHeader className="items-center text-center">
          <Logo className="mb-2 h-12" />
          <CardTitle>Rations et transport garde armée</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col gap-4">{children}</div>
        </CardContent>
      </Card>
    </div>
  )
}

/**
 * Ecran rendu tant que la session n'est pas CONNECTE.
 *
 * Il n'y a volontairement aucun formulaire : "Se connecter" redirige vers Keycloak,
 * qui verifie les identifiants aupres de l'annuaire. L'application ne voit jamais de
 * mot de passe (CLAUDE.md section 10). La redirection reste un geste volontaire
 * (check-sso, Sprint 0.4, confirme au 7F.3), et l'adresse demandee est conservee.
 */
export function EcranSession() {
  const { etat, causeErreur, connecter, deconnecter } = useAuth()

  if (etat === 'CHARGEMENT') {
    return (
      <CadreSession>
        <p role="status" className="flex items-center justify-center gap-2 text-sm text-neutral-600">
          <Loader2 className="h-4 w-4 animate-spin motion-reduce:animate-none" aria-hidden />
          Ouverture de la session…
        </p>
      </CadreSession>
    )
  }

  if (etat === 'NON_HABILITE') {
    return (
      <CadreSession>
        <Alert variant="warning">
          <AlertCircle className="h-4 w-4" aria-hidden />
          <AlertDescription>
            <p className="font-medium">Aucun profil ouvert dans ce module</p>
            <p>
              Votre compte de la banque est bien reconnu, mais l'administrateur du module ne vous a pas encore
              ouvert d'accès. Rapprochez-vous de lui.
            </p>
          </AlertDescription>
        </Alert>
        <Button type="button" variant="outline" onClick={() => void deconnecter()}>
          Se déconnecter
        </Button>
      </CadreSession>
    )
  }

  if (etat === 'ERREUR') {
    return (
      <CadreSession>
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" aria-hidden />
          <AlertDescription>
            <p className="font-medium">Connexion impossible</p>
            <p>{MESSAGES_ERREUR[causeErreur ?? 'PROFIL_INDISPONIBLE']}</p>
          </AlertDescription>
        </Alert>
        <Button type="button" onClick={() => window.location.reload()}>
          Réessayer
        </Button>
      </CadreSession>
    )
  }

  // DECONNECTE (CONNECTE n'arrive jamais ici : ProtectedRoute rend alors la route).
  return (
    <CadreSession>
      <p className="text-center text-sm text-neutral-600">
        Connectez-vous avec votre compte de la banque pour accéder au module.
      </p>
      <Button type="button" onClick={() => void connecter()}>
        Se connecter
      </Button>
    </CadreSession>
  )
}
