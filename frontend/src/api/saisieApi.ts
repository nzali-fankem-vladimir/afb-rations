import { creerClientApi } from './apiClient'
import type { NatureEnum, SessionEnum, StatutFicheEnum } from '../types/enums'

/** Client du service Saisie (port 8082, CLAUDE.md section 11). */
const saisieApiClient = creerClientApi(import.meta.env.VITE_API_SAISIE_URL)

/**
 * Entree de POST /saisie/fiches (OuvertureFicheRequest.java). Aucun codeUnite :
 * il vient de la reponse de GET /processus/{id}, jamais du client (le laisser
 * saisir reviendrait a lui demander sur quelle unite il a le droit d'ecrire).
 */
export interface OuvertureFicheRequest {
  idProcessus: number
  dateJour: string
}

/**
 * Identite du beneficiaire telle que l'agent la saisit (IdentiteBeneficiaireRequest.java).
 * Aucun referentiel : un beneficiaire est cree a la premiere saisie qui le concerne.
 */
export interface IdentiteBeneficiaireRequest {
  nom: string
  prenom: string
  numCompteCourant: string
  codeAgence: string
}

/**
 * Entree de POST /saisie/lignes (CreationLigneRequest.java). Aucun champ montant :
 * RG-03 l'interdit structurellement, il n'existe nulle part ou le mettre.
 */
export interface CreationLigneRequest {
  idFicheJournaliere: number
  beneficiaire: IdentiteBeneficiaireRequest
  nature: NatureEnum
  session: SessionEnum
}

/**
 * Entree de PUT /saisie/lignes/{id} : nature et session, plus, facultativement,
 * l'identite du beneficiaire. Une modification se fait sur place, en une seule
 * transaction : compte inchange, nom/prenom/agence corrigent la fiche du
 * beneficiaire ; compte different, la ligne est rattachee au beneficiaire de ce
 * compte (cree s'il n'existe pas).
 */
export interface ModificationLigneRequest {
  nature: NatureEnum
  session: SessionEnum
  nom?: string
  prenom?: string
  numCompteCourant?: string
  codeAgence?: string
}

export interface BeneficiaireResume {
  id: number
  nom: string
  prenom: string
  numCompteCourant: string
  codeAgence: string
}

/** Vue de sortie d'une ligne de prestation (LigneResponse.java). */
export interface LigneResponse {
  id: number
  idFicheJournaliere: number
  idBeneficiaire: number
  beneficiaire: BeneficiaireResume
  nature: NatureEnum
  session: SessionEnum
  /** Resolu par la grille active (RG-03), jamais saisi. */
  montantApplique: number | null
  idGrille: number | null
  dateCreation: string
}

/**
 * Vue de sortie d'une fiche journaliere, avec ses lignes et son sous-total
 * (FicheResponse.java). codeUnite/dateDebut/dateFin sont recopies et figes du
 * processus a l'ouverture de la fiche (migration V3/V5, cote backend).
 */
export interface FicheResponse {
  id: number
  idProcessus: number
  dateJour: string
  statut: StatutFicheEnum
  codeUnite: string
  dateDebut: string
  dateFin: string
  lignes: LigneResponse[]
  nombreLignes: number
  sousTotalFcfa: number
  dateCreation: string
}

/**
 * Ouvre -- ou recupere -- la fiche d'un jour (RG-05). Idempotent et non
 * destructif : rouvrir un jour deja saisi renvoie la meme fiche, ses lignes
 * intactes. `creee` distingue les deux issues (le backend rend 201 a la
 * creation, 200 a la recuperation) sans jamais obliger l'ecran a vider son
 * contenu pour le savoir.
 */
export async function ouvrirFiche(
  requete: OuvertureFicheRequest,
): Promise<{ fiche: FicheResponse; creee: boolean }> {
  const reponse = await saisieApiClient.post<FicheResponse>('/saisie/fiches', requete)
  return { fiche: reponse.data, creee: reponse.status === 201 }
}

/** Lignes d'une fiche, beneficiaire et sous-total inclus. */
export async function listerLignesFiche(idFiche: number): Promise<FicheResponse> {
  const reponse = await saisieApiClient.get<FicheResponse>(`/saisie/fiches/${idFiche}/lignes`)
  return reponse.data
}

export async function creerLigne(requete: CreationLigneRequest): Promise<LigneResponse> {
  const reponse = await saisieApiClient.post<LigneResponse>('/saisie/lignes', requete)
  return reponse.data
}

/** Traitee comme une creation cote backend : RG-03, RG-04 et RG-15 rejouent integralement. */
export async function modifierLigne(
  idLigne: number,
  requete: ModificationLigneRequest,
): Promise<LigneResponse> {
  const reponse = await saisieApiClient.put<LigneResponse>(`/saisie/lignes/${idLigne}`, requete)
  return reponse.data
}

/** 204 sans corps. Une seconde suppression de la meme ligne rend 404 (non idempotent). */
export async function supprimerLigne(idLigne: number): Promise<void> {
  await saisieApiClient.delete(`/saisie/lignes/${idLigne}`)
}
