# Partage des énumérations `NatureEnum` et `SessionEnum` entre services

**Date :** 27 août 2026
**Sprint :** 2.1, domaine Grille tarifaire et cycle de statuts
**Statut :** tranchée

## Question

`NatureEnum` (RATION, TRANSPORT) et `SessionEnum` (JOUR, SOIR) sont créées
au Sprint 2.1 dans `service-grilles`. Elles seront aussi nécessaires à
`service-saisie` au Sprint 3.1 : une `ligne_prestation` porte une nature et
une session. Comment éviter la duplication sans créer de dépendance entre
services ?

## Options examinées

| Option | Coût | Risque |
|---|---|---|
| Duplication assumée : chaque service redéfinit les deux `enum` dans son propre package `domaine` | 4 lignes recopiées par service | Dérive théorique entre les deux définitions |
| Module Maven partagé `rations-domaine-commun` | Nouveau module, nouveau pom, périmètre à cadrer | Crée une 2ᵉ brique de code partagé alors que CLAUDE.md §3 pose `rations-audit-commun` comme *« la seule mutualisation de code du backend »* — décision d'architecture transverse, hors périmètre d'un sous-sprint « de structure » |
| Placer les énumérations dans `rations-audit-commun` | Aucun module nouveau | **Interdit** par CLAUDE.md §15 : aucun type métier dans ce module, vérifié au build (`PerimetreDuModuleTest` + enforcer) |

## Décision

**Duplication assumée.** `NatureEnum` et `SessionEnum` sont définies dans
`service-grilles/domaine` au Sprint 2.1, et seront **redéfinies à
l'identique** dans `service-saisie/domaine` au Sprint 3.1. Même traitement
que `RoleEnum`, déjà dupliquée entre `service-identite` et `service-grilles`.

## Motifs

- **Isolement microservices (CLAUDE.md §3).** Chaque service reste
  déployable et compréhensible seul, sans dépendance de compilation vers un
  autre service ni vers un module partagé supplémentaire.
- **Précédent du projet.** `RoleEnum` est déjà dupliquée service par
  service ; ce choix prolonge une convention établie plutôt que d'en
  inventer une.
- **Le vrai garde-fou est en base.** Chaque service porte sa propre
  contrainte `CHECK (nature IN ('RATION','TRANSPORT'))` et
  `CHECK (session IN ('JOUR','SOIR'))` dans sa migration Flyway. Ajouter une
  valeur imposerait de toute façon une migration coordonnée dans chaque
  base : la dérive silencieuse est structurellement improbable.
- **Périmètre du sous-sprint 2.1** : « ne porte que la structure ». Ouvrir
  un module partagé serait une décision d'architecture disproportionnée ici.

## Conséquences

- **Sprint 3.1 (entités du service Saisie)** : recréer `NatureEnum` et
  `SessionEnum` dans `cm.afrilandfirstbank.rations.saisie.domaine`, copie
  conforme des valeurs, avec la contrainte `CHECK` correspondante dans la
  migration de `service-saisie`. Ne pas chercher à importer celles de
  `service-grilles`, ne pas créer de module partagé pour l'occasion.
- Toute évolution des valeurs (peu probable) est une migration coordonnée
  base par base, à traiter comme un changement de contrat transverse.
- `StatutGrilleEnum` n'est pas concernée : elle reste propre au domaine
  Grilles.
