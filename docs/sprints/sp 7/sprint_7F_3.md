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

Source, en lecture seule : `D:\stage afriland\formation spécialisée DSI\projet de gestion des absences\implementation\afb-dottel-mm\frontend`

Fichiers utiles : `src/pages/auth/AccesInterdit.jsx`, `PageIntrouvable.jsx`, `CallbackKeycloak.jsx`
(pour ses états d'erreur à l'écran uniquement). L'état des lieux complet est dans le guide
**7F.1**, section « Réutilisation du projet DOTTEL ».

> *Correction d'une version antérieure de ce guide*, qui affirmait que « DOTTEL simulait
> Keycloak par un appel à une route de login du backend, avec un formulaire matricule et mot
> de passe ». **C'est faux depuis son Sprint MM.7** : `POST /auth/login` a disparu, et DOTTEL
> utilise un vrai Keycloak en Authorization Code avec PKCE S256. Vérifié le 16 septembre 2026.

**DOTTEL et ce projet authentifient désormais par deux chemins différents**, tous deux
corrects :

| | Ce projet | DOTTEL |
| --- | --- | --- |
| Mécanisme | bibliothèque **`keycloak-js`** | PKCE **écrit à la main** (`crypto.subtle`, `sessionStorage`, page `/auth/callback`) |

**On garde `keycloak-js`, et on ne transpose pas le mécanisme de DOTTEL.** Il ferait la même
chose avec davantage de code à maintenir et à auditer, sur la partie la plus sensible de
l'interface. Ce qui se reprend de DOTTEL, ce sont ses **écrans** d'accès refusé et de page
introuvable, et la protection de routes par un composant enveloppant.

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

Le Sprint 0.4 a mis en place le flux Authorization Code avec PKCE côté frontend. **Il est allé plus loin que « la forme minimale »** que cette section annonçait à l'origine — vérifié dans le code le 16 septembre 2026 :

| Objectif de ce sous-sprint | État réel |
| --- | --- |
| Contexte exposant utilisateur, rôle, code unité | **Fait** — `contexts/AuthContext.tsx`, `hooks/useAuth.ts` : `etat`, `utilisateur`, `role`, `possedeRole(...)` |
| Profil issu de `GET /identite/moi`, rôle du module et non du jeton | **Fait** — `api/identiteApi.ts` |
| Connexion par redirection, déconnexion | **Fait** — `auth/fournisseurKeycloak.ts` (`check-sso`, `pkceMethod: 'S256'`) |
| Jeton ajouté aux appels | **Fait** — intercepteur de requête de `api/apiClient.ts` |
| 401 → reconnexion, 403 laissé à l'appelant | **Fait** — intercepteur de réponse |
| Jeton sans habilitation locale → état distinct | **Fait** — état `NON_HABILITE` sur un `403` de `/identite/moi` |
| **Protection des routes par rôle** | **À faire** |
| **Écrans `NON_HABILITE`, `ERREUR`, accès refusé** | **À vérifier / compléter** |
| Filtrage de la sidebar | Traité au 7F.2 |

**Ce sous-sprint se recentre donc sur la protection des routes et les écrans d'état.** Il ne
réécrit pas l'authentification.

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
Authorization Code avec PKCE existe depuis le Sprint 0.4, et il est
PLUS COMPLET que ce que ce guide annoncait a l'origine : contexte,
profil, intercepteurs et etat NON_HABILITE sont deja en place (voir
la section 3). On complete la protection des routes et les ecrans
d'etat. On NE REECRIT PAS l'authentification.

Tu as acces en LECTURE SEULE au frontend de reference DOTTEL :
D:\stage afriland\formation spécialisée DSI\projet de gestion des absences\implementation\afb-dottel-mm\frontend
N'ECRIS JAMAIS dans ce depot.

ATTENTION : DOTTEL utilise AUSSI un vrai Keycloak, mais avec un PKCE
ecrit a la main. Ce projet utilise la bibliotheque keycloak-js. Ne
transpose PAS le mecanisme d'authentification de DOTTEL. Reprends
seulement ses ecrans d'acces refuse et de page introuvable, et l'idee
de proteger les routes par un composant enveloppant.

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

Question : ou conserver le jeton cote navigateur ?

ELLE EST DEJA TRANCHEE DANS LE CODE : le jeton reste EN MEMOIRE, detenu
par keycloak-js, et apiClient.ts n'en conserve aucun ("Aucun jeton
n'est conserve ici : le fournisseur en reste le seul detenteur").
DOTTEL a pris la meme decision, par un autre chemin.
Ne rouvre pas l'arbitrage : VERIFIE que c'est toujours vrai (aucun
localStorage, aucun sessionStorage pour le jeton, recherche dans tout
src/), et presente-moi le resultat.

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
l'utilisateur se reconnecter ?

ELLE EST DEJA TRANCHEE DANS LE CODE : fournisseurKeycloak.ts appelle
updateToken(MARGE_RENOUVELLEMENT_SECONDES) avant chaque appel, et le
401 ne survient qu'apres l'echec de ce renouvellement. Les
intercepteurs 401 et 403 sont egalement en place.
VERIFIE-le, et ne modifie ces fichiers que si tu constates un defaut
-- en me le montrant d'abord.

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

**Tester l'écran `NON_HABILITE` avec `thomas_ndzana`**, le compte de contrôle du refus 403 : un jeton Keycloak parfaitement valide, sans profil ouvert dans le module. **S'y connecter est sans danger et constitue le test lui-même.** Ne jamais lui ouvrir de profil local, par aucun chemin — la vérification d'environnement rendrait alors `200` là où elle attend `403`, sans un mot d'explication (CLAUDE.md §15).

Tester ensuite un accès direct par l'URL à une route non autorisée, puis le comportement après expiration du jeton.

## 6. Critères de validation

| Élément | Statut attendu |
|---|---|
| Contexte exposant utilisateur, rôle et code unité | Fait |
| Profil issu de `GET /identite/moi` | Vérifié |
| Jeton en mémoire confirmé par recherche dans `src/` (aucun `localStorage`/`sessionStorage`) | Vérifié |
| Renouvellement par `updateToken` confirmé, authentification non réécrite | Vérifié |
| Écran `NON_HABILITE` testé avec `thomas_ndzana`, aucun profil ouvert | Vérifié |
| Connexion par redirection, sans formulaire | Vérifié |
| 401 renvoyant à la connexion, 403 n'y renvoyant pas | Vérifié |
| Routes protégées, accès direct par URL bloqué | Vérifié |
| Sidebar filtrée par rôle réel | Vérifié |
| Six utilisateurs de test vérifiés | Fait |
| Aucun formulaire de mot de passe, aucun appel de login | Vérifié |

## 7. Points de vigilance

- **Aucun formulaire de connexion.** Sa présence contredirait CLAUDE.md section 10. DOTTEL n'en contient plus depuis son Sprint MM.7 — mais il contient toujours un **champ mot de passe** dans `CreerUtilisateurPage.jsx`, risque traité au sous-sprint 7F.6.
- **Ne pas réécrire l'authentification.** Elle existe et fonctionne. Le risque de ce sous-sprint n'est plus l'absence, c'est le remplacement : transposer le PKCE manuel de DOTTEL à la place de `keycloak-js`.
- **Ne jamais ouvrir de profil local à `thomas_ndzana`.** Se connecter avec lui teste l'écran `NON_HABILITE` ; lui créer un profil détruit ce test en silence.
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
