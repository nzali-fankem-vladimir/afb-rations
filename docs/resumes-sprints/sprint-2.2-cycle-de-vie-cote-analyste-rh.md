# Résumé Sprint 2.2 — Cycle de vie côté Analyste RH

**Service :** service-grilles · **Date :** 27 août 2026 · **Config :** Opus / effort Élevé

Deuxième sous-sprint du service Grilles. Objectif : exposer côté Analyste RH la
création, la soumission et la consultation des grilles, avec le contrôle
d'unicité de RG-14. **Aucun endpoint de décision DRH** — c'est le 2.3.

---

## Décisions prises en cours de route

Quatre questions ouvertes, tranchées avec toi avant d'écrire la moindre ligne.

| # | Question | Décision | Trace |
|---|---|---|---|
| 1 | Modifier une grille ACTIVE : nouvelle ligne ou mise à jour en place ? | **Nouvelle ligne (versionnement).** La remplaçante naît `EN_ATTENTE_DRH` ; l'ancienne sera fermée à la veille, au moment de la validation DRH (2.3). La mise à jour en place perdrait l'historique, rendrait injustifiables les lignes déjà payées, exigerait une transition interdite au Sprint 2.1, et laisserait le couple soit avec un montant non validé, soit sans grille active du tout. | `docs/decisions/2026-08-27-versionnement-et-unicite-des-grilles.md` §1 |
| 2 | Comment obtenir le libellé du créateur sans dépendance croisée ? | **Appel unique à `GET /identite/moi` à l'écriture, libellé recopié et figé dans la ligne** (migration `V4`). Point soulevé au passage : le vrai problème n'était pas le libellé mais `id_createur BIGINT NOT NULL`, que le jeton Keycloak ne peut pas fournir — **l'appel est imposé par le schéma, pas choisi**. Les lectures restent 100 % locales. | `docs/decisions/2026-08-27-libelle-acteur-grille.md` |
| 3 | Le contrôle d'unicité refuse-t-il toute grille sur un couple déjà actif ? | **Non : le refus porte sur la cohérence de période.** Une proposition postérieure à la grille en vigueur est acceptée (c'est le remplacement) ; une proposition antérieure ou du même jour est refusée, comme l'est une seconde proposition concurrente. *Ambiguïté du guide soulevée et arbitrée : son test n°10 et sa vérification complémentaire se contredisaient ; son propre exemple `curl` attend un 201 sur un couple déjà actif.* | `docs/decisions/2026-08-27-versionnement-et-unicite-des-grilles.md` §2 |
| 4a | Création et soumission en un ou deux temps ? | **Un seul appel.** Aucun `BROUILLON` n'est jamais persisté. L'option en deux temps ajouterait un 27ᵉ endpoint hors contrat et obligerait à rejouer le contrôle d'unicité à la soumission. | idem §3 |
| 4b | Quel code HTTP si service-identite est injoignable ? | **`503 SERVICE_IDENTITE_INDISPONIBLE`, pas 403.** Le refus est le même (doctrine 1.3), mais le diagnostic est juste : un 403 enverrait l'ARH réclamer une habilitation qu'il possède déjà pendant que la panne resterait invisible. Les deux cas sont tracés en audit. | `docs/decisions/2026-08-27-libelle-acteur-grille.md` §5 |

---

## Ce qui a été fait (critères de validation du guide, §11)

| Critère | Statut | Détail |
|---|---|---|
| Décision sur la modification d'une grille active tranchée | ✅ | Versionnement par nouvelle ligne (décision n°1), consignée et répercutée dans `CLAUDE.md` §17 |
| Décision sur création et soumission en un ou deux temps tranchée | ✅ | Un seul appel (décision n°4a) |
| Question du libellé créateur résolue sans dépendance croisée | ✅ *(avec nuance)* | **Aucune dépendance de code ni de base** : pas de classe partagée, pas d'accès à `rations_identite`, pas de clé étrangère inter-base. Il reste une **dépendance d'exécution à l'écriture seule** (`POST /grilles` → `GET /identite/moi`), imposée par `id_createur NOT NULL`. Voir la note d'honnêteté ci-dessous. |
| Contrôle d'unicité couvrant les grilles actives et en attente | ✅ | `UniciteGrilleService` : deux contrôles, propositions `EN_ATTENTE_DRH` d'abord, cohérence de période ensuite |
| `POST /grilles` conforme au contrat d'API | ✅ | 201 + `statutValidation: EN_ATTENTE_DRH`, rôle ARH, code `GRILLE_ACTIVE_EXISTANTE` du contrat |
| `GET /grilles` conforme, avec pagination de référence | ✅ | Format `PageResponse` du Sprint 1.2 (6 champs), filtre `statut` optionnel, rôles ARH et DRH, tri nature → session → date de début décroissante |
| Conflit refusé par le service avec message explicite | ✅ | Le refus vient du service, jamais de la base. Message citant nature, session, date et montant en vigueur. L'index partiel reste le garde-fou de course, traduit en 409 lisible plutôt qu'en 500 |
| Grille en attente sans effet sur la résolution du montant | ✅ | Vérifié **contre la vraie base** (`GrilleTarifaireRepositoryTest` n°12 bis) : proposition à 999 999 FCFA insérée, `rechercherGrilleActive` retourne toujours l'ancienne grille — aujourd'hui **et** à la date de prise d'effet demandée |
| Douze tests passants | ✅ | **55 tests service-grilles + 18 `rations-audit-commun`, 0 échec** — BUILD SUCCESS. Les 12 du guide sont couverts (table ci-dessous), 43 autres complètent |
| Aucun endpoint de validation créé | ✅ | Aucun `/grilles/{id}/validation` ni `/rejet`. **Un test dédié le vérifie** (404 sur les deux), pour que leur ajout au 2.3 soit un acte délibéré |

### Les douze tests du guide

| # | Test | Où |
|---|---|---|
| 1 | Création nominale, couple libre → `EN_ATTENTE_DRH` | `GrilleServiceTest` › Nominale |
| 2 | Couple déjà couvert par une grille ACTIVE → refusée, code de conflit | `GrilleServiceTest` › Conflits |
| 3 | Couple déjà `EN_ATTENTE_DRH` → refusée | `GrilleServiceTest` › Conflits |
| 4 | Montant négatif ou nul → refusé | `GrilleServiceTest` › ValidationEntree (3 cas : négatif, nul, absent) |
| 5 | Nature ou session absente → refusée | `GrilleServiceTest` › ValidationEntree (3 cas : nature, session, date) |
| 6 | Conformité aux décisions des étapes 1 et 4 | `GrilleServiceTest` › ConformiteAuxDecisions |
| 7 | `POST /grilles` sans jeton → 401 | `GrilleControllerIT` |
| 8 | `POST /grilles` avec jeton DRH → 403 | `GrilleControllerIT` |
| 9 | `POST /grilles` jeton ARH, couple libre → 201 `EN_ATTENTE_DRH` | `GrilleControllerIT` |
| 10 | `POST /grilles`, conflit → 409 citant nature et session | `GrilleControllerIT` (+ 10 bis : code distinct pour la proposition concurrente) |
| 11 | `GET /grilles` jeton ARH → 200, pagination correcte | `GrilleControllerIT` |
| 12 | `GET /grilles?statut=EN_ATTENTE_DRH` → filtre appliqué | `GrilleControllerIT` |
| **+** | **Vérification complémentaire (CT-25)** | `GrilleTarifaireRepositoryTest` n°12 bis et 12 ter, contre PostgreSQL |

---

## Deux points d'honnêteté

**1. La dépendance vers service-identite est réelle.** Le §2 du guide invitait à
vérifier par cartographie que « le service Grilles reste sans dépendance vers un
autre service ». Ce n'est plus tout à fait vrai : `POST /grilles` appelle
`GET /identite/moi`. Cette dépendance était **inévitable** — `id_createur` est
`NOT NULL` et son contenu n'existe que dans `service-identite` — et elle est
strictement bornée :

| Chemin | Dépend de service-identite ? |
|---|---|
| `POST /grilles` | Oui. Une panne empêche de proposer un tarif. |
| `GET /grilles` | Non. La consultation fonctionne service Identité arrêté. |
| Résolution du montant (Sprint 2.4) | Non. **La chaîne de paiement n'est pas touchée.** |

La cartographie graphify n'a pas été relancée : elle confirmerait une dépendance
désormais documentée et assumée, pas une dérive.

**2. La trace d'audit s'arrête au topic.** Les deux événements
(`CREATION_GRILLE`, `SOUMISSION_GRILLE`) ont été observés **sur
`rations.audit.evenement`** lors de la vérification manuelle. La ligne écrite
dans `audit_log` n'a pas été vue : elle suppose que `service-audit` tourne et
consomme. Le côté producteur, seul en jeu dans ce sous-sprint, est vérifié.

---

## Ce que la vérification manuelle a révélé

Les tests automatisés passaient tous. La vérification manuelle de bout en bout,
services démarrés, a fait apparaître **deux défauts qu'aucun d'eux ne pouvait
voir**.

### 1. Les dates du journal d'audit n'étaient pas en ISO 8601

Le détail d'un événement sortait ainsi :

```json
"dateDebut":{"avant":null,"apres":[2026,9,1]}
```

Jackson sérialisait les `LocalDate` en tableau de composants, contre l'ISO 8601
exigé par CLAUDE.md §11. Le journal restait lisible — ce qui rendait le défaut
d'autant plus facile à laisser passer : il ne cassait rien, il obligeait
seulement quiconque relit une trace à reconstituer la date de tête.

La cause était dans `DeltaAudit`, donc dans `rations-audit-commun` : **le défaut
valait pour les six services**, et aurait grossi à chaque nouvelle date tracée.
Corrigé à la source (`WRITE_DATES_AS_TIMESTAMPS` désactivé), hors du périmètre
annoncé du sous-sprint, avec un test de non-régression dans `DeltaAuditTest`. Le
module reste à 18 tests passants, contrôle de périmètre compris.

### 2. Deux tests dépendaient de l'état de la base de développement

`GrilleTarifaireRepositoryTest` comptait les propositions en attente
(`hasSize(1)`), en supposant qu'aucune n'existait au départ. La grille créée par
la vérification manuelle a fait échouer le test — pour une raison entièrement
étrangère à ce qu'il vérifiait.

Corrigé : les deux tests mesurent désormais un **écart** (avant / après) ou
retrouvent leur ligne **par identifiant**, jamais un total absolu. Un test qui
compte les lignes d'une base partagée finit toujours par échouer un jour, et ce
jour-là il ne dit rien d'utile.

C'est l'argument même de la vérification manuelle : elle exerce le système dans
des conditions que les tests, isolés par construction, ne reproduisent pas.

### Ce qui a été observé, services démarrés

| Vérification | Résultat |
|---|---|
| `POST /grilles`, couple libre | **201**, `"createur":"NKOLO Claire"` — le libellé, pas l'identifiant |
| Rejeu du même appel | **409** `GRILLE_EN_ATTENTE_EXISTANTE` |
| `RATION/JOUR` au 01/08 | **409** `GRILLE_ACTIVE_EXISTANTE`, message citant nature, session, date et montant en vigueur |
| Montant nul | **400** `REQUETE_INVALIDE` |
| `GET /grilles?statut=EN_ATTENTE_DRH` | **200**, six champs de pagination, une seule ligne |
| `POST` avec jeton DRH | **403** `ACCES_REFUSE` — et la même DRH lit en **200** |
| `POST` sans jeton | **401** |
| Table `grille_tarifaire` | les 4 grilles ACTIVE **inchangées**, la proposition à part — **CT-25 vérifiée à l'œil** |
| Topic `rations.audit.evenement` | `CREATION_GRILLE`, `SOUMISSION_GRILLE` (`idUtilisateur: 4`) et `ACCES_REFUSE` (motif `ROLE_INSUFFISANT`, login `agnes_tchinda`) |

`audit_log` n'a pas été alimentée : `service-audit`, le consommateur, ne tournait
pas. Les événements sont bien sur le topic — c'est le côté producteur qui relève
de ce sous-sprint.

---

## Héritage des sprints précédents, appliqué

- **Sprint 1.2** — `PageResponse` recopié à l'identique (duplication assumée,
  comme `NatureEnum` / `SessionEnum` au 2.1 : `rations-audit-commun` est la seule
  mutualisation du backend, et son périmètre est vérifié au build).
- **Sprint 1.3** — publication des refus **centralisée dans
  `GestionnaireErreursApi`**, seul point où tous convergent ; producteur d'audit
  de `rations-audit-commun` utilisé tel quel, aucun réglage surchargé ; jeton
  utilisateur relayé sans compte de service ; refus conservateur sur panne.
- **Sprint 2.1** — machine à états réutilisée telle quelle
  (`TransitionGrille.soumettre`), aucune transition ajoutée ni assouplie ; index
  partiel conservé comme garde-fou de course.

---

## Fichiers

**Créés (13).**
`api/GrilleController.java`, `api/GestionnaireErreursApi.java`,
`api/ErreurApiDto.java`, `api/dto/CreationGrilleRequest.java`,
`api/dto/GrilleResponse.java`, `api/dto/PageResponse.java`,
`application/GrilleService.java`, `application/UniciteGrilleService.java`,
`domaine/exception/ConflitGrilleException.java`,
`domaine/exception/AuteurNonHabiliteException.java`,
`infrastructure/identite/ClientIdentite.java`,
`infrastructure/identite/AuteurIdentifie.java`,
`infrastructure/identite/IdentiteIndisponibleException.java`,
`resources/db/migration/V4__grille_ajout_libelles_acteurs.sql`.

**Tests créés (3).** `application/UniciteGrilleServiceTest.java` (9 tests),
`application/GrilleServiceTest.java` (19 tests),
`api/GrilleControllerIT.java` (13 tests).

**Modifiés (5).** `domaine/GrilleTarifaire.java` (2 champs, constructeur,
mutateur, accesseurs), `infrastructure/GrilleTarifaireRepository.java`
(`rechercherGrilleCourante`, `rechercherPropositionsEnAttente`),
`infrastructure/GrilleTarifaireRepositoryTest.java` (vérification
complémentaire), `domaine/TransitionGrilleTest.java` (site d'appel),
`service-grilles/pom.xml` (starters de test webmvc et security),
`application-dev.yml` (`app.identite.url`).

**Hors périmètre, assumé (2).** `rations-audit-commun/DeltaAudit.java`
(sérialisation ISO 8601 des dates) et `DeltaAuditTest.java` (non-régression).

**Documentation (3).**
`docs/decisions/2026-08-27-versionnement-et-unicite-des-grilles.md`,
`docs/decisions/2026-08-27-libelle-acteur-grille.md`, ce résumé.
`CLAUDE.md` §17 complétée de **sept lignes** (six du sous-sprint, une pour le
correctif hors périmètre).

---

## Suite

**Sprint 2.3 — décision de la Directrice RH.** Trois points hérités d'ici :

1. **La validation est une opération à deux lignes, transactionnelle** :
   fermeture de l'ancienne (`date_fin` à la **veille** de la `date_debut` de la
   remplaçante) et activation de la nouvelle. Les deux ou aucune.
2. `id_validateur` et `libelle_validateur` suivent le même chemin que le
   créateur — le mutateur `enregistrerLibelleValidateur` existe déjà.
3. Le test « aucun endpoint de validation » de `GrilleControllerIT` devra être
   **retiré sciemment**, pas contourné.
