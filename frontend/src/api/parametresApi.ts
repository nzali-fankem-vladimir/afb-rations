import apiClient from './apiClient'

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
 * Corps de GET /parametres/fonctionnalites (FonctionnalitesActivesResponse.java,
 * Sprint 6bis.1, docs/dispositifs_provisoires.md section 1.4).
 *
 * Un objet, un champ par fonctionnalite pilotee par drapeau -- jamais un booleen
 * nu : ajouter un second drapeau changerait alors le TYPE de la reponse, donc
 * casserait ce client, la ou un champ supplementaire est simplement ignore.
 */
export interface FonctionnalitesActivesResponse {
  rattrapageActif: boolean
}

/**
 * Les fonctionnalites que l'interface a le droit d'afficher. Ouvert a tout
 * utilisateur authentifie, appele une seule fois au chargement de l'application
 * (FonctionnalitesProvider), apres la resolution du profil du Sprint 7F.3.
 *
 * Ce que cette reponse ne fait PAS : autoriser quoi que ce soit. Elle sert a
 * masquer une entree de menu ; le controle qui compte est celui du backend, en
 * tete de OuvertureComplementaireService.
 */
export async function consulterFonctionnalitesActives(): Promise<FonctionnalitesActivesResponse> {
  const reponse = await apiClient.get<FonctionnalitesActivesResponse>('/parametres/fonctionnalites')
  return reponse.data
}

/**
 * Consulte un parametre par son code, reserve a l'ADMIN. Sert a afficher la
 * valeur courante avant modification -- une ecriture a l'aveugle exposerait
 * a un ecrasement non voulu.
 */
export async function consulterParametre(code: string): Promise<ParametreResponse> {
  const reponse = await apiClient.get<ParametreResponse>(`/parametres/${code}`)
  return reponse.data
}

/**
 * Modifie un des trois parametres modifiables, reserve a l'ADMIN. Publie un
 * evenement d'audit portant l'ancienne et la nouvelle valeur (ajout backend
 * du guide 7F.6, etape 6).
 */
export async function modifierParametre(code: string, valeur: string): Promise<ParametreResponse> {
  const reponse = await apiClient.put<ParametreResponse>(`/parametres/${code}`, { valeur })
  return reponse.data
}
