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

Source : `[CHEMIN_PROJET_DOTTEL]`

La sidebar verticale sombre de DOTTEL, sa structure de liens filtrés par rôle et son bloc utilisateur en bas se transposent directement, à trois conditions : typage TypeScript, rôles de ce module, routes de ce module.

Les rôles diffèrent entièrement. DOTTEL utilise EMPLOYE, ARH, CRH, DRH et ADMIN ; ce module utilise AGENT_UNITE, CHEF_UNITE_DA, DIRECTEUR_RESEAU_DR, ARH, DRH et ADMIN. Ne pas transposer les rôles, seulement le mécanisme de filtrage.

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

Le filtrage des liens par rôle est posé ici mais ne devient effectif qu'au sous-sprint 7F.3, quand l'utilisateur courant sera connu. À ce stade, les pages sont des espaces réservés.

## 4. Rôles et routes prévues

| Rôle | Routes accessibles |
|---|---|
| AGENT_UNITE | `/processus`, `/saisie` |
| CHEF_UNITE_DA | `/validation`, `/suivi` |
| DIRECTEUR_RESEAU_DR | `/validation`, `/suivi` |
| ARH | `/grilles`, `/suivi`, `/rapports` |
| DRH | `/grilles`, `/suivi` |
| ADMIN | `/admin/utilisateurs`, `/admin/parametres`, `/admin/audit` |

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

Tu as acces en lecture au projet DOTTEL :
[CHEMIN_PROJET_DOTTEL]

Reprends de sa sidebar la LOGIQUE : structure de liens avec tableau
de roles, filtrage, menu utilisateur, fermeture au clic exterieur.
N'en reprends NI les roles, NI les routes, qui sont ceux d'un autre
module.

SERVICE CONCERNE : frontend.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- TypeScript strict, aucun any.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : cree la sidebar. Navigation verticale sombre, logo
Afriland en haut, liens filtres selon le role de l'utilisateur
courant, bloc utilisateur en bas avec nom, role et bouton de
deconnexion. Le role sera fourni par le contexte du Sprint 7F.3 :
prevois la dependance sans l'implementer. Montre le fichier.
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
| Layout avec défilement indépendant | Vérifié |
| En-tête de page réutilisable | Fait |
| Toutes les routes du tableau configurées | Vérifié |
| Pages d'accès refusé et introuvable | Fait |
| Navigation fonctionnelle entre routes provisoires | Vérifié |
| Aucun résidu de route ou de rôle DOTTEL | Vérifié |
| `npm run build` réussi | Vérifié |

## 7. Points de vigilance

- Les rôles de DOTTEL n'existent pas ici. Un `CRH` laissé dans le filtrage produirait un lien jamais affiché, et masquerait l'absence du rôle réellement attendu.
- Le filtrage par rôle n'est pas testable avant le 7F.3. Le poser correctement maintenant évite d'y revenir.
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
