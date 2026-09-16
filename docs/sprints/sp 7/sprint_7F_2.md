# SPRINT 7F.2

## Layout global et navigation

*Module Paiement des Rations et du Transport de la Garde Armée — Frontend*

| | |
|---|---|
| **Objet** | Structure de page commune, navigation filtrée par rôle, configuration des routes |
| **Livrable** | Sidebar, layout applicatif, en-tête de page, routage complet |
| **Durée** | Une journée |
| **Prérequis** | Sprint 7F.1 validé et commité |
| **Sprint suivant** | 7F.3, authentification Keycloak |

## Réutilisation du projet DOTTEL

Source, en lecture seule : `D:\stage afriland\formation spécialisée DSI\projet de gestion des absences\implementation\afb-dottel-mm\frontend`

Fichiers utiles : `src/components/layout/AppLayout.jsx`, `Sidebar.jsx`, `PageHeader.jsx`,
`src/router/AppRouter.jsx`, `src/pages/auth/AccesInterdit.jsx`, `PageIntrouvable.jsx`.
L'état des lieux complet des deux dépôts est dans le guide **7F.1**, section « Réutilisation
du projet DOTTEL » : le lire avant de commencer.

La sidebar verticale sombre de DOTTEL, sa structure de liens filtrés par rôle et son bloc utilisateur en bas se transposent directement, à trois conditions : typage TypeScript, rôles de ce module, routes de ce module.

Les rôles diffèrent entièrement. DOTTEL utilise EMPLOYE, ARH, CRH, DRH et ADMIN ; ce module utilise AGENT_UNITE, CHEF_UNITE_DA, DIRECTEUR_RESEAU_DR, ARH, DRH et ADMIN. Ne pas transposer les rôles, seulement le mécanisme de filtrage.

Ne pas transposer non plus les entrées de menu **Enrôlement** et **Fonctions éligibles** de la sidebar DOTTEL : ces fonctions sont interdites dans ce module (CLAUDE.md §15).

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Sidebar et layout (étapes 2-3) | Sonnet | Moyen |
| Routage (étapes 4-5) | Sonnet | Moyen |

## 2. Outil de cartographie

```
py -3.14 -m graphify update .
```

## 3. Contexte

Les composants de base existent. Ce sous-sprint construit la coquille dans laquelle les écrans viendront s'insérer.

> *Correction d'une version antérieure de ce guide*, qui annonçait un filtrage « effectif
> seulement au 7F.3, quand l'utilisateur courant sera connu ». **L'utilisateur courant est
> déjà connu** : le contexte d'authentification existe depuis le Sprint 0.4
> (`hooks/useAuth.ts`, vérifié le 16 septembre 2026).

`useAuth()` expose `etat`, `utilisateur`, `role` et **`possedeRole(...roles)`**. Le rôle vient
de `GET /identite/moi`, **jamais du jeton** : c'est une donnée du module, décidée par
l'administrateur (CLAUDE.md §10). Le filtrage par rôle est donc posé **et testable** dès ce
sous-sprint. À ce stade, les pages sont des espaces réservés.

## 4. Rôles et routes prévues

*Tableau révisé le 16 septembre 2026 contre les `@PreAuthorize` réels des contrôleurs du
backend.* La version initiale aurait fait construire des liens répondant `403` : un lien
affiché vers une route refusée est pire qu'un lien absent, car l'utilisateur conclut à une
panne.

| Rôle | Routes accessibles |
|---|---|
| AGENT_UNITE | `/processus`, `/saisie`, `/suivi` |
| CHEF_UNITE_DA | `/validation`, `/suivi` |
| DIRECTEUR_RESEAU_DR | `/validation`, `/suivi` |
| ARH | `/grilles`, `/suivi`, `/rapports`, `/audit` |
| DRH | `/grilles`, `/audit` |
| ADMIN | `/admin/utilisateurs`, `/audit` |

**Ce qui a changé, et pourquoi :**

| Écart corrigé | Source dans le backend |
| --- | --- |
| **AGENT_UNITE gagne `/suivi`** | `ReportingController` est ouvert à `AGENT_UNITE` : l'agent suit ses propres dossiers (motif de retour, statut d'intégration) |
| **DRH perd `/suivi`** | `ReportingController` ne l'autorise **pas** (`ARH`, `AGENT_UNITE`, `CHEF_UNITE_DA`, `DIRECTEUR_RESEAU_DR`) |
| **L'audit devient `/audit`, partagé** | `AuditController` : `ARH`, `DRH`, `ADMIN` — et non l'administrateur seul, d'où la sortie de `/admin` |
| **`/admin/parametres` retiré** | **Aucun endpoint d'écriture** des paramètres n'existe ; seul `GET /parametres/fonctionnalites` est exposé, en lecture |

**Deux points à faire confirmer en début de session, sans les trancher seul :**

1. **Le DRH sans suivi des dossiers** est-il voulu ? Le backend l'exclut du Reporting, ce
   qui peut être une décision (le DRH valide des grilles, pas des états) ou un oubli.
   L'interface suit le backend ; un désaccord se corrige côté backend, pas en affichant un
   lien qui échouera.
2. **Une page de paramètres pour l'administrateur** : le seuil RG-08, le délai de
   régularisation et le compte de charge se modifient aujourd'hui par `UPDATE` en base. Une
   page exigerait un endpoint d'écriture — **et un audit**, s'agissant de valeurs qui
   commandent le niveau d'approbation et l'imputation comptable. Hors périmètre de ce
   sous-sprint dans tous les cas.

## 5. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis CLAUDE.md, charte graphique et roles applicatifs.
Confirme en 3 lignes les six roles de ce module.

CONTEXTE DE CETTE SESSION : Sprint 7F.2, layout et navigation. Les
composants de base existent depuis le Sprint 7F.1.

Tu as acces en LECTURE SEULE au frontend de reference DOTTEL :
D:\stage afriland\formation spécialisée DSI\projet de gestion des absences\implementation\afb-dottel-mm\frontend
N'ECRIS JAMAIS dans ce depot. Lis d'abord la section "Reutilisation
du projet DOTTEL" du guide 7F.1.

Reprends de sa sidebar la LOGIQUE : structure de liens avec tableau
de roles, filtrage, menu utilisateur, fermeture au clic exterieur.
N'en reprends NI les roles, NI les routes, qui sont ceux d'un autre
module, NI les entrees Enrolement et Fonctions eligibles.

L'utilisateur courant est DEJA connu : utilise useAuth() de
src/hooks/useAuth.ts (etat, role, possedeRole). Ne cree pas un second
contexte d'authentification.

AVANT DE CODER, pose-moi les deux questions de la section 4 de ce
guide : le DRH sans suivi, et la page de parametres. Donne ta
recommandation pour chacune et attends ma reponse.

SERVICE CONCERNE : frontend.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- TypeScript strict, aucun any.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : cree la sidebar. Navigation verticale sombre, logo
Afriland en haut, liens filtres selon le role de l'utilisateur
courant, bloc utilisateur en bas avec nom, role et bouton de
deconnexion. Le role et la deconnexion viennent de useAuth(), qui
existe deja. Montre le fichier.
```

### Étape 2. Layout applicatif

```
Cree le layout global : sidebar a gauche, zone de contenu a droite
avec defilement independant, point d'insertion des routes enfants.

Montre le fichier.
```

### Étape 3. En-tête de page

```
Adapte l'en-tete de page a partir du patron de DOTTEL : sur-titre,
titre principal, separateur discret.

La charte reserve le rouge aux accents. Propose-moi le traitement du
separateur avant de le generaliser.

Montre le fichier.
```

### Étape 4. Configuration des routes

```
Configure les routes du module, toutes enfants du layout, selon le
tableau des roles et routes du guide de ce sous-sprint.

Cree une page provisoire par route a ce stade : un simple en-tete
avec le titre. Les ecrans reels viennent aux sous-sprints suivants.

Prevois aussi une page d'acces refuse et une page introuvable.

Montre le fichier de configuration des routes.
```

### Étape 5. Vérification visuelle

```bash
npm run dev
```

Vérifier l'affichage de la sidebar, la navigation entre les routes provisoires, l'absence d'erreur de compilation et de console.

## 6. Critères de validation

| Élément | Statut attendu |
|---|---|
| Sidebar affichée, structure de liens par rôle en place | Fait |
| Rôles et routes de ce module, non ceux de DOTTEL | Vérifié |
| Aucun lien affiché ne mène à un `403` (un compte par rôle) | Vérifié |
| `useAuth()` réutilisé, aucun second contexte d'authentification | Vérifié |
| Questions DRH/suivi et page de paramètres tranchées | Fait |
| Layout avec défilement indépendant | Vérifié |
| En-tête de page réutilisable | Fait |
| Toutes les routes du tableau configurées | Vérifié |
| Pages d'accès refusé et introuvable | Fait |
| Navigation fonctionnelle entre routes provisoires | Vérifié |
| Aucun résidu de route ou de rôle DOTTEL | Vérifié |
| `npm run build` réussi | Vérifié |

## 7. Points de vigilance

- Les rôles de DOTTEL n'existent pas ici. Un `CRH` laissé dans le filtrage produirait un lien jamais affiché, et masquerait l'absence du rôle réellement attendu.
- **Le filtrage par rôle est testable dès maintenant** : `useAuth()` existe. Le tester avec au moins un compte par rôle, en vérifiant qu'**aucun lien affiché ne mène à un `403`** — c'est tout l'objet de la révision du tableau de la section 4.
- Le filtrage des liens est un **confort**, pas une protection : c'est le backend qui refuse. Ne jamais en déduire qu'une route masquée est une route protégée.
- Ne pas créer les écrans réels dans ce sous-sprint : les pages provisoires suffisent.

## 8. Commit

```bash
git add .
git commit -m "sprint-7F.2: layout global et navigation

- Sidebar verticale avec filtrage par role, adaptee de dottel
- Layout applicatif et en-tete de page reutilisable
- Routes du module configurees, pages provisoires

Refs: charte graphique, roles applicatifs"
```

---

**Fin du Sprint 7F.2** — en attente de validation avant le Sprint 7F.3
