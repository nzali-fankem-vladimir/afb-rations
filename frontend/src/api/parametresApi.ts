import { creerClientApi } from './apiClient'

/** Client du service Workflow (port 8084, CLAUDE.md section 11). */
const workflowApiClient = creerClientApi(import.meta.env.VITE_API_WORKFLOW_URL)

/**
 * Les trois seuls codes modifiables par PUT /parametres/{code} (guide 7F.6,
 * etape 6, ajout backend scope : ParametreAdminService.CODES_MODIFIABLES).
 * RATTRAPAGE_ACTIF en est exclu -- il reste gouverne par sa propre doctrine
 * (CLAUDE.md section 7, migration + UPDATE hors module en cas d'urgence).
 */
export const CODES_PARAMETRES_MODIFIABLES = [
  'SEUIL_AIGUILLAGE_DR',
  'DELAI_REGULARISATION_JOURS',
  'COMPTE_CHARGE_RATIONS',
] as const

export type CodeParametreModifiable = (typeof CODES_PARAMETRES_MODIFIABLES)[number]

/** Vue d'un parametre systeme (ParametreResponse.java). */
export interface ParametreResponse {
  code: string
  libelle: string
  valeur: string
  actif: boolean
}

/**
 * Consulte un parametre par son code, reserve a l'ADMIN. Sert a afficher la
 * valeur courante avant modification -- une ecriture a l'aveugle exposerait
 * a un ecrasement non voulu.
 */
export async function consulterParametre(code: string): Promise<ParametreResponse> {
  const reponse = await workflowApiClient.get<ParametreResponse>(`/parametres/${code}`)
  return reponse.data
}

/**
 * Modifie un des trois parametres modifiables, reserve a l'ADMIN. Publie un
 * evenement d'audit portant l'ancienne et la nouvelle valeur (ajout backend
 * du guide 7F.6, etape 6).
 */
export async function modifierParametre(code: string, valeur: string): Promise<ParametreResponse> {
  const reponse = await workflowApiClient.put<ParametreResponse>(`/parametres/${code}`, { valeur })
  return reponse.data
}
