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
