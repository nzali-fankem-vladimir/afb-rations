import type { StatutEnum } from '../types/enums'

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
