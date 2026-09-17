import { creerClientApi } from './apiClient'
import type { PageResponse } from '../types/pagination'
import type {
  NatureEnum,
  NomEtapeEnum,
  SessionEnum,
  StatutEnum,
  StatutEtapeEnum,
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
  /**
   * Ajoute au Sprint 7F.5 : relaye jusqu'a GET /processus/recherche (service
   * Workflow), qui l'acceptait deja depuis le Sprint 6.1 -- seul le relais
   * manquait cote Reporting (voir docs/decisions/2026-09-16-ecrans-de-validation-
   * hierarchique-et-trois-manques-backend.md). Sert la liste des dossiers en
   * attente d'un niveau de validation precis (EN_ATTENTE_DA, EN_ATTENTE_DR).
   */
  statut?: StatutEnum
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

// --- Sprint 7F.5 : historique, pour l'ecran d'examen -------------------------

/**
 * Une etape de GET /reporting/processus/{id}/historique (EtapeHistoriqueResponse.java).
 *
 * loginActeur et nomActeur sont nuls quand le compte n'a plus de libelle
 * disponible (idActeur reste alors la seule trace, jamais absent) -- l'ecran
 * d'examen doit donc prevoir ce cas, pas seulement l'absence d'acteur.
 */
export interface EtapeHistoriqueResponse {
  ordreEtape: number
  nomEtape: NomEtapeEnum
  statutEtape: StatutEtapeEnum
  idActeur: number | null
  loginActeur: string | null
  nomActeur: string | null
  motifRetour: string | null
  signee: boolean
  dateAction: string
}

/** Reponse de GET /reporting/processus/{id}/historique (HistoriqueResponse.java). */
export interface HistoriqueResponse {
  idProcessus: number
  dateDebut: string
  dateFin: string
  codeUnite: string
  statut: string
  etapes: EtapeHistoriqueResponse[]
}

/**
 * Historique complet d'un dossier, toutes les etapes dans l'ordre du rang --
 * y compris les passages repetes au meme niveau apres un retour et une
 * resoumission (Sprint 4.4). Sert l'ecran d'examen (guide 7F.5, etape 3) pour
 * les signatures deja apposees, et pour la date/l'agent de soumission
 * (manque backend n°2 de l'ouverture de session, arbitre sans ajout backend).
 */
export async function consulterHistorique(idProcessus: number): Promise<HistoriqueResponse> {
  const reponse = await reportingApiClient.get<HistoriqueResponse>(
    `/reporting/processus/${idProcessus}/historique`,
  )
  return reponse.data
}
