import { creerClientApi } from './apiClient'
import type { PageResponse } from '../types/pagination'
import type {
  NatureEnum,
  SessionEnum,
  StatutEnum,
  StatutIntegrationEnum,
  TypeProcessusEnum,
} from '../types/enums'

/** Client du service Reporting (port 8085, CLAUDE.md section 11). */
const reportingApiClient = creerClientApi(import.meta.env.VITE_API_REPORTING_URL)

/**
 * Copie conforme de SituationIntegration.java (service Reporting) : statutIntegration
 * seul est nul dans deux situations sans rapport (jamais transmis, publication non
 * confirmee) ; cette valeur les distingue explicitement sur une liste de suivi.
 */
export type SituationIntegrationEnum =
  | 'NON_TRANSMIS'
  | 'PUBLICATION_NON_CONFIRMEE'
  | 'EN_ATTENTE_ACCUSE'
  | 'INTEGRE'
  | 'REJETE'

/** Une ligne de GET /reporting/demandes (DemandeResponse.java). */
export interface DemandeResponse {
  idProcessus: number
  dateDebut: string
  dateFin: string
  codeUnite: string
  typeProcessus: TypeProcessusEnum
  montantTotal: number
  statut: StatutEnum
  transmisComptabilite: boolean
  statutIntegration: StatutIntegrationEnum | null
  situationIntegration: SituationIntegrationEnum
  dateCreation: string
}

export interface CriteresRechercheDemandes {
  dateDebut?: string
  dateFin?: string
  codeUnite?: string
  session?: SessionEnum
  nature?: NatureEnum
  beneficiaire?: string
  page?: number
  size?: number
}

/**
 * Recherche multicritere paginee (contrat section 6), ouverte a AGENT_UNITE en
 * plus du circuit et de l'ARH. Sert l'ecran de liste des processus de l'agent
 * (guide 7F.4, etape 2). Filtres optionnels, tous combinables ; une recherche
 * sans resultat rend 200 avec une page vide.
 */
export async function rechercherDemandes(
  criteres: CriteresRechercheDemandes = {},
): Promise<PageResponse<DemandeResponse>> {
  const reponse = await reportingApiClient.get<PageResponse<DemandeResponse>>('/reporting/demandes', {
    params: criteres,
  })
  return reponse.data
}
