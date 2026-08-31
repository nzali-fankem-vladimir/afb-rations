# Résumé Sprint 3.3 — Endpoints de saisie

**Service :** service-saisie · **Date :** 31 août 2026 · **Config :** Opus / Élevé
(étapes 1-3), puis **Sonnet / Moyen** (étapes 4-6), conformément au guide §1.

**Statut :** livré. `mvn -pl service-saisie test` → **BUILD SUCCESS, 45 tests, 0
échec** (26 des Sprints 3.1/3.2 + 19 nouveaux, dont les 15 exigés par le guide).
Backend complet recompilé sans régression. `mvn -pl service-saisie
dependency:list` → seule dépendance interne `rations-audit-commun`, aucun import
direct vers `service-grilles`, `service-workflow` ou `service-identite`.
Cartographie relancée : 2921 nœuds, 5724 liens. Démarrage réel du service vérifié
sous `dev,bouchon-workflow` (`Started ServiceSaisieApplication`).

Les cinq endpoints du contrat (§3) sont exposés — ni plus, ni moins, vérifié par
grep sur les annotations de mapping. RG-05 (fiche vierge, non destructive) et la
protection de l'écriture avant soumission portent l'essentiel du sprint.

---

## Décisions prises en cours de route

| # | Décision | Trace | Impact sprints suivants |
|---|---|---|---|
| 1 | **`PUT /saisie/lignes/{id}` ne modifie que la nature et la session.** Le bénéficiaire ne mute jamais en place — une erreur de destinataire se corrige par suppression puis recréation, deux traces distinctes plutôt qu'une mutation ambiguë. Tranché avec l'utilisateur à l'étape 1. | `ModificationLigneRequest.java` | Le domaine (`LignePrestation.reviser`) n'expose donc que nature/session/montant/grille, jamais le bénéficiaire. |
| 2 | **Refus sur état verrouillé : `422 ETAT_NON_MODIFIABLE`, et non `409`** malgré la lecture littérale du contrat d'API §1.3 (« transition non permise » → 409). Aligné sur le précédent du Sprint 2.3 (`TRANSITION_INTERDITE`, 422). **Écart consigné explicitement pour correction du contrat de référence**, à la demande de l'utilisateur. | `docs/decisions/2026-08-31-code-http-du-refus-sur-etat-non-modifiable.md` (point ouvert **C-01**) | Sprint 4 (Workflow) : le refus de valider au mauvais statut doit suivre la même convention (422), pas 409. |
| 3 | **Vérification du statut réellement câblée sur les quatre écritures**, malgré l'absence du service Workflow (Sprint 4). Un bouchon (`BouchonVerificationProcessus`) comble le vide, sous **trois garde-fous** : jamais actif par défaut, **refuse de démarrer** hors profil `dev` (garde-fou ajouté à la demande explicite de l'utilisateur), bruyant (WARN au démarrage et à chaque appel). Tranché avec l'utilisateur à l'étape 3, contre les deux autres options (tout refuser en 503, ou ne rien câbler). | `docs/decisions/2026-08-31-bouchon-workflow-et-cablage-de-la-verification.md` (quatre actions **obligatoires** au Sprint 4 : B-01 à B-04) | Sprint 4 : supprimer le bouchon et l'annotation `@Profile` qui l'exclut, vérifier en intégration réelle le contrat de `GET /processus/{id}` (déduit, non vérifié — risque signalé en B-04). |
| 4 | **`idUtilisateur` reste nul** dans les traces d'audit du service, malgré la note du résumé 3.2. `GET /identite/habilitation` ne rend qu'un `login`, jamais un identifiant numérique ; le résoudre exigerait un **quatrième appel synchrone** (`GET /identite/moi`) sur un chemin qui en empile déjà trois, pour un seul besoin d'audit — sans qu'aucune colonne du schéma ne l'exige (à la différence de `grille_tarifaire.id_createur`, `NOT NULL`). `adresseIp`, elle, est désormais renseignée partout, sans coût. Tranché avec l'utilisateur. | `docs/decisions/2026-08-31-idutilisateur-non-renseigne-en-saisie.md` | Réouvrable si un besoin métier futur ajoute une colonne d'auteur à `ligne_prestation` ou `fiche_journaliere` : l'appel deviendrait alors un sous-produit nécessaire, comme chez Grilles. |
| 5 | **Migrations `V3` et `V4`, dettes explicitement renvoyées par les Sprints 3.1 et 3.2, réglées ici.** `V3` : recopie figée de `code_unite` / `mois_paiement` / `annee_paiement` sur `fiche_journaliere` (portée d'accès + RG-15 future). `V4` : index unique `(id_fiche_journaliere, id_beneficiaire, nature, session)`, filet contre les écritures concurrentes que le contrôle applicatif ne couvre pas seul (point ouvert n°1 du résumé 3.2). | `V3__fiche_journaliere_recopie_unite_et_periode.sql`, `V4__ligne_prestation_unicite_rg04.sql` | Sprint 6bis (RG-15) : le regroupement par unité/période s'appuiera directement sur les colonnes de `V3`, sans appel à Workflow. |
| 6 | **`EtatModifiableService` porte à la fois le contrôle de statut et la portée d'accès (RG-12)**, plutôt que deux services séparés. Motif : les deux se déduisent du même appel à `GET /processus/{id}` (statut + code unité en une seule réponse) ; les séparer aurait dupliqué l'appel ou fait transiter le code unité à la main entre deux services. | `EtatModifiableService.java` | Point de passage unique pour les cinq endpoints — un futur endpoint de saisie n'a qu'à l'appeler, pas à recomposer la logique. |
| 7 | **`CreationLigneService` (Sprint 3.2, déjà testé) n'a pas été rouvert en profondeur.** `LigneService` (nouveau) charge la fiche et vérifie l'état *avant* de déléguer à `CreationLigneService`, au prix d'une seconde lecture de la fiche. Seule modification additive : une surcharge à 3 arguments pour transmettre `adresseIp`, l'ancienne signature à 2 arguments continuant de servir les 6 tests du Sprint 3.2 sans changement. | `CreationLigneService.java`, `LigneService.java` | Compromis assumé : une lecture en base de plus plutôt qu'un risque de régression sur du code déjà livré. |

---

## Ce qui a été fait — critères de validation du guide (§11)

| Critère | Statut | Détail |
|---|---|---|
| Cinq endpoints conformes au contrat d'API | ✅ | `POST /saisie/fiches`, `GET /saisie/fiches/{id}/lignes`, `POST/PUT/DELETE /saisie/lignes[/{id}]` — vérifié par grep sur les mappings, aucun endpoint hors contrat |
| Ouverture de fiche idempotente, non destructrice | ✅ | `FicheJournaliereService.ouvrir` : lecture avant création, relecture sur violation de contrainte concurrente ; test 4 (`SaisieControllerIT`) prouve que la seconde ouverture rend `200` avec les lignes intactes |
| Trois décisions de l'étape 3 tranchées | ✅ | Fréquence (à chaque écriture, acté au 3.1), panne Workflow (refus conservateur, acté au 3.1), **code HTTP** (422, tranché ici) — plus la question non anticipée par le guide (absence du service Workflow), également tranchée avec l'utilisateur |
| Modification rejouant montant et doublon | ✅ | `LigneService.modifier` : RG-04 rejouée en excluant la ligne (migration additive du repository), RG-03 intégralement rejouée à la date de la fiche ; tests 10 et 11 |
| Montant client toujours ignoré | ✅ | `CreationLigneRequest` sans champ montant, `@JsonIgnoreProperties(ignoreUnknown = true)` ; test 9, assertion structurelle sur les composants du record |
| Portée d'accès appliquée sur les cinq endpoints | ✅ | `EtatModifiableService.exigerEcriturePossible` (3 endpoints d'écriture) et `.exigerLecturePossible` (ouverture et consultation) — un seul point de passage |
| Opérations tracées dans le journal | ✅ | `OUVERTURE_FICHE_JOURNALIERE`, `CREATION_LIGNE_PRESTATION`, `MODIFICATION_LIGNE_PRESTATION`, `SUPPRESSION_LIGNE_PRESTATION`, `ACCES_REFUSE` (habilitation absente, rôle insuffisant, panne Identité) |
| Quinze tests passants | ✅ | **19 nouveaux tests** (16 dans `SaisieControllerIT`, dont les 15 exigés + 1 bonus rôle étranger ; 3 dans `BouchonVerificationProcessusTest`) |
| Aucun endpoint hors contrat | ✅ | 5 mappings, préfixe `/saisie`, aucun de plus |

---

## Les 19 tests

**`SaisieControllerIT` — 16 tests**, sur le modèle de `GrilleControllerIT` (Sprint
2.2) : services applicatifs simulés, jeton réel traversant `RoleJwtConverter`.

| # | Test | Ce qu'il prouve |
|---|---|---|
| 1 | ouverture sans jeton | `401` |
| 2 | ouverture hors portée | `403 UTILISATEUR_NON_HABILITE` |
| 3 | première ouverture | `201`, fiche vierge |
| 4 | **seconde ouverture du même jour** | `200`, même fiche, ligne déjà saisie conservée — **le test le plus important**, RG-05 n'est pas une remise à zéro |
| 5 | ouverture d'un autre jour | fiche distincte, vierge |
| 6 | création nominale | `201`, montant et grille du service |
| 7 | création en doublon | `409 DOUBLON_LIGNE` |
| 8 | création sans grille | `422 GRILLE_INDISPONIBLE` |
| 9 | montant transmis par le client | ignoré ; commande sans aucun composant « montant » (réflexion) |
| 10 | modification de session | montant recalculé (2500 → 3000, grille 12 → 13) |
| 11 | modification créant un doublon | `409` |
| 12 | suppression | `204` |
| 13 | écriture sur état verrouillé | `422 ETAT_NON_MODIFIABLE`, message citant le statut |
| 14 | consultation | lignes + bénéficiaire + sous-total (4000 = 2500 + 1500) |
| 15 | consultation hors portée | `403` |
| — | rôle étranger (DRH) sur un endpoint agent | `403 ACCES_REFUSE` — couvre la nuance rôle/portée du test 2 du guide |

**`BouchonVerificationProcessusTest` — 3 tests**, sans contexte Spring
(`MockEnvironment`) : un boot complet hors profil `dev` échoue pour une raison
non probante (datasource absente avant même ce garde-fou) — le comportement du
garde-fou s'isole donc au niveau unitaire.

| # | Test | Ce qu'il prouve |
|---|---|---|
| 1 | profils `dev` + `bouchon-workflow` | démarrage toléré |
| 2 | `bouchon-workflow` seul, sans `dev` | `IllegalStateException`, message citant « demarrage refuse » |
| 3 | `verifier(...)` | rend un `ProcessusVerifie` conforme à la configuration, sans appel réseau |

---

## Points ouverts créés par ce sous-sprint

**1. Quatre actions obligatoires au Sprint 4** (contrat écrit, non vérifié en
intégration — `docs/rattachement-processus.md` §6, rappelé par ce sprint) :
supprimer le bouchon et son `@Profile`, retirer la configuration
`app.workflow.bouchon`, vérifier en intégration réelle le mapping JSON de
`GET /processus/{id}`, et **surtout** s'assurer que Workflow nomme ses champs de
réponse exactement `idProcessus`, `statut`, `codeUnite`, `moisPaiement`,
`anneePaiement` — sans quoi `VerificationProcessusHttpClient` les lira comme
absents et refusera toute écriture en `503`, avec un message de journal nommant
les champs manquants pour rendre ce diagnostic rapide. Voir
`docs/decisions/2026-08-31-bouchon-workflow-et-cablage-de-la-verification.md`
§5, actions B-01 à B-04.

**2. Écart avec le contrat d'API §1.3, à corriger dans le document de
référence** (action **C-01**, hors du dépôt d'implémentation) : la transition
refusée relève désormais du `422`, pas du `409`, pour deux services du module.
Voir `docs/decisions/2026-08-31-code-http-du-refus-sur-etat-non-modifiable.md`
§4.

**3. `idUtilisateur` reste nul dans les traces d'audit du service Saisie.**
Réouvrable si un besoin métier futur exige une colonne d'auteur sur
`ligne_prestation` ou `fiche_journaliere`. Voir
`docs/decisions/2026-08-31-idutilisateur-non-renseigne-en-saisie.md` §4.

**4. `CLAUDE.md` §17 n'est pas encore mis à jour**, conformément à ce qu'avait
prévu le Sprint 3.1 : les décisions du Sprint 3 y seront reportées **à la
clôture du Sprint 3** (après le 3.4), en une fois.

---

## Fichiers

**Créés — DTO api (7) :** `api/dto/OuvertureFicheRequest.java`,
`IdentiteBeneficiaireRequest.java`, `CreationLigneRequest.java`,
`ModificationLigneRequest.java`, `LigneResponse.java`, `FicheResponse.java`,
`api/ErreurApiDto.java`.

**Créés — api (2) :** `api/GestionnaireErreursApi.java`, `api/SaisieController.java`.

**Créés — application (9) :** `EtatModifiableService.java`,
`FicheJournaliereService.java`, `LigneService.java`, `LigneAvecBeneficiaire.java`,
`VerificationProcessusClient.java`, `ResultatVerificationProcessus.java`,
`HabilitationClient.java`, `ResultatHabilitationUnite.java`.

**Créés — domaine (6) :** `StatutProcessusEnum.java`,
`exception/EtatNonModifiableException.java`,
`exception/ProcessusIntrouvableException.java`,
`exception/LigneIntrouvableException.java`,
`exception/AgentNonHabiliteException.java`,
`exception/ServiceIdentiteIndisponibleException.java`,
`exception/ServiceWorkflowIndisponibleException.java`.

**Créés — infrastructure (5) :** `infrastructure/workflow/ProcessusReponse.java`,
`VerificationProcessusHttpClient.java`, `BouchonVerificationProcessus.java`,
`infrastructure/identite/HabilitationReponse.java`, `HabilitationHttpClient.java`.

**Créés — migrations (2) :** `V3__fiche_journaliere_recopie_unite_et_periode.sql`,
`V4__ligne_prestation_unicite_rg04.sql`.

**Créés — tests (2) :** `api/SaisieControllerIT.java`,
`infrastructure/workflow/BouchonVerificationProcessusTest.java`.

**Créés — docs (3) :**
`docs/decisions/2026-08-31-code-http-du-refus-sur-etat-non-modifiable.md`,
`docs/decisions/2026-08-31-bouchon-workflow-et-cablage-de-la-verification.md`,
`docs/decisions/2026-08-31-idutilisateur-non-renseigne-en-saisie.md`, ce résumé.

**Modifiés (11) :** `domaine/FicheJournaliere.java` (constructeur + 3 champs
recopiés), `domaine/LignePrestation.java` (méthode `reviser`),
`application/ControleDoublonService.java` (variante hors-ligne pour PUT),
`application/CreationLigneService.java` (surcharge additive `adresseIp`),
`infrastructure/LignePrestationRepository.java` (méthode `...AndIdNot`),
`application-dev.yml` (config `app.identite.url`, `app.workflow.url` et bouchon),
`pom.xml` (dépendances `spring-boot-starter-webmvc-test` et
`spring-boot-starter-security-test`), 4 fichiers de test du Sprint 3.1/3.2 mis à
jour pour la nouvelle signature du constructeur de `FicheJournaliere`.

---

## Vérification manuelle réelle — trois services démarrés, jeton Keycloak réel

Faite par l'assistant à la demande de l'utilisateur (« fait le reste toi même »
après lancement de Docker). PostgreSQL était déjà actif ; le conteneur partagé
`dottel-keycloak` (arrêté) a été redémarré pour l'occasion. Les trois services —
Identité (8081), Grilles (8083), Saisie (8082, profils `dev,bouchon-workflow`) —
ont tourné réellement, avec un jeton obtenu par grant `password` auprès du realm
`afb-rations-dev` pour `jean_mbarga` (AGENT_UNITE, code unité `00002`, confirmé
via `GET /identite/moi`).

| # | Scénario | Résultat obtenu | Conforme |
|---|---|---|---|
| 1 | `POST /saisie/fiches`, première ouverture | `201`, fiche vierge (id 111) | ✅ |
| 2 | `POST /saisie/fiches`, même jour rejoué | `200`, **même id 111** | ✅ RG-05 |
| 3 | `POST /saisie/lignes` nominal | `201`, montant `1500` (grille réelle #1) | ✅ RG-03 |
| 4 | Rejeu identique de la ligne | `409 DOUBLON_LIGNE` | ✅ RG-04 |
| 5 | Réouverture de la fiche après création de ligne | `200`, **la ligne est là**, sous-total `1500` | ✅ RG-05 non destructif |
| 6 | `GET /saisie/fiches/111/lignes` | ligne + bénéficiaire + sous-total | ✅ |
| 7 | `PUT /saisie/lignes/55` (session → SOIR, `montantApplique:999999` injecté) | `200`, montant **`2000`** (grille réelle #2), le `999999` du client **jamais lu** | ✅ |
| 8 | `DELETE /saisie/lignes/55` | `204` | ✅ |
| 9 | Consultation après suppression | `200`, `lignes: []` | ✅ |
| 10 | Seconde suppression de la même ligne | `404 LIGNE_INTROUVABLE` | ✅ non-idempotence voulue |
| 11 | `POST /saisie/fiches` sans jeton | `401` | ✅ |
| 12 | Création sur une fiche du 2020-01-01 (aucune grille réelle) | `422 GRILLE_INDISPONIBLE` | ✅ |
| 13 | `POST /saisie/fiches` avec un jeton DRH | `403 ACCES_REFUSE` | ✅ |
| 14 | Écriture avec le bouchon réglé sur `WORKFLOW_BOUCHON_STATUT=SOUMIS` | `422 ETAT_NON_MODIFIABLE`, message citant `SOUMIS` et l'unité `00002` | ✅ |

**Les 14 scénarios sont conformes**, contre les vrais services (PostgreSQL réel,
grilles tarifaires réellement enregistrées côté Grilles, jeton Keycloak réel) —
au-delà de ce que les tests automatisés (mocks) peuvent prouver. Les trois
services ont été arrêtés proprement après vérification ; `dottel-keycloak` a été
laissé actif (partagé avec le projet DOTTEL).

---

## Suite

Sprint 3.4 — consolidation mensuelle et clôture du Sprint 3. `CLAUDE.md` §17 sera
mis à jour à cette occasion avec l'ensemble des décisions du Sprint 3, y compris
celles de ce sous-sprint.
