/**
 * Controles de forme de l'identite d'un beneficiaire, AVANT envoi (Sprint 7F.6,
 * proposition n°3). Ils anticipent le refus du serveur pour que l'agent voie
 * sa faute sous le champ concerne ; ils ne le remplacent pas -- le service
 * Saisie revalide tout (`IdentiteBeneficiaireRequest.java`).
 *
 * Memes regles que le backend, et aucune de plus :
 * - numero de compte courant : exactement 11 chiffres (point T-02) ;
 * - code agence : exactement 5 chiffres (contrat d'API §1.1) -- seul le FORMAT
 *   est verifiable, le referentiel des codes guichets vit hors du module ;
 * - nom et prenom : non vides.
 */

export const LONGUEUR_COMPTE_COURANT = 11
export const LONGUEUR_CODE_AGENCE = 5

export interface IdentiteBeneficiaireSaisie {
  nom: string
  prenom: string
  numCompteCourant: string
  codeAgence: string
}

export type ErreursBeneficiaire = Partial<Record<keyof IdentiteBeneficiaireSaisie, string>>

/**
 * Retire les espaces d'un numero saisi par blocs (« 037 020 999 11 ») : un
 * espace ne fait jamais partie d'un numero. Tout autre caractere est conserve,
 * pour etre signale plutot que corrige en silence.
 */
export function sansEspaces(valeur: string): string {
  return valeur.replace(/\s+/g, '')
}

function controlerNumero(valeur: string, longueur: number): string | undefined {
  if (valeur === '') return 'Obligatoire'
  if (!/^[0-9]+$/.test(valeur)) return 'Chiffres uniquement, sans lettre ni séparateur'
  if (valeur.length !== longueur) {
    return `${longueur} chiffres attendus (vous en avez saisi ${valeur.length})`
  }
  return undefined
}

export function validerBeneficiaire(identite: IdentiteBeneficiaireSaisie): ErreursBeneficiaire {
  const erreurs: ErreursBeneficiaire = {}
  if (identite.nom.trim() === '') erreurs.nom = 'Obligatoire'
  if (identite.prenom.trim() === '') erreurs.prenom = 'Obligatoire'
  const compte = controlerNumero(identite.numCompteCourant, LONGUEUR_COMPTE_COURANT)
  if (compte) erreurs.numCompteCourant = compte
  const agence = controlerNumero(identite.codeAgence, LONGUEUR_CODE_AGENCE)
  if (agence) erreurs.codeAgence = agence
  return erreurs
}
