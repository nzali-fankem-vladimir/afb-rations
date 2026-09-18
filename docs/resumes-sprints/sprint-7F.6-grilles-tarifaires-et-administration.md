# Résumé Sprint 7F.6 — Grilles tarifaires et administration

**Date :** 17 septembre 2026
**Objet du guide :** parcours de l'Analyste RH, de la Directrice RH et de
l'administrateur — grilles tarifaires, utilisateurs, paramètres système,
journal d'audit
**Ce qui a réellement été fait :** l'intégralité des 7 étapes du guide, plus un
ajout backend scopé à l'étape 6 (endpoint d'écriture des paramètres système),
tranché avec l'utilisateur en cours de sprint

---

## En une phrase

L'Analyste RH propose des grilles tarifaires, la Directrice RH les valide ou
les rejette avec l'écart de montant affiché comme aide à la décision,
l'administrateur attribue des habilitations sans jamais créer de compte et
peut désormais modifier le seuil d'aiguillage, le délai de régularisation et
le compte de charge par un formulaire plutôt qu'un `UPDATE` SQL direct, et les
trois rôles habilités (ARH, DRH, ADMIN) consultent un journal d'audit
strictement en lecture qui signale lui-même ses propres angles morts.

---

## Point d'arrêt en ouverture : prérequis non rempli

Le guide exige "Sprint 7F.5 validé et commité". Le statut git en début de
session montrait les fichiers du 7F.5 (écrans de validation, correctifs
backend Reporting/Saisie) comme non commités — l'état exact laissé par le
résumé 7F.5, qui attendait encore la vérification visuelle avant commit.
Question posée à l'utilisateur ; réponse : "revérifie, c'est clos à présent."
Vérification faite : `10f0365 sprint-7F.5: confirmation du parcours complet
par l'utilisateur` était déjà commité, arbre de travail propre. Sprint démarré
sur cette confirmation.

---

## Ce qui a été vérifié avant tout codage

**Cycle de vie d'une grille (RG-14), confirmé contre le contrôleur réel**
(`GrilleController.java`, `GestionnaireErreursApi.java`) : une grille naît
`EN_ATTENTE_DRH` (création et soumission en un seul appel `POST /grilles`,
aucun `BROUILLON` jamais persisté depuis le Sprint 2.2), la DRH la fait
basculer `ACTIVE` — fermant l'ancienne grille du couple **à la veille** de la
nouvelle date de début, y compris quand cette date est future (fermeture
programmée) — ou `REJETEE` avec motif obligatoire, sans toucher à la grille en
vigueur.

**Piège relevé et évité** : une grille close conserve le statut `ACTIVE`, avec
seulement `dateFin` renseignée — la fermeture est une borne, pas un statut
(`ValidationGrilleResponse.java`). La "grille active courante" d'un couple est
donc celle au statut `ACTIVE` **dont `dateFin` est nulle**, jamais simplement
la dernière `ACTIVE` reçue. Toute autre `ACTIVE` appartient déjà à l'historique.

**Trois corrections de fond du guide, vérifiées contre le code avant
codage** (section "Ce qui a changé" du guide) :
1. Aucun endpoint d'écriture des paramètres système n'existait au départ —
   confirmé en lisant `ParametreController.java` : seul `GET
   /parametres/fonctionnalites` existait, et il ne renvoie qu'un booléen
   `rattrapageActif`, rien d'autre.
2. Le journal d'audit est ouvert à ARH, DRH et ADMIN (`AuditController.java`,
   `@PreAuthorize("hasAnyRole('ARH', 'DRH', 'ADMIN')")`), pas réservé à
   l'administrateur.
3. Une grille active ne bloque pas une proposition (RG-14) : les deux seuls
   refus 409 sont `GRILLE_EN_ATTENTE_EXISTANTE` et `GRILLE_ACTIVE_EXISTANTE`
   (anti-datage), jamais "une grille active existe déjà".

---

## Arbitrage tranché avec l'utilisateur (étape 6)

**Le problème posé par le guide** : aucun endpoint d'écriture n'existe pour le
seuil d'aiguillage, le délai de régularisation et le compte de charge — seul
`UPDATE` SQL direct. Trois options proposées : reporter, créer l'endpoint
(hors périmètre frontend), ou consultation seule.

**Discussion demandée par l'utilisateur avant de trancher** : puisque les
sprints backend fonctionnels sont tous terminés (il ne reste que les Sprints 8
— déploiement — et 9 — recette), reporter rendrait ce manque **permanent**
plutôt que temporaire. Une proposition concise a été formulée : petit ajout
backend scopé (endpoint `PUT /parametres/{code}`, rôle ADMIN, validation
stricte reprise du Sprint 4.3, événement d'audit obligatoire), même schéma que
le "petit ajout backend" du Sprint 7F.5.

**Décision retenue** : faire l'ajout backend maintenant, plutôt que reporter
ou se limiter à une consultation. Détail complet dans
`docs/decisions/2026-09-17-endpoint-ecriture-parametres-systeme.md`.

---

## Ce qui a été livré, étape par étape

| Étape | Contenu |
| --- | --- |
| 1 | Modules d'appel API `grillesApi.ts` et `adminApi.ts`, dérivés des DTO Java réels (`GrilleResponse`, `CreationGrilleRequest`, `RejetGrilleRequest`, `ValidationGrilleResponse`, `UtilisateurResponse`, `AttributionRoleRequest`). Variables d'environnement `VITE_API_GRILLES_URL` (8083) et `VITE_API_AUDIT_URL` (8087) ajoutées. |
| 2 | `GrillesTarifairesPage` (`/grilles`) : quatre combinaisons nature/session en cartes, montant actif distinct visuellement d'une proposition en attente (bandeau + badge, option retenue après question à l'utilisateur), historique des grilles fermées/rejetées dans une section repliable. |
| 3 | `CreationGrilleModale`, réservée ARH : formulaire nature/session/montant/date, aucun blocage sur une grille active existante, confirmation post-soumission rappelant l'absence d'effet jusqu'à la décision DRH. |
| 4 | `DecisionGrillesPage` (`/grilles/decisions`, route détail réservée DRH par un second `ProtectedRoute` imbriqué) : écart de montant affiché avant décision, `RejetGrilleModale` avec motif obligatoire, message post-validation distinguant fermeture effective et fermeture programmée. |
| 5 | `UtilisateursAdminPage` (`/admin/utilisateurs`) : liste filtrable (rôle, code unité, actif), `AttributionRoleModale` adaptant l'obligation du code unité selon la portée du rôle choisi (`ROLES_PORTEE_LOCALE`), action désactivée sur la ligne de l'administrateur connecté. |
| 6 | Arbitrage discuté et tranché (voir ci-dessus) ; ajout backend implémenté et testé ; `ParametresAdminPage` (`/admin/parametres`, route réintroduite dans la navigation) avec avertissement fort et formulaire de modification. |
| 7 | `AuditPage` (`/audit`) : filtres réels de `GET /audit/entrees`, dates envoyées en date-heure ISO, avertissement explicite sur le filtre par utilisateur (point A-01), aucun tri par colonne, aucune action de modification. |

---

## L'ajout backend en détail (étape 6)

**Fichiers créés** (`backend/service-workflow/`) :
- `domaine/exception/ParametreIntrouvableException.java` (404),
  `ParametreNonModifiableException.java` (422), `ValeurParametreInvalideException.java` (400)
- `api/dto/ModificationParametreRequest.java`, `ParametreResponse.java`
- `application/ParametreAdminService.java`
- `test/.../ParametreAdminServiceTest.java` (13 cas, `@DataJpaTest` contre la
  vraie base, seul `ProfilClient` simulé — même doctrine que `RetourServiceTest`)

**Fichiers modifiés** :
- `domaine/ParametreSysteme.java` (mutateur `changerValeur` ajouté)
- `api/ParametreController.java` (`GET` et `PUT /parametres/{code}` ajoutés)
- `api/GestionnaireErreursApi.java` (trois gestionnaires ajoutés)
- `test/.../ParametreControllerIT.java` (mock du nouveau service ajouté)

**Décisions de conception notables** :
- **Trois codes modifiables seulement** (`SEUIL_AIGUILLAGE_DR`,
  `DELAI_REGULARISATION_JOURS`, `COMPTE_CHARGE_RATIONS`) —
  `RATTRAPAGE_ACTIF` explicitement exclu de l'écriture (mais lisible), pour ne
  pas mélanger deux mécanismes de gouvernance distincts.
- **`GET /parametres/{code}` ajouté en complément**, non demandé par le
  guide : sans lui, l'écran de modification écrirait à l'aveugle.
- **Validation de forme avant tout appel réseau** (même ordre que
  `RetourService` pour le motif RG-10) : le format est vérifié avant de
  résoudre l'acteur auprès du service Identité.
- **Ordre des refus** : liste blanche vérifiée avant l'existence en base —
  un code jamais autorisé n'a pas besoin d'aller chercher une ligne.
- **Aucune seconde comparaison montant/seuil** : la validation de forme
  (entier positif) est distincte de la comparaison RG-08, qui reste unique
  dans `AiguillageService`.

**Tests** : `mvn test -pl service-workflow` — **358/358**, aucune régression
sur les 345 tests existants, 13 nouveaux (dont profil absent, service Identité
indisponible, code hors liste blanche, valeur négative, valeur non numérique).

**`docs/points-en-attente.md` mis à jour** : la ligne "Restreindre l'écriture
sur `parametre_systeme`" et la piste "endpoint d'administration du seuil" sont
marquées résolues, avec renvoi vers la décision datée.

---

## Vérification des critères de validation du guide

| Critère | Statut |
| --- | --- |
| Grilles présentées par couple nature et session | ✅ Vérifié |
| Grille en attente clairement sans effet | ✅ Vérifié (bandeau + badge, distincts du montant actif) |
| Conflit d'unicité expliqué avec la grille en cause | ✅ Vérifié (message backend relayé tel quel) |
| Écart de montant affiché à la DRH | ✅ Vérifié |
| Motif de rejet obligatoire | ✅ Vérifié |
| Aucune création ni suppression de compte | ✅ Vérifié |
| Code unité adapté selon la portée du rôle | ✅ Vérifié |
| Arbitrage sur l'écran de paramètres présenté et tranché, aucun écran de modification sans endpoint | ✅ Fait (endpoint créé, donc écran de modification légitime) |
| Journal d'audit en lecture seule, ouvert à ARH, DRH et ADMIN | ✅ Vérifié |
| Aucun champ mot de passe, `CreerUtilisateurPage.jsx` non transposé | ✅ Vérifié (DOTTEL non consulté, non nécessaire) |
| Aucun écran de fonctions éligibles | ✅ Vérifié |
| Grille active non présentée comme un blocage de proposition | ✅ Vérifié |
| Nouvelle grille présentée par date de prestation, fermeture programmée signalée | ✅ Vérifié |
| Refus 409 d'auto-modification et de dernier administrateur gérés | ✅ Vérifié |
| Filtre d'audit par utilisateur signalé comme incomplet (A-01) | ✅ Vérifié |
| Dates de l'audit envoyées en date-heure ISO | ✅ Vérifié |

Contrôles techniques : `tsc -b --force` (0 erreur), `oxlint` (0 avertissement),
`npm run build` réussi ; `mvn test -pl service-workflow` (358/358).

---

## Fichiers créés ou modifiés

**Backend** (`backend/service-workflow/`) : voir section dédiée ci-dessus.

**API frontend** (`src/api/`) : `grillesApi.ts`, `adminApi.ts`, `parametresApi.ts`,
`auditApi.ts` (créés).

**Pages** (`src/pages/`) :
- `grilles/GrillesTarifairesPage.tsx`, `CreationGrilleModale.tsx`,
  `DecisionGrillesPage.tsx`, `RejetGrilleModale.tsx` (créés)
- `admin/UtilisateursAdminPage.tsx`, `AttributionRoleModale.tsx`,
  `ParametresAdminPage.tsx`, `ModificationParametreModale.tsx` (créés)
- `audit/AuditPage.tsx` (créé)

**Modifiés** : `router/AppRouter.tsx` (six routes câblées, dont
`/grilles/decisions` en sous-route protégée DRH), `components/layout/navigation.ts`
(route `/admin/parametres` réintroduite), `utils/messagesErreur.ts` (dix codes
ajoutés), `.env` et `.env.example` (deux variables ajoutées).

**Documentation** :
`docs/decisions/2026-09-17-endpoint-ecriture-parametres-systeme.md` (créé),
`docs/points-en-attente.md` (mis à jour), ce résumé.

---

## Redémarrage effectué par l'assistant

**`service-workflow` (port 8084) a été redémarré** pour charger les nouveaux
endpoints `GET`/`PUT /parametres/{code}` — vérifié actif via
`/actuator/health`, puis testé sans jeton (401 sur les trois endpoints,
confirmant qu'ils sont bien protégés et non muets). Les cinq autres services
et le frontend tournaient déjà et n'ont pas eu besoin de redémarrage.

---

## Vérification visuelle attendue de l'utilisateur

Tous les services (Postgres, Kafka, Keycloak, les six services backend, le
frontend) sont déjà lancés. Rien à démarrer vous-même avant de commencer.

1. **Grilles — ARH** : se connecter avec un compte `ARH`, ouvrir `/grilles`.
   Vérifier les quatre cartes (Ration/Transport × Jour/Soir). Cliquer
   « Proposer une grille », soumettre une proposition sur un couple avec une
   date de début future : vérifier la confirmation rappelant l'absence
   d'effet, puis que la carte affiche désormais un bandeau « Proposition en
   attente » distinct du montant actif.
2. **Grilles — tentative de remplacement normal** : proposer une seconde
   grille sur un couple **déjà actif**, à une date postérieure à la grille en
   vigueur : vérifier qu'elle est **acceptée** (pas de refus « grille active
   existe déjà »). Proposer une grille avec une date **antérieure ou égale** à
   la grille active : vérifier le refus `GRILLE_ACTIVE_EXISTANTE` nommant la
   grille en cause. Proposer une seconde fois sur un couple **déjà en attente
   DRH** : vérifier `GRILLE_EN_ATTENTE_EXISTANTE`.
3. **Grilles — DRH** : se connecter avec un compte `DRH`, ouvrir `/grilles`,
   cliquer « Décisions en attente ». Vérifier l'écart de montant affiché,
   valider une proposition à date future : vérifier la mention « fermeture
   programmée ». Rejeter une autre proposition avec un motif : vérifier le
   message et que le montant actif n'a pas changé sur `/grilles`.
4. **Administration — utilisateurs** : se connecter en `ADMIN`, ouvrir
   `/admin/utilisateurs`. Filtrer par rôle et code unité. Cliquer
   « Attribuer » sur un utilisateur, choisir `AGENT_UNITE` : vérifier que le
   code unité devient obligatoire. Vérifier que le bouton est désactivé sur
   la ligne du compte connecté.
5. **Administration — paramètres** : ouvrir `/admin/parametres`. Vérifier
   l'avertissement et les trois valeurs affichées (seuil, délai, compte de
   charge). Modifier le seuil d'aiguillage (ex. `120000`), vérifier
   l'enregistrement puis la nouvelle valeur affichée. Tenter une valeur
   négative ou non numérique : vérifier le refus `400 VALEUR_PARAMETRE_INVALIDE`.
6. **Journal d'audit** : se connecter en `ARH`, `DRH` ou `ADMIN`, ouvrir
   `/audit`. Vérifier que la modification de paramètre de l'étape 5 apparaît
   (action `MODIFICATION_PARAMETRE`). Renseigner un identifiant utilisateur
   dans le filtre : vérifier l'avertissement sur le filtre incomplet (A-01).
7. Vérifier l'absence d'erreur dans la console et l'onglet réseau du
   navigateur sur l'ensemble du parcours.

Si des ajustements sont faits après ce test, ce résumé sera mis à jour avant
le commit.

---

## Ajustements après la vérification visuelle (17 septembre 2026)

### Défaut trouvé en préparant les cas de test : la « grille active » mal identifiée

En construisant les cas de test sur les données réelles de la base, un défaut de
l'écran de consultation est apparu : la « grille active » d'un couple était
identifiée par `dateFin === null`. Or une grille **déjà validée à effet futur**
porte aussi `dateFin === null` (Sprint 2.3 : c'est l'ancienne qui reçoit sa borne
de fin, à la veille). La base en contenait une : TRANSPORT / SOIR, 3 500 FCFA à
partir du 01/12/2026. L'écran l'aurait affichée comme montant actif, alors que le
tarif réellement appliqué aujourd'hui est 3 000 FCFA (01/09 → 30/11/2026).

**Corrigé** dans `GrillesTarifairesPage.tsx` et `DecisionGrillesPage.tsx` : la
grille active est celle qui **couvre la date du jour** (`dateDebut <= aujourd'hui
<= dateFin` ou `dateFin` nulle), même règle que la résolution du montant (RG-03).
Une grille validée à effet futur est signalée à part, dans un bandeau « Grille
programmée ». L'historique ne contient plus que les périodes entièrement passées.
L'écart de montant présenté à la DRH se calcule contre le tarif réellement en
vigueur. Vérifié visuellement par l'utilisateur (capture du 17/09).

### Retours de l'utilisateur : onze demandes, traitées une à une

L'utilisateur a validé le reste du parcours et formulé onze demandes. Méthode
convenue : une proposition, une validation, une implémentation, dans cet ordre.

| # | Demande | Statut |
| --- | --- | --- |
| 1 | Confirmation avant toute action sensible | ✅ Implémentée (option A) |
| 2 | Montant affiché dès que nature et session sont choisies | ✅ Implémentée (option A) |
| 3 | Contrôle du format du n° de compte et du code agence | ✅ Implémentée (option A) |
| 4 | Champs vidés au changement de jour | ✅ Implémentée (option A) |
| 5 | Nouvelle mise en page de la saisie | ✅ Implémentée (variante B) |
| 6 | Modifier le bénéficiaire d'une ligne | ✅ Implémentée (supprime et recrée, aucun ajout backend) |
| 7 | L'ARH modifie sa grille en attente | ✅ Implémentée (retrait par l'auteur + nouvelle proposition, endpoint ajouté) |
| 8 | Téléchargement de l'état PDF | ✅ Implémentée (endpoint ajouté, ferme le point ouvert au 7F.5) |
| 9 | Barre latérale groupée, avec compteur, réductible | ✅ Implémentée |
| 10 | Journal d'audit lisible sans JSON ni identifiant | ✅ Implémentée |
| 11 | Page des paramètres modernisée | ✅ Implémentée |

**Les onze demandes sont closes le 18 septembre 2026.** Détail des quatre
dernières (n°6, n°7, n°8, n°11) ci-dessous.

**Deux demandes supplémentaires formulées à la relecture de la maquette (17/09/2026)** :
retirer tout emoji utilisé comme icône et tout tiret cadratin (« — ») du texte affiché,
l'utilisateur y voyant des marqueurs d'écriture générée. Traitées à cette occasion, sur la
maquette et sur 34 occurrences réelles du frontend (voir
`docs/decisions/2026-09-17-refonte-sidebar-reductible-et-audit-lisible.md`). Une proposition
pour l'écran de connexion (« page de login », absente de la liste initiale des onze demandes)
a également été ajoutée à la maquette.

### Proposition n°1 — Confirmation des actions sensibles (option A retenue)

**Inventaire préalable** : deux actions engageantes partaient en un seul clic, sans
confirmation : la validation d'une grille (DRH) et la validation d'un dossier
(Chef d'unité / Directeur Réseau). Quatre formulaires étaient envoyés directement,
sans relecture : proposer une grille, attribuer un rôle, modifier un paramètre,
déclencher un état.

**Ce qui a été fait :**
- **Valider une grille** : fenêtre de confirmation qui rappelle la combinaison,
  l'ancien et le nouveau montant, l'écart, la date d'effet, et si la fermeture de la
  grille actuelle est programmée ou effective.
- **Valider un dossier** : fenêtre qui rappelle l'unité, la période, le type, le
  montant et la suite. Cette suite est déduite du **statut**, jamais du seuil, que
  le frontend n'a pas à connaître : « clôture et envoi à la comptabilité » au niveau
  du Directeur Réseau ; « transmission au Directeur Réseau » pour un état
  complémentaire ; « clôture ou transmission selon le seuil » au premier niveau d'un
  état normal.
- **Rejeter une grille, retourner un dossier** : pas de seconde fenêtre empilée. La
  fenêtre du motif affiche désormais un rappel de ce qui est refusé.
- **Proposer une grille, attribuer un rôle, modifier un paramètre, déclencher un
  état** : dans la même fenêtre, une étape « Vérifier » affiche l'avant et l'après
  (ex. « Seuil : 100000 → 120000 ») avec un bouton « Retour », puis « Confirmer ».
  Un refus du serveur reste visible après « Retour », pour corriger la saisie. La
  modification d'un paramètre refuse en outre une valeur identique à l'actuelle.
- **Ajouter / modifier une ligne** : volontairement **sans confirmation** (geste
  répété, corrigeable tant que l'état n'est pas soumis).
- Soumettre un état et supprimer une ligne avaient déjà une confirmation : rien de
  changé.

**Fichiers** : `components/communs/Recapitulatif.tsx` (créé),
`components/communs/Modale.tsx` (prop `libelleAnnuler`), `utils/formatters.ts`
(`formatCombinaison`, `veilleIso`), `pages/grilles/DecisionGrillesPage.tsx`,
`RejetGrilleModale.tsx`, `CreationGrilleModale.tsx`, `GrillesTarifairesPage.tsx`,
`pages/validation/ExamenProcessusPage.tsx`, `RetourModale.tsx`,
`pages/admin/AttributionRoleModale.tsx`, `ModificationParametreModale.tsx`,
`pages/processus/DeclenchementModale.tsx`.

Contrôles : `tsc -b --force` 0 erreur, `oxlint` 0 avertissement, `npm run build`
réussi.

### Proposition n°2 — Montant affiché dès la sélection (option A retenue)

**Avant** : le champ Montant restait vide. Le montant, ou le refus « aucun tarif »,
n'apparaissait qu'après le clic sur « Ajouter ».

**Ce qui a été fait** (frontend uniquement, **aucun ajout backend**) :
- Dès que la nature et la session sont choisies, le formulaire interroge
  `GET /grilles/active`, un endpoint déjà ouvert à tout utilisateur authentifié
  (Sprint 2.4), **à la date de la fiche** et non à la date du jour (RG-03).
- Cinq états sont affichés : choix incomplet, recherche en cours, tarif trouvé
  (« tarif du JJ/MM/AAAA, confirmé à l'enregistrement »), aucun tarif (erreur de
  champ), service injoignable.
- **Aucun tarif** : le bouton « Ajouter » est désactivé (option A), le serveur
  refusant de toute façon (422 `GRILLE_INDISPONIBLE`). **Service injoignable** :
  le bouton reste actif, la décision revient au serveur.
- Le montant affiché **n'est jamais envoyé** : la requête de création reste
  inchangée, le service Saisie résout et fige le montant lui-même.
- Si le montant enregistré diffère de celui affiché (grille validée entre-temps),
  un avertissement le signale.
- Un résultat de tarif est rattaché à sa combinaison (nature, session, date). Une
  réponse arrivée pour une ancienne combinaison n'est jamais affichée.

**Vérifié en réel** avec un jeton `jean_mbarga` (AGENT_UNITE) : `200` et CORS
autorisé pour `localhost:5173` ; RATION/JOUR au 03/09/2026 → 1 500 FCFA ; au
01/01/2020 → `disponible: false`, `montantFcfa: null`.

**Fichiers** : `api/grillesApi.ts` (`resoudreMontant`,
`MontantApplicableResponse`), `components/communs/ChampMontant.tsx` (props
`placeholder`, `aide`, `erreur`), `pages/saisie/FormulaireAjoutLigne.tsx`,
`pages/saisie/SaisieJournaliereTab.tsx` (transmet `fiche.dateJour`).

Contrôles : `tsc -b --force` 0 erreur, `oxlint` 0 avertissement, `npm run build`
réussi.

### Proposition n°3 — Contrôle du n° de compte et du code agence (option A retenue)

**Constat qui a changé la proposition** : la règle métier « un compte courant fait
11 chiffres » est arrêtée depuis le 9 septembre 2026 (point T-02) et figure dans le
dictionnaire de données. Le résumé du sprint Maille 1 annonçait « T-02 corrigé,
`@Pattern` posé ». **Rien de cela n'était vrai dans le code** :
`IdentiteBeneficiaireRequest.java` ne portait que `@Size(max = 20)`, et `git log`
montre que le fichier n'a jamais été modifié depuis le Sprint 3.3. Une faute de
frappe pouvait donc créer un bénéficiaire fantôme et payer un mauvais compte, sans
aucune erreur visible.

**Ce qui a été fait :**
- **Serveur** (`service-saisie`) : `@Pattern(regexp = "^[0-9]{11}$")` sur le numéro
  de compte courant, avec un message explicite. Le code agence était déjà contrôlé
  (5 chiffres).
- **Tests** : les comptes de test à 14 chiffres (deux fichiers de contrôleur, cinq
  de service ou de dépôt) ramenés à leurs 11 derniers chiffres, ce qui les garde
  distincts. Quatre cas de refus ajoutés dans `SaisieControllerIT` (14 chiffres,
  10 chiffres, espace, lettre), qui vérifient aussi que le service n'est jamais
  appelé. `mvn clean test -pl service-saisie` : **102/102**. Le premier passage sans
  `clean` avait échoué sur « Unresolved compilation problem », le piège de l'IDE
  Eclipse déjà consigné au Sprint 4.2.
- **Écran** (`FormulaireAjoutLigne.tsx`, règles dans `utils/validationSaisie.ts`,
  réutilisables au point n°6) : au clic sur « Ajouter », contrôle de la nature, de
  la session, du nom, du prénom, du compte (11 chiffres, ex. « 11 chiffres attendus
  (vous en avez saisi 14) ») et du code agence (5 chiffres). Les erreurs s'affichent
  sous chaque champ, le premier champ fautif prend le focus, et rien ne part au
  serveur. Les erreurs se mettent ensuite à jour à chaque frappe. Les espaces sont
  retirés automatiquement ; tout autre caractère est signalé. Pas de longueur
  maximale sur le compte, pour qu'un numéro collé à 14 chiffres soit signalé et
  jamais tronqué en silence.
- **Vérifié en réel** après redémarrage de `service-saisie` : compte à 14 chiffres
  → `400 REQUETE_INVALIDE` nommant le champ ; code agence à 4 chiffres → `400`.
- **Documentation** : rectificatif daté dans le résumé Maille 1 (texte d'origine
  conservé), T-02 passé à « Corrigé » dans `docs/dispositifs_provisoires.md`,
  résolution notée en tête de T-02 dans `docs/points-en-attente.md`.

**À savoir pour les tests** : les comptes de développement à 14 chiffres ne sont pas
corrigés (arbitrage du 9 septembre). Les ressaisir est désormais refusé ; utiliser
des comptes à 11 chiffres (ex. `03702099911`). Reste ouvert : l'exemple faux du
contrat d'API §7.1, document de référence hors dépôt.

### Proposition n°4 — Formulaire vidé au changement de jour (option A retenue)

**Défaut trouvé en préparant la proposition** : le formulaire d'ajout n'était jamais
réinitialisé, et pendant le chargement d'un nouveau jour il restait **actif en
pointant sur la fiche du jour précédent**. Un clic sur « Ajouter » dans cette fenêtre
enregistrait la ligne sur le mauvais jour. Il en allait de même si le chargement du
nouveau jour échouait : le formulaire se réactivait sur l'ancienne fiche.

**Ce qui a été fait** (frontend uniquement) :
- **Formulaire vidé au changement de jour** : il est remonté à chaque nouvelle fiche
  (`key={fiche.id}`). Nature, session, bénéficiaire, erreurs, montant affiché et
  avertissements repartent de zéro.
- **Formulaire désactivé tant que la fiche affichée n'est pas celle du jour
  sélectionné** (`fiche.dateJour === dateSelectionnee`) : cela couvre à la fois le
  chargement et l'échec de chargement. En cas d'échec, le formulaire et le tableau
  disparaissent ; seule l'erreur reste, jamais les lignes d'un autre jour.
- **Au sein d'une même journée, rien ne change** : après un ajout, seul le
  bénéficiaire se vide, la nature et la session restent.
- **Avertissement avant perte d'une saisie** : si un champ du bénéficiaire est rempli
  et non enregistré, cliquer sur un autre jour ouvre « Saisie non enregistrée », avec
  « Rester sur ce jour » ou « Changer de jour ». La nature et la session seules ne
  déclenchent pas l'avertissement. Recliquer sur le jour courant ne fait rien.

**Fichiers** : `pages/saisie/SaisieJournaliereTab.tsx`,
`pages/saisie/FormulaireAjoutLigne.tsx` (prop `onBrouillonChange`).

Contrôles : `tsc -b --force` 0 erreur, `oxlint` 0 avertissement, `npm run build`
réussi. **Non vérifié au navigateur par l'assistant** (aucun navigateur disponible) :
à confirmer à la vérification visuelle.

### Proposition n°5 — Mise en page de la saisie (variante B retenue)

**Méthode** : trois dispositions ont été maquettées dans un artefact publié
(page HTML interactive, charte du module reprise à l'identique, données fictives) :
A « ligne de saisie rapide », B « Bénéficiaire / Prestation », C « tableau et
panneau latéral », plus l'écran actuel pour comparaison —
https://claude.ai/artifact/SJtpyyx4Wgrzx4N6uRn9ap. L'utilisateur a retenu **B**,
avec conservation du logo Afriland dans la barre latérale.

**Ce qui a été fait :**
- **Formulaire en deux blocs** (`FormulaireAjoutLigne.tsx`), dans l'ordre du
  geste : « 1 Bénéficiaire » (nom, prénom, n° de compte, code agence) puis
  « 2 Prestation » (nature, session, montant, bouton d'ajout). L'ancienne grille
  à trois colonnes mélangeait les deux.
- **Nature et session en boutons à un clic** — nouveau composant commun
  `BoutonsSegmentes.tsx`, réservé aux énumérations courtes et fermées (RG-01,
  RG-02). Une liste déroulante demandait deux gestes, répétés à chaque ligne.
- **Montant en tuile lisible** dans le bloc Prestation, juste au-dessus du
  bouton qui l'enregistre, avec ses quatre états (tarif, recherche, aucun tarif,
  service injoignable).
- **Sélecteur de jour enrichi** (`SelecteurJour.tsx`) : chaque jour porte son
  nombre de lignes et son sous-total, le jour courant est marqué « aujourd'hui ».
  Les chiffres viennent de l'état consolidé (RG-06), aucune somme n'est refaite.
- **Total de la période affiché** au-dessus du sélecteur, sans changer d'onglet.
- **Tableau du jour enrichi** : n° de compte et code agence affichés — ce sont
  eux qui décident qui est payé et sur quelle agence —, sous-total du jour mis
  en évidence dans un bandeau plutôt qu'en petite ligne de texte.
- **Onglets soulignés** au lieu de pastilles rouges pleines : le rouge de la
  charte redevient un accent.
- **Le curseur revient sur « Nom » après chaque ajout**, nature et session
  conservées : l'agent enchaîne les lignes sans repasser à la souris.
- Un appel supplémentaire à `GET /processus/{id}/etat` alimente les compteurs ;
  son échec est **non bloquant** (les compteurs disparaissent, la saisie
  continue). **Aucun ajout backend.**

**Fichiers** : `components/communs/BoutonsSegmentes.tsx` (créé),
`pages/saisie/FormulaireAjoutLigne.tsx`, `SelecteurJour.tsx`,
`TableauLignesJour.tsx`, `SaisieJournaliereTab.tsx`, `SaisieProcessusPage.tsx`,
`utils/formatters.ts` (`formatDateLongue`).

Contrôles : `tsc -b --force` 0 erreur, `oxlint` 0 avertissement, `npm run build`
réussi. **Non vérifié au navigateur par l'assistant** : à confirmer à la
vérification visuelle.

### Proposition n°6 (demande n°9) : barre latérale groupée, avec compteur, réductible

**Méthode** : la maquette publiée à la proposition n°5 a été enrichie d'un onglet
« Barre latérale » présentant l'état actuel (huit liens à plat) et l'état proposé
(trois groupes, compteur, bouton de réduction), avant implémentation.
L'utilisateur a validé la proposition, en demandant en plus le bouton de
réduction, avec le logo réduit à sa forme ronde, centrée, sans le texte.

**Ce qui a été fait** :
- **Trois groupes** dans `navigation.ts` (type `GroupeNavigation`) : « Mon
  travail » (Processus, Saisie, Validation, Suivi), « Référentiel » (Grilles
  tarifaires, Rapports), « Administration » (Journal d'audit, Utilisateurs,
  Paramètres système). Filtrage par rôle inchangé : un agent ne voit toujours
  que son groupe.
- **Compteur réel sur Validation** (`hooks/useCompteurValidation.ts`) : un
  appel `GET /reporting/demandes?statut=EN_ATTENTE_DA|EN_ATTENTE_DR&size=1`,
  dont seul `totalElements` est retenu. Relu à chaque changement de page (donc
  après une validation ou un retour). Échec réseau : le compteur disparaît, la
  navigation continue.
- **Bouton de réduction**, préférence gardée dans `localStorage`
  (`rations.sidebar.reduite`), lectures et écritures protégées par
  `try/catch` (navigation privée, stockage bloqué : repli sur l'état déplié).
  Réduite, la sidebar ne montre plus que le motif rond du logo
  (`Logo variant="embleme"`, déjà présent dans le projet), centré, et chaque
  icône de lien, centrée, avec son libellé porté par `title` et un texte
  `sr-only` pour le lecteur d'écran.
- **État actif toujours par liseré**, pas par aplat plein : décision déjà
  retenue à la maquette, reprise sans changement.

**Fichiers** : `components/layout/navigation.ts`, `Sidebar.tsx`,
`hooks/useCompteurValidation.ts` (créé).

Contrôles : `tsc -b --force` 0 erreur, `oxlint` 0 avertissement, `npm run build`
réussi.

### Proposition n°7 (demande n°10) : journal d'audit lisible sans JSON ni identifiant

**Inventaire préalable** : `AuditPage.tsx` affichait le contenu brut de
`detail_json` dans un `<pre>`, l'identifiant numérique de l'utilisateur
(`#42`) et celui de l'entité concernée, et sept champs de filtre techniques
(dont deux identifiants à saisir à la main).

**Ce qui a été fait** :
- **Aucun JSON, aucun identifiant à l'écran.** `detail_json` suit toujours la
  même forme, écrite une fois pour tout le backend par `DeltaAudit.java`
  (`{"champ":{"avant":...,"apres":...}}` ou `{"contexte":valeur}`), ce qui
  rend possible un analyseur générique
  (`pages/audit/libellesAudit.ts`, `analyserDetailJson`) plutôt qu'un rendu
  écrit action par action.
- **Trois dictionnaires** traduisent l'action, l'entité concernée et le
  service émetteur en français (les 30 actions arrêtées au Sprint 6.3 plus
  `MODIFICATION_PARAMETRE`), avec un repli qui met en forme toute clé non
  répertoriée plutôt que de rien afficher.
- **Colonne « Par »** : un login quand le détail en porte un (ex.
  `MODIFICATION_PARAMETRE` porte un contexte `auteur`), sinon « Compte
  identifié » (un `idUtilisateur` existe, sans nom disponible ici) ou « non
  renseigné » (aucun auteur enregistré, le cas de 21 des 30 points de
  publication, CLAUDE.md §9.2). Jamais un numéro.
- **Filtres réduits à quatre champs métier** (service, type d'action, depuis,
  jusqu'à), plus deux familles de raccourcis : périodes (24 h, 7 j, 30 j) et
  quatre recherches courantes (refus d'accès, validations d'états, envois à
  la comptabilité, modifications de paramètres) — un seul code d'action par
  raccourci, le filtre serveur comparant `action` par égalité stricte.
  L'avertissement sur les auteurs manquants reste affiché en permanence.

**Limite assumée et consignée** : sans identifiant affiché, une ligne de
prestation ou un dossier sans information nommante dans son détail n'est
plus repérable par numéro depuis cet écran. Les identifiants restent en
base pour l'investigation technique.

**Fichiers** : `pages/audit/AuditPage.tsx`, `libellesAudit.ts` (créé).

Contrôles : `tsc -b --force` 0 erreur, `oxlint` 0 avertissement, `npm run build`
réussi.

### Nettoyage transversal : aucun emoji, aucun tiret cadratin

Demande formulée à la relecture de la maquette du 17/09/2026, appliquée à la
maquette et au frontend réel : 34 occurrences du tiret cadratin corrigées
dans 14 fichiers (`utils/formatters.ts` et treize pages ou modales),
remplacées par une virgule, des parenthèses, un point milieu (« · ») entre
deux termes courts, ou une phrase reformulée selon le sens. Aucun emoji
n'était présent dans le frontend réel (seule la maquette en portait, corrigée
de même). Détail dans
`docs/decisions/2026-09-17-refonte-sidebar-reductible-et-audit-lisible.md`.

### Nouvelle proposition, non demandée initialement : écran de connexion

À la relecture, l'utilisateur a demandé une proposition pour l'écran de
connexion. Aucun changement réel n'était nécessaire : `EcranSession.tsx`
(Sprint 7F.3) ne comporte déjà aucun champ de mot de passe, distingue trois
causes d'échec et n'effectue aucune redirection automatique invisible.
Un onglet « Connexion » a été ajouté à la maquette pour le présenter tel
qu'il existe, ses quatre états (chargement, écran de connexion, aucun profil
ouvert, connexion impossible).

### Refonte des écrans restants de l'artefact (18/09/2026)

Toutes les propositions de la maquette de refonte non encore implémentées
l'ont été, écran par écran :

- **Processus** (`/processus`) : quatre compteurs réels (`useStatistiquesProcessus`,
  six appels `GET /reporting/demandes` ciblés, jamais une estimation depuis
  une seule page), bandeau de dossier retourné avec accès direct, colonne
  Type, chevron.
- **Saisie journalière** : fil d'Ariane ajouté (`PageHeader` étendu d'un
  prop `filAriane`, réutilisable partout).
- **Consultation & soumission** : trois indicateurs, journées vides
  reconstituées à partir des bornes de la période (l'API ne rend que les
  journées avec au moins une ligne) et affichées grisées plutôt que masquées,
  alerte informative.
- **Validation liste** : chevron ajouté. L'auteur et la date de soumission,
  proposés par la maquette, sont **écartés** : donnée absente de
  `GET /reporting/demandes`, l'ajouter coûterait un appel par ligne visible.
- **Validation examen** : actions dans l'en-tête (`PageHeader` étendu d'un
  prop `actions`), trois indicateurs, parcours de signature complet avec
  étapes à venir (`construireEtapesAffichees`, distingue « en attente »,
  « sans objet, clôturé sous le seuil » et « toujours requise, état
  complémentaire »).
- **Grilles tarifaires** : déjà conforme à la maquette (vérifié, aucun
  changement nécessaire).
- **Décisions DRH** : l'écart devient une tuile à part entière avec
  pourcentage et couleur, conséquence de fermeture annoncée avant la
  décision. Une coquille corrigée en passant (tiret double dans un texte
  affiché).
- **Utilisateurs** : bandeau « cet écran n'ouvre ni ne supprime de compte »,
  propre ligne signalée par un badge « vous ».

**Composant partagé créé** : `StatTile.tsx`, réutilisé sur Processus,
Consultation et Validation examen.

### Proposition finale — Paramètres système modernisés (demande n°11)

`ParametresAdminPage.tsx` reconstruite : une ligne par paramètre (au lieu
d'une carte), valeur avec son unité (FCFA, jours), rappel des dernières
modifications lu depuis le journal d'audit et traduit en phrases
(réutilise `utils/auditLisible.ts`, déplacé de `pages/audit/` pour ce
second usage).

### Proposition finale — Correction du bénéficiaire d'une ligne (demande n°6)

Aucun endpoint ne permet de réécrire le bénéficiaire d'une ligne
(`PUT /saisie/lignes/{id}` ne porte que nature et session, décision du
Sprint 3.1 : un bénéficiaire est identifié par son seul numéro de compte,
jamais réécrit). `ModaleCorrectionBeneficiaire.tsx` **supprime la ligne et
en recrée une autre**, dans cet ordre précis : créer d'abord, supprimer
ensuite — si la création échoue, la ligne d'origine reste intacte. Si la
suppression de l'ancienne échoue après une création réussie, un bandeau
persistant (affiché par la page parente, pas par la modale qui se ferme)
prévient l'agent que les deux lignes existent désormais.

### Proposition finale — L'ARH retire sa grille en attente (demande n°7)

Aucune grille n'est modifiable en place dans ce module (décision Sprint 2.2,
étendue ici). Nouvel endpoint `POST /grilles/{id}/retrait`, réservé à
l'auteur de la proposition (vérifié par identifiant, jamais par libellé) :
retire la proposition (statut REJETEE, comme un rejet de la DRH mais à
l'initiative de l'ARH), qui repropose ensuite une grille corrigée par le
chemin existant. Troisième code de refus en 403 côté service Grilles,
`GRILLE_NON_PROPRIETAIRE`, distinct d'`ACCES_REFUSE` et
`UTILISATEUR_NON_HABILITE`. Voir
`docs/decisions/2026-09-18-retrait-dune-grille-en-attente-par-son-auteur.md`.

### Proposition finale — Téléchargement du document PDF signé (demande n°8)

Ferme le point ouvert au Sprint 7F.5. Nouvel endpoint
`GET /processus/{id}/document`, réservé aux trois rôles du circuit,
événement d'audit `TELECHARGEMENT_DOCUMENT` obligatoire (même doctrine
qu'`EXPORT_RAPPORT`). **Correction associée** dans `apiClient.ts` : les
réponses d'erreur d'un appel en `responseType: 'blob'` arrivent elles-mêmes
sous forme de `Blob`, jamais de JSON déjà décodé — l'intercepteur les
reconvertit désormais, sans quoi `AffichageErreur` aurait reçu un `Blob`
sur ce nouvel endpoint et sur tout futur export binaire. Voir
`docs/decisions/2026-09-18-endpoint-telechargement-document-signe.md`.

### Contrôles finaux

| Module | Résultat |
| --- | --- |
| `service-workflow` (`mvn clean test`) | 363/363 |
| `service-grilles` (`mvn clean test`) | 104 tests ; 2 échecs **préexistants et non liés** dans `GrilleTarifaireRepositoryTest`, qui suppose l'état pristine du jeu de données de référence du Sprint 0.5 et casse dès que la base de développement réelle en diverge (constaté : une grille a été réellement validée pendant une vérification manuelle antérieure) |
| Frontend (`tsc -b --force`, `oxlint`, `npm run build`) | 0 erreur, à chaque étape de ce rattrapage |

---

## Post-vérification (18 septembre 2026) — écarts trouvés en direct, corrigés

L'utilisateur a testé l'ensemble au navigateur, comme demandé ci-dessus.
Quatre écarts réels entre l'artefact de maquette et le rendu, tous corrigés
avant commit :

- **Sidebar** : l'état actif d'un lien restait un aplat rouge plein au lieu
  du liseré demandé par la maquette (« le rouge reste réservé aux actions et
  aux alertes ») ; le pied de la sidebar affichait le rôle technique brut
  (`AGENT_UNITE`) au lieu du libellé humain avec l'unité (« Agent d'unité ·
  00002 ») ; le sous-titre `RATIONS & TRANSPORT` sous le logo, prévu par la
  maquette, n'avait pas été repris. Les trois corrigés dans `Sidebar.tsx` et
  le nouveau `utils/libelleRole.ts` (source unique, repris aussi par
  `UtilisateursAdminPage.tsx`).
- **Écran de validation (Chef d'Unité / Directeur Réseau)** : le tableau
  « Détail par journée » ne portait que le nombre de lignes et le sous-total,
  sans l'identité des bénéficiaires — un valideur ne pouvait pas voir qui
  était payé. Ajout d'un bouton « Détails » (icône œil, ligne entière
  cliquable) ouvrant `DetailJourneeModale.tsx`, lecture seule, réutilisant
  une donnée déjà chargée par `GET /processus/{id}/etat`.
- **Journal d'audit** : même demande transposée au journal — colonne « Ce qui
  s'est passé » simplifiée à la seule action (le détail complet vit dans
  `DetailEntreeAuditModale.tsx`), colonne « Concerne » ajoutée pour identifier
  précisément le dossier touché (unité et période, construits depuis le
  contexte déjà publié dans `detail_json`, aucun appel réseau
  supplémentaire), filtre « Type d'action » passé d'un champ texte libre à une
  liste déroulante **tirée du backend** (nouvel endpoint `GET /audit/actions`,
  troisième endpoint du contrat du service Audit — voir
  `AuditController.listerActions()`), filtre « Utilisateur » ajouté et ouvert
  à ARH et DRH en plus d'ADMIN (`GET /identite/utilisateurs` passé d'une
  protection de classe à une protection méthode par méthode : lecture ouverte
  aux trois rôles, écriture — attribution de rôle — restée réservée à
  l'ADMIN).
- **`TELECHARGEMENT_DOCUMENT`** (demande n°8) ne portait ni l'unité ni la
  période ni l'auteur dans son contexte d'audit, contrairement aux autres
  actions touchant `processus_mensuel`. Corrigé : `ProcessusService.consulter`
  expose désormais l'`AgentHabilite` qu'il obtenait déjà en vérifiant la
  portée d'accès (`DetailProcessus` porte un troisième champ), et
  `DocumentTelechargementService` l'utilise pour publier `codeUnite`,
  `dateDebut`, `dateFin`, `auteur` et `role` — sans second appel au service
  Identité.

**Deux écarts identifiés mais non corrigés, par choix documenté.**
`ACCUSE_COMPTABLE_APPLIQUE`/`REFUSE` (service Transmission) restent sans
auteur : ce sont des événements déclenchés par un message Kafka de la
comptabilité, sans utilisateur humain derrière — leur inventer un auteur
serait faux. Les ~20 autres actions déjà recensées sans `idUtilisateur`
(point A-01) ne sont pas rouvertes : arbitrage déjà tranché avec
l'utilisateur au Sprint 6.3.

**Contrôles refaits après ces correctifs** : `service-identite` 64/64,
`service-workflow` 363/363 (dont les tests touchés par le nouveau champ de
`DetailProcessus`), `service-audit` 11/11, frontend `tsc --noEmit` +
`oxlint` + `npm run build` sans erreur.

**Sous-produit** : la méthode de refonte (artefact unique, cycle de feedback,
implémentation sans interruption, vérification exhaustive en direct) a été
transcrite en guide réutilisable pour le projet Dotations Téléphoniques
Mensuelles (`afb-dottel-mm/GUIDE-REFONTE-ECRANS-ET-MODALES.md`, hors de ce
dépôt), avec la même doctrine anti-émoji / anti-tiret-cadratin et une
recommandation de cohérence visuelle des modales et animations avec ce
module.

## Prochaine étape

Les onze demandes de la vérification visuelle, toutes les propositions
d'écran de la maquette de refonte, et les quatre écarts trouvés lors de la
vérification en direct du 18 septembre sont implémentés et vérifiés. Sprint
7F.6 considéré clos, à commiter. Sprint suivant : 7F.7, suivi, reporting et
clôture (guide déjà complété d'une étape de maquette préalable, voir
`docs/sprints/sp 7/sprint_7F_7.md` étape 1bis).
