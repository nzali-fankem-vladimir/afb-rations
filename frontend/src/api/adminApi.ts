import apiClient from './apiClient'
import type { PageResponse } from '../types/pagination'
import type { RoleEnum } from '../types/enums'

/**
 * Un utilisateur pour la liste d'administration (UtilisateurResponse.java).
 * `subKeycloak` n'est jamais expose : detail d'implementation du fournisseur
 * d'identite, sans interet cote client (CLAUDE.md section 10).
 */
export interface UtilisateurResponse {
  id: number
  login: string
  nom: string
  prenom: string
  email: string
  role: RoleEnum
  codeUnite: string | null
  actif: boolean
  dateDernierAcces: string | null
}

export interface CriteresRechercheUtilisateurs {
  role?: RoleEnum
  codeUnite?: string
  actif?: boolean
  page?: number
  size?: number
}

/**
 * Corps de PUT /identite/utilisateurs/{id}/role (AttributionRoleRequest.java).
 * codeUnite est valide en FORME ici seulement (cinq chiffres) ; sa presence
 * obligatoire pour un role a portee locale est une regle de coherence
 * verifiee cote service (UtilisateurAdminService), pas une contrainte isolee.
 */
export interface AttributionRoleRequest {
  role: RoleEnum
  codeUnite: string | null
  /** Absent : statut inchange. false desactive le profil, true le reactive. */
  actif?: boolean
}

/**
 * Roles a portee locale : code unite obligatoire. Les autres (portee
 * nationale) l'ont facultatif -- regle verifiee dans PorteeAccesService
 * (service Identite), reprise ici pour adapter le formulaire, jamais pour
 * remplacer le controle serveur.
 */
export const ROLES_PORTEE_LOCALE: RoleEnum[] = ['AGENT_UNITE', 'CHEF_UNITE_DA']

/**
 * Liste paginee des profils locaux, reserve a l'ADMIN. Filtres combinables,
 * tous facultatifs. Taille de page bornee a 100 cote serveur quelle que soit
 * la valeur demandee.
 */
export async function listerUtilisateurs(
  criteres: CriteresRechercheUtilisateurs = {},
): Promise<PageResponse<UtilisateurResponse>> {
  const reponse = await apiClient.get<PageResponse<UtilisateurResponse>>('/identite/utilisateurs', {
    params: criteres,
  })
  return reponse.data
}

/**
 * Attribue un role et un code unite a un profil existant, reserve a l'ADMIN.
 * Aucune creation ni suppression de compte : les comptes viennent de
 * l'annuaire, cet endpoint n'attribue que des habilitations (CLAUDE.md
 * section 10). Deux refus 409 sont des regles de controle interne, pas des
 * pannes : AUTO_MODIFICATION_INTERDITE (un administrateur ne peut pas se
 * cibler lui-meme) et DERNIER_ADMINISTRATEUR (le dernier administrateur actif
 * ne peut pas perdre le role ADMIN).
 */
export async function attribuerRole(
  id: number,
  requete: AttributionRoleRequest,
): Promise<UtilisateurResponse> {
  const reponse = await apiClient.put<UtilisateurResponse>(`/identite/utilisateurs/${id}/role`, requete)
  return reponse.data
}
