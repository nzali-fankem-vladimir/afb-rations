import axios from 'axios'
import type { AxiosInstance } from 'axios'

import { fournisseurAuth } from '../auth'
import { variableRequise } from '../config/configurationExecution'
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

/**
 * Une seule redirection vers le fournisseur, quel que soit le nombre d'appels
 * concurrents revenus en 401 -- et quel que soit le client (donc le service
 * backend) qui l'a declenchee. Garde en memoire, partagee par tous les clients
 * crees par creerClientApi : elle disparait avec la page, ce qui est exactement
 * sa duree de vie utile.
 */
let redirectionEnCours = false

/**
 * Cree le client Axios de l'application : jeton joint automatiquement,
 * traitement uniforme du 401 et mise en forme du format d'erreur
 * (CLAUDE.md section 11).
 *
 * <p><b>Fonction privee depuis le Sprint 8.1.</b> Elle etait exportee tant que
 * chaque service etait appele sur son propre port (arbitrage Sprint 7F.4, six
 * clients, six variables d'environnement). La passerelle etant desormais le
 * point d'entree unique, il n'existe plus qu'un seul client, et ne plus exporter
 * la fabrique fait qu'un appel a une adresse de service ne compile plus -- au
 * lieu de rester un oubli silencieux que rien ne signalerait.
 */
function creerClientApi(baseURL: string): AxiosInstance {
  const client = axios.create({
    baseURL,
    headers: {
      'Content-Type': 'application/json',
    },
  })

  /**
   * Chaque appel part avec le jeton courant, renouvele si necessaire.
   * Aucun jeton n'est conserve ici : le fournisseur en reste le seul detenteur.
   */
  client.interceptors.request.use(async (config) => {
    const jeton = await fournisseurAuth.jetonAcces()
    if (jeton) {
      config.headers.Authorization = `Bearer ${jeton}`
    }
    return config
  })

  client.interceptors.response.use(
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

      // Un appel en responseType "blob" (telechargement d'un document, Sprint
      // 7F.8) recoit aussi ses erreurs sous forme de Blob : Axios ne redecodes
      // jamais selon le code de statut. Sans cette conversion, AffichageErreur
      // recevrait un Blob a la place du format d'erreur uniforme.
      let donnees = error.response?.data
      if (donnees instanceof Blob && donnees.type.includes('json')) {
        try {
          donnees = JSON.parse(await donnees.text())
        } catch {
          donnees = undefined
        }
      }

      const apiError: ApiErrorResponse = donnees ?? {
        timestamp: new Date().toISOString(),
        status: error.response?.status ?? 0,
        code: 'ERREUR_RESEAU',
        message: error.message,
        path: error.config?.url ?? '',
      }
      return Promise.reject(apiError)
    },
  )

  return client
}

/**
 * Client unique de l'application : toutes les requetes passent par la
 * passerelle, seule adresse que le frontend connaisse (Sprint 8.1). Aucune
 * adresse de service individuel ne subsiste ici -- un appel direct
 * fonctionnerait en developpement et echouerait en production, ou seule la
 * passerelle est exposee.
 *
 * <p>L'adresse (prefixe /api compris, CLAUDE.md section 11) vient de la
 * configuration d'execution et est EXIGEE : voir {@code variableRequise}, qui
 * refuse plutot que de laisser Axios prendre l'origine de la page pour base.
 */
const apiClient = creerClientApi(variableRequise('API_BASE_URL'))

export default apiClient
