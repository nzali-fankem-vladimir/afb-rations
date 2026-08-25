import { FournisseurKeycloak } from './fournisseurKeycloak'
import type { FournisseurAuthentification } from './FournisseurAuthentification'

function variableRequise(nom: string, valeur: string | undefined): string {
  if (!valeur) {
    throw new Error(
      `Variable d'environnement ${nom} absente. Renseigner le fichier .env a partir de .env.example.`,
    )
  }
  return valeur
}

/**
 * Fournisseur d'authentification de l'application.
 *
 * Seul point du frontend ou l'implementation concrete est choisie : les composants
 * et le client HTTP ne connaissent que l'interface.
 */
export const fournisseurAuth: FournisseurAuthentification = new FournisseurKeycloak(
  variableRequise('VITE_KEYCLOAK_URL', import.meta.env.VITE_KEYCLOAK_URL),
  variableRequise('VITE_KEYCLOAK_REALM', import.meta.env.VITE_KEYCLOAK_REALM),
  variableRequise('VITE_KEYCLOAK_CLIENT_ID', import.meta.env.VITE_KEYCLOAK_CLIENT_ID),
)

export type { FournisseurAuthentification, IdentiteJeton } from './FournisseurAuthentification'
