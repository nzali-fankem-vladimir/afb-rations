import { createContext } from 'react'

import type { ProfilUtilisateur } from '../api/identiteApi'
import type { RoleEnum } from '../types/enums'

/** Etat de la session, du chargement initial jusqu'a la resolution du profil local. */
export type EtatSession =
  | 'CHARGEMENT'
  | 'DECONNECTE'
  /** Authentifie chez le fournisseur, mais aucun profil ouvert dans le module. */
  | 'NON_HABILITE'
  /** Echec technique : service injoignable ou erreur serveur. */
  | 'ERREUR'
  | 'CONNECTE'

export interface AuthContexte {
  etat: EtatSession
  utilisateur: ProfilUtilisateur | null
  role: RoleEnum | null
  connecter: () => Promise<void>
  deconnecter: () => Promise<void>
  possedeRole: (...roles: RoleEnum[]) => boolean
}

export const AuthContext = createContext<AuthContexte | null>(null)
