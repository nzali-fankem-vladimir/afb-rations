import axios from 'axios'

export interface ApiErrorResponse {
  timestamp: string
  status: number
  code: string
  message: string
  path: string
}

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
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
