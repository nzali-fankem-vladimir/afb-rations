import { useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'

import { consulterFonctionnalitesActives } from '../api/parametresApi'
import { useAuth } from '../hooks/useAuth'
import { FonctionnalitesContext } from './fonctionnalitesContexte'
import type { FonctionnalitesContexte } from './fonctionnalitesContexte'

/**
 * Valeur retenue quand la lecture du drapeau echoue.
 *
 * VRAI, et ce n'est pas la doctrine du refus conservateur appliquee a l'envers :
 * ce drapeau n'autorise rien. Le controle qui protege contre une regularisation
 * indue est backend, en tete de OuvertureComplementaireService, et il s'applique
 * quoi qu'affiche cette interface (docs/dispositifs_provisoires.md section 1.4 :
 * « le masquage n'est qu'un confort d'usage »).
 *
 * Restait a choisir laquelle des deux erreurs on prefere quand on ne sait pas :
 * afficher un menu qui menera peut-etre a un refus explicite
 * (FONCTIONNALITE_NON_OUVERTE, message clair, traite a l'etape 6), ou masquer
 * une fonctionnalite peut-etre ouverte, sans aucun message. La premiere se voit
 * et s'explique, la seconde est silencieuse -- c'est exactement le defaut que le
 * guide 7F.7 signale a propos d'un drapeau oublie ferme.
 */
const REPLI_SI_LECTURE_IMPOSSIBLE = true

/**
 * Lit les fonctionnalites actives une seule fois par session, APRES la
 * resolution du profil utilisateur (Sprint 7F.3) : l'appel porte le jeton, et
 * partir avant que la session soit CONNECTE le ferait refuser en 401.
 *
 * Place au meme niveau que le contexte d'authentification et sous lui, jamais
 * a cote : il en depend.
 */
export function FonctionnalitesProvider({ children }: { children: ReactNode }) {
  const { etat } = useAuth()
  const [chargement, setChargement] = useState(true)
  const [rattrapageActif, setRattrapageActif] = useState(false)
  const [lectureEchouee, setLectureEchouee] = useState(false)

  useEffect(() => {
    if (etat !== 'CONNECTE') return
    let annule = false
    consulterFonctionnalitesActives()
      .then((reponse) => {
        if (annule) return
        setRattrapageActif(reponse.rattrapageActif)
        setLectureEchouee(false)
      })
      .catch(() => {
        if (annule) return
        setRattrapageActif(REPLI_SI_LECTURE_IMPOSSIBLE)
        setLectureEchouee(true)
      })
      .finally(() => {
        if (!annule) setChargement(false)
      })
    return () => {
      annule = true
    }
  }, [etat])

  const valeur = useMemo<FonctionnalitesContexte>(
    () => ({ chargement: etat === 'CONNECTE' ? chargement : true, rattrapageActif, lectureEchouee }),
    [etat, chargement, rattrapageActif, lectureEchouee],
  )

  return <FonctionnalitesContext.Provider value={valeur}>{children}</FonctionnalitesContext.Provider>
}
