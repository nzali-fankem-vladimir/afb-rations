import type { RoleEnum, StatutEnum } from '../types/enums'

/**
 * Copie conforme de StatutProcessusEnum.estModifiable() (service Saisie) : seuls
 * EN_COURS_SAISIE et RETOURNE laissent encore ecrire une ligne. Liste fermee et
 * positive -- elle enumere ce qui autorise, pas ce qui interdit -- pour qu'un
 * statut ajoute plus tard au module ne devienne pas modifiable par omission.
 *
 * Le frontend ne fait jamais confiance a cette copie pour REFUSER une ecriture :
 * elle ne sert qu'a desactiver une action avant meme l'appel (guide 7F.4, etape 5,
 * "desactiver une action impossible vaut mieux que la laisser echouer"). Le
 * controle qui compte reste celui du backend, revarifie a chaque ecriture,
 * jamais mis en cache.
 */
const STATUTS_MODIFIABLES = new Set<StatutEnum>(['EN_COURS_SAISIE', 'RETOURNE'])

export function estStatutModifiable(statut: StatutEnum): boolean {
  return STATUTS_MODIFIABLES.has(statut)
}

/**
 * Le statut qui designe "en attente de MON niveau" selon le role du valideur
 * connecte (Sprint 7F.5). Copie du cote client de NiveauValidation.attenduPour
 * (service Workflow) : c'est le STATUT du dossier qui designe le niveau, jamais
 * le role seul (RG-07) -- cette table ne sert qu'a filtrer une liste et a
 * griser des actions avant meme l'appel, jamais a autoriser une ecriture. Le
 * backend revarifie a chaque appel, sans cache, comme pour estStatutModifiable.
 */
const STATUT_ATTENTE_PAR_ROLE: Partial<Record<RoleEnum, StatutEnum>> = {
  CHEF_UNITE_DA: 'EN_ATTENTE_DA',
  DIRECTEUR_RESEAU_DR: 'EN_ATTENTE_DR',
}

/** Vrai si ce role est celui attendu par le statut actuel du dossier. */
export function statutAttendPourRole(statut: StatutEnum, role: RoleEnum | null): boolean {
  return role !== null && STATUT_ATTENTE_PAR_ROLE[role] === statut
}

export { STATUT_ATTENTE_PAR_ROLE }
