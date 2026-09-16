/**
 * Format de pagination de reference du backend (Sprint 1.2, CLAUDE.md section
 * 17), repris a l'identique par toutes les listes paginees (services identite,
 * grilles, reporting, audit). `page` est 0-indexee ; `totalPages` et
 * `dernierePage` sont calcules cote serveur et ne doivent jamais etre
 * redderives cote frontend.
 */
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  dernierePage: boolean
}
