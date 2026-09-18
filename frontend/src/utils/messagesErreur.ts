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
  // Sprint 7F.5 -- ecrans de validation hierarchique.
  TRANSITION_INTERDITE: 'Dossier déjà traité',
  // 500, pas 422 : le validateur n'a rien a corriger, c'est le parametre
  // d'aiguillage RG-08 qui est indisponible (CLAUDE.md section 15).
  SEUIL_INDISPONIBLE: "Seuil d'aiguillage indisponible",
  // Sprint 7F.6 -- grilles tarifaires et administration.
  // Deux causes distinctes du meme 409, RG-14 (Sprint 2.2) : ne jamais fusionner
  // sous un seul libelle "grille deja existante", le remede differe.
  GRILLE_EN_ATTENTE_EXISTANTE: 'Une proposition attend déjà la DRH',
  GRILLE_ACTIVE_EXISTANTE: 'Date de début trop proche',
  GRILLE_INTROUVABLE: 'Grille introuvable',
  // Deux 409 de controle interne (Sprint 1.2), pas des pannes -- distincts
  // l'un de l'autre pour que l'administrateur comprenne l'action a corriger.
  AUTO_MODIFICATION_INTERDITE: 'Auto-modification impossible',
  DERNIER_ADMINISTRATEUR: 'Dernier administrateur actif',
  CODE_UNITE_INCOHERENT: 'Code unité requis pour ce rôle',
  UTILISATEUR_INTROUVABLE: 'Utilisateur introuvable',
  // Sprint 7F.6 -- ajout backend scope, ecriture des parametres systeme.
  PARAMETRE_INTROUVABLE: 'Paramètre introuvable',
  PARAMETRE_NON_MODIFIABLE: 'Paramètre non modifiable',
  VALEUR_PARAMETRE_INVALIDE: 'Valeur invalide',
  // Sprint 7F.7 -- les huit refus a l'ouverture d'un etat complementaire
  // (GestionnaireErreursApi du service Workflow, verifies un a un le
  // 18 septembre 2026). MOTIF_OBLIGATOIRE est le huitieme : il figure deja
  // plus haut, partage avec le retour d'un dossier (meme regle, RG-10).
  //
  // Chacun a son propre libelle : ces huit refus appellent huit gestes
  // differents, et les fondre sous un "ouverture impossible" obligerait
  // l'agent a lire le detail pour savoir lequel le concerne.
  FONCTIONNALITE_NON_OUVERTE: 'Régularisation fermée',
  ORIGINE_REQUISE: "État d'origine à choisir",
  // On ne regularise qu'un etat clos : un etat encore dans le circuit se
  // corrige directement, sans passer par un complementaire.
  ETAT_NON_CLOTURE: 'État encore en cours',
  PERIODE_NON_CONCORDANTE: "Période différente de celle de l'origine",
  // Ce n'est pas une panne : le delai est une regle de gestion
  // (DELAI_REGULARISATION_JOURS), et le message doit le dire.
  DELAI_REGULARISATION_DEPASSE: 'Période trop ancienne pour être régularisée',
  UNITE_NON_CONCORDANTE: "Unité différente de celle de l'origine",
  // 500 : le parametre de delai est illisible, l'agent n'a rien a corriger.
  DELAI_REGULARISATION_INDISPONIBLE: 'Délai de régularisation indisponible',
}

const LIBELLE_PAR_DEFAUT = 'Une erreur est survenue'

export function libelleErreur(code: string): string {
  return LIBELLES_ERREUR[code] ?? LIBELLE_PAR_DEFAUT
}
