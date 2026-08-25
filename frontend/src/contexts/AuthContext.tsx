import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'

import type { ApiErrorResponse } from '../api/apiClient'
import { chargerProfilCourant } from '../api/identiteApi'
import type { ProfilUtilisateur } from '../api/identiteApi'
import { fournisseurAuth } from '../auth'
import type { RoleEnum } from '../types/enums'
import { AuthContext } from './authContexte'
import type { AuthContexte, EtatSession } from './authContexte'

/**
 * Expose la session courante a l'application.
 *
 * Deux sources distinctes : le fournisseur d'identite dit qui se presente, le
 * module dit ce qu'il a le droit de faire. Le contexte n'est CONNECTE qu'une fois
 * les deux resolus.
 *
 * Aucun champ de mot de passe n'existe ici : la connexion est une redirection.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [etat, setEtat] = useState<EtatSession>('CHARGEMENT')
  const [utilisateur, setUtilisateur] = useState<ProfilUtilisateur | null>(null)

  useEffect(() => {
    let annule = false

    async function ouvrirSession() {
      let authentifie: boolean
      try {
        authentifie = await fournisseurAuth.initialiser()
      } catch (erreur) {
        // Fournisseur injoignable ou mal configure. On retombe sur l'ecran de
        // connexion plutot que de laisser l'application figee sur le chargement.
        console.error("Initialisation de l'authentification en echec.", erreur)
        if (!annule) setEtat('DECONNECTE')
        return
      }
      if (annule) return

      if (!authentifie) {
        setEtat('DECONNECTE')
        return
      }

      try {
        const profil = await chargerProfilCourant()
        if (annule) return
        setUtilisateur(profil)
        setEtat('CONNECTE')
      } catch (erreur) {
        if (annule) return
        setUtilisateur(null)
        // 403 : jeton valide, mais aucun profil ouvert dans le module, ou profil
        // desactive. L'utilisateur reste authentifie chez le fournisseur.
        if ((erreur as ApiErrorResponse)?.status === 403) {
          setEtat('NON_HABILITE')
          return
        }
        // Tout autre echec est technique, pas une question d'habilitation :
        // service injoignable, erreur serveur. On le distingue pour ne pas
        // accuser a tort le profil de l'utilisateur.
        console.error('Chargement du profil courant en echec.', erreur)
        setEtat('ERREUR')
      }
    }

    void ouvrirSession()
    return () => {
      annule = true
    }
  }, [])

  const connecter = useCallback(() => fournisseurAuth.connecter(), [])

  const deconnecter = useCallback(async () => {
    setUtilisateur(null)
    setEtat('DECONNECTE')
    await fournisseurAuth.deconnecter()
  }, [])

  const possedeRole = useCallback(
    (...roles: RoleEnum[]) => utilisateur !== null && roles.includes(utilisateur.role),
    [utilisateur],
  )

  const valeur = useMemo<AuthContexte>(
    () => ({
      etat,
      utilisateur,
      role: utilisateur?.role ?? null,
      connecter,
      deconnecter,
      possedeRole,
    }),
    [etat, utilisateur, connecter, deconnecter, possedeRole],
  )

  return <AuthContext.Provider value={valeur}>{children}</AuthContext.Provider>
}
