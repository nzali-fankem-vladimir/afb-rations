import { useContext } from 'react'

import { ToastContext } from '../contexts/toastContexte'
import type { ToastContexte } from '../contexts/toastContexte'

/** Notifications passagères de succès. A n'utiliser que sous un ToastProvider. */
export function useToast(): ToastContexte {
  const contexte = useContext(ToastContext)
  if (contexte === null) {
    throw new Error("useToast doit etre utilise a l'interieur d'un ToastProvider.")
  }
  return contexte
}
