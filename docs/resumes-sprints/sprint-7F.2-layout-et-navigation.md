# Résumé Sprint 7F.2 — Layout global et navigation (frontend)

**Date :** 16 septembre 2026
**Objet du guide :** structure de page commune, navigation filtrée par rôle,
configuration des routes
**Ce qui a réellement été fait :** l'intégralité du guide (5 étapes), plus une
vérification demandée en cours de route sur l'emplacement du traitement de la
page de paramètres administrateur

---

## En une phrase

Le frontend dispose désormais d'une coquille applicative complète — sidebar
filtrée par rôle, layout à défilement indépendant, en-tête de page réutilisable
— et d'un routage entier vers des pages provisoires, une par route du tableau
de rôles du guide ; aucun écran fonctionnel n'est encore construit.

---

## Arbitrages tranchés avec l'utilisateur

### 1. DRH sans `/suivi` : confirmé

Le contrôleur Reporting backend n'autorise pas le rôle DRH. Recommandation
suivie : l'interface reste alignée sur le backend, aucun lien `/suivi` pour
DRH. Un désaccord métier se corrigerait côté backend, pas en frontend.

### 2. Page de paramètres administrateur : vérification demandée, document déjà existant retrouvé

L'utilisateur a demandé de vérifier si un sous-sprint frontend traitait déjà
ce sujet avant de créer ou modifier un document. **Trouvé** :
`docs/sprints/sp 7/sprint_7F_6.md` (« Grilles tarifaires et administration »)
porte déjà, à son Étape 6, l'arbitrage exact (aucun endpoint d'écriture,
trois options posées : reporter l'écran / créer un endpoint backend / écran
de consultation seule). Aucune création ni modification de document n'était
donc nécessaire — le sujet est déjà couvert au bon endroit, à trancher quand
ce sprint sera atteint.

### 3. Traitement du séparateur d'en-tête : validé

Proposition reprise telle quelle de DOTTEL — trait vertical décoratif
(dégradé `primary-500` vers transparent, `aria-hidden`), jugée conforme à la
charte (rouge réservé aux accents, ici un accent ponctuel de quelques pixels,
pas un aplat). Validée sans changement.

---

## Ce qui a été livré, étape par étape

| Étape | Contenu |
| --- | --- |
| 1 | `Sidebar` (navigation verticale sombre, logo Afriland, liens filtrés par `possedeRole()`, bloc utilisateur avec initiales/nom/rôle, menu de déconnexion avec fermeture au clic extérieur et à `Échap`) ; `Logo` ; table `LIENS_NAVIGATION` (source unique rôles↔routes, réutilisée par la sidebar et le routage) |
| 2 | `AppLayout` (sidebar fixe + zone de contenu en défilement indépendant, point d'insertion `<Outlet />`) |
| 3 | `PageHeader` (sur-titre, titre, séparateur décoratif validé à l'étape ci-dessus) |
| 4 | `AppRouter` (routes générées depuis `LIENS_NAVIGATION`, une par rôle-route du tableau du guide), `ProtectedRoute` (redirige vers `/acces-interdit` si le rôle courant n'a pas accès), pages `AccesInterdit` et `PageIntrouvable` (adaptées de DOTTEL, bouton de retour vers `routeAccueil(role)`), `PageProvisoire` (espace réservé générique) |
| 5 | Vérification de compilation (`npm run build`, `oxlint`) faite par l'agent ; vérification visuelle en navigateur laissée à l'utilisateur (voir plus bas) |

**Intégration** : `App.tsx` rend désormais `<AppRouter />` quand
`etat === 'CONNECTE'`, sinon l'écran de connexion existant (inchangé) ;
`main.tsx` enveloppe l'application dans `BrowserRouter`. Aucun second contexte
d'authentification créé — `useAuth()` du Sprint 0.4 est la seule source.

**Asset ajouté** : logo Afriland (`logo-afriland.png`, `logo-afriland-embleme.png`)
copié depuis DOTTEL en lecture seule (même geste que la police au Sprint
7F.1) vers `src/assets/logo/`. Token `--text-xxs` ajouté au thème
(`index.css`), absent depuis le Sprint 7F.1 mais nécessaire aux libellés en
petites majuscules de la sidebar et de l'en-tête.

---

## Vérification des critères de validation du guide

| Critère | Statut |
| --- | --- |
| Sidebar affichée, structure de liens par rôle en place | ✅ Fait |
| Rôles et routes de ce module, non ceux de DOTTEL | ✅ Vérifié (aucune trace de `CRH`/`EMPLOYE`, routes conformes au tableau du guide) |
| Aucun lien affiché ne mène à un `403` (un compte par rôle) | ⏳ À vérifier visuellement par l'utilisateur (voir ci-dessous) |
| `useAuth()` réutilisé, aucun second contexte d'authentification | ✅ Vérifié |
| Questions DRH/suivi et page de paramètres tranchées | ✅ Fait |
| Layout avec défilement indépendant | ✅ Vérifié (`overflow-y-auto` sur la zone de contenu, sidebar `h-screen shrink-0`) |
| En-tête de page réutilisable | ✅ Fait |
| Toutes les routes du tableau configurées | ✅ Vérifié (8 routes, générées depuis la même table que la sidebar) |
| Pages d'accès refusé et introuvable | ✅ Fait |
| Navigation fonctionnelle entre routes provisoires | ⏳ À vérifier visuellement par l'utilisateur |
| Aucun résidu de route ou de rôle DOTTEL | ✅ Vérifié |
| `npm run build` réussi | ✅ Vérifié |

---

## Fichiers créés ou modifiés

**Composants layout** (`src/components/layout/`) : `Sidebar.tsx`,
`AppLayout.tsx`, `PageHeader.tsx`, `Logo.tsx`, `navigation.ts`
(table `LIENS_NAVIGATION` + `routeAccueil`).

**Router** (`src/router/`) : `AppRouter.tsx`, `ProtectedRoute.tsx`.

**Pages** (`src/pages/`) : `AccesInterdit.tsx`, `PageIntrouvable.tsx`,
`PageProvisoire.tsx`.

**Modifiés** : `src/App.tsx` (rend `AppRouter` une fois connecté),
`src/main.tsx` (`BrowserRouter`), `src/index.css` (`--text-xxs`).

**Assets** : `src/assets/logo/logo-afriland.png`,
`src/assets/logo/logo-afriland-embleme.png` (copiés de DOTTEL en lecture
seule).

**Documentation** : ce résumé. Aucune décision nouvelle à consigner dans
`docs/decisions/` — les arbitrages de ce sprint sont déjà couverts par le
guide lui-même ou par un sprint existant.

---

## Vérification visuelle attendue de l'utilisateur

Environnement déjà en place au moment du sprint (aucun lancement nécessaire) :
Docker actif (`rations-postgres`, `rations-kafka`, `dottel-keycloak`),
service Identité (8081) démarré, serveur de développement frontend déjà lancé
sur le port 5173.

1. Ouvrir `http://localhost:5173`, se connecter.
2. Vérifier que la sidebar affiche les bons liens pour le rôle du compte
   utilisé, et qu'aucun lien ne mène à un `403`.
3. Naviguer entre les routes provisoires : layout stable, défilement de la
   zone de contenu indépendant de la sidebar, en-tête affiché sur chaque page.
4. Tester le menu utilisateur (ouverture, fermeture au clic extérieur, à
   `Échap`, déconnexion).
5. Si possible, répéter avec un compte par rôle (6 rôles) pour couvrir le
   critère « aucun lien ne mène à un 403 ». À défaut de six comptes
   disponibles, tester au moins deux ou trois rôles différents.
6. Vérifier l'absence d'erreur dans la console et dans l'onglet réseau du
   navigateur.

Si des ajustements sont nécessaires après ce test, ils seront faits et ce
résumé sera mis à jour en conséquence avant le commit.

---

## Prochaine étape

Sprint 7F.3 : authentification Keycloak et protection des routes (branchement
réel déjà largement anticipé par ce sprint — reste à couvrir : intercepteur
de renouvellement de jeton en échec, cas d'accès direct par URL avant
résolution de session).
