# Résumé Sprint 7F.3 — Authentification Keycloak et protection des routes (frontend)

**Date :** 16 septembre 2026
**Objet du guide :** brancher le SSO Keycloak, exposer l'utilisateur courant,
protéger les routes
**Ce qui a réellement été fait :** l'authentification du Sprint 0.4 a été
**vérifiée, pas réécrite** (`keycloak-js` conservé, PKCE manuel de DOTTEL non
transposé). Le sprint a déplacé le contrôle de session dans le composant de
protection de routes, refait les écrans d'état, et corrigé **deux boucles de
redirection infinies** trouvées à la relecture.

---

## En une phrase

Toutes les routes du module passent désormais par un `ProtectedRoute` qui
contrôle la session puis le rôle du profil applicatif. Un 401 ne relance plus
la connexion que lorsque la session est réellement perdue, et chaque état de
session (chargement, connexion, non habilité, trois causes d'erreur) a son
écran, sans aucun formulaire.

---

## Arbitrages tranchés avec l'utilisateur

### 1. Sans session : bouton « Se connecter », pas de redirection automatique (option A)

Choix du Sprint 0.4 maintenu (`check-sso`, geste volontaire). Un Keycloak
injoignable produit un message du module et non une page d'erreur du
navigateur. L'adresse demandée est conservée à travers l'aller-retour Keycloak.

### 2. Correctif de l'intercepteur 401 : appliqué

Défaut montré avant toute modification, comme le guide l'exige : tout 401
relançait `keycloak.login()`, y compris quand le backend refuse un jeton frais
(émetteur ou audience non reconnus, par exemple Keycloak ouvert en `127.0.0.1`).
Keycloak renvoyant aussitôt au module, **la page bouclait sans fin et sans
message**. Désormais :
- **aucun jeton joint** (renouvellement échoué) : reconnexion, une seule même
  avec plusieurs appels concurrents ;
- **jeton joint et refusé** : aucune reconnexion ; au chargement du profil,
  état `ERREUR` cause `JETON_REFUSE` avec un message clair.

---

## Ce qui a été livré, étape par étape

| Étape | Contenu |
| --- | --- |
| 1 | Inventaire : contexte, profil `/identite/moi`, `keycloak-js`, intercepteurs, `NON_HABILITE` déjà en place (0.4) ; `ProtectedRoute`, sidebar filtrée, pages d'accès interdit et introuvable déjà en place (7F.2). Cinq manques relevés. |
| 2 | Vérification du stockage du jeton (voir plus bas). Contexte complété : `codeUnite` exposé au même niveau que `role`, `causeErreur` (`FOURNISSEUR_INJOIGNABLE`, `PROFIL_INDISPONIBLE`, `JETON_REFUSE`). Keycloak injoignable au démarrage → `ERREUR` au lieu d'un bouton de connexion voué à l'échec. |
| 3 | Intercepteurs vérifiés (Bearer, `updateToken(30)`, 403 sans déconnexion). Correctif 401 (arbitrage 2). Libellés `ACCES_REFUSE` et `UTILISATEUR_NON_HABILITE` ajoutés à `messagesErreur.ts`. |
| 4 | `ProtectedRoute` contrôle la session (rend `EcranSession` à la place de la route, sans redirection prématurée) puis, optionnellement, le rôle. `AppRouter` : un `ProtectedRoute` sans rôle à la racine de **toutes** les routes. Nouvel `EcranSession` (logo, `Card`, `Alert`, `Button`, présentation reprise de DOTTEL). `App.tsx` réduit à `<AppRouter />`. |
| 5 | Sidebar et routes confirmées sur la même source (`LIENS_NAVIGATION` + `possedeRole`). **Seconde boucle corrigée** : `routeAccueil` se repliait sur `/`, que la route index redirige vers `routeAccueil` — `/` → `/` sans fin pour un rôle sans lien. Repli sur `/acces-interdit`. |
| 6 | Vérifications automatisées (ci-dessous) ; vérification visuelle en navigateur laissée à l'utilisateur. |

---

## Vérification des critères de validation du guide

| Critère | Statut | Preuve |
| --- | --- | --- |
| Contexte exposant utilisateur, rôle et code unité | ✅ Fait | `AuthContexte` : `utilisateur`, `role`, `codeUnite` |
| Profil issu de `GET /identite/moi` | ✅ Vérifié | appel réel pour les six comptes : `200`, rôle et unité conformes à `V1000` |
| Jeton en mémoire, aucun `localStorage`/`sessionStorage` dans `src/` | ✅ Vérifié | recherche dans `src/` : zéro occurrence (y compris `document.cookie`). `keycloak-js` n'écrit que `kc-callback-<state>` (state, nonce, vérificateur PKCE), supprimé au retour de Keycloak — pas un jeton |
| Renouvellement par `updateToken`, authentification non réécrite | ✅ Vérifié | `fournisseurKeycloak.ts` inchangé |
| Écran `NON_HABILITE` avec `thomas_ndzana`, aucun profil ouvert | ✅ Vérifié côté API · ⏳ écran à voir en navigateur | `/identite/moi` → `403 UTILISATEUR_NON_HABILITE` ; `SELECT count(*)` = **0 avant et après** |
| Connexion par redirection, sans formulaire | ✅ Vérifié | bouton → `keycloak.login()` |
| 401 renvoyant à la connexion, 403 n'y renvoyant pas | ✅ Vérifié dans le code · ⏳ expiration à éprouver en navigateur | jeton altéré / absent → `401` réels ; 403 non intercepté |
| Routes protégées, accès direct par URL bloqué | ✅ Fait · ⏳ à éprouver en navigateur | `ProtectedRoute` sur toutes les routes |
| Sidebar filtrée par rôle réel | ✅ Vérifié | même table et même `possedeRole` que les routes |
| Six utilisateurs de test vérifiés | ✅ côté API · ⏳ liens visibles à confirmer en navigateur | 6 × `200` |
| Aucun formulaire de mot de passe, aucun appel de login | ✅ Vérifié | recherche `password` / `mot de passe` / `login` : seuls le champ `login` (nom d'utilisateur), `keycloak.login()` et des commentaires |

Contrôles techniques : `tsc -b`, `oxlint` et `npm run build` réussis. CORS
depuis `http://localhost:5173` vers le service Identité : pré-vol `200`.

---

## Fichiers créés ou modifiés

**Créés :** `frontend/src/pages/EcranSession.tsx`,
`docs/decisions/2026-09-16-protection-des-routes-et-traitement-du-401.md`, ce résumé.

**Modifiés :** `frontend/src/router/ProtectedRoute.tsx`,
`frontend/src/router/AppRouter.tsx`, `frontend/src/App.tsx`,
`frontend/src/api/apiClient.ts`, `frontend/src/contexts/AuthContext.tsx`,
`frontend/src/contexts/authContexte.ts`,
`frontend/src/components/layout/navigation.ts`,
`frontend/src/utils/messagesErreur.ts`.

**Non touchés :** `fournisseurKeycloak.ts`, `FournisseurAuthentification.ts`,
`identiteApi.ts`, `Sidebar.tsx`, et le dépôt DOTTEL (lecture seule).

---

## Décision consignée pour les sprints suivants

`docs/decisions/2026-09-16-protection-des-routes-et-traitement-du-401.md` :
toute nouvelle route (7F.4 et après) passe sous `ProtectedRoute`, les routes de
détail sous le même que leur parente ; ne jamais revenir à « 401 ⇒ login » sans
condition ; `kc-callback-*` n'est pas une fuite de jeton.

---

## Vérification visuelle attendue de l'utilisateur

Environnement déjà en place (constaté, aucun lancement nécessaire) : Docker
(`rations-postgres`, `rations-kafka`, `dottel-keycloak`), service Identité
(8081), serveur de développement (5173). Mot de passe des comptes de dev :
voir `infra/keycloak/README.md`.

1. Ouvrir `http://localhost:5173` dans une fenêtre privée → écran « Se
   connecter » (logo, aucun champ). Cliquer → page Keycloak.
2. Pour chacun des six comptes : vérifier les liens de la sidebar et la page
   d'accueil contre le tableau de l'étape 5, taper une URL interdite → « Accès
   interdit » **sans déconnexion**, puis se déconnecter.
3. Fenêtre privée, taper directement `http://localhost:5173/grilles` avant
   toute connexion → écran de connexion ; se connecter en `claire_nkolo` → retour
   sur `/grilles`.
4. `thomas_ndzana` → écran « Aucun profil ouvert dans ce module », bouton « Se
   déconnecter ». **Ne rien lui créer.**
5. Expiration (facultatif) : session fermée dans la console Keycloak et durée
   du jeton raccourcie temporairement → prochain appel renvoie à la connexion ;
   **remettre la durée à 28800** ensuite.
6. Console et onglet réseau du navigateur : aucune boucle de redirection,
   aucune erreur.

Si des ajustements sont faits après ce test, ce résumé sera mis à jour avant le commit.

---

## Prochaine étape

Sprint 7F.4 : écrans de saisie.
