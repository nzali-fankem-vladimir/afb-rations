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

Le frontend de ce module reprend et adapte celui du projet DOTTEL.

**Chemin du projet source, à renseigner avant la première session :**

```
[CHEMIN_PROJET_DOTTEL]
```

**Deux adaptations structurelles concernent tous les sous-sprints 7F.**

La première est le passage de JavaScript à TypeScript. DOTTEL est écrit en `.jsx` ; ce projet est en `.tsx`, avec typage strict et sans `any`. Reprendre un composant DOTTEL suppose donc de le typer, pas seulement de le copier.

La seconde est l'authentification. DOTTEL simulait Keycloak par un appel à une route de login du backend. Ce module utilise le **SSO Keycloak réel**, en Authorization Code avec PKCE. Tout ce qui, dans DOTTEL, touche au formulaire de connexion, au stockage d'un jeton obtenu par mot de passe ou à un contexte d'authentification maison, ne se transpose pas. C'est le sous-sprint 7F.3 qui traite ce point.

Ce qui se reprend le plus directement : la structure des composants, la logique de filtrage par rôle, les patrons de formulaire et de tableau, l'organisation des appels d'API.

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

Tu as acces en lecture au projet DOTTEL :
[CHEMIN_PROJET_DOTTEL]

Reprends-en la LOGIQUE et la structure, jamais le code tel quel :
DOTTEL est en JavaScript, ce projet est en TypeScript strict. Chaque
composant repris doit etre type.

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
```

### Étape 2. Composants de formulaire

```
A partir de l'inventaire, cree les composants de formulaire :
champ de saisie, liste deroulante, selecteur de date.

Ils seront tres sollicites : la saisie journaliere repose sur une
liste deroulante de nature, une liste de session et un calendrier.

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

Montre le composant et la table de correspondance.
```

### Étape 5. Badge de statut

```
Cree le composant de badge affichant un statut.

Il doit couvrir les statuts de processus, de grille et d'integration
comptable, dont les valeurs sont dans src/types/enums.ts depuis le
Sprint 0.3.

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
| Badge couvrant les statuts des trois énumérations | Vérifié |
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
