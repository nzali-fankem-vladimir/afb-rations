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

/**
 * Origine d'un etat ERREUR. Les deux pannes n'appellent pas le meme message : un
 * fournisseur d'identite injoignable empeche toute connexion, un service Identite
 * muet empeche seulement de lire le profil d'une session deja ouverte, et un jeton
 * refuse par le backend releve d'une configuration que se reconnecter ne corrige pas.
 */
export type CauseErreurSession = 'FOURNISSEUR_INJOIGNABLE' | 'PROFIL_INDISPONIBLE' | 'JETON_REFUSE'

export interface AuthContexte {
  etat: EtatSession
  /** Renseignee uniquement quand etat vaut ERREUR. */
  causeErreur: CauseErreurSession | null
  utilisateur: ProfilUtilisateur | null
  /** Role du profil applicatif, jamais celui du jeton (CLAUDE.md section 10). */
  role: RoleEnum | null
  /** Unite du profil applicatif, jamais celle de l'annuaire. */
  codeUnite: string | null
  connecter: () => Promise<void>
  deconnecter: () => Promise<void>
  possedeRole: (...roles: RoleEnum[]) => boolean
}

export const AuthContext = createContext<AuthContexte | null>(null)
