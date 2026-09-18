import type { RoleEnum } from '../types/enums'

/**
 * Libelle humain d'un role, jamais le nom technique de l'enumeration
 * (Sprint 7F.6, onglet "Barre laterale" de la maquette de refonte : le pied
 * de la sidebar doit lire "Agent d'unité", pas "AGENT_UNITE"). Source unique,
 * reprise par la sidebar et par la liste des utilisateurs (admin).
 */
export const LIBELLE_ROLE: Record<RoleEnum, string> = {
  AGENT_UNITE: "Agent d'unité",
  CHEF_UNITE_DA: "Chef d'unité",
  DIRECTEUR_RESEAU_DR: 'Directeur réseau',
  ARH: 'Analyste RH',
  DRH: 'Directrice RH',
  ADMIN: 'Administrateur',
}
