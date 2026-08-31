# Incohérence du guide Sprint 3.1 : champ `actif` sur `beneficiaires`

**Date :** 28 août 2026
**Sprint :** 3.1, domaine de la saisie
**Statut :** tranchée

## Constat

Le texte de l'**étape 1** du guide Sprint 3.1 demande de créer l'entité
`Beneficiaire` « strictement conforme au dictionnaire » avec les champs :
`id, nom, prenom, num_compte_courant, code_agence, actif, date_creation`.

Or le champ **`actif` n'existe pas** :

- il n'est **pas dans le dictionnaire** CLAUDE.md §4, qui liste pour
  `beneficiaires` : `nom`, `prenom`, `num_compte_courant`, `code_agence`
  (+ `date_creation` par la convention transverse du Sprint 0.7) ;
- il n'est **pas dans la migration** `V1__creation_tables_saisie.sql`
  (Sprint 0.5, commit `2bf33af`), déjà appliquée à la base `rations_saisie`.

La liste « Fichiers à créer » du guide ne prévoit d'ailleurs aucune migration
pour ce sous-sprint — l'ajout de `actif` en aurait exigé une.

## Décision

**`actif` n'est pas ajouté.** L'entité `Beneficiaire` s'en tient au
dictionnaire : `id, nom, prenom, numCompteCourant, codeAgence, dateCreation`.

## Motifs

- **« Conformité au dictionnaire » est un critère de validation** du sous-sprint
  (guide §9 et §11). Le dictionnaire fait référence, pas le rappel de l'étape 1.
- **Aucun besoin métier ne le réclame.** Contrairement à `date_validation` /
  `motif_rejet` ajoutés au Sprint 2.1 — dont l'écriture était planifiée aux
  Sprints 2.2/2.3 et adossée à US-14 — rien, nulle part, ne demande de
  désactiver un bénéficiaire. Il n'y a pas d'enrôlement, pas de liste
  déroulante de bénéficiaires, donc pas même un écran où un filtre « actif »
  aurait un sens. Le texte de l'étape 1 a vraisemblablement recopié le motif de
  `utilisateurs.actif` (Sprint 0.7) sans que le besoin soit transposable.
- **Précédent inverse au Sprint 2.1** : on n'ajoute une colonne hors
  dictionnaire que lorsqu'un besoin est confirmé ailleurs. Ici il ne l'est pas.

## Portée pour les sprints suivants

Ce fichier existe pour qu'une future session qui relit le guide Sprint 3.1
**ne repose pas la question**. Si le besoin de désactiver un bénéficiaire
apparaît un jour (doublon créé par faute de frappe, par exemple — voir la
décision de rattachement et le contrôle d'identité de l'étape 4), il se
traitera par une migration additive dédiée, à ce moment-là, avec le besoin
réel en face.
