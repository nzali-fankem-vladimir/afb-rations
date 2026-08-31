# Identifiants plats plutôt qu'associations JPA dans `service-saisie`

**Date :** 28 août 2026
**Sprint :** 3.1, domaine de la saisie
**Statut :** tranchée — portée limitée aux entités de la saisie

## Question

`ligne_prestation` porte trois références :

| Colonne | Cible | Base |
|---|---|---|
| `id_processus` (sur `fiche_journaliere`) | `processus_mensuel` | `rations_workflow` |
| `id_fiche_journaliere` | `fiche_journaliere` | `rations_saisie` |
| `id_beneficiaire` | `beneficiaires` | `rations_saisie` |

`id_processus` **doit** rester un identifiant simple : aucune clé étrangère
n'existe entre deux bases, et créer une entité `ProcessusMensuel` ici
dupliquerait le domaine du service Workflow (guide §10, CLAUDE.md points de
vigilance).

Restait à décider pour les deux références **intra-base** :
`id_fiche_journaliere` et `id_beneficiaire`. Elles pourraient être des
`@ManyToOne`.

## Décision

**Identifiants `Long` simples**, pas d'associations JPA, pour ces deux
références intra-base.

## Motif principal — maîtrise du chargement sur le chemin de contrôle

`ligne_prestation` est la table sur laquelle s'exécutent les contrôles de
doublon les plus fréquents du module :

- **RG-04** (Sprint 3.2) : unicité `(bénéficiaire, journée, nature, session)`
  au sein d'une fiche ;
- **RG-15** (Sprint 8) : unicité inter-états de la même combinaison sur toute
  une unité et une période — un balayage potentiellement large.

Ces contrôles interrogent la table en volume et n'ont **jamais besoin de
naviguer** vers le bénéficiaire ou la fiche : ils comparent des identifiants et
des valeurs d'énumération. Une association `@ManyToOne`, même en `LAZY`,
ouvrirait la porte à un chargement transitif non voulu dès qu'un appelant
touche `ligne.getBeneficiaire()` — exactement le type de N+1 silencieux qui ne
se voit pas en test unitaire et se paie en production sur une table de contrôle.
Le champ `Long` rend ce coût explicite : pour lire le bénéficiaire, il faut le
demander au repository, et cet appel se voit dans le code.

## Ce que cette décision n'est PAS

**Ce n'est pas une règle « toujours plat, comme `GrilleTarifaire` ».**
`GrilleTarifaire` ne porte que des références inter-base (`id_createur`,
`id_validateur`) : elle n'avait pas le choix, la comparaison ne s'applique
donc pas. La présente décision est motivée par le profil d'accès de
`ligne_prestation` (table de contrôle à fort volume, jamais navigée), pas par
mimétisme.

Une autre table de `service-saisie` — ou d'un autre service — qui aurait un
profil différent (agrégat lu et affiché avec ses enfants, faible volume,
navigation naturelle) pourrait légitimement utiliser une vraie association JPA
sans contredire ce document. Le critère est : **est-ce que le chemin d'accès
dominant navigue vers l'entité liée ?** Si non, identifiant simple. Si oui, et
que le volume le permet, association assumée.

## Conséquences

- `FicheJournaliere.idProcessus`, `LignePrestation.idFicheJournaliere`,
  `LignePrestation.idBeneficiaire`, `LignePrestation.idGrille` sont des `Long`.
- Les clés étrangères réelles restent en base pour les deux références
  intra-`rations_saisie` (migration V1) : l'intégrité est garantie par
  PostgreSQL, seule la navigation ORM est écartée.
- Les repositories exposent les recherches par identifiant
  (`findByIdFicheJournaliere`, contrôle d'existence de RG-04) plutôt que par
  entité liée.
