import Keycloak from 'keycloak-js'

import type { RoleEnum } from '../types/enums'
import type { FournisseurAuthentification, IdentiteJeton } from './FournisseurAuthentification'

const ROLES_CONNUS: readonly RoleEnum[] = [
  'AGENT_UNITE',
  'CHEF_UNITE_DA',
  'DIRECTEUR_RESEAU_DR',
  'ARH',
  'DRH',
  'ADMIN',
]

/** Revendications lues dans le jeton d'acces emis par le realm. */
interface JetonKeycloak {
  sub?: string
  preferred_username?: string
  given_name?: string
  family_name?: string
  email?: string
  realm_access?: { roles?: string[] }
}

/** Marge avant expiration, en secondes, en deca de laquelle le jeton est renouvele. */
const MARGE_RENOUVELLEMENT_SECONDES = 30

/**
 * Implementation Keycloak du fournisseur d'authentification.
 *
 * Flux authorization code avec PKCE en S256 : le client est public, il ne detient
 * aucun secret. Le navigateur est redirige vers Keycloak, qui verifie les
 * identifiants aupres de l'annuaire ; l'application ne voit jamais de mot de passe
 * et n'en stocke aucun (CLAUDE.md section 10).
 *
 * Ce fichier est le seul du frontend a connaitre Keycloak.
 */
export class FournisseurKeycloak implements FournisseurAuthentification {
  private readonly keycloak: Keycloak

  /**
   * Initialisation en cours ou deja terminee.
   *
   * On memorise la promesse, pas un booleen : keycloak-js refuse d'etre
   * initialise deux fois, et React monte les effets deux fois en mode strict.
   * Un drapeau pose apres l'attente laisserait passer le second appel, parti
   * avant que le premier ne soit revenu.
   */
  private initialisation: Promise<boolean> | null = null

  constructor(url: string, realm: string, clientId: string) {
    this.keycloak = new Keycloak({ url, realm, clientId })
  }

  initialiser(): Promise<boolean> {
    this.initialisation ??= this.keycloak.init({
      // check-sso restaure une session existante sans imposer l'ecran de connexion
      // au premier chargement : la redirection reste un acte volontaire.
      onLoad: 'check-sso',
      pkceMethod: 'S256',
      checkLoginIframe: false,
      redirectUri: window.location.origin + window.location.pathname,
    })
    return this.initialisation
  }

  async connecter(): Promise<void> {
    await this.keycloak.login({ redirectUri: window.location.href })
  }

  async deconnecter(): Promise<void> {
    await this.keycloak.logout({ redirectUri: window.location.origin })
  }

  estAuthentifie(): boolean {
    return this.keycloak.authenticated ?? false
  }

  identite(): IdentiteJeton | null {
    const jeton = this.keycloak.tokenParsed as JetonKeycloak | undefined
    if (!jeton?.sub || !jeton.preferred_username) {
      return null
    }
    return {
      subKeycloak: jeton.sub,
      login: jeton.preferred_username,
      prenom: jeton.given_name,
      nom: jeton.family_name,
      email: jeton.email,
      roles: this.rolesConnus(jeton.realm_access?.roles),
    }
  }

  async jetonAcces(): Promise<string | null> {
    if (!this.keycloak.authenticated) {
      return null
    }
    try {
      await this.keycloak.updateToken(MARGE_RENOUVELLEMENT_SECONDES)
      return this.keycloak.token ?? null
    } catch {
      // La session ne peut plus etre prolongee : l'appelant traitera l'absence
      // de jeton comme une deconnexion.
      return null
    }
  }

  /** Un realm partage peut porter des roles d'autres modules : ils sont ignores. */
  private rolesConnus(roles: string[] | undefined): RoleEnum[] {
    if (!roles) {
      return []
    }
    return roles.filter((role): role is RoleEnum => ROLES_CONNUS.includes(role as RoleEnum))
  }
}
