/**
 * Table de correspondance entre les codes d'erreur du backend (CLAUDE.md
 * section 11, format uniforme `{ timestamp, status, code, message, path }`) et
 * un libelle humain en francais. L'utilisateur ne doit jamais voir un code
 * technique brut.
 *
 * Le libelle est un TITRE court ; le detail exact (ex. le nom de l'etat en
 * conflit pour DOUBLON_INTER_ETATS) reste toujours affiche a cote, tel que
 * rendu par le backend dans `message` -- jamais remplace. Table volontairement
 * ouverte : chaque sous-sprint y ajoute les codes qu'il introduit.
 *
 * ATTENTION -- DOUBLON_LIGNE et DOUBLON_INTER_ETATS se ressemblent mais
 * appellent deux gestes differents (RG-04 contre RG-15) : ne jamais les
 * fusionner sous un meme libelle "doublon".
 */
export const LIBELLES_ERREUR: Record<string, string> = {
  DOUBLON_LIGNE: 'Déjà saisie sur cette fiche',
  DOUBLON_INTER_ETATS: 'Déjà enregistrée dans un autre état',
  GRILLE_INDISPONIBLE: 'Aucun tarif applicable',
  MOTIF_OBLIGATOIRE: 'Motif obligatoire',
  // Trois refus en 403, trois gestes differents (Sprint 4.4) : aucun ne deconnecte.
  ACCES_REFUSE: 'Action non autorisée pour votre rôle',
  UTILISATEUR_NON_HABILITE: 'Dossier hors de votre périmètre',
  SEPARATION_TACHES: 'Séparation des tâches',
  ETAT_NON_MODIFIABLE: 'État non modifiable',
}

const LIBELLE_PAR_DEFAUT = 'Une erreur est survenue'

export function libelleErreur(code: string): string {
  return LIBELLES_ERREUR[code] ?? LIBELLE_PAR_DEFAUT
}
