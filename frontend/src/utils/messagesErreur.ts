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
  // Sprint 7F.4 -- ecrans de saisie de l'agent d'unite.
  PROCESSUS_EXISTANT: 'Un état couvre déjà cette période',
  ETAT_INCOMPLET: 'État incomplet',
  FICHE_INTROUVABLE: 'Fiche introuvable',
  LIGNE_INTROUVABLE: 'Ligne introuvable',
  PROCESSUS_INTROUVABLE: 'Processus introuvable',
  REQUETE_INVALIDE: 'Demande invalide',
  SERVICE_GRILLES_INDISPONIBLE: 'Service des grilles tarifaires indisponible',
  SERVICE_WORKFLOW_INDISPONIBLE: 'Service de workflow indisponible',
  SERVICE_IDENTITE_INDISPONIBLE: "Service d'identité indisponible",
}

const LIBELLE_PAR_DEFAUT = 'Une erreur est survenue'

export function libelleErreur(code: string): string {
  return LIBELLES_ERREUR[code] ?? LIBELLE_PAR_DEFAUT
}
