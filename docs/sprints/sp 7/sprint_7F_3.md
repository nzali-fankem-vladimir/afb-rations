# SPRINT 7F.3

## Authentification Keycloak et protection des routes

*Module Paiement des Rations et du Transport de la Garde Armée — Frontend*

| | |
|---|---|
| **Objet** | Brancher le SSO Keycloak réel, exposer l'utilisateur courant, protéger les routes |
| **Livrable** | Contexte d'authentification, redirection Keycloak, intercepteur de jeton, routes protégées |
| **Durée** | Une journée |
| **Prérequis** | Sprint 7F.2 validé et commité |
| **Sprint suivant** | 7F.4, écrans de saisie |

## Réutilisation du projet DOTTEL

Source : `[CHEMIN_PROJET_DOTTEL]`

**C'est le sous-sprint où DOTTEL se transpose le moins.** DOTTEL simulait Keycloak par un appel à une route de login du backend, avec un formulaire matricule et mot de passe. Ce module utilise le SSO réel : la connexion est une redirection, il n'y a ni formulaire, ni mot de passe, ni route de login côté backend.

Ce qui reste utile de DOTTEL : l'idée d'un contexte global exposant l'utilisateur courant et ses rôles, la protection de routes par un composant enveloppant, et l'intercepteur ajoutant le jeton aux appels.

Ce qui ne se transpose pas : la page de connexion, l'appel à une route de login, et toute logique de stockage d'un jeton obtenu par mot de passe.

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Ensemble du sous-sprint | Opus | Élevé |

**Changement manuel requis.** Ce sous-sprint conditionne la sécurité de toute l'interface : basculer en Opus effort élevé.

## 2. Outil de cartographie

```
py -3.14 -m graphify update .
```

## 3. Contexte

Le Sprint 0.4 a mis en place le flux Authorization Code avec PKCE côté frontend, dans sa forme minimale, pour vérifier que la chaîne fonctionnait. Ce sous-sprint le consolide et le branche sur l'interface : contexte global, protection des routes, filtrage effectif des liens de la sidebar.

Le principe posé dans CLAUDE.md ne souffre pas d'exception : **le module ne gère aucun mot de passe.** Toute réintroduction d'un formulaire de connexion est un écart interdit.

## 4. Objectifs

- Contexte d'authentification exposant l'utilisateur courant, son rôle et son code unité
- Connexion par redirection vers Keycloak, déconnexion propre
- Intercepteur ajoutant le jeton aux appels et traitant l'expiration
- Composant de protection de routes, filtrant par rôle
- Filtrage effectif des liens de la sidebar

## 5. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis CLAUDE.md, section 10 sur l'authentification et
section 15 sur les erreurs interdites. Confirme en 3 lignes ce qui
est interdit en matiere d'authentification.

CONTEXTE DE CETTE SESSION : Sprint 7F.3, authentification. Le flux
Authorization Code avec PKCE existe depuis le Sprint 0.4 dans sa
forme minimale. On le consolide et on le branche sur l'interface.

Tu as acces en lecture au projet DOTTEL :
[CHEMIN_PROJET_DOTTEL]

ATTENTION : DOTTEL SIMULAIT Keycloak avec un formulaire matricule et
mot de passe appelant une route de login du backend. Ce module
utilise le SSO REEL. Ne transpose ni sa page de connexion, ni son
appel de login, ni son stockage de jeton obtenu par mot de passe.
Ne reprends que l'idee d'un contexte global et la protection de
routes.

SERVICE CONCERNE : frontend.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Aucun formulaire avec champ mot de passe, aucun appel a une route
  de login.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : dresse l'inventaire de ce qui existe deja depuis le
Sprint 0.4 en matiere d'authentification, et liste ce qui manque pour
atteindre les objectifs de ce sous-sprint. Ne code rien encore.
```

### Étape 2. Contexte d'authentification

```
Consolide le contexte d'authentification :

Il expose l'utilisateur courant, son role, son code unite, l'etat de
chargement, et les fonctions de connexion et de deconnexion.

Le profil vient de GET /identite/moi, endpoint du Sprint 1.2, appele
apres validation du jeton. Le role affiche est celui du profil
applicatif, pas celui du jeton : c'est le module qui fait autorite
sur l'habilitation metier.

Question a trancher : ou conserver le jeton cote navigateur ? Presente
les options avec leurs consequences de securite, notamment vis-a-vis
d'une injection de script. Attends ma decision.

Montre le fichier.
```

### Étape 3. Intercepteurs

```
Complete le client axios :

- Intercepteur de requete ajoutant l'en-tete Authorization au format
  Bearer.
- Intercepteur de reponse traitant le 401 : le jeton a expire ou est
  invalide, l'utilisateur est renvoye vers la connexion.
- Traitement du 403 : l'utilisateur est authentifie mais non
  habilite. Il ne doit pas etre deconnecte, seulement informe.

La distinction entre 401 et 403 est importante : deconnecter sur un
403 ferait perdre sa session a un utilisateur qui a simplement
tente une action hors de ses droits.

Question : faut-il rafraichir le jeton avant expiration, ou laisser
l'utilisateur se reconnecter ? Presente les deux, j'arbitre.

Montre le fichier.
```

### Étape 4. Protection des routes

```
Cree le composant de protection de routes :

- Redirige vers la connexion si aucun utilisateur n'est authentifie.
- Verifie optionnellement une liste de roles autorises et redirige
  vers la page d'acces refuse sinon.
- Affiche un etat de chargement pendant la resolution du profil,
  plutot qu'un ecran vide ou une redirection prematuree.

Applique-le aux routes configurees au Sprint 7F.2, selon le tableau
des roles et routes.

Montre le composant et la configuration de routes mise a jour.
```

### Étape 5. Filtrage de la sidebar

```
Branche le filtrage des liens de la sidebar sur le role reel de
l'utilisateur courant, mecanisme pose au Sprint 7F.2.

Verifie qu'un utilisateur ne voit que les liens correspondant a son
role, et qu'un acces direct par l'url a une route non autorisee est
bloque par la protection de l'etape 4. Masquer un lien ne suffit
jamais : la protection doit doubler le filtrage.

Montre les modifications.
```

### Étape 6. Vérification

```bash
npm run dev
```

Tester la connexion avec chacun des six utilisateurs de test créés au Sprint 0.4, et vérifier pour chacun les liens visibles et les routes accessibles.

Tester ensuite un accès direct par l'URL à une route non autorisée, puis le comportement après expiration du jeton.

## 6. Critères de validation

| Élément | Statut attendu |
|---|---|
| Contexte exposant utilisateur, rôle et code unité | Fait |
| Profil issu de `GET /identite/moi` | Vérifié |
| Décision sur le stockage du jeton tranchée | Fait |
| Décision sur le rafraîchissement tranchée | Fait |
| Connexion par redirection, sans formulaire | Vérifié |
| 401 renvoyant à la connexion, 403 n'y renvoyant pas | Vérifié |
| Routes protégées, accès direct par URL bloqué | Vérifié |
| Sidebar filtrée par rôle réel | Vérifié |
| Six utilisateurs de test vérifiés | Fait |
| Aucun formulaire de mot de passe, aucun appel de login | Vérifié |

## 7. Points de vigilance

- **Aucun formulaire de connexion.** C'est l'écart le plus probable, DOTTEL en contenant un. Sa présence contredirait CLAUDE.md section 10.
- Masquer un lien ne protège pas une route. Le filtrage de la sidebar est un confort d'usage ; la protection de route est la sécurité. Les deux sont nécessaires.
- Ne pas déconnecter sur un 403. L'utilisateur est bien authentifié, il a seulement dépassé ses droits.
- Le rôle affiché vient du profil applicatif, pas du jeton. C'est le module qui décide de l'habilitation métier, pas l'annuaire.
- Afficher un état de chargement pendant la résolution du profil. Une redirection prématurée renverrait un utilisateur authentifié vers la connexion.

## 8. Commit

```bash
git add .
git commit -m "sprint-7F.3: authentification keycloak et protection des routes

- Contexte exposant l'utilisateur courant et son role applicatif
- Connexion par redirection, sans formulaire ni mot de passe
- Intercepteurs distinguant expiration et droit insuffisant
- Routes protegees et sidebar filtree par role

Refs: CLAUDE.md section 10, US-01, US-02"
```

---

**Fin du Sprint 7F.3** — en attente de validation avant le Sprint 7F.4
