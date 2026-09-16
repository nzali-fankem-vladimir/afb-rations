import { AlertCircle } from 'lucide-react'

import { Alert, AlertDescription } from './Alert'
import { libelleErreur } from '../../utils/messagesErreur'
import type { ApiErrorResponse } from '../../api/apiClient'

export interface AffichageErreurProps {
  erreur: ApiErrorResponse
  className?: string
}

// Aligne sur le format d'erreur uniforme (CLAUDE.md section 11) : le TITRE vient
// de la table de correspondance (jamais le code brut), le detail est toujours le
// `message` du backend -- pour DOUBLON_INTER_ETATS (RG-15) il nomme l'etat en
// conflit, seule information qui permette a l'agent de verifier. `manques`
// (422 ETAT_INCOMPLET) se rend en liste, un point par controle en echec.
export function AffichageErreur({ erreur, className }: AffichageErreurProps) {
  return (
    <Alert variant="destructive" className={className}>
      <AlertCircle className="h-4 w-4" aria-hidden="true" />
      <AlertDescription>
        <p className="font-medium">{libelleErreur(erreur.code)}</p>
        <p>{erreur.message}</p>
        {erreur.manques && erreur.manques.length > 0 && (
          <ul className="mt-2 list-disc pl-5">
            {erreur.manques.map((manque) => (
              <li key={manque.code}>{manque.message}</li>
            ))}
          </ul>
        )}
      </AlertDescription>
    </Alert>
  )
}
