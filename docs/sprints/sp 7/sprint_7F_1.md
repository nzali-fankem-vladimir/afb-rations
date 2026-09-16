# SPRINT 7F.1

## Socle et composants de base

*Module Paiement des Rations et du Transport de la Garde Armée — Frontend*

| | |
|---|---|
| **Objet** | Poser les composants d'interface réutilisables, adaptés depuis le projet DOTTEL |
| **Livrable** | Composants de base typés en TypeScript, thème appliqué, gestion d'erreur commune |
| **Durée** | Une journée |
| **Prérequis** | Sprint 6bis.2 validé et commité |
| **Sprint suivant** | 7F.2, layout global et navigation |

## Réutilisation du projet DOTTEL

Le frontend de ce module reprend et adapte celui du projet DOTTEL, **frontend de référence
requis pour l'ensemble des sous-sprints 7F**.

**Chemin du frontend de référence :**

```
D:\stage afriland\formation spécialisée DSI\projet de gestion des absences\implementation\afb-dottel-mm\frontend
```

Accès **en lecture seule** : on s'en inspire, on n'y écrit jamais.

### État des lieux vérifié le 16 septembre 2026

Cette section a été établie **par inspection des deux dépôts**, pas par mémoire. Une version
antérieure de ce guide décrivait un DOTTEL qui n'existe plus ; les écarts sont signalés.

**Ce qu'est DOTTEL.** Le frontend d'un module voisin du même programme Afriland (la
Direction Financière pilote aussi les dotations téléphoniques). React 19, Vite, Tailwind v4,
axios, react-router 7 — la même famille de stack. Il porte des écrans de grilles, de
workflow, de reporting et d'audit, d'où sa valeur de référence.

**Ce qu'est le frontend de CE projet aujourd'hui** (`afb-rations/frontend`). Il n'est pas
vide : le Sprint 0.3 l'a initialisé et le Sprint 0.4 y a branché l'authentification.

| Élément | État |
| --- | --- |
| Langage | TypeScript (`tsconfig.app.json`, `.tsx`), `typescript ~6.0` |
| Authentification | **Déjà en place** — `auth/fournisseurKeycloak.ts`, `contexts/AuthContext.tsx`, `hooks/useAuth.ts` |
| Client d'API | `api/apiClient.ts`, `api/identiteApi.ts` |
| Énumérations | `types/enums.ts` — **neuf énumérations, `StatutIntegrationEnum` absente** (voir étape 5) |
| Thème | `index.css`, trois couleurs de charte seulement |
| Composants | Aucun — `components/communs` et `components/layout` sont vides |

### Les trois écarts de fond entre DOTTEL et ce projet

**1. JavaScript contre TypeScript.** DOTTEL est en JavaScript pur — 55 fichiers `.jsx`, 13
`.js`, **aucun** `.ts`. Ce projet est en TypeScript strict, sans `any` (CLAUDE.md §2).
Reprendre un composant DOTTEL suppose de le **typer**, pas de le copier.

**2. Deux implémentations de Keycloak, et c'est celle de CE projet qui fait foi.**

> *Correction d'une version antérieure de ce guide*, qui affirmait que « DOTTEL simulait
> Keycloak par un appel à une route de login du backend ». **C'est faux depuis son Sprint
> MM.7** : son `apiClient.js` le dit lui-même — « POST /auth/login a disparu ». DOTTEL utilise
> désormais un vrai Keycloak en Authorization Code avec PKCE S256.

Les deux projets font donc la même chose, **par deux chemins différents** :

| | Ce projet | DOTTEL |
| --- | --- | --- |
| Mécanisme | bibliothèque **`keycloak-js`** (`check-sso`, `pkceMethod: 'S256'`) | PKCE **écrit à la main** (`crypto.subtle`, `code_verifier` en `sessionStorage`, page `/auth/callback`) |
| État | en place, fonctionnel | en place, fonctionnel |

**On garde `keycloak-js`.** Remplacer une authentification qui marche par une réécriture
artisanale serait une régression déguisée en harmonisation. Ce qui se reprend de DOTTEL,
c'est une **décision de sécurité** : le jeton est gardé **en mémoire**, jamais en
`localStorage` ni en `sessionStorage`. À vérifier dans `apiClient.ts` au sous-sprint 7F.3.

**3. DOTTEL contient ce que ce module interdit.** À ne **jamais** transposer (CLAUDE.md §2
et §15) :

- l'**enrôlement** — `pages/enrolement/` (`ImporterBeneficiairesPage`,
  `VerifierMatriculePage`, `ConfirmerEnrolementPage`) ;
- les **fonctions éligibles** — `pages/admin/FonctionsEligiblesListPage`,
  `CreerFonctionPage`, `ModifierFonctionModal` ;
- le **champ mot de passe** de `pages/admin/CreerUtilisateurPage.jsx`, qui envoie un
  `motDePasse` au backend. Ce module n'a **aucun mot de passe** : un profil se
  pré-provisionne par `login`, `role` et `code_unite` (Sprint 0.4) ;
- la **resynchronisation** et l'**ajustement de lignes** de `pages/workflow/`, qui
  répondent à un parcours DOTTEL sans équivalent ici.

### Ce qui se reprend le plus directement

La structure des composants `components/ui/` (bouton, champ, tableau, modale, badge, alerte,
liste vide), le layout (`AppLayout`, `Sidebar`, `PageHeader`), la logique de filtrage par
rôle, les patrons de formulaire et de tableau, l'organisation des appels d'API, et les
écrans de grilles, de reporting et d'audit comme **patrons de présentation**.

## Changements du backend depuis la rédaction des guides 7F

Les guides 7F ont été écrits quand le module raisonnait en **mois**. Le métier a
établi depuis que **le cycle de paiement est hebdomadaire** (point M-04), et trois
sprints hors séquence ont suivi. **Tout sous-sprint 7F doit en tenir compte** ; les
guides concernés le rappellent à l'endroit utile.

| Ce qui a changé | Avant | Maintenant | Sprint |
| --- | --- | --- | --- |
| Période d'un état | `moisPaiement`, `anneePaiement` (entiers) | **`dateDebut`, `dateFin`** (ISO 8601, bornes **incluses**) | Maille 1 |
| Déclenchement `POST /processus` | mois et année | `dateDebut`, `dateFin` | Maille 1 |
| Unicité des états NORMAL | un par (unité, mois) | **aucun chevauchement**, même partiel | Maille 1 |
| Filtres du Reporting | `periode=AAAA-MM` | **`dateDebut` et `dateFin`** | Maille 1 |
| Nom des fichiers exportés | `…-AAAAMM.pdf` | `…-AAAAMMJJ.pdf` (premier jour de la période) | Maille 1 |
| Régularisation | fermée | **ouverte** (`RATTRAPAGE_ACTIF = true`, migration V8) | 6bis.2 |
| Complémentaire au circuit | selon le seuil | **toujours au Directeur Réseau**, seuil non lu | 6bis.1 |
| Doublon inter-états | — | **`409 DOUBLON_INTER_ETATS`**, distinct de `DOUBLON_LIGNE` | 6bis.2 |

**Un piège connu** : `POST /processus` rend `compteCharge: null`, là où
`GET /processus/{id}` rend la valeur. Un écran ne doit pas afficher le compte de charge
depuis la réponse de création (résumé du Sprint 6bis.2, §12).

**Le seuil RG-08 reste à 100 000 XAF** (décision métier), appliqué désormais à un état
hebdomadaire. Ne jamais l'écrire en dur dans l'interface : il est lu côté backend, et
`ValidationResponse.seuilApplique` le rend quand il a été lu.

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Inventaire du projet source (étape 2) | Sonnet | Moyen |
| Composants de base (étapes 3-5) | Sonnet | Moyen |

## 2. Outil de cartographie

```
py -3.14 -m graphify update .
```

## 3. Contexte

Le Sprint 0.3 a initialisé le projet React 19 en TypeScript, avec Vite, Tailwind aux couleurs de la charte, la structure de dossiers et le client axios. Ce sous-sprint le remplit de composants réutilisables.

Aucun écran fonctionnel n'est créé ici : on construit la boîte à outils dont les sous-sprints suivants se serviront.

## 4. Objectifs

- Inventaire de ce qui se reprend de DOTTEL et de ce qui s'adapte
- Composants de base typés : bouton, champ de formulaire, tableau, modale, badge de statut
- Affichage d'erreur commun, aligné sur le format du contrat d'API
- Composants d'état : chargement, liste vide

## 5. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis CLAUDE.md, en particulier la stack frontend et la
charte graphique. Confirme en 3 lignes ce que tu y as trouve.

CONTEXTE DE CETTE SESSION : Sprint 7F.1, socle du frontend. Le projet
React 19 TypeScript existe depuis le Sprint 0.3. On cree les
composants de base.

Tu as acces en LECTURE SEULE au frontend de reference DOTTEL :
D:\stage afriland\formation spécialisée DSI\projet de gestion des absences\implementation\afb-dottel-mm\frontend

Lis d'abord la section "Reutilisation du projet DOTTEL" de ce guide :
elle dresse l'etat des lieux verifie des deux depots.

Reprends-en la LOGIQUE et la structure, jamais le code tel quel :
DOTTEL est en JavaScript, ce projet est en TypeScript strict. Chaque
composant repris doit etre type.

N'ECRIS JAMAIS dans le depot DOTTEL.

L'authentification de CE projet existe deja (keycloak-js). Ne la
remplace pas par celle de DOTTEL, qui fait la meme chose autrement.

SERVICE CONCERNE : frontend.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- TypeScript strict, aucun any, aucun fichier .jsx.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : parcours le dossier des composants de DOTTEL et
dresse-moi l'inventaire de ce qui est reutilisable ici. Pour chaque
composant : nom, role, et ton verdict parmi reprendre en typant,
adapter, ou ecarter. Justifie les verdicts adapter et ecarter. Ne
cree aucun fichier.

DEUX ARBITRAGES A ME PRESENTER avec cet inventaire, avec ta
recommandation pour chacun :

1. LES DEPENDANCES. Les composants ui de DOTTEL importent
   @radix-ui/react-checkbox, @radix-ui/react-label,
   class-variance-authority et lucide-react -- et probablement clsx et
   tailwind-merge. AUCUNE n'est installee dans ce projet. Deux voies :
   les ajouter pour reprendre les composants au plus pres, ou reecrire
   les composants sans elles. Verifie d'abord la liste exacte en lisant
   les imports, puis presente les deux lectures.

2. LA POLICE. DOTTEL embarque Source Sans 3 (licence SIL Open Font,
   donc redistribuable). La charte §8.2 prescrit Bookman Old Style, que
   le Sprint 4.2 a ecartee pour le PDF parce que c'est une fonte
   Monotype sous licence -- l'embarquer serait une redistribution.
   Le meme motif vaut-il pour le web ? Presente les options.

Attends ma reponse avant tout fichier.
```

### Étape 2. Composants de formulaire

```
A partir de l'inventaire, cree les composants de formulaire :
champ de saisie, liste deroulante, selecteur de date.

Ils seront tres sollicites : la saisie journaliere repose sur une
liste deroulante de nature, une liste de session et un calendrier.

Prevois aussi un SELECTEUR DE PERIODE a deux bornes (date de debut,
date de fin). La periode d'un etat n'est plus un mois : c'est un
intervalle de dates, bornes INCLUSES, depuis la Maille 1 (voir la
section "Changements du backend" de ce guide). Il servira au
declenchement d'un etat (7F.4) et aux filtres du reporting (7F.7).
N'impose aucune duree : le cycle est hebdomadaire aujourd'hui, et
l'intervalle a ete choisi precisement pour ne pas figer une cadence.

Le champ de montant est un cas particulier : il est TOUJOURS en
lecture seule cote frontend, le montant venant de la grille active.
Prevois cette variante des maintenant.

Montre les fichiers un par un.
```

### Étape 3. Tableau réutilisable

```
Cree le composant de tableau generique : colonnes configurables,
pagination, etat de chargement, etat vide.

Il doit consommer le format de pagination arrete au Sprint 1.2 cote
backend. Verifie ce format avant de typer les props.

Ce composant servira aux ecrans de suivi, de grilles, d'utilisateurs
et de journal d'audit. Montre-moi sa signature avant d'ecrire
l'implementation.
```

### Étape 4. Affichage d'erreur

```
Cree le composant d'affichage d'erreur, aligne sur le format du
contrat d'api : timestamp, status, code, message, path.

L'utilisateur ne doit jamais voir un code technique brut. Prevois une
correspondance entre les codes d'erreur du backend et des messages
comprehensibles en francais. Commence par les codes deja connus :
doublon de ligne, grille indisponible, motif obligatoire, separation
des taches, etat non modifiable.

ATTENTION a deux codes de doublon qui se ressemblent et appellent
deux gestes differents :
- DOUBLON_LIGNE (409, RG-04) : "vous l'avez deja saisie ICI" -- la
  ligne est sur la fiche que l'agent a sous les yeux.
- DOUBLON_INTER_ETATS (409, RG-15) : "elle a deja ete servie
  AILLEURS" -- la ligne fautive est dans un AUTRE etat, le plus souvent
  deja cloture et paye.
Ne les fusionne pas en un seul message "doublon". Et pour
DOUBLON_INTER_ETATS, AFFICHE le message du backend : il nomme l'etat
en conflit, seule information qui permette a l'agent de verifier.

Montre le composant et la table de correspondance.
```

### Étape 5. Badge de statut

```
Cree le composant de badge affichant un statut.

Il doit couvrir les statuts de processus, de grille et d'integration
comptable.

ATTENTION : src/types/enums.ts contient les neuf enumerations de
CLAUDE.md §5, mais PAS StatutIntegrationEnum
(EN_ATTENTE | INTEGRE | REJETE), ajoutee cote backend au Sprint 5.1.
Ajoute-la d'abord. Aucune valeur n'y represente "jamais transmis" :
cette situation se lit sur transmisComptabilite, et le statut reste
nul. Le badge doit donc savoir afficher un statut d'integration nul.

La charte reserve le rouge aux accents : n'attribue pas une couleur
vive a chaque statut. Propose-moi une palette sobre avant de la
generaliser.
```

## 6. Critères de validation

| Élément | Statut attendu |
|---|---|
| Inventaire DOTTEL réalisé, verdicts justifiés | Fait |
| Composants de formulaire typés, variante montant en lecture seule | Fait |
| Tableau générique consommant le format de pagination du backend | Vérifié |
| Affichage d'erreur avec messages en français | Vérifié |
| `StatutIntegrationEnum` ajoutée à `enums.ts`, statut nul géré | Fait |
| Badge couvrant les statuts des trois énumérations | Vérifié |
| Arbitrages dépendances et police présentés et tranchés | Fait |
| `DOUBLON_LIGNE` et `DOUBLON_INTER_ETATS` distingués | Vérifié |
| Sélecteur de période à deux bornes, sans durée imposée | Fait |
| Aucune écriture dans le dépôt DOTTEL | Vérifié |
| Palette de statuts sobre, conforme à la charte | Fait |
| Aucun fichier `.jsx`, aucun `any` | Vérifié |
| `npm run build` réussi | Vérifié |

## 7. Points de vigilance

- Reprendre la logique de DOTTEL, jamais le code brut. Un composant copié sans typage introduit une dette immédiate.
- Le champ de montant est toujours en lecture seule. Un champ éditable côté interface contredirait RG-03, même si le backend ignore la valeur.
- La table de correspondance des codes d'erreur sera enrichie à chaque sous-sprint. La concevoir extensible dès maintenant.

## 8. Commit

```bash
git add .
git commit -m "sprint-7F.1: socle et composants de base

- Inventaire et adaptation des composants dottel en typescript
- Composants de formulaire, tableau generique, affichage d'erreur
- Badge de statut conforme a la charte

Refs: sprint 0.3, charte graphique"
```

---

**Fin du Sprint 7F.1** — en attente de validation avant le Sprint 7F.2
