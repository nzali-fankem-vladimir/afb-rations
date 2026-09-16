# Protection des routes et traitement du 401 : conventions posées au Sprint 7F.3

**Date :** 16 septembre 2026
**Sprint :** 7F.3, authentification Keycloak et protection des routes
**Statut :** appliqué, à respecter par tous les sous-sprints frontend suivants (7F.4 et au-delà)

---

## 1. Toute route passe sous `ProtectedRoute`, qui porte la session ET le rôle

Jusqu'au 7F.2, `App.tsx` ne montait le routeur qu'une fois la session `CONNECTE`,
et `ProtectedRoute` ne vérifiait que le rôle. La protection dépendait donc d'un
emplacement (être sous `App`), pas d'un composant : une route montée ailleurs
aurait été publique sans que rien ne le signale.

Désormais `ProtectedRoute` contrôle, dans cet ordre :

1. **la session** — tant qu'elle n'est pas `CONNECTE`, il rend `EcranSession`
   (chargement, connexion, non habilité, erreur) **à la place** de la route, sans
   changer l'adresse. Une redirection pendant le chargement du profil renverrait
   un utilisateur authentifié vers la connexion et perdrait l'adresse demandée ;
2. **le rôle** (optionnel, prop `roles`) — lu dans le profil applicatif via
   `possedeRole`, jamais dans le jeton. Refus : `/acces-interdit`, sans déconnexion.

`AppRouter` place un `ProtectedRoute` sans rôle **à la racine de toutes les
routes**, `/acces-interdit` et la page introuvable comprises : aucune page du
module n'est publique.

**Pour les sous-sprints 7F.4 et suivants :** un nouvel écran s'ajoute dans
`LIENS_NAVIGATION` (la sidebar et la route en sont générées ensemble). Une route
de détail sans lien propre (ex. `/validation/:id`) se déclare **sous le même
`ProtectedRoute` que sa route parente**, jamais à côté. Masquer un lien ne
protège rien.

## 2. Pas de redirection automatique vers Keycloak (option A, confirmée par l'utilisateur)

Sans session, l'écran affiche un bouton « Se connecter » (redirection Keycloak,
`check-sso`). Choix du Sprint 0.4 maintenu : la redirection reste un geste
volontaire, et un Keycloak injoignable produit un message du module plutôt
qu'une page d'erreur du navigateur. L'adresse demandée est conservée
(`redirectUri: window.location.href`).

## 3. Un 401 ne déclenche une reconnexion que si AUCUN jeton n'a été joint

Défaut trouvé à la vérification de l'intercepteur : tout 401 relançait
`keycloak.login()`. Or le backend refuse un jeton **frais et valide** quand
l'émetteur ou l'audience ne correspondent pas (`issuer-uri`, `audiences:
rations-api`) — par exemple Keycloak ouvert en `127.0.0.1` au lieu de
`localhost`. Keycloak ayant une session, il renvoyait aussitôt au module, qui
rejouait le même 401 : **boucle de redirection infinie, sans message**.

Règle :

| 401 reçu | Signification | Traitement |
| --- | --- | --- |
| aucun en-tête `Authorization` joint | `updateToken` a échoué, session perdue | reconnexion Keycloak (une seule, même avec plusieurs appels concurrents) |
| jeton joint et refusé | configuration (émetteur, audience) | aucune reconnexion ; journal `JETON REFUSE PAR LE BACKEND`, erreur rendue à l'appelant ; au chargement du profil, état `ERREUR` cause `JETON_REFUSE` |

**Ne jamais revenir à « 401 ⇒ login » sans condition**, fût-ce pour « simplifier ».

Le 403 ne déconnecte jamais. Les trois refus en 403 ont chacun leur libellé dans
`messagesErreur.ts` (`ACCES_REFUSE`, `UTILISATEUR_NON_HABILITE`,
`SEPARATION_TACHES`) : tout nouvel écran les rend par `AffichageErreur`.

## 4. Trois causes d'erreur de session, trois messages

`AuthContexte.causeErreur` distingue `FOURNISSEUR_INJOIGNABLE` (Keycloak muet au
démarrage — auparavant l'application retombait sur un bouton « Se connecter »
voué à l'échec), `PROFIL_INDISPONIBLE` (service Identité muet) et
`JETON_REFUSE` (§3). Le contexte expose aussi `codeUnite` au même niveau que
`role`, tous deux issus de `GET /identite/moi`.

## 5. Le jeton reste en mémoire — ce que `keycloak-js` écrit malgré tout

Aucun `localStorage`, `sessionStorage` ni cookie dans `src/`. `keycloak-js`
26.2.4 écrit en revanche une entrée **`kc-callback-<state>`** dans
`localStorage` (state, nonce, redirectUri, vérificateur PKCE) le temps de
l'aller-retour vers Keycloak : supprimée au retour, expirée au bout d'une heure.
**Ce n'est pas un jeton** — ne pas la prendre pour une fuite lors d'un audit des
outils du navigateur. Les jetons sont des propriétés en mémoire de l'objet Keycloak.

## 6. `routeAccueil` ne se replie jamais sur `/`

La route index redirige vers `routeAccueil(role)`. Un repli sur `/` pour un rôle
sans lien produisait une redirection `/` → `/` sans fin. Repli sur
`/acces-interdit`.
