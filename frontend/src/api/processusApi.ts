import { creerClientApi } from './apiClient'
import type { NatureEnum, SessionEnum, StatutEnum, TypeProcessusEnum } from '../types/enums'

/** Client du service Workflow (port 8084, CLAUDE.md section 11). */
const workflowApiClient = creerClientApi(import.meta.env.VITE_API_WORKFLOW_URL)

/**
 * Entree de POST /processus (DeclenchementProcessusRequest.java). Les trois
 * derniers champs n'appartiennent qu'a l'etat COMPLEMENTAIRE (regularisation,
 * sprint 7F.7) : ce sous-sprint ne declenche que des etats NORMAL, mais le type
 * les porte tels quels pour rester le reflet exact du DTO backend.
 *
 * Aucune duree n'est imposee entre dateDebut et dateFin (bornes toutes deux
 * incluses) : l'intervalle a ete choisi precisement pour ne pas figer une
 * cadence (point M-04). Le seul controle backend est l'ordre des bornes.
 */
export interface DeclenchementProcessusRequest {
  dateDebut: string
  dateFin: string
  codeUnite: string
  typeProcessus?: TypeProcessusEnum
  idProcessusOrigine?: number
  motifOuverture?: string
}

/**
 * Detail et statut d'un processus (ProcessusResponse.java), corps de
 * POST /processus (201) et GET /processus/{id} (200).
 */
export interface ProcessusResponse {
  idProcessus: number
  statut: StatutEnum
  codeUnite: string
  dateDebut: string
  dateFin: string
  typeProcessus: TypeProcessusEnum
  idProcessusOrigine: number | null
  motifOuverture: string | null
  montantTotal: number
  transmisComptabilite: boolean
  dateCreation: string
  /** Non nul seulement sur un etat au statut RETOURNE (US-11). */
  motifRetour: string | null
  /**
   * Vaut toujours null sur la reponse de POST /processus : ce champ n'est
   * renseigne que par GET /processus/{id} (point de vigilance du guide 7F.4).
   */
  compteCharge: string | null
}

export interface BeneficiaireConsolide {
  id: number
  nom: string
  prenom: string
  numCompteCourant: string
  codeAgence: string
}

export interface LigneConsolidee {
  id: number
  idFicheJournaliere: number
  idBeneficiaire: number
  beneficiaire: BeneficiaireConsolide
  nature: NatureEnum
  session: SessionEnum
  montantApplique: number | null
  idGrille: number | null
  dateCreation: string
}

/** Une journee saisie, avec ses lignes et son sous-total (EtatConsolide.Journee). */
export interface JourneeConsolidee {
  idFicheJournaliere: number
  dateJour: string
  statut: string
  nombreLignes: number
  sousTotalFcfa: number
  lignes: LigneConsolidee[]
}

/**
 * Etat consolide de la periode (EtatProcessusResponse.java, GET /processus/{id}/etat).
 *
 * Deux montants distincts, a ne jamais fondre : montantTotalPorte est la valeur
 * enregistree sur le processus (0 tant que l'etat n'est pas soumis) ;
 * montantTotalFcfa est le total calcule a l'instant par le service Saisie. Ils
 * different legitimement avant soumission.
 */
export interface EtatProcessusResponse {
  idProcessus: number
  statut: StatutEnum
  typeProcessus: TypeProcessusEnum
  codeUnite: string
  dateDebut: string
  dateFin: string
  montantTotalPorte: number
  transmisComptabilite: boolean
  nombreJournees: number | null
  nombreLignes: number | null
  nombreBeneficiaires: number | null
  montantTotalFcfa: number | null
  journees: JourneeConsolidee[]
}

export interface PieceJointeSoumission {
  id: number
  cheminFichier: string
  typeMime: string
  nombreSignatures: number
  dateCreation: string
}

export interface EtapeSoumission {
  id: number
  ordreEtape: number
  nomEtape: string
  statutEtape: string
  signatureNumerique: string
  dateCreation: string
}

/** Reponse de POST /processus/{id}/soumission (SoumissionResponse.java). */
export interface SoumissionResponse {
  idProcessus: number
  statut: string
  codeUnite: string
  dateDebut: string
  dateFin: string
  montantTotalFcfa: number
  pieceJointe: PieceJointeSoumission
  etape: EtapeSoumission
}

/**
 * Declenche un etat NORMAL (409 PROCESSUS_EXISTANT si un etat NORMAL chevauche
 * deja la periode demandee pour cette unite, meme partiellement).
 */
export async function declencherProcessus(
  requete: DeclenchementProcessusRequest,
): Promise<ProcessusResponse> {
  const reponse = await workflowApiClient.post<ProcessusResponse>('/processus', requete)
  return reponse.data
}

export async function consulterProcessus(id: number): Promise<ProcessusResponse> {
  const reponse = await workflowApiClient.get<ProcessusResponse>(`/processus/${id}`)
  return reponse.data
}

/**
 * Etat consolide journee par journee. Un processus sans aucune fiche rend 200
 * avec zero journee et un total de zero -- jamais une erreur.
 */
export async function consulterEtatProcessus(id: number): Promise<EtatProcessusResponse> {
  const reponse = await workflowApiClient.get<EtatProcessusResponse>(`/processus/${id}/etat`)
  return reponse.data
}

/** Aucun corps de requete : tout se deduit du processus et du jeton (US-07). */
export async function soumettreProcessus(id: number): Promise<SoumissionResponse> {
  const reponse = await workflowApiClient.post<SoumissionResponse>(`/processus/${id}/soumission`)
  return reponse.data
}
