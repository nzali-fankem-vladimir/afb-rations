import type { RoleEnum } from '../types/enums'
import apiClient from './apiClient'

/** Profil retourne par GET /identite/moi. */
export interface ProfilUtilisateur {
  id: number
  login: string
  nom: string
  prenom: string
  role: RoleEnum
  codeUnite: string
}

/**
 * Profil local de l'utilisateur courant.
 *
 * Le role et le code unite viennent d'ici, pas du jeton : ce sont des donnees du
 * module, decidees par l'administrateur (CLAUDE.md section 10).
 */
export async function chargerProfilCourant(): Promise<ProfilUtilisateur> {
  const reponse = await apiClient.get<ProfilUtilisateur>('/identite/moi')
  return reponse.data
}
