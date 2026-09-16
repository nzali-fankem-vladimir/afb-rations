import { useEffect, useRef } from 'react'
import type { RefObject } from 'react'

const SELECTEUR_FOCUSABLE =
  'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])'

// Piege de focus pour une modale construite a la main : focus initial dans la
// modale, Tab/Shift+Tab boucles a l'interieur, Echap declenche `onClose`, et le
// focus revient au declencheur d'origine a la fermeture. Passer `undefined`
// comme `onClose` desactive la fermeture au clavier (ex. soumission en cours)
// sans desactiver le piege de focus.
export function useFocusTrap(onClose: (() => void) | undefined): RefObject<HTMLDivElement | null> {
  const containerRef = useRef<HTMLDivElement>(null)
  // `onClose` est souvent recalcule a chaque rendu par l'appelant. En dependance
  // directe de l'effet, il le ferait se demonter puis se remonter a chaque
  // basculement d'etat, et le focus retomberait sur le premier element de la
  // modale a un mauvais moment. La reference garde la derniere fonction sans
  // reveiller l'effet, qui ne depend plus que du montage.
  const onCloseRef = useRef(onClose)
  onCloseRef.current = onClose

  useEffect(() => {
    const declencheur = document.activeElement as HTMLElement | null
    const container = containerRef.current
    const focusables = container?.querySelectorAll<HTMLElement>(SELECTEUR_FOCUSABLE)
    ;(focusables?.[0] ?? container)?.focus()

    const gererClavier = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onCloseRef.current?.()
        return
      }
      if (event.key !== 'Tab' || !container) return
      const elements = Array.from(container.querySelectorAll<HTMLElement>(SELECTEUR_FOCUSABLE))
      if (elements.length === 0) return
      const premier = elements[0]
      const dernier = elements[elements.length - 1]
      if (event.shiftKey && document.activeElement === premier) {
        event.preventDefault()
        dernier.focus()
      } else if (!event.shiftKey && document.activeElement === dernier) {
        event.preventDefault()
        premier.focus()
      }
    }

    document.addEventListener('keydown', gererClavier)
    return () => {
      document.removeEventListener('keydown', gererClavier)
      declencheur?.focus?.()
    }
    // Volontairement vide : le piege s'installe au montage de la modale et se
    // defait a sa fermeture, jamais entre les deux. Voir onCloseRef ci-dessus.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return containerRef
}
