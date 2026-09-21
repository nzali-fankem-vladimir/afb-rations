/**
 * Configuration du frontend, lue AU DEMARRAGE et non plus figee au build
 * (Sprint 8.2, option A).
 *
 * <p>Vite remplace les {@code import.meta.env.VITE_*} par leur valeur au moment
 * du build : une image construite ainsi ne peut servir qu'un seul environnement.
 * En conteneur, le script de demarrage ecrit {@code /config.js} a partir des
 * variables d'environnement du conteneur ; la page le charge avant
 * l'application, et {@code window.__CONFIG__} porte alors les valeurs du
 * deploiement. La meme image sert donc la recette et la production.
 *
 * <p>Ordre de lecture : la configuration d'execution d'abord, les variables de
 * build ensuite. En developpement ({@code npm run dev}), {@code public/config.js}
 * est vide et le fichier {@code .env} fait foi, comme avant.
 */

export type NomVariable = 'API_BASE_URL' | 'KEYCLOAK_URL' | 'KEYCLOAK_REALM' | 'KEYCLOAK_CLIENT_ID'

type ConfigurationExecution = Partial<Record<NomVariable, string>>

declare global {
  interface Window {
    __CONFIG__?: ConfigurationExecution
  }
}

/** Repli de developpement : les variables VITE_ du fichier .env. */
const VALEURS_DE_BUILD: Record<NomVariable, string | undefined> = {
  API_BASE_URL: import.meta.env.VITE_API_BASE_URL,
  KEYCLOAK_URL: import.meta.env.VITE_KEYCLOAK_URL,
  KEYCLOAK_REALM: import.meta.env.VITE_KEYCLOAK_REALM,
  KEYCLOAK_CLIENT_ID: import.meta.env.VITE_KEYCLOAK_CLIENT_ID,
}

/**
 * Valeur d'une variable de configuration, ou une erreur explicite si elle
 * manque : jamais de valeur par defaut.
 *
 * <p>Exigee, et non facultative : sans l'adresse de la passerelle, Axios
 * prendrait l'origine de la page pour base et chaque appel partirait vers le
 * serveur du frontend, qui rendrait une page HTML en 404. L'ecran afficherait
 * une erreur reseau sans rapport avec la cause.
 */
export function variableRequise(nom: NomVariable): string {
  const valeur = window.__CONFIG__?.[nom] || VALEURS_DE_BUILD[nom]
  if (!valeur) {
    throw new Error(
      `Variable de configuration ${nom} absente. En conteneur : la definir dans l'environnement du conteneur. ` +
        `En developpement : renseigner VITE_${nom} dans le fichier .env a partir de .env.example.`,
    )
  }
  return valeur
}
