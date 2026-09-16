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

Source, en lecture seule : `D:\stage afriland\formation spécialisée DSI\projet de gestion des absences\implementation\afb-dottel-mm\frontend`

Fichiers utiles : `src/pages/grilles/` (`GrillesListPage`, `CreerGrillePage`, `ValiderGrilleModal`,
`HistoriqueGrillePage`), `src/pages/admin/UtilisateursListPage.jsx`,
`src/pages/reporting/AuditPage.jsx`. L'état des lieux complet est dans le guide **7F.1**, section
« Réutilisation du projet DOTTEL ».

Les écrans de grilles tarifaires et d'administration des utilisateurs de DOTTEL se transposent assez directement : même cycle de validation par la DRH, même logique de liste paginée avec filtres.

Deux différences. Les grilles de DOTTEL sont indexées par fonction éligible ; ici elles le sont par **couple nature et session**, quatre combinaisons au total. Et l'administration des utilisateurs de ce module n'a **pas de création de compte** : les comptes viennent de l'annuaire, l'administrateur ne fait qu'attribuer un rôle et un code unité.

**Trois fichiers de DOTTEL à NE PAS transposer**, vérifiés le 16 septembre 2026 :

- **`pages/admin/CreerUtilisateurPage.jsx`** — il porte un champ **`motDePasse`** (`type="password"`) envoyé à `POST /admin/utilisateurs`. **Ce module ne gère aucun mot de passe** (CLAUDE.md §10 et §15) et n'expose aucune création de compte. C'est le risque le plus concret de ce sous-sprint : l'écran est proche, bien fait, et il suffirait de le « typer ».
- **`pages/admin/FonctionsEligiblesListPage.jsx`**, **`CreerFonctionPage.jsx`**, **`ModifierFonctionModal.jsx`** — les fonctions éligibles sont interdites dans ce module.
- **`pages/dashboard/GrillesTarifairesValider.jsx`** et les grilles indexées par fonction — ici, les grilles le sont par **couple nature × session**.

## Ce qui a changé, et deux corrections de fond

*Vérifié le 16 septembre 2026 contre les contrôleurs et les décisions arrêtées (CLAUDE.md §17).*

**1. Il n'existe aucun endpoint d'écriture des paramètres système.** Le seul endpoint est
`GET /parametres/fonctionnalites`, en lecture. L'étape 6 de la version initiale de ce guide
demandait un écran de modification : il n'aurait eu aucune API à appeler. Elle est
transformée en arbitrage (voir l'étape 6), et la route `/admin/parametres` a été retirée du
tableau du 7F.2 pour la même raison.

**2. Le journal d'audit n'est pas réservé à l'administrateur.** `GET /audit/entrees` et
`GET /audit/processus/{id}` sont ouverts à **`ARH`, `DRH` et `ADMIN`**. La route est `/audit`,
partagée, et non `/admin/audit` (tableau révisé du 7F.2).

**3. Correction — une grille active ne bloque PAS une proposition.** La version initiale de
l'étape 3 parlait d'un refus quand « une grille active ou en attente existe déjà ». **C'est
contraire à la décision du Sprint 2.2** : proposer une grille postérieure à celle en vigueur
est **le remplacement normal**, et il est accepté. Les deux refus réels sont :

| Code | Refus réel |
| --- | --- |
| `409 GRILLE_EN_ATTENTE_EXISTANTE` | une proposition est **déjà en attente** de la DRH sur ce couple |
| `409 GRILLE_ACTIVE_EXISTANTE` | la date de début n'est **pas strictement postérieure** à celle de la grille en vigueur (anti-datage) |

Un écran qui dirait « une grille active existe déjà » empêcherait l'ARH de faire exactement ce
pour quoi l'écran existe.

**4. Correction — une grille validée ne s'applique pas « aux nouvelles saisies ».** Elle
s'applique aux **prestations datées à partir de sa date de début** (Sprint 2.4 : le montant est
résolu **à la date de la prestation**, jamais à la date de saisie). Une saisie rétroactive d'une
journée antérieure prend l'**ancienne** grille, et c'est voulu. Et l'ancienne grille est fermée
**à la veille** de la date de début de la nouvelle (Sprint 2.3) — une date qui peut être **future** :
c'est alors une **fermeture programmée**, à présenter comme telle.

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

Un point de contrôle interne sur les paramètres : le seuil d'aiguillage change le niveau d'approbation requis pour tous les dossiers à venir. **Il n'est aujourd'hui modifiable que par `UPDATE` en base** — aucun endpoint d'écriture n'existe (voir l'étape 6).

## 4. Objectifs

- Écran de consultation des grilles, avec leur statut
- Écran de proposition d'une grille par l'Analyste RH
- Écran de validation ou de rejet motivé par la Directrice RH
- Écran d'administration des utilisateurs
- Arbitrage sur un écran de paramètres système (aucun endpoint d'écriture n'existe)
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

Tu as acces en LECTURE SEULE au frontend de reference DOTTEL :
D:\stage afriland\formation spécialisée DSI\projet de gestion des absences\implementation\afb-dottel-mm\frontend
N'ECRIS JAMAIS dans ce depot.

Ses ecrans de grilles et d'utilisateurs se transposent assez
directement. Deux differences : les grilles sont ici indexees par
couple nature et session, pas par fonction ; et l'administration des
utilisateurs n'a PAS de creation de compte, les comptes venant de
l'annuaire.

INTERDIT : pages/admin/CreerUtilisateurPage.jsx porte un champ
motDePasse. Ne le transpose sous AUCUNE forme. Ne transpose pas non
plus les ecrans de fonctions eligibles.

Lis la section "Ce qui a change, et deux corrections de fond" de ce
guide avant tout fichier : deux formulations de la version initiale
contredisaient des decisions arretees aux Sprints 2.2 et 2.4.

SERVICE CONCERNE : frontend, contre les services Grilles, Identite,
Workflow et Reporting.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : cree les modules d'appel a l'api pour les grilles et
pour l'administration. Fonctions typees, types derives des DTO Java du
backend -- pas du seul contrat d'api, qui est ignore par git et absent
d'un clone du depot. Montre les fichiers.
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

Traite les deux refus d'unicite, qui ne disent PAS la meme chose :
- 409 GRILLE_EN_ATTENTE_EXISTANTE : une proposition est deja en attente
  de la DRH sur ce couple. Il faut attendre sa decision.
- 409 GRILLE_ACTIVE_EXISTANTE : la date de debut n'est pas strictement
  posterieure a celle de la grille en vigueur. Il faut une date plus
  tardive.
Le message doit citer la combinaison et indiquer la grille en cause.

ATTENTION : une grille ACTIVE sur le couple ne bloque PAS une
proposition. Proposer une grille a date posterieure est le
remplacement normal (Sprint 2.2), et il est accepte. N'ecris jamais
"une grille active existe deja" comme motif de refus.

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

Apres validation, indique :
- que l'ancienne grille est fermee A LA VEILLE de la date de debut de la
  nouvelle (Sprint 2.3). Si cette date est future, dis-le comme une
  FERMETURE PROGRAMMEE, pas comme une fermeture deja effective ;
- que la nouvelle s'applique aux PRESTATIONS DATEES a partir de sa date
  de debut -- PAS "aux nouvelles saisies". Le montant est resolu a la
  date de la prestation (Sprint 2.4) : une saisie retroactive d'une
  journee anterieure prend l'ancienne grille, et c'est voulu.

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

Le code unite est OBLIGATOIRE pour les roles a portee locale --
AGENT_UNITE et CHEF_UNITE_DA -- et facultatif pour les roles a portee
nationale -- DIRECTEUR_RESEAU_DR, ARH, DRH, ADMIN. Regle verifiee dans
UtilisateurAdminService. L'interface doit l'adapter selon le role
choisi. Cinq chiffres exactement.

Filtres disponibles sur GET /identite/utilisateurs : role, codeUnite,
actif, et pagination.

Traite les deux refus 409 du Sprint 1.2, qui sont des regles de
controle interne et non des pannes :
- un administrateur ne peut pas modifier SON PROPRE role ;
- le dernier administrateur actif ne peut pas perdre le role ADMIN.
Mieux : desactive l'action sur la ligne de l'administrateur connecte,
plutot que de la laisser echouer.

Montre le fichier.
```

### Étape 6. Paramètres système

```
ATTENTION : AUCUN ENDPOINT D'ECRITURE DES PARAMETRES N'EXISTE.
Le seul endpoint est GET /parametres/fonctionnalites, en lecture.
Le seuil RG-08, le delai de regularisation et le compte de charge se
modifient aujourd'hui par UPDATE en base.

N'ECRIS AUCUN ECRAN DE MODIFICATION. Presente-moi l'arbitrage, avec ta
recommandation :

1. Reporter l'ecran. Les parametres restent un geste d'exploitation.
2. Creer un endpoint d'ecriture cote Workflow. S'il est cree, il
   exige : un role ADMIN ; la lecture stricte deja appliquee au seuil
   (Long.parseLong, negatif refuse, Sprint 4.3) ; et UN EVENEMENT
   D'AUDIT portant l'ancienne et la nouvelle valeur -- il s'agit des
   valeurs qui commandent le niveau d'approbation de la banque et
   l'imputation comptable. Ce serait un sprint backend, pas une etape
   frontend.
3. Un ecran de CONSULTATION seule, sur GET /parametres/fonctionnalites.

Attends ma decision.
```

### Étape 7. Journal d'audit

```
Cree l'ecran de consultation du journal d'audit, reserve aux roles
habilites :

- Filtres reels de GET /audit/entrees : serviceEmetteur, action,
  entiteCible, idEntite, idUtilisateur, dateDebut, dateFin, page, size.
  ATTENTION : dateDebut et dateFin sont ici des DATE-HEURES ISO
  (2026-09-01T00:00:00), pas des dates comme au Reporting. Envoyer
  "2026-09-01" rendrait un 400.
- Le filtre par utilisateur est INCOMPLET PAR CONSTRUCTION : 21 des 30
  points de publication du backend laissent idUtilisateur nul, dont les ACCES_REFUSE de
  CT-04 et la creation de ligne (point A-01, CLAUDE.md §9.2). Signale-le
  a l'ecran a cote du filtre : "aucun resultat" ne doit pas se lire
  "cet utilisateur n'a rien fait".
- Le tri est impose par le serveur, sur date_action. N'offre AUCUN tri
  par colonne : l'ordre d'arrivee des evenements n'est pas l'ordre des
  faits (Sprint 6.3).
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
| Arbitrage sur l'écran de paramètres présenté et tranché, aucun écran de modification sans endpoint | Fait |
| Journal d'audit en lecture seule, ouvert à ARH, DRH et ADMIN | Vérifié |
| Aucun champ mot de passe, `CreerUtilisateurPage.jsx` non transposé | Vérifié |
| Aucun écran de fonctions éligibles | Vérifié |
| Grille active non présentée comme un blocage de proposition | Vérifié |
| Nouvelle grille présentée par date de prestation, fermeture programmée signalée | Vérifié |
| Refus 409 d'auto-modification et de dernier administrateur gérés | Vérifié |
| Filtre d'audit par utilisateur signalé comme incomplet (A-01) | Vérifié |
| Dates de l'audit envoyées en date-heure ISO | Vérifié |

## 7. Points de vigilance

- **Une grille en attente n'est pas une grille active.** Si l'interface ne le montre pas clairement, un Analyste RH croira son tarif appliqué alors que les saisies utilisent toujours l'ancien. C'est une source d'erreur de paiement.
- Ne pas proposer de création de compte. Les comptes viennent de l'annuaire ; un formulaire de création laisserait croire le contraire et contredirait CLAUDE.md.
- Le seuil d'aiguillage n'est pas un paramètre anodin. Le modifier change le niveau d'approbation requis, donc le dispositif de contrôle interne. **Aucun endpoint ne permet de le modifier aujourd'hui** : ne pas construire un écran qui le laisserait croire.
- **`CreerUtilisateurPage.jsx` de DOTTEL porte un champ mot de passe.** C'est l'écart le plus probable du sous-sprint, précisément parce que l'écran est proche et soigné. Ce module n'a ni mot de passe, ni création de compte.
- **Une grille active ne bloque pas une proposition.** Le laisser croire interdirait à l'ARH le remplacement normal d'un tarif.
- **« Aucune entrée d'audit pour cet utilisateur » ne prouve rien** : 21 des 30 points de publication du backend ne portent pas d'identifiant d'utilisateur (A-01). L'écran doit le dire.
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
