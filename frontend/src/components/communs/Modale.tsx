import { useId, useState } from 'react'
import type { ReactNode } from 'react'

import { Card, CardContent, CardHeader, CardTitle, CardFooter } from './Card'
import { Button } from './Button'
import type { ButtonProps } from './Button'
import { useFocusTrap } from '../../hooks/useFocusTrap'

export interface ModaleProps {
  titre: string
  message?: string
  /** Alternative a `message` pour un contenu qui ne tient pas dans une phrase (plusieurs blocs). */
  contenu?: ReactNode
  libelleConfirmer?: string
  variantConfirmer?: ButtonProps['variant']
  largeur?: string
  /** Absent : la modale est purement informative, seul le bouton "Fermer" est propose. */
  onConfirmer?: () => void | Promise<void>
  onAnnuler?: () => void
  /**
   * Desactive le bouton de confirmation independamment de `enCours` (Sprint 7F.5) --
   * ex. un motif obligatoire vide ou compose uniquement d'espaces (RG-10). Le
   * controle reste redit cote serveur, cette desactivation n'evite qu'un aller-retour
   * pour rien.
   */
  confirmerDesactive?: boolean
}

export function Modale({
  titre,
  message,
  contenu,
  libelleConfirmer = 'Confirmer',
  variantConfirmer = 'destructive',
  largeur = 'max-w-sm',
  onConfirmer,
  onAnnuler,
  confirmerDesactive = false,
}: ModaleProps) {
  const [enCours, setEnCours] = useState(false)
  const titreId = useId()
  const containerRef = useFocusTrap(enCours ? undefined : onAnnuler)

  const confirmer = async () => {
    setEnCours(true)
    try {
      await onConfirmer?.()
    } finally {
      setEnCours(false)
    }
  }

  return (
    <div
      ref={containerRef}
      role="dialog"
      aria-modal="true"
      aria-labelledby={titreId}
      tabIndex={-1}
      className="fixed inset-0 z-50 flex items-center justify-center overscroll-contain bg-black/40 p-4"
      onClick={(event) => {
        if (event.target === event.currentTarget && !enCours) onAnnuler?.()
      }}
    >
      <Card className={`max-h-[calc(100vh-2rem)] w-full overflow-y-auto ${largeur}`}>
        <CardHeader>
          <CardTitle id={titreId}>{titre}</CardTitle>
        </CardHeader>
        <CardContent>{contenu ?? <p className="text-sm text-neutral-700">{message}</p>}</CardContent>
        <CardFooter className="gap-3">
          <Button variant="outline" onClick={onAnnuler} disabled={enCours}>
            {onConfirmer ? 'Annuler' : 'Fermer'}
          </Button>
          {onConfirmer && (
            <Button variant={variantConfirmer} onClick={confirmer} disabled={enCours || confirmerDesactive}>
              {enCours ? 'Veuillez patienter…' : libelleConfirmer}
            </Button>
          )}
        </CardFooter>
      </Card>
    </div>
  )
}
