import { createContext } from 'react'

/**
 * Notifications passagères, pour les succès (rattrapage post-7F.6, l'un des
 * sept changements transversaux annoncés dans la maquette de refonte du
 * Sprint 7F.6 : "au lieu d'un bandeau qui pousse le contenu vers le bas").
 *
 * Un seul type pour l'instant : le succès. Les échecs restent affichés en
 * bannière fixe (`AffichageErreur`) -- ce qui doit être lu absolument (un
 * refus, une erreur bloquante) ne doit jamais disparaître tout seul après
 * quatre secondes.
 */
export interface ToastContexte {
  /** @param detail Ligne secondaire, plus discrète (ex. "MBARGA Jean · Ration jour · 1 500 FCFA"). */
  succes: (titre: string, detail?: string) => void
}

export const ToastContext = createContext<ToastContexte | null>(null)
