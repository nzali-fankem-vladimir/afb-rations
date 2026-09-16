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

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    // 401 : jeton absent, expire ou invalide. Le renouvellement a deja echoue en
    // amont, il reste a rouvrir une session chez le fournisseur.
    // 403 est laisse a l'appelant : l'utilisateur est authentifie mais pas habilite,
    // le rediriger vers la connexion ne changerait rien.
    if (error.response?.status === 401) {
      await fournisseurAuth.connecter()
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
