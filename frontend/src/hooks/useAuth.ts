import { useContext } from 'react'

import { AuthContext } from '../contexts/authContexte'
import type { AuthContexte } from '../contexts/authContexte'

/** Session courante. A n'utiliser que sous un AuthProvider. */
export function useAuth(): AuthContexte {
  const contexte = useContext(AuthContext)
  if (contexte === null) {
    throw new Error("useAuth doit etre utilise a l'interieur d'un AuthProvider.")
  }
  return contexte
}
