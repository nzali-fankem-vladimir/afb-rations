/**
 * Traduction en français lisible du journal d'audit, pour un contrôleur
 * interne qui n'est pas développeur (Sprint 7F.6, retour utilisateur sur la
 * maquette de refonte : "pas de JSON ni d'identifiant technique, une
 * présentation lisible").
 *
 * Le detail_json de chaque evenement suit TOUJOURS la meme forme, ecrite une
 * fois pour tout le backend par DeltaAudit.java (module rations-audit-commun) :
 *   { "champ": { "avant": ..., "apres": ... }, "contexte": <valeur simple> }
 * Cette regularite est ce qui rend possible un analyseur GENERIQUE plutot
 * qu'un rendu ecrit action par action : les 30 actions du tableau CLAUDE.md
 * section 9.2 passent toutes par le meme constructeur.
 */

import type { NatureEnum, SessionEnum } from '../types/enums'
import { formatCombinaison, formatDateJJMMAAAA } from './formatters'

/**
 * Les 30 actions arretees a la cloture du Sprint 6.3 (CLAUDE.md section 9.2),
 * plus MODIFICATION_PARAMETRE (Sprint 7F.6). Exporte pour alimenter la liste
 * deroulante du filtre "Type d'action" de AuditPage : les seules valeurs que
 * le champ `action` du backend accepte utilement sont celles qui existent
 * reellement (egalite stricte, AuditLogRechercheRepositoryImpl) -- un champ
 * texte libre laissait taper un code inexistant sans jamais le signaler.
 */
export const LIBELLE_ACTION: Record<string, string> = {
  ATTRIBUTION_ROLE: "Modification d'un profil utilisateur",
  LIAISON_COMPTE_KEYCLOAK: 'Première connexion (liaison du compte)',
  ACCES_REFUSE: "Refus d'accès",
  CREATION_GRILLE: "Proposition d'une grille tarifaire",
  SOUMISSION_GRILLE: 'Soumission d\'une grille pour validation',
  VALIDATION_GRILLE: "Validation d'une grille tarifaire",
  FERMETURE_GRILLE: "Fermeture d'une grille remplacée",
  REJET_GRILLE: "Rejet d'une grille tarifaire",
  OUVERTURE_FICHE_JOURNALIERE: "Ouverture d'une fiche journalière",
  CREATION_LIGNE_PRESTATION: "Ajout d'une ligne de prestation",
  MODIFICATION_LIGNE_PRESTATION: "Modification d'une ligne de prestation",
  SUPPRESSION_LIGNE_PRESTATION: "Suppression d'une ligne de prestation",
  CREATION_BENEFICIAIRE: "Création d'un bénéficiaire",
  INCOHERENCE_BENEFICIAIRE: 'Écart de nom sur un bénéficiaire déjà connu',
  DECLENCHEMENT_PROCESSUS: "Déclenchement d'un état",
  SOUMISSION_PROCESSUS: "Soumission d'un état pour validation",
  VALIDATION_PROCESSUS: "Validation d'un état",
  RETOUR_PROCESSUS: "Retour d'un état à l'agent",
  INTEGRATION_COMPTABLE: 'Réponse reçue de la comptabilité',
  TRANSMISSION_COMPTABLE: 'Envoi à la comptabilité',
  TRANSMISSION_MANQUEE: "Échec d'envoi à la comptabilité",
  TRANSMISSION_DOUBLON_REFUSEE: 'Second envoi refusé (déjà envoyé)',
  TRANSMISSION_RESERVATION_LIBEREE: 'Réservation de transmission libérée',
  TRANSMISSION_ETAT_VALIDE: 'État publié vers la comptabilité',
  TRANSMISSION_REFUSEE: 'Transmission refusée',
  TRANSMISSION_CONFIRMATION_MANQUEE: 'Confirmation de transmission manquante',
  ACCUSE_COMPTABLE_APPLIQUE: 'Accusé de la comptabilité appliqué',
  ACCUSE_COMPTABLE_REFUSE: 'Accusé de la comptabilité refusé',
  GENERATION_RAPPORT: "Génération d'un rapport",
  EXPORT_RAPPORT: "Export d'un rapport",
  MODIFICATION_PARAMETRE: "Modification d'un paramètre système",
}

const LIBELLE_ENTITE: Record<string, string> = {
  utilisateurs: 'Compte utilisateur',
  grille_tarifaire: 'Grille tarifaire',
  // Le nom technique date du cycle mensuel (CLAUDE.md section 17, ajustement
  // metier) et n'a pas ete renomme : 193 evenements le portent deja tel quel.
  processus_mensuel: 'Dossier (période)',
  fiche_journaliere: 'Fiche journalière',
  ligne_prestation: 'Ligne de prestation',
  beneficiaires: 'Bénéficiaire',
  parametre_systeme: 'Paramètre système',
  piece_jointe: 'Document PDF',
}

const LIBELLE_SERVICE: Record<string, string> = {
  'service-identite': 'Identité',
  'service-saisie': 'Saisie',
  'service-grilles': 'Grilles',
  'service-workflow': 'Workflow',
  'service-transmission': 'Transmission',
  'service-reporting': 'Reporting',
}

const LIBELLE_CHAMP: Record<string, string> = {
  valeur: 'Valeur',
  role: 'Rôle',
  actif: 'Statut',
  codeUnite: 'Unité',
  code: 'Code',
  montant: 'Montant',
  montantFcfa: 'Montant',
  montantTotal: 'Montant total',
  statut: 'Statut',
  statutValidation: 'Statut',
  motif: 'Motif',
  motifRejet: 'Motif',
  motifRetour: 'Motif',
  motifIntegration: 'Motif',
  motifOuverture: "Motif d'ouverture",
  auteur: 'Auteur',
  login: 'Compte',
  nom: 'Nom',
  prenom: 'Prénom',
  numCompteCourant: 'Numéro de compte',
  numeroCompteCourant: 'Numéro de compte',
  codeAgence: 'Agence',
  nature: 'Nature',
  session: 'Session',
  referenceComptable: 'Référence comptable',
  resultat: 'Résultat',
}

/** Un booleen `actif` se lit « Actif » / « Inactif », jamais « true » / « false ». */
function formaterValeur(cle: string, valeur: unknown): string {
  if (cle === 'actif' && typeof valeur === 'boolean') return valeur ? 'Actif' : 'Inactif'
  return String(valeur)
}

/** Prettifie une cle camelCase ou snake_case inconnue en libelle lisible ("codeGuichet" -> "Code guichet"). */
function prettifierCle(cle: string): string {
  const espace = cle.replace(/_/g, ' ').replace(/([a-z0-9])([A-Z])/g, '$1 $2')
  const minuscule = espace.toLowerCase()
  return minuscule.charAt(0).toUpperCase() + minuscule.slice(1)
}

export function libelleAction(action: string): string {
  return LIBELLE_ACTION[action] ?? prettifierCle(action)
}

export function libelleEntite(entiteCible: string): string {
  return LIBELLE_ENTITE[entiteCible] ?? prettifierCle(entiteCible)
}

export function libelleService(serviceEmetteur: string): string {
  return LIBELLE_SERVICE[serviceEmetteur] ?? serviceEmetteur
}

export function libelleChamp(champ: string): string {
  return LIBELLE_CHAMP[champ] ?? prettifierCle(champ)
}

/** Une ligne du delta, deja traduite, prete a etre affichee sans aucun JSON ni cle technique. */
export interface LigneDelta {
  cle: string
  libelle: string
  avant?: string
  apres?: string
  valeur?: string
}

/**
 * Traduit un detail_json en lignes lisibles. Ne renvoie jamais le JSON brut :
 * un delta illisible (absent, malforme, ou d'une forme imprevue) rend une
 * liste vide plutot qu'un bloc de code -- l'absence d'explication est
 * preferable a une explication fausse.
 */
export function analyserDetailJson(detailJson: string | null): LigneDelta[] {
  if (detailJson === null) return []
  let objet: unknown
  try {
    objet = JSON.parse(detailJson)
  } catch {
    return []
  }
  if (typeof objet !== 'object' || objet === null || Array.isArray(objet)) return []

  const lignes: LigneDelta[] = []
  for (const [cle, valeur] of Object.entries(objet as Record<string, unknown>)) {
    const libelle = libelleChamp(cle)
    if (
      typeof valeur === 'object' &&
      valeur !== null &&
      !Array.isArray(valeur) &&
      ('avant' in valeur || 'apres' in valeur)
    ) {
      const { avant, apres } = valeur as { avant?: unknown; apres?: unknown }
      lignes.push({
        cle,
        libelle,
        avant: avant === null || avant === undefined ? 'aucune' : formaterValeur(cle, avant),
        apres: apres === null || apres === undefined ? 'aucune' : formaterValeur(cle, apres),
      })
    } else if (valeur !== null && valeur !== undefined) {
      lignes.push({ cle, libelle, valeur: formaterValeur(cle, valeur) })
    }
  }
  return lignes
}

/** Lit une cle du delta deja traduit, qu'elle soit portee en `contexte` (valeur) ou en `champ` (avant/apres). */
function valeurDelta(delta: LigneDelta[], cle: string): string | undefined {
  const ligne = delta.find((element) => element.cle === cle)
  return ligne?.valeur ?? ligne?.apres
}

/**
 * Identifie PRECISEMENT le dossier ou l'enregistrement concerne par un
 * evenement, au-dela du seul type d'entite -- "Dossier (période)" ne dit pas
 * LEQUEL parmi tous ceux de l'unite (retour utilisateur : "je veux que le
 * dossier concerne soit aussi bien identifie").
 *
 * Construit a partir du contexte DEJA publie dans detail_json (CLAUDE.md
 * section 9.2) : aucun appel reseau supplementaire. `codeUnite`, `dateDebut`
 * et `dateFin` accompagnent la quasi-totalite des actions portant sur un
 * processus (declenchement, soumission, validation, retour, verrou de
 * transmission, integration comptable) ; `nature`/`session` accompagnent
 * celles portant sur une grille. Repli sur le seul libelle d'entite quand le
 * contexte ne les porte pas (ex. modification d'un parametre systeme).
 */
export function identifiantConcerne(entiteCible: string, delta: LigneDelta[]): string {
  const base = libelleEntite(entiteCible)

  if (entiteCible === 'processus_mensuel') {
    const codeUnite = valeurDelta(delta, 'codeUnite')
    const dateDebut = valeurDelta(delta, 'dateDebut')
    const dateFin = valeurDelta(delta, 'dateFin')
    if (codeUnite && dateDebut && dateFin) {
      return `${base} · Unité ${codeUnite} (${formatDateJJMMAAAA(dateDebut)} → ${formatDateJJMMAAAA(dateFin)})`
    }
    if (codeUnite) return `${base} · Unité ${codeUnite}`
    return base
  }

  if (entiteCible === 'grille_tarifaire') {
    const nature = valeurDelta(delta, 'nature')
    const session = valeurDelta(delta, 'session')
    if (nature && session) {
      return `${base} · ${formatCombinaison(nature as NatureEnum, session as SessionEnum)}`
    }
    return base
  }

  return base
}
