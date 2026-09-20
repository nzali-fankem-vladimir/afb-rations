import apiClient from './apiClient'
import type { PageResponse } from '../types/pagination'
import type { NatureEnum, SessionEnum, StatutGrilleEnum } from '../types/enums'

/**
 * Une grille tarifaire (GrilleResponse.java). `createur` et `validateur` sont
 * des libelles lisibles, deja recopies et figes cote backend au moment de
 * l'acte (Sprint 2.2) -- jamais un identifiant a resoudre ici.
 */
export interface GrilleResponse {
  id: number
  nature: NatureEnum
  session: SessionEnum
  montantFcfa: number
  dateDebut: string
  /** Nul tant que la grille n'a pas ete remplacee. */
  dateFin: string | null
  statutValidation: StatutGrilleEnum
  createur: string | null
  /** Nul avant la decision de la DRH. */
  validateur: string | null
  dateCreation: string
  /** Nul avant la decision de la DRH. */
  dateValidation: string | null
  /** Renseigne uniquement au statut REJETEE (RG-10). */
  motifRejet: string | null
}

/**
 * Corps de POST /grilles (CreationGrilleRequest.java). Ni statut, ni createur,
 * ni dateFin : RG-14 impose EN_ATTENTE_DRH, l'auteur vient du jeton, la borne
 * de fin est posee par la DRH a la validation d'une remplacante (Sprint 2.3).
 */
export interface CreationGrilleRequest {
  nature: NatureEnum
  session: SessionEnum
  montantFcfa: number
  dateDebut: string
}

/** Corps de POST /grilles/{id}/rejet (RejetGrilleRequest.java) : un seul champ, obligatoire. */
export interface RejetGrilleRequest {
  motif: string
}

/**
 * Reponse de POST /grilles/{id}/validation (ValidationGrilleResponse.java).
 * `ancienneFermee` est nul quand la proposition est la premiere grille du
 * couple nature/session -- cas normal, pas une anomalie.
 */
export interface ValidationGrilleResponse {
  grille: GrilleResponse
  ancienneFermee: GrilleResponse | null
}

/**
 * Reponse de GET /grilles/active (MontantApplicableResponse.java). Aucune
 * grille ne couvrant la date : 200 avec `disponible: false` et `montantFcfa`
 * NUL -- jamais zero, jamais un 404 (decision Sprint 2.4).
 */
export interface MontantApplicableResponse {
  disponible: boolean
  nature: NatureEnum
  session: SessionEnum
  date: string
  montantFcfa: number | null
  idGrille: number | null
  dateDebut: string | null
  dateFin: string | null
}

/**
 * Montant applicable a une prestation, A LA DATE DE LA PRESTATION (RG-03) --
 * jamais la date du jour : une saisie retroactive prend l'ancien tarif. Ouvert
 * a tout utilisateur authentifie, sans role exige (Sprint 2.4).
 *
 * Sert uniquement a l'AFFICHAGE du montant dans le formulaire de saisie
 * (Sprint 7F.6, proposition n°2) : le montant n'est jamais envoye au service
 * Saisie, qui le resout et le fige lui-meme a l'enregistrement.
 */
export async function resoudreMontant(
  nature: NatureEnum,
  session: SessionEnum,
  date: string,
): Promise<MontantApplicableResponse> {
  const reponse = await apiClient.get<MontantApplicableResponse>('/grilles/active', {
    params: { nature, session, date },
  })
  return reponse.data
}

export interface CriteresRechercheGrilles {
  statut?: StatutGrilleEnum
  page?: number
  size?: number
}

/** Liste paginee, filtrable par statut. Ouvert a ARH et DRH (RG-14). */
export async function listerGrilles(
  criteres: CriteresRechercheGrilles = {},
): Promise<PageResponse<GrilleResponse>> {
  const reponse = await apiClient.get<PageResponse<GrilleResponse>>('/grilles', {
    params: criteres,
  })
  return reponse.data
}

/**
 * Propose une grille, reserve a l'ARH. Cree ET soumet en un seul appel --
 * aucun brouillon n'est jamais persiste (decision Sprint 2.2). Conflits
 * possibles : 409 GRILLE_EN_ATTENTE_EXISTANTE (une proposition attend deja la
 * DRH sur ce couple) ou 409 GRILLE_ACTIVE_EXISTANTE (date de debut pas
 * strictement posterieure a la grille en vigueur -- anti-datage, PAS "une
 * grille active existe deja" : proposer une date posterieure est le
 * remplacement normal et il est accepte).
 */
export async function proposerGrille(requete: CreationGrilleRequest): Promise<GrilleResponse> {
  const reponse = await apiClient.post<GrilleResponse>('/grilles', requete)
  return reponse.data
}

/**
 * Valide une grille en attente, reserve a la DRH. Ferme atomiquement
 * l'ancienne grille du couple a la veille de la date de debut de la nouvelle
 * (Sprint 2.3) -- une fermeture qui peut donc etre future (fermeture
 * programmee). Effet immediat sur GET /grilles/active des le commit.
 */
export async function validerGrille(id: number): Promise<ValidationGrilleResponse> {
  const reponse = await apiClient.post<ValidationGrilleResponse>(`/grilles/${id}/validation`)
  return reponse.data
}

/**
 * Rejette une grille en attente, reserve a la DRH, motif obligatoire (RG-10).
 * Ne touche jamais la grille active du couple : un rejet dit "ce tarif ne
 * s'appliquera pas", pas "il n'y a plus de tarif".
 */
export async function rejeterGrille(id: number, motif: string): Promise<GrilleResponse> {
  const reponse = await apiClient.post<GrilleResponse>(`/grilles/${id}/rejet`, {
    motif,
  } satisfies RejetGrilleRequest)
  return reponse.data
}

/** Corps de POST /grilles/{id}/retrait (RetraitGrilleRequest.java) : un seul champ, obligatoire. */
export interface RetraitGrilleRequest {
  motif: string
}

/**
 * Retire une proposition en attente, reserve a son AUTEUR (retour utilisateur,
 * demande n°7 de la verification visuelle du Sprint 7F.6), motif obligatoire
 * (RG-10). Passe la grille au statut REJETEE, comme un rejet de la DRH, mais a
 * l'initiative de l'Analyste RH lui-meme, avant toute decision. Un Analyste RH
 * qui tenterait de retirer la proposition d'un collegue recoit
 * 403 GRILLE_NON_PROPRIETAIRE.
 */
export async function retirerGrille(id: number, motif: string): Promise<GrilleResponse> {
  const reponse = await apiClient.post<GrilleResponse>(`/grilles/${id}/retrait`, {
    motif,
  } satisfies RetraitGrilleRequest)
  return reponse.data
}
