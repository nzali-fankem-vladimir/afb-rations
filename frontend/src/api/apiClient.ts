import axios from 'axios'

import { fournisseurAuth } from '../auth'
import type { CodeManqueEnum } from '../types/enums'

export interface ManqueCompletude {
  code: CodeManqueEnum
  message: string
}

export interface ApiErrorResponse {
  timestamp: string
  status: number
  code: string
  message: string
  path: string
  /** Present uniquement sur 422 ETAT_INCOMPLET (service Workflow, Sprint 4.2). */
  manques?: ManqueCompletude[]
}

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

/**
 * Chaque appel part avec le jeton courant, renouvele si necessaire.
 * Aucun jeton n'est conserve ici : le fournisseur en reste le seul detenteur.
 */
apiClient.interceptors.request.use(async (config) => {
  const jeton = await fournisseurAuth.jetonAcces()
  if (jeton) {
    config.headers.Authorization = `Bearer ${jeton}`
  }
  return config
})

/**
 * Une seule redirection vers le fournisseur, quel que soit le nombre d'appels
 * concurrents revenus en 401. Garde en memoire : elle disparait avec la page, ce
 * qui est exactement sa duree de vie utile.
 */
let redirectionEnCours = false

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    // 401 : deux situations sans rapport, qui ne se traitent pas pareil.
    // - Aucun jeton n'a pu etre joint : le renouvellement a echoue, la session est
    //   perdue. Se reconnecter la retablit.
    // - Un jeton frais a ete joint et le backend le refuse (emetteur ou audience
    //   non reconnus) : c'est une configuration. Se reconnecter rendrait le meme
    //   jeton, Keycloak renverrait aussitot vers le module, et la page bouclerait
    //   sans fin et sans message. On laisse remonter l'erreur a la place.
    // 403 est laisse a l'appelant : l'utilisateur est authentifie mais pas habilite,
    // le rediriger vers la connexion ne changerait rien.
    if (error.response?.status === 401) {
      const jetonJoint = Boolean(error.config?.headers?.Authorization)
      if (!jetonJoint) {
        if (!redirectionEnCours) {
          redirectionEnCours = true
          await fournisseurAuth.connecter()
        }
      } else {
        console.error('JETON REFUSE PAR LE BACKEND : verifier emetteur et audience du jeton.')
      }
    }

    const apiError: ApiErrorResponse = error.response?.data ?? {
      timestamp: new Date().toISOString(),
      status: error.response?.status ?? 0,
      code: 'ERREUR_RESEAU',
      message: error.message,
      path: error.config?.url ?? '',
    }
    return Promise.reject(apiError)
  },
)

export default apiClient
