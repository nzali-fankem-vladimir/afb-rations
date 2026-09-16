# Écrans de saisie de l'agent : appels multi-services et architecture de l'écran

**Date :** 16 septembre 2026
**Sprint :** 7F.4, écrans de saisie de l'agent d'unité
**Statut :** appliqué, à respecter par tous les sous-sprints frontend suivants

---

## 1. Un client Axios par service backend, en attendant la passerelle

**Constat vérifié en tête de sprint** : la passerelle (`backend/gateway`) et le
registre (`backend/registry`) existent en code mais ne tournent pas en local
(ports 8080 et 8761 muets, vérifié par `netstat`). `frontend/.env` pointait un
unique `apiClient` sur le seul service Identité (8081). Ce sprint est le
premier à devoir appeler trois services différents depuis l'écran (Saisie 8082,
Workflow 8084, Reporting 8085), sans passerelle pour les unifier.

**Décision, tranchée avec l'utilisateur** : un client Axios par service, chacun
construit par la même fabrique `creerClientApi(baseURL)` (intercepteurs jeton
et 401 partagés, factorisés depuis `apiClient.ts`), avec sa propre variable
d'environnement — `VITE_API_SAISIE_URL`, `VITE_API_WORKFLOW_URL`,
`VITE_API_REPORTING_URL`, en plus de `VITE_API_BASE_URL` (Identité, inchangée).

**Pour les sous-sprints suivants qui appellent un nouveau service** (Grilles au
7F.6, Audit au 7F.9, Transmission si jamais exposé) : ajouter une variable
`VITE_API_<SERVICE>_URL` et appeler `creerClientApi(import.meta.env.VITE_API_..._URL)`
dans le module d'API du service, jamais réutiliser un client d'un autre
service ni recréer une logique d'intercepteurs locale. Le jour où la passerelle
route réellement, il suffira de faire pointer toutes ces variables vers la même
URL — aucun changement de code.

## 2. Route de détail `/saisie/:idProcessus`, bare `/saisie` redirige vers `/processus`

`LIENS_NAVIGATION` (Sprint 7F.2) ne portait que des routes plates, une par
rôle-lien. Ce sprint introduit le premier paramètre de route du module :
`/saisie/:idProcessus`, la page de saisie journalière d'un processus précis.

Suivant la doctrine du Sprint 7F.3 (§1, route de détail sous le même
`ProtectedRoute` que sa route parente), elle est déclarée à l'intérieur du même
bloc que le lien `/saisie` dans `AppRouter.tsx`, jamais à côté. Le lien
`/saisie` lui-même (sans identifiant) n'a rien à afficher : il redirige vers
`/processus`, où l'agent choisit d'abord un dossier. **Pour tout futur écran de
détail** (ex. `/validation/:id` au 7F.5), reprendre le même motif : route
enfant du même bloc, jamais un second `<Route>` isolé.

## 3. Un seul écran, deux onglets (saisie journalière / consultation et soumission)

Le guide décrit deux écrans (étapes 3 et 6), mais le parcours réel de l'agent
est un aller-retour permanent entre saisir et vérifier avant de soumettre.
Créer une seconde route aurait ajouté une navigation que
`LIENS_NAVIGATION`/`ProtectedRoute` n'anticipent pas, pour un geste qui reste
sur le même dossier. **Décision** : une seule page (`SaisieProcessusPage`),
deux onglets locaux (`SaisieJournaliereTab`, `ConsultationEtatTab`), le statut
du processus (badge, `estStatutModifiable`) partagé entre les deux.

**Pour le Sprint 7F.7** (état complémentaire, qui réutilise explicitement cet
écran selon le guide) : la page ne connaît que `idProcessus`, `dateDebut`,
`dateFin` et le statut — aucune hypothèse sur `typeProcessus` n'y est câblée,
elle devrait donc fonctionner sans modification pour un état COMPLEMENTAIRE.
Point à vérifier au 7F.7 : le message `DOUBLON_INTER_ETATS`, plus fréquent en
régularisation, est déjà distingué de `DOUBLON_LIGNE` dans
`messagesErreur.ts` depuis le 7F.1.

## 4. `estStatutModifiable` : copie frontend de `StatutProcessusEnum.estModifiable()`

`src/utils/statutProcessus.ts` recopie la liste fermée et positive du service
Saisie (`EN_COURS_SAISIE`, `RETOURNE`) pour désactiver les actions côté
interface avant même l'appel (guide 7F.4, étape 5 : « désactiver une action
impossible vaut mieux que la laisser échouer »). **Cette copie ne remplace
jamais le contrôle serveur** : le frontend ne fait confiance à cette valeur que
pour l'ergonomie (griser un bouton), jamais pour autoriser une écriture — le
backend revérifie à chaque appel, sans cache, comme documenté dans
`EtatModifiableService.java`. Si le service Saisie ajoute un jour un troisième
statut modifiable, cette liste devra être mise à jour à la main : aucun test de
garde ne la lie au backend, faute d'endpoint qui l'exposerait.

---

**Références :** guide `sprint actuel.md` (7F.4) ; CLAUDE.md sections 2, 6, 11 ;
`docs/decisions/2026-09-16-protection-des-routes-et-traitement-du-401.md` (doctrine
de routage héritée) ; `docs/decisions/2026-09-16-socle-frontend-composants-de-base.md`
(table de correspondance des erreurs, format de pagination).
