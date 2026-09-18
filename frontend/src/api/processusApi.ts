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

// --- Sprint 7F.5 : validation, aiguillage et retour -------------------------

/**
 * Les TROIS valeurs d'aiguillage, plus `null` (guide 7F.5, tableau section "Ce
 * qui a change"). Deduit de DecisionAiguillage.java (service Workflow), pas du
 * seul contrat d'api : celui-ci ne connait pas encore
 * COMPLEMENTAIRE_ENVOI_DIRECTEUR_RESEAU (Sprint 6bis.1), et le contrat est de
 * toute facon ignore par git.
 */
export type AiguillageEnum =
  | 'SOUS_SEUIL_CLOTURE_DIRECTE'
  | 'ENVOI_DIRECTEUR_RESEAU'
  | 'COMPLEMENTAIRE_ENVOI_DIRECTEUR_RESEAU'

/**
 * Le document, apres apposition du visa (ValidationResponse.PieceJointeResponse).
 *
 * ATTENTION -- distinct de PieceJointeSoumission ci-dessus : pas de typeMime,
 * et le dernier champ se nomme dateDerniereModification (pas dateCreation). Les
 * deux DTO Java ne sont pas le meme type, malgre la ressemblance.
 */
export interface PieceJointeValidation {
  id: number
  cheminFichier: string
  nombreSignatures: number
  dateDerniereModification: string
}

/**
 * Ce que la mise a disposition comptable a donne (ValidationResponse.TransmissionResponse).
 * Nul quand la validation ne cloture pas -- un etat aiguille vers le directeur
 * reseau n'a rien a transmettre. `transmis` peut valoir false MEME quand la
 * validation a reussi : la cloture est acquise quand meme (CLAUDE.md 9.1), et
 * aucune reprise automatique n'existe -- c'est le seul endroit ou un humain
 * l'apprend (guide 7F.5, section "Ce qui a change").
 */
export interface TransmissionValidation {
  transmis: boolean
  motif: string | null
  tentatives: number
}

/**
 * Reponse de POST /processus/{id}/validation (ValidationResponse.java).
 *
 * aiguillage et seuilApplique sont NULS au second niveau (validation par le
 * Directeur Reseau) : apres son visa il n'y a plus d'echelon, aucune
 * comparaison n'a lieu. seuilApplique est aussi nul pour un etat COMPLEMENTAIRE
 * au premier niveau -- le seuil n'est meme pas lu (Sprint 6bis.1). Un seuil nul
 * ne veut jamais dire "seuil a zero" : zero est une valeur de seuil acceptee et
 * se distinguerait alors par aiguillage = SOUS_SEUIL_CLOTURE_DIRECTE avec
 * seuilApplique = 0.
 */
export interface ValidationResponse {
  idProcessus: number
  statut: string
  montantTotal: number
  aiguillage: AiguillageEnum | null
  seuilApplique: number | null
  pieceJointe: PieceJointeValidation
  etape: EtapeSoumission
  transmission: TransmissionValidation | null
}

/** Corps de POST /processus/{id}/retour (RetourRequest.java) : le motif, rien d'autre. */
export interface RetourRequest {
  motif: string
}

/** L'etape RETOURNEE qui porte le motif (RetourResponse.EtapeRetourneeResponse). */
export interface EtapeRetourneeResponse {
  id: number
  ordreEtape: number
  nomEtape: string
  statutEtape: string
  motifRetour: string
  dateCreation: string
}

/**
 * Reponse de POST /processus/{id}/retour (RetourResponse.java).
 *
 * niveauOrigine et statut sont rendus cote a cote deliberement : le statut
 * vaut TOUJOURS RETOURNE, que le niveau d'origine soit CHEF_UNITE ou
 * DIRECTEUR_RESEAU (RG-11) -- jamais un statut intermediaire.
 *
 * ATTENTION -- niveauOrigine vient de NiveauValidation.java (domaine), dont
 * les constantes sont CHEF_UNITE et DIRECTEUR_RESEAU : PAS les memes libelles
 * que RoleEnum (CHEF_UNITE_DA, DIRECTEUR_RESEAU_DR).
 */
export interface RetourResponse {
  idProcessus: number
  statut: string
  niveauOrigine: 'CHEF_UNITE' | 'DIRECTEUR_RESEAU'
  etape: EtapeRetourneeResponse
}

/**
 * Valide un etat au niveau ou il se trouve (aucun corps de requete : le montant
 * vient du processus, le seuil de parametre_systeme). Reserve a CHEF_UNITE_DA
 * et DIRECTEUR_RESEAU_DR ; c'est le STATUT du dossier qui designe le niveau
 * traite, pas le role de l'appelant (RG-07).
 */
export async function validerProcessus(id: number): Promise<ValidationResponse> {
  const reponse = await workflowApiClient.post<ValidationResponse>(`/processus/${id}/validation`)
  return reponse.data
}

/**
 * Retourne un etat a l'agent d'unite, motif obligatoire (RG-10). RG-11 : le
 * statut cible est toujours RETOURNE, quel que soit le niveau d'origine.
 */
export async function retournerProcessus(id: number, motif: string): Promise<RetourResponse> {
  const reponse = await workflowApiClient.post<RetourResponse>(`/processus/${id}/retour`, {
    motif,
  } satisfies RetourRequest)
  return reponse.data
}

// --- Sprint 7F.8 : telechargement du document signe -------------------------

/** Le contenu binaire du document, et le nom de fichier annonce par le serveur. */
export interface DocumentTelecharge {
  contenu: Blob
  nomFichier: string
}

/** "attachment; filename=\"etat-rations-00002-20260907-p740.pdf\"" -> le nom seul. */
function nomFichierDepuisEnTete(enTeteDisposition: string | undefined, repli: string): string {
  const correspondance = enTeteDisposition?.match(/filename="?([^"]+)"?/)
  return correspondance?.[1] ?? repli
}

/**
 * Telecharge le document PDF signe d'un etat (guide 7F.8, point ferme au
 * Sprint 7F.5 -- docs/points-en-attente.md, section « PDF signe »).
 *
 * ATTENTION -- `responseType: 'blob'` : une erreur du serveur (404, 403...)
 * arrive alors elle-meme sous forme de Blob, jamais de JSON deja decode.
 * `creerClientApi` la reconvertit dans son intercepteur de reponse avant de la
 * rendre a l'appelant : AffichageErreur ne voit donc jamais qu'un
 * ApiErrorResponse ordinaire, quel que soit le type de requete qui a echoue.
 */
export async function telechargerDocument(id: number): Promise<DocumentTelecharge> {
  const reponse = await workflowApiClient.get<Blob>(`/processus/${id}/document`, {
    responseType: 'blob',
  })
  return {
    contenu: reponse.data,
    nomFichier: nomFichierDepuisEnTete(reponse.headers['content-disposition'], `etat-${id}.pdf`),
  }
}
