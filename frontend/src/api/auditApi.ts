import { creerClientApi } from './apiClient'
import type { PageResponse } from '../types/pagination'

/** Client du service Audit (port 8087, CLAUDE.md section 11). */
const auditApiClient = creerClientApi(import.meta.env.VITE_API_AUDIT_URL)

/**
 * Les six services qui publient sur le topic d'audit (CLAUDE.md section 9.2).
 * Le service Audit lui-meme n'emet rien -- il consomme.
 */
export const SERVICES_EMETTEURS = [
  'service-identite',
  'service-saisie',
  'service-grilles',
  'service-workflow',
  'service-transmission',
  'service-reporting',
] as const

/**
 * Une ligne du journal d'audit (AuditEntreeResponse.java). idUtilisateur et
 * idEntite sont nullables : 21 des 30 points de publication du backend
 * laissent idUtilisateur nul (point A-01, CLAUDE.md section 9.2), dont les
 * ACCES_REFUSE de CT-04 -- les plus importants a conserver.
 */
export interface AuditEntreeResponse {
  id: number
  idUtilisateur: number | null
  serviceEmetteur: string
  action: string
  entiteCible: string
  idEntite: number | null
  dateAction: string
  adresseIp: string | null
  detailJson: string | null
}

/**
 * Criteres de GET /audit/entrees, tous facultatifs et combinables en ET.
 *
 * ATTENTION -- dateDebut et dateFin sont des DATE-HEURES ISO
 * (2026-09-01T00:00:00), pas des dates comme au Reporting (guide 7F.6, etape
 * 7). Envoyer "2026-09-01" rendrait un 400.
 */
export interface CriteresRechercheAudit {
  serviceEmetteur?: string
  action?: string
  entiteCible?: string
  idEntite?: number
  idUtilisateur?: number
  dateDebut?: string
  dateFin?: string
  page?: number
  size?: number
}

/**
 * Recherche filtree et paginee. Le tri est impose par le serveur, sur
 * date_action -- aucun parametre `sort` n'est accepte, l'ordre d'arrivee sur
 * le topic n'etant pas l'ordre des faits (Sprint 6.3).
 */
export async function rechercherAuditEntrees(
  criteres: CriteresRechercheAudit = {},
): Promise<PageResponse<AuditEntreeResponse>> {
  const reponse = await auditApiClient.get<PageResponse<AuditEntreeResponse>>('/audit/entrees', {
    params: criteres,
  })
  return reponse.data
}

/** Journal complet d'un processus, niveau workflow uniquement, trie du premier au dernier evenement. */
export async function consulterAuditProcessus(idProcessus: number): Promise<AuditEntreeResponse[]> {
  const reponse = await auditApiClient.get<AuditEntreeResponse[]>(`/audit/processus/${idProcessus}`)
  return reponse.data
}

/**
 * Les codes d'action REELLEMENT presents dans le journal (GET /audit/actions,
 * rattrapage post-7F.6), jamais une copie figee cote client : alimente la
 * liste deroulante "Type d'action" de AuditPage, qui reprenait jusqu'ici les
 * 30 codes de CLAUDE.md section 9.2 en dur -- rien ne garantissait que cette
 * copie suive le backend si une septieme action apparaissait un jour.
 */
export async function listerActionsDisponibles(): Promise<string[]> {
  const reponse = await auditApiClient.get<string[]>('/audit/actions')
  return reponse.data
}
