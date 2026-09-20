import apiClient from './apiClient'
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
  const reponse = await apiClient.get<PageResponse<DemandeResponse>>('/reporting/demandes', {
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

/**
 * Reponse de GET /reporting/processus/{id}/historique (HistoriqueResponse.java).
 *
 * `statut` est typé StatutEnum et non `string` : le backend le déclare
 * `String` (comme sur DemandeResponse), mais il n'y écrit jamais qu'une valeur
 * du statut de processus. Le typer au plus juste ici est ce qui permet de le
 * rendre par le badge partagé, sans conversion de complaisance.
 */
export interface HistoriqueResponse {
  idProcessus: number
  dateDebut: string
  dateFin: string
  codeUnite: string
  statut: StatutEnum
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
  const reponse = await apiClient.get<HistoriqueResponse>(
    `/reporting/processus/${idProcessus}/historique`,
  )
  return reponse.data
}

// --- Sprint 7F.7 : rapports et exports ---------------------------------------

/** Une ligne du rapport (RapportResponse.LigneRapportResponse.java). */
export interface LigneRapport {
  idProcessus: number
  codeUnite: string
  typeProcessus: TypeProcessusEnum
  statut: StatutEnum
  montantTotal: number
  envoyeComptabilite: boolean
  situationIntegration: SituationIntegrationEnum
  dateCreation: string
}

/** Cumul d'une unite, present uniquement sur un rapport national (sans codeUnite demande). */
export interface SousTotalUnite {
  codeUnite: string
  nombreEtats: number
  montantTotal: number
}

/**
 * Les totaux generaux (CT-32). Trois montants distincts, a ne jamais fondre :
 * envoye + non envoye + rejete ne resument pas "paye" -- un etat envoye peut
 * etre rejete ensuite par la comptabilite (vocabulaire impose au Sprint 6.2).
 */
export interface SyntheseRapport {
  nombreEtats: number
  montantTotalPeriode: number
  montantEnvoyeComptabilite: number
  montantNonEnvoyeComptabilite: number
  montantRejeteComptabilite: number
  repartitionParStatut: Record<string, number>
  repartitionParSituation: Partial<Record<SituationIntegrationEnum, number>>
}

/**
 * Reponse de GET /reporting/rapports (RapportResponse.java). `vide` a true et
 * une synthese a zero sur une periode sans aucun etat -- jamais une erreur
 * (CT-33). Aucun total n'est recalcule cote frontend : ecran et exports
 * partagent la meme instance de rapport cote serveur (CT-32).
 */
export interface RapportResponse {
  periodeDebut: string
  periodeFin: string
  codeUnite: string | null
  dateGeneration: string
  loginUtilisateur: string
  vide: boolean
  lignes: LigneRapport[]
  sousTotauxParAgence: SousTotalUnite[]
  synthese: SyntheseRapport
}

export interface CriteresRapport {
  dateDebut: string
  dateFin: string
  codeUnite?: string
}

/** Rapport d'activite d'une periode, reserve a l'ARH. dateDebut/dateFin obligatoires. */
export async function produireRapport(criteres: CriteresRapport): Promise<RapportResponse> {
  const reponse = await apiClient.get<RapportResponse>('/reporting/rapports', {
    params: criteres,
  })
  return reponse.data
}

export type FormatExportRapport = 'pdf' | 'excel'

export interface RapportExporte {
  contenu: Blob
  nomFichier: string
}

/** "attachment; filename=\"rapport-rations-00002-20260901.pdf\"" -> le nom seul. */
function nomFichierDepuisEnTete(enTeteDisposition: string | undefined, repli: string): string {
  const correspondance = enTeteDisposition?.match(/filename="?([^"]+)"?/)
  return correspondance?.[1] ?? repli
}

/**
 * Export du meme rapport que produireRapport, en PDF ou Excel -- memes chiffres,
 * seule la mise en forme change (CT-32). Le nom de fichier vient du serveur
 * (Content-Disposition), jamais reconstruit ici.
 *
 * ATTENTION -- `responseType: 'blob'` : une erreur (ex. 400 PERIODE_INVALIDE)
 * arrive elle-meme sous forme de Blob. L'intercepteur de `apiClient.ts` la
 * reconvertit deja (meme mecanisme que le telechargement du document signe,
 * Sprint 7F.6).
 */
export async function exporterRapport(
  criteres: CriteresRapport,
  format: FormatExportRapport,
): Promise<RapportExporte> {
  const reponse = await apiClient.get<Blob>('/reporting/rapports/export', {
    params: { ...criteres, format },
    responseType: 'blob',
  })
  return {
    contenu: reponse.data,
    nomFichier: nomFichierDepuisEnTete(reponse.headers['content-disposition'], `rapport-rations.${format === 'pdf' ? 'pdf' : 'xlsx'}`),
  }
}
