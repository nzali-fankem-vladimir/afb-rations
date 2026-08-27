# SPRINT 7F.6

## Grilles tarifaires et administration

*Module Paiement des Rations et du Transport de la Garde Armée — Frontend*

| | |
|---|---|
| **Objet** | Parcours de l'Analyste RH, de la Directrice RH et de l'administrateur |
| **Livrable** | Écrans de grilles tarifaires, d'utilisateurs, de paramètres et de journal d'audit |
| **Durée** | Une à deux journées |
| **Prérequis** | Sprint 7F.5 validé et commité |
| **Sprint suivant** | 7F.7, suivi, reporting et clôture |

## Réutilisation du projet DOTTEL

Source : `[CHEMIN_PROJET_DOTTEL]`

Les écrans de grilles tarifaires et d'administration des utilisateurs de DOTTEL se transposent assez directement : même cycle de validation par la DRH, même logique de liste paginée avec filtres.

Deux différences. Les grilles de DOTTEL sont indexées par fonction éligible ; ici elles le sont par **couple nature et session**, quatre combinaisons au total. Et l'administration des utilisateurs de ce module n'a **pas de création de compte** : les comptes viennent de l'annuaire, l'administrateur ne fait qu'attribuer un rôle et un code unité.

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Écrans de grilles (étapes 2-4) | Sonnet | Moyen |
| Administration (étapes 5-7) | Sonnet | Moyen |

## 2. Outil de cartographie

```
py -3.14 -m graphify update .
```

## 3. Contexte

Ce sous-sprint couvre trois rôles à la fois : l'Analyste RH qui propose les grilles, la Directrice RH qui les valide, et l'administrateur qui gère les habilitations et les paramètres.

Un point d'ergonomie important sur les grilles : **une grille proposée reste sans effet tant qu'elle n'est pas validée.** L'interface doit le montrer sans ambiguïté, sinon un Analyste RH pourrait croire que son nouveau tarif s'applique déjà, alors que les saisies continuent d'utiliser l'ancien.

Un point de contrôle interne sur les paramètres : le seuil d'aiguillage y est modifiable. L'interface doit signaler la portée de cette modification, qui change le niveau d'approbation requis pour tous les dossiers à venir.

## 4. Objectifs

- Écran de consultation des grilles, avec leur statut
- Écran de proposition d'une grille par l'Analyste RH
- Écran de validation ou de rejet motivé par la Directrice RH
- Écran d'administration des utilisateurs
- Écran de paramètres système, dont le seuil
- Écran de consultation du journal d'audit

## 5. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis CLAUDE.md, section 11 pour les contrats d'api des
services Grilles et Identite, et section 6 pour RG-14. Confirme en 3
lignes le cycle de vie d'une grille.

CONTEXTE DE CETTE SESSION : Sprint 7F.6, grilles tarifaires et
administration. Trois roles concernes : ARH, DRH et ADMIN.

Tu as acces en lecture au projet DOTTEL :
[CHEMIN_PROJET_DOTTEL]

Ses ecrans de grilles et d'utilisateurs se transposent assez
directement. Deux differences : les grilles sont ici indexees par
couple nature et session, pas par fonction ; et l'administration des
utilisateurs n'a PAS de creation de compte, les comptes venant de
l'annuaire.

SERVICE CONCERNE : frontend, contre les services Grilles, Identite,
Workflow et Reporting.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : cree les modules d'appel a l'api pour les grilles et
pour l'administration. Fonctions typees, types derives du contrat
d'api. Montre les fichiers.
```

### Étape 2. Consultation des grilles

```
Cree l'ecran de consultation des grilles tarifaires :

- Les quatre combinaisons nature et session, avec pour chacune la
  grille active et son montant.
- L'historique des grilles fermees et rejetees, accessible sans
  encombrer la vue principale.
- Le statut de chaque grille, avec le badge du Sprint 7F.1.

Le point essentiel : une grille en attente de validation doit
apparaitre clairement comme sans effet sur les saisies. Propose-moi
la maniere de le rendre evident avant d'ecrire le composant.

Accessible aux roles ARH et DRH. Montre le fichier.
```

### Étape 3. Proposition d'une grille

```
Cree l'ecran de proposition, reserve a l'Analyste RH :

Formulaire : nature, session, montant en FCFA, date de debut.

Traite le refus pour conflit d'unicite : une grille active ou en
attente existe deja pour cette combinaison. Le message doit citer la
combinaison et indiquer la grille en cause.

Apres soumission, rappelle a l'utilisateur que la grille est en
attente de la Directrice RH et sans effet jusqu'a sa validation.

Montre le fichier.
```

### Étape 4. Validation d'une grille

```
Cree l'ecran de decision, reserve a la Directrice RH :

- Liste des grilles en attente, avec la grille active qu'elles
  remplaceraient et l'ecart de montant.
- Deux actions : valider, rejeter avec motif obligatoire.

L'affichage de l'ecart avec la grille en vigueur aide a la decision :
une DRH doit voir qu'un tarif passe de 2 500 a 3 000 FCFA, pas
seulement le nouveau montant.

Apres validation, indique que l'ancienne grille a ete fermee et que
la nouvelle s'applique aux nouvelles saisies.

Montre le fichier.
```

### Étape 5. Administration des utilisateurs

```
Cree l'ecran d'administration des utilisateurs, reserve a
l'administrateur :

- Liste paginee et filtrable par role, code unite et statut actif.
- Attribution d'un role et d'un code unite.

Il n'y a AUCUNE creation ni suppression de compte : les comptes
viennent de l'annuaire. L'ecran attribue des habilitations a des
comptes existants.

Le code unite est obligatoire pour les roles a portee locale et
facultatif pour les roles a portee nationale : l'interface doit
l'adapter selon le role choisi.

Montre le fichier.
```

### Étape 6. Paramètres système

```
Cree l'ecran de parametres, reserve a l'administrateur :

- Liste des parametres avec leur libelle et leur valeur.
- Modification d'une valeur.

Pour le seuil d'aiguillage, signale la portee de la modification :
elle change le niveau d'approbation requis pour tous les dossiers a
venir. Demande une confirmation explicite.

Montre le fichier.
```

### Étape 7. Journal d'audit

```
Cree l'ecran de consultation du journal d'audit, reserve aux roles
habilites :

- Filtres : periode, utilisateur, entite ciblee, type d'action.
- Tableau chronologique, avec le detail de la modification quand il
  existe.

Aucune action de modification ni de suppression : le journal est
immuable. L'interface ne doit proposer aucun bouton en ce sens.

Montre le fichier.
```

## 6. Critères de validation

| Élément | Statut attendu |
|---|---|
| Grilles présentées par couple nature et session | Vérifié |
| Grille en attente clairement sans effet | Vérifié |
| Conflit d'unicité expliqué avec la grille en cause | Vérifié |
| Écart de montant affiché à la DRH | Vérifié |
| Motif de rejet obligatoire | Vérifié |
| Aucune création ni suppression de compte | Vérifié |
| Code unité adapté selon la portée du rôle | Vérifié |
| Modification du seuil confirmée explicitement | Vérifié |
| Journal d'audit en lecture seule | Vérifié |

## 7. Points de vigilance

- **Une grille en attente n'est pas une grille active.** Si l'interface ne le montre pas clairement, un Analyste RH croira son tarif appliqué alors que les saisies utilisent toujours l'ancien. C'est une source d'erreur de paiement.
- Ne pas proposer de création de compte. Les comptes viennent de l'annuaire ; un formulaire de création laisserait croire le contraire et contredirait CLAUDE.md.
- Le seuil d'aiguillage n'est pas un paramètre anodin. Le modifier change le niveau d'approbation requis, donc le dispositif de contrôle interne. Une confirmation explicite s'impose.
- Le journal d'audit est en lecture seule. Aucun bouton de suppression, même désactivé : sa seule présence suggérerait que le journal est modifiable.
- L'écart de montant affiché à la DRH est une aide à la décision, pas un ornement. Valider un tarif sans voir ce qu'il remplace revient à décider à l'aveugle.

## 8. Commit

```bash
git add .
git commit -m "sprint-7F.6: grilles tarifaires et administration

- Consultation et proposition de grilles, statut sans effet explicite
- Validation drh avec ecart de montant affiche
- Attribution d'habilitations sans creation de compte
- Parametres systeme et journal d'audit en lecture seule

Refs: US-13, US-14, US-19, RG-14"
```

---

**Fin du Sprint 7F.6** — en attente de validation avant le Sprint 7F.7
