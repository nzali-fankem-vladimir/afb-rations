export type RoleEnum =
  | 'AGENT_UNITE'
  | 'CHEF_UNITE_DA'
  | 'DIRECTEUR_RESEAU_DR'
  | 'ARH'
  | 'DRH'
  | 'ADMIN'

export type StatutEnum =
  | 'EN_COURS_SAISIE'
  | 'SOUMIS'
  | 'EN_ATTENTE_DA'
  | 'EN_ATTENTE_DR'
  | 'RETOURNE'
  | 'CLOTURE'

export type TypeProcessusEnum = 'NORMAL' | 'COMPLEMENTAIRE'

export type NatureEnum = 'RATION' | 'TRANSPORT'

export type SessionEnum = 'JOUR' | 'SOIR'

export type NomEtapeEnum = 'SOUMISSION_AGENT' | 'VALIDATION_DA' | 'VALIDATION_DR'

export type StatutEtapeEnum = 'EN_ATTENTE' | 'VALIDEE' | 'RETOURNEE'

export type StatutFicheEnum = 'EN_SAISIE' | 'ENREGISTREE'

export type StatutGrilleEnum = 'BROUILLON' | 'EN_ATTENTE_DRH' | 'ACTIVE' | 'REJETEE'

/**
 * Ajoutee cote backend au Sprint 5.1 (contrat d'API section 7.2), absente des
 * neuf enumerations d'origine de CLAUDE.md section 5. Aucune valeur ne
 * represente "jamais transmis" : cette situation se lit sur
 * `transmisComptabilite`, et le statut d'integration reste nul.
 */
export type StatutIntegrationEnum = 'EN_ATTENTE' | 'INTEGRE' | 'REJETE'

/**
 * Vocabulaire ferme des manques de completude (service Workflow, Sprint 4.2,
 * CodeManqueEnum). N'appartient pas aux neuf enumerations de CLAUDE.md section 5 :
 * ce ne sont pas des codes d'erreur HTTP, mais les elements de la liste
 * `manques` qui accompagne le seul refus 422 ETAT_INCOMPLET.
 */
export type CodeManqueEnum =
  | 'ETAT_VIDE'
  | 'LIGNE_HORS_PERIODE'
  | 'LIGNE_SANS_MONTANT'
  | 'BENEFICIAIRE_SANS_COMPTE'
