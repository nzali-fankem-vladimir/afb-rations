import type { RoleEnum } from '../types/enums'

/**
 * Identite du porteur du jeton, telle que le fournisseur la restitue.
 *
 * Le role et le code unite ne figurent pas ici : ils sont geres localement par le
 * module et proviennent de GET /identite/moi, pas du fournisseur d'identite
 * (CLAUDE.md section 10).
 */
export interface IdentiteJeton {
  subKeycloak: string
  login: string
  nom?: string
  prenom?: string
  email?: string
  /** Roles portes par le jeton, utiles a l'affichage avant le chargement du profil. */
  roles: RoleEnum[]
}

/**
 * Contrat du fournisseur d'authentification.
 *
 * Toute l'application passe par cette interface. Le remplacement de Keycloak par
 * un autre fournisseur OIDC se limite alors a une nouvelle implementation, sans
 * toucher aux composants ni au client HTTP.
 *
 * Aucune methode ne prend d'identifiant ni de mot de passe : la connexion est une
 * redirection vers le fournisseur, jamais un formulaire de l'application.
 */
export interface FournisseurAuthentification {
  /**
   * Restaure la session si elle existe, sans forcer de redirection.
   * @returns true si une session valide est disponible
   */
  initialiser(): Promise<boolean>

  /** Redirige vers la page de connexion du fournisseur (authorization code + PKCE). */
  connecter(): Promise<void>

  /** Ferme la session chez le fournisseur et revient a l'application. */
  deconnecter(): Promise<void>

  estAuthentifie(): boolean

  /** Identite courante, nulle tant qu'aucune session n'est ouverte. */
  identite(): IdentiteJeton | null

  /**
   * Jeton d'acces courant, rafraichi si necessaire.
   * @returns le jeton, ou null si la session ne peut plus etre prolongee
   */
  jetonAcces(): Promise<string | null>
}
