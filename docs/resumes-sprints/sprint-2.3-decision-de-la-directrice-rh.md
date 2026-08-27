# Sprint 2.3 — Décision de la Directrice RH

**Date :** 2026-08-27
**Service :** `service-grilles` (port 8083, base `rations_grilles`)
**Règles et stories :** RG-14, RG-10, US-14, CT-27, CT-28, CT-29
**Statut :** livré, 83 tests passants, `BUILD SUCCESS`, vérification manuelle bout en bout concluante (§8)

---

## 1. Ce que fait le sprint

L'ARH propose (Sprint 2.2), la DRH décide. Ce sous-sprint implémente la décision :
deux endpoints, une bascule transactionnelle, un rejet motivé.

| Endpoint | Rôle | Effet |
| --- | --- | --- |
| `POST /grilles/{id}/validation` | DRH | La grille devient `ACTIVE` ; celle qu'elle remplace est fermée à la veille |
| `POST /grilles/{id}/rejet` | DRH | La grille passe `REJETEE` avec motif ; **la grille en vigueur n'est pas touchée** |

Le point délicat n'est pas la validation, c'est la **bascule** : activer une
grille suppose d'en fermer une autre, et ces deux écritures portent sur deux
lignes différentes.

---

## 2. Décisions prises en cours de sprint

### 2.1 Bornage — la veille de la `date_debut` de la remplaçante

**Question posée à l'étape 2, arbitrée avant tout code.** Deux options : fermer
l'ancienne à la veille de la prise d'effet de la nouvelle, ou à la date de la
décision.

**Retenu : la veille de la `date_debut`.**

Validation et prise d'effet sont deux instants différents — la DRH tranche le
27 août une grille qui prend effet le 1er septembre. Fermer à la date de décision
laisserait les 28, 29, 30 et 31 août **sans aucune grille active** : une
prestation datée de ces jours-là, saisie en retard ou régularisée au Sprint 6bis,
ne trouverait aucun montant applicable. La veille est la seule borne produisant
un partitionnement exact du temps : à toute date, une grille et une seule répond.

*Conséquence assumée :* une remplaçante à effet futur fait porter dès aujourd'hui
une `date_fin` future à l'ancienne. Elle reste en vigueur jusque-là — à présenter
comme une **fermeture programmée**, pas comme une grille close. Le champ
`ancienneFermee` de la réponse porte cette information.

*Ce qui en dépend :* toute la résolution du montant du Sprint 2.4, et la
régularisation du Sprint 6bis.

### 2.2 Atomicité, et ordre d'écriture fixé plutôt que subi

Fermeture et activation sont dans **une seule transaction**. Une fermeture réussie
suivie d'une activation échouée laisserait le couple sans tarif, et le défaut ne
se verrait qu'au Sprint 3, sous la forme d'une saisie qui ne trouve plus de
grille.

S'ajoute un **vidage explicite** (`saveAndFlush`) entre les deux écritures.
L'index partiel `ux_grille_active_par_couple` est évalué par PostgreSQL
*instruction par instruction*, pas au commit : si Hibernate écrivait l'activation
avant la fermeture, il existerait l'espace d'un ordre SQL deux lignes actives
sans `date_fin`, et la base refuserait une bascule pourtant légitime. L'ordre de
vidage du contexte de persistance n'étant pas un contrat public d'Hibernate, il
est fixé ici.

Frontière retenue : l'appel réseau au service Identité est **hors** transaction
(sinon une transaction resterait ouverte pendant 3 s de délai de lecture) ;
l'audit est publié **après commit** (doctrine 1.3 — l'audit ne fait jamais
échouer le métier).

### 2.3 Le contrôle de statut précède toute écriture

`TransitionGrille.exigerValidationPossible` est appelé **avant** de toucher à
l'ancienne grille. Vérifier après aurait fonctionné par le rollback, mais aurait
fait dépendre la correction du résultat d'un mécanisme technique plutôt que de
l'ordre des étapes.

### 2.4 Le rejet ne consulte même pas la grille en vigueur

Un rejet dit « ce tarif ne s'appliquera pas », pas « il n'y a plus de tarif ».
Confondre rejet et fermeture priverait le couple de grille active sans raison.
Le service ne va pas chercher la grille courante : il n'a rien à lui faire — et
un test le verrouille.

### 2.5 Passage par la machine à états plutôt qu'ouverture de visibilité

Les mutateurs de `GrilleTarifaire` sont en visibilité paquet par conception
(Sprint 2.1) : aucune couche au-dessus du domaine ne change l'état d'une grille
sans qu'une transition ait été jugée légale. Le compilateur l'a rappelé au
moment d'écrire le service. Plutôt que d'ouvrir la visibilité, deux surcharges
ont été ajoutées à `TransitionGrille` — `valider(..., libelleValidateur)` et
`rejeter(..., idValidateur, libelleValidateur)` — qui recopient le libellé figé
de la DRH dans la même opération que la transition.

### 2.6 Trois codes d'erreur ajoutés au contrat

| Code | HTTP | Cause |
| --- | --- | --- |
| `GRILLE_INTROUVABLE` | 404 | Aucune grille ne porte cet identifiant |
| `TRANSITION_INTERDITE` | 422 | Statut incompatible (valider une grille déjà ACTIVE, rejeter une REJETEE…) |
| `MOTIF_OBLIGATOIRE` | 422 | Rejet sans motif exploitable |

`TRANSITION_INTERDITE` est en **422 et non 409** : rien n'est dupliqué, c'est une
règle de gestion qui refuse. `MOTIF_OBLIGATOIRE` reprend le code déjà retenu par
le contrat pour le retour d'un processus sans motif — même règle, même code, un
seul traitement à écrire côté frontend.

---

## 3. Fichiers

### Créés

| Chemin | Rôle |
| --- | --- |
| `application/DecisionGrilleService.java` | Validation avec bascule, rejet motivé |
| `api/dto/RejetGrilleRequest.java` | Motif, `@NotBlank` |
| `api/dto/ValidationGrilleResponse.java` | Grille activée **et** grille fermée |
| `domaine/exception/GrilleIntrouvableException.java` | 404 |
| `test/.../application/DecisionGrilleServiceTest.java` | 16 tests unitaires |
| `docs/decisions/2026-08-27-bornage-et-atomicite-de-la-bascule.md` | Décisions 2.1 à 2.4 ci-dessus |

### Modifiés

| Chemin | Modification |
| --- | --- |
| `api/GrilleController.java` | Deux endpoints de décision, documentation Swagger |
| `api/GestionnaireErreursApi.java` | Trois gestionnaires : 404, 422 ×2 |
| `domaine/TransitionGrille.java` | `exigerValidationPossible` + surcharges portant le validateur |
| `test/.../api/GrilleControllerIT.java` | 12 tests d'intégration ajoutés ; retrait du test de périmètre du 2.2 |
| `test/.../infrastructure/GrilleTarifaireRepositoryTest.java` | Test 13 : bascule de bout en bout contre PostgreSQL, robuste à l'historique accumulé (§8) |
| `CLAUDE.md` | Six lignes en section 17 (dont l'hygiène de build relevée en §8), précision en section 11 |

Aucune migration Flyway : les colonnes `date_validation`, `motif_rejet` (V3) et
`libelle_validateur` (V4) existaient déjà.

---

## 4. Tests — 83 passants

| Groupe | Nombre | Couvre |
| --- | --- | --- |
| `DecisionGrilleServiceTest` — Validation | 10 | Bascule nominale, ordre d'écriture, première grille, statuts refusés (BROUILLON / ACTIVE / REJETEE), échec simulé aux deux points, 404, traçabilité |
| `DecisionGrilleServiceTest` — Rejet | 6 | Rejet nominal, sans motif, motif en espaces, grille ACTIVE, ancienne intacte, traçabilité |
| `GrilleControllerIT` | 24 | 401, 403 ARH, 200 DRH, 404, 422 ×2, 503, rejet 400/422/200/403 |
| `GrilleTarifaireRepositoryTest` | 6 | Dont le **test 13** : bascule réelle contre PostgreSQL |
| Sprints antérieurs | 37 | Aucune régression |

### Les tests qui comptent

**Test 6 — échec simulé pendant la fermeture.** La fermeture de l'ancienne lève
une `DataIntegrityViolationException`. Vérifié : la seconde écriture n'est même
pas tentée, la cible reste `EN_ATTENTE_DRH` sans validateur, et **aucun
événement d'audit n'est publié** — une bascule qui n'a pas eu lieu ne doit pas
laisser dans le journal la trace d'une décision.

**Test 6bis — échec pendant l'activation.** C'est le cas dangereux : l'ancienne
a bien été fermée *en mémoire*, et seul le rollback empêche cette fermeture
d'atteindre la base. Sans transaction, le couple TRANSPORT / SOIR se retrouverait
sans aucun tarif applicable.

**Test 1bis — ordre d'écriture.** `InOrder` verrouille `saveAndFlush(ancienne)`
avant `save(cible)`. Ce test échouera si quelqu'un « simplifie » en un `saveAll`.

**Test 13 — bout en bout contre PostgreSQL.** Après bascule réelle :
`rechercherGrilleCourante` renvoie une seule ligne, la nouvelle ; à la prise
d'effet le nouveau montant s'applique ; **à la veille, l'ancien montant
s'applique encore**. Aucun trou, aucun chevauchement, index partiel jamais violé.

---

## 5. Critères de validation du guide

| Critère | Statut |
| --- | --- |
| Séquence de validation décrite et validée avant codage | ✅ Fait (étape 1, pseudo-code approuvé) |
| Décision sur la `date_fin` tranchée | ✅ Fait (veille de la `date_debut`) |
| Bascule atomique, prouvée par le test d'échec simulé | ✅ Vérifié (tests 6 et 6bis) |
| `POST /grilles/{id}/validation` conforme au contrat | ✅ Vérifié |
| `POST /grilles/{id}/rejet` conforme au contrat | ✅ Vérifié |
| Motif de rejet obligatoire et non vide | ✅ Vérifié (deux étages) |
| Rejet laissant l'ancienne grille active | ✅ Vérifié (test 10) |
| Transitions interdites refusées | ✅ Vérifié (tests 3, 4, 5, 9) |
| Décisions tracées dans le journal | ✅ Vérifié (`VALIDATION_GRILLE`, `FERMETURE_GRILLE`, `REJET_GRILLE`) |
| Quinze tests passants | ✅ Dépassé — 28 tests pour ce sous-sprint, 83 au total |
| Index partiel jamais violé | ✅ Vérifié (test 13, contre la vraie base) |

---

## 6. Hors périmètre, volontairement

- **La résolution du montant** (`GET /grilles/active`) reste au Sprint 2.4.
- **L'interface DRH** n'existe pas : le frontend viendra plus tard.
- **Aucune notification** à l'ARH lors d'un rejet — le contrat n'en prévoit pas,
  et le module n'a pas de canal de notification.

---

## 7. Ce que le sprint suivant hérite

Le Sprint 2.4 s'appuie sur un partitionnement du temps garanti par construction :
`rechercherGrilleActive(nature, session, date)` renvoie au plus une ligne **parce
que** les périodes ne se chevauchent pas. Cette garantie vient d'ici, et le test
13 est ce qui la maintient.

---

## 8. Vérification manuelle du 2026-08-27 — résultats

Exécutée bout en bout contre les services réels (`service-identite` 8081,
`service-grilles` 8083), Keycloak `afb-rations-dev`, PostgreSQL et Kafka.

| # | Vérification | Résultat |
| --- | --- | --- |
| 1 | Jetons ARH / DRH, `aud: rations-api` | ✅ `claire_nkolo → [ARH]`, `agnes_tchinda → [DRH]` |
| 2 | `POST /grilles` sur un couple déjà en attente | ✅ 409 `GRILLE_EN_ATTENTE_EXISTANTE` (2.2 intact) |
| 3 | Validation par l'ARH | ✅ 403 `ACCES_REFUSE` |
| 4 | Validation sans jeton | ✅ 401 |
| 5 | **Bascule par la DRH** | ✅ 200 — nouvelle `ACTIVE` sans `date_fin`, ancienne fermée au **31/08**, veille du 01/09 |
| 6 | Rejet sans motif / motif en espaces | ✅ 400 `REQUETE_INVALIDE` les deux fois |
| 7 | Rejet motivé | ✅ 200, `REJETEE`, motif enregistré |
| 8 | Revalider une `ACTIVE` / valider une `REJETEE` / rejeter une `ACTIVE` | ✅ 422 `TRANSITION_INTERDITE` les trois fois |
| 9 | Identifiant inexistant | ✅ 404 `GRILLE_INTROUVABLE` |
| 10 | Invariant RG-14 en base | ✅ **une seule** grille courante sur chacun des quatre couples |
| 11 | Rejet n'ayant fermé personne | ✅ RATION/SOIR garde sa grille active, `date_fin` NULL |
| 12 | Journal d'audit | ✅ `VALIDATION_GRILLE`, `FERMETURE_GRILLE`, `REJET_GRILLE` sur `rations.audit.evenement` |
| 13 | Swagger | ✅ les quatre endpoints déclarés |

**Deuxième bascule en chaîne**, jouée pour éprouver le cas où la grille fermée
est elle-même issue d'une bascule. Historique final de TRANSPORT / SOIR :

| id | montant | date_debut | date_fin |
| --- | --- | --- | --- |
| 4 | 1500 | 2026-08-01 | 2026-08-31 |
| 12 | 3000 | 2026-09-01 | 2026-11-30 |
| 26 | 3500 | 2026-12-01 | *(courante)* |

Trois périodes qui s'enchaînent sans trou ni chevauchement. C'est exactement ce
dont le Sprint 2.4 a besoin.

### Un défaut relevé et corrigé pendant la vérification

Les premiers événements d'audit sortaient avec les dates en **tableau de
composants** (`"dateDebut":[2026,9,1]`) au lieu de l'ISO 8601 — précisément le
défaut corrigé au Sprint 2.2.

Le code de `DeltaAudit` était pourtant juste. La cause était ailleurs : le jar
`rations-audit-commun` installé dans `~/.m2` datait de **12h19**, alors que le
correctif avait été commité à **18h07**. `mvn spring-boot:run -pl service-grilles`
résout la dépendance depuis le dépôt local, **pas depuis le réacteur** : un
module commun modifié mais non réinstallé reste invisible à l'exécution, même
après un `mvn test` réussi — les tests, eux, passent par le réacteur.

Corrigé par `mvn -pl rations-audit-commun install`, puis revérifié : les dates
sortent désormais en `"2026-11-30"` et `"2026-12-01"`.

**À retenir pour les Sprints 3 à 6 :** toute modification de
`rations-audit-commun` doit être suivie d'un `install` avant de démarrer un
service à la main. Les tests ne le signaleront pas.

### Un second défaut, dans le test 13 lui-même

La vérification manuelle a laissé sur TRANSPORT / SOIR un historique de trois
grilles. Le test 13 a alors échoué sur une violation de
`ux_grille_active_par_couple` — et il avait raison de le faire : il prenait
l'ancienne grille via `rechercherGrilleActive(aujourd'hui)`, alors que le service
de décision, lui, utilise `rechercherGrilleCourante`.

Les deux coïncident tant qu'un couple n'a qu'une grille, et **divergent dès la
première bascule** : la grille qui s'applique aujourd'hui peut être une grille
déjà close, que fermer une seconde fois laisserait deux lignes sans `date_fin`.

Le test a donc été aligné sur le service — grille courante comme point de départ,
prise d'effet calculée depuis sa `date_debut` plutôt que depuis la date du jour.
Il est désormais robuste à l'historique accumulé, ce que deux exécutions
successives sur une base à trois lignes ont confirmé.

Le défaut était dans le test, pas dans le code de production : `DecisionGrilleService`
utilisait `rechercherGrilleCourante` depuis le début.
