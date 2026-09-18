import { useCallback, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { CheckCircle2, X } from 'lucide-react'

import { ToastContext } from './toastContexte'

interface ToastAffiche {
  id: number
  titre: string
  detail?: string
}

const DUREE_AFFICHAGE_MS = 4000

/**
 * Fournit les notifications passagères de succès à toute l'application
 * (rattrapage post-7F.6). Montée une seule fois, dans `main.tsx`, au même
 * niveau que `AuthProvider`.
 *
 * Empilées en haut à droite, chacune se retire d'elle-même après quatre
 * secondes (`docs`/maquette de refonte du Sprint 7F.6) ou sur un clic de
 * fermeture -- une notification qui ne peut pas être écartée avant son délai
 * ne respecterait pas WCAG 2.2.1 (Timing Adjustable).
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastAffiche[]>([])
  const prochainId = useRef(0)

  const retirer = useCallback((id: number) => {
    setToasts((precedent) => precedent.filter((toast) => toast.id !== id))
  }, [])

  const succes = useCallback(
    (titre: string, detail?: string) => {
      const id = prochainId.current++
      setToasts((precedent) => [...precedent, { id, titre, detail }])
      window.setTimeout(() => retirer(id), DUREE_AFFICHAGE_MS)
    },
    [retirer],
  )

  return (
    <ToastContext.Provider value={{ succes }}>
      {children}
      <div
        role="status"
        aria-live="polite"
        className="pointer-events-none fixed right-4 top-4 z-[60] flex w-full max-w-sm flex-col items-end gap-2"
      >
        {toasts.map((toast) => (
          <div
            key={toast.id}
            className="animation-toast pointer-events-auto flex w-full items-start gap-2 rounded-lg border border-neutral-200 border-l-4 border-l-emerald-600 bg-white p-3 shadow-lg"
          >
            <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" aria-hidden="true" />
            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold text-neutral-900">{toast.titre}</p>
              {toast.detail && <p className="truncate text-xs text-neutral-600">{toast.detail}</p>}
            </div>
            <button
              type="button"
              onClick={() => retirer(toast.id)}
              aria-label="Fermer la notification"
              className="shrink-0 rounded p-0.5 text-neutral-400 hover:bg-neutral-100 hover:text-neutral-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
            >
              <X className="h-3.5 w-3.5" aria-hidden="true" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}
