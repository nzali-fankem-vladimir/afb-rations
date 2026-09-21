import { variableRequise } from '../config/configurationExecution'
import { FournisseurKeycloak } from './fournisseurKeycloak'
import type { FournisseurAuthentification } from './FournisseurAuthentification'

/**
 * Fournisseur d'authentification de l'application.
 *
 * Seul point du frontend ou l'implementation concrete est choisie : les composants
 * et le client HTTP ne connaissent que l'interface.
 */
export const fournisseurAuth: FournisseurAuthentification = new FournisseurKeycloak(
  variableRequise('KEYCLOAK_URL'),
  variableRequise('KEYCLOAK_REALM'),
  variableRequise('KEYCLOAK_CLIENT_ID'),
)

export type { FournisseurAuthentification, IdentiteJeton } from './FournisseurAuthentification'
