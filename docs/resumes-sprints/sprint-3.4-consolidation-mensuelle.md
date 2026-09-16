# Résumé Sprint 3.4 — Consolidation mensuelle et clôture du Sprint 3

> ⚠️ **Lire à la lumière du sprint Maille 1 (10 septembre 2026).** Ce document décrit
> l'état du module **à sa date**, quand la période de paiement était un mois porté par
> le couple `(mois_paiement, annee_paiement)`. Le métier a depuis établi que le cycle
> est **hebdomadaire** (point M-04), et la période est devenue un intervalle de dates
> `(date_debut, date_fin)`. Ce qui est écrit ici reste vrai de son époque et n'est
> **pas** réécrit : un enregistrement daté qu'on corrige après coup cesse d'être un
> enregistrement. Voir
> `docs/decisions/2026-09-09-rythme-de-paiement-et-maille-de-la-periode.md` et
> `docs/resumes-sprints/sprint-maille-1-periode-en-intervalle-de-dates.md`.

**Service :** service-saisie · **Date :** 31 août 2026 · **Config :** Opus / Élevé
(étapes 1-3 et suivantes, l'utilisateur ayant choisi de rester en Opus).

**Statut :** livré. `mvn -pl service-saisie test` → **BUILD SUCCESS, 76 tests, 0
échec** (45 des Sprints 3.1 à 3.3 + **31 nouveaux**). Backend complet recompilé
sans régression. Cartographie relancée : **3056 nœuds, 6106 liens, 179
communautés**.

**Le Sprint 3 est clos. Le service Saisie est complet :** cinq endpoints publics
au contrat, un seul endpoint interne, aucun endpoint créé par anticipation pour le
Sprint 4.

---

## Le cœur du sous-sprint

RG-06 est **partagée entre deux services**. Saisie détient les données et produit
l'état consolidé ; Workflow détient `processus_mensuel`, porte le `montant_total`
et applique l'aiguillage au seuil (RG-08). Ce sous-sprint construit la moitié
Saisie.

Le montant total n'est pas un chiffre parmi d'autres : c'est lui qui décidera, au
Sprint 4, si un état est clôturé directement ou monte au Directeur Réseau. Une
erreur d'un franc autour de 100 000 XAF envoie un dossier au mauvais niveau de
validation — un défaut de contrôle interne, pas une gêne d'exploitation. Trois
garanties structurelles ont été posées pour cela :

1. **Le total est la somme des sous-totaux, eux-mêmes sommes des lignes rendues
   dans la réponse.** Aucun `SUM` SQL parallèle. La divergence entre le détail
   affiché et le total est donc *structurellement impossible* : retirer une ligne
   du détail la retire du total dans le même mouvement.
2. **Lecture par `IN`, jamais par jointure.** Une jointure mal posée peut
   multiplier les lignes et compter deux fois un montant.
3. **Entiers uniquement.** `int` par ligne, `long` en somme. Aucun `double`,
   aucun `BigDecimal`, vérifié par grep sur tout le service et par deux tests par
   réflexion.

---

## Décisions prises en cours de route

| # | Décision | Trace | Impact sprints suivants |
|---|---|---|---|
| 1 | **L'endpoint de consolidation est interne, distinct du `/processus/{id}/etat` public du contrat.** `GET /saisie/processus/{id}/etat` vit côté Saisie (8082), n'est pas routé par la passerelle et ne change pas le compte des 26 endpoints. Même statut que `GET /identite/habilitation` (Sprint 1.3). Question posée par l'utilisateur, documentée à sa demande sur le même modèle. | `docs/decisions/2026-08-31-endpoint-interne-de-consolidation.md` §2 | Sprint 4 : Workflow reprend cette réponse, y ajoute statut / type / `montant_total`, et sert l'endpoint public. |
| 2 | **`codeUnite` transmis en paramètre obligatoire par Workflow, et non lu sur les fiches.** **Correction apportée par l'utilisateur** sur ma proposition initiale. Ma version laissait le contrôle de portée *disparaître* sur un état vide, faute de fiche d'où lire l'unité — un contrôle qui s'évapore selon l'état des données, donc sur lequel on ne peut pas raisonner. Le paramètre le rend applicable dans les deux cas. | `docs/decisions/…-endpoint-interne-de-consolidation.md` §3, `docs/appel-consolidation.md` §2.2 | Sprint 4 : Workflow **doit** transmettre `codeUnite` à chaque appel. Paramètre absent → `400`, aucun repli silencieux. |
| 3 | **Le paramètre est recoupé contre le `code_unite` figé sur les fiches, qui fait autorité.** Trou que j'ai identifié en appliquant la correction n°2 : sans recoupement, un agent habilité sur `00002` lirait les lignes de `00007` en déclarant `00002` sur un processus qui ne lui appartient pas. Désaccord → **`403 UNITE_NON_CONCORDANTE`**, tracé en audit. Choix du code soumis à l'utilisateur (403 tracé vs 422 vs ignorer) : **403 retenu**. | `UniteNonConcordanteException.java`, `GestionnaireErreursApi.java` | Un défaut de Workflow apparaîtra bruyamment au journal d'audit, avec un message nommant les deux unités. |
| 4 | **Processus sans aucune fiche : `200` avec zéro journée et total 0, jamais `404`.** Même parti qu'au Sprint 2.4 pour `GET /grilles/active`. Un `404` serait faux : Saisie ne sait pas si le processus existe, elle sait qu'elle ne détient rien pour lui. Tranché avec l'utilisateur à l'étape 1. | `docs/appel-consolidation.md` §4 | Sprint 4 : **c'est à Workflow de refuser la soumission d'un état vide**, sur `nombreLignes == 0`. Sa règle, pas celle de Saisie. |
| 5 | **Rôles du circuit (`AGENT_UNITE`, `CHEF_UNITE_DA`, `DIRECTEUR_RESEAU_DR`), dans un contrôleur séparé.** Le chef d'unité et le directeur réseau doivent lire l'état qu'ils valident. Les loger dans `SaisieController` aurait imposé d'assouplir le rôle des cinq endpoints d'écriture. Tranché avec l'utilisateur à l'étape 1. | `ConsolidationController.java` | Sprint 4 : le circuit de validation peut consulter l'état sans changement côté Saisie. |
| 6 | **`LigneResponse` réutilisé pour le détail des lignes**, plutôt qu'un troisième DTO. Il porte déjà tout ce qu'exigent US-06 et la charge Kafka §7.1. Tranché avec l'utilisateur à l'étape 1 : deux DTO de lecture pour la même donnée divergeraient d'un champ à la première évolution. | `JourneeConsolideeResponse.java` | Sprint 6 (Transmission) : la charge du topic se construit directement depuis cette réponse. |
| 7 | **La méthode de consolidation n'est pas transactionnelle.** Elle commence par un appel réseau à Identité ; l'englober immobiliserait une connexion pendant tout l'appel (doctrine 2.3). Conséquence assumée : fiches et lignes lues en deux temps. Une journée ouverte entre les deux serait absente — mais *cohéremment* (compte et total la reflètent tous deux), et jamais comptée deux fois. | `ConsolidationService.java`, javadoc de classe | Fenêtre inexistante après soumission : `EtatModifiableService` refuse alors toute écriture. Le montant qui décide de l'aiguillage porte sur un état qui ne peut plus changer. |
| 8 | **Le calcul du montant vit dans `ConsolidationService` et nulle part ailleurs.** Les DTO recopient sans recalculer — contrairement à `FicheResponse` (Sprint 3.3), qui somme lui-même sa fiche. | `EtatConsolideResponse.java`, `JourneeConsolideeResponse.java` | Un seul fichier à relire pour auditer le montant qui commande RG-08. |

---

## Ce qui a été fait — critères de validation du guide (§11)

| Critère | Statut | Détail |
|---|---|---|
| Forme de l'état consolidé validée avant codage | ✅ | Structure présentée à l'étape 1 (détaillé / agrégé / volontairement non agrégé), quatre questions posées avant toute ligne de code, une cinquième après la correction de l'utilisateur |
| Consolidation conforme à US-06 | ✅ | Jour, bénéficiaires développés (nom, prénom, **compte courant**, **code agence**), nature, session, montants, sous-totaux, total — tests 6, 7 et 11 |
| Total exact, prouvé par un test à valeur calculée | ✅ | Jeu de 4 journées / 3 bénéficiaires / 8 lignes, total **20 000 FCFA** additionné à la main dans le javadoc du test et réécrit en constante `TOTAL_ATTENDU` — tests 1, 2, 3 |
| Aucun flottant dans le calcul | ✅ | Grep `double\|float\|BigDecimal` sur tout le service → **aucun**. Tests 13 et 14 par réflexion sur les composants des records ; test 12 de `ConsolidationControllerIT` vérifie l'absence de décimale dans le JSON |
| Montants figés, jamais recalculés | ✅ | Agrégation sur `montant_applique` uniquement. Test 11 (montant de 7 777 FCFA, qu'aucune grille ne produirait, rendu tel quel) et test 12 (assertion structurelle : aucun `ResolutionMontantClient` dans les dépendances) |
| Portée d'accès appliquée | ✅ | `exigerHabilitationSurUnite` appelé **avant toute lecture**, y compris sur état vide (tests 16, 17) ; `403` au niveau HTTP (test 8 de l'IT) |
| Note de convention rédigée pour le service Workflow | ✅ | `docs/appel-consolidation.md` — 7 sections : partage de RG-06, appel, réponse, état vide, erreurs, panne de Saisie, quand appeler |
| Comportement sur processus vide tranché | ✅ | `200` / total 0 / `journees: []` — tests 15 (service) et 13 (HTTP) |
| CLAUDE.md complété des cinq décisions du Sprint 3 | ✅ | **8 lignes** ajoutées en §17 (les 5 demandées, plus 3 pour les décisions 3, 4 et 5 ci-dessus) + endpoint interne déclaré en §11 |
| Aucun endpoint créé par anticipation | ✅ | Grep sur les annotations de mapping : 5 publics dans `SaisieController` + 1 interne dans `ConsolidationController`, rien d'autre |
| `mvn -pl service-saisie test` | ✅ | **BUILD SUCCESS, 76 tests, 0 échec** |

---

## Les 31 nouveaux tests

**`ConsolidationServiceTest` — 18 tests, contre la vraie base `rations_saisie`**
(et non contre des mocks : ce qu'il faut prouver, c'est qu'aucune ligne n'est
oubliée ni comptée deux fois par la requête, ce qu'un mock ne peut pas montrer).

| # | Objet |
|---|---|
| 1 | Total du mois égal au total calculé à la main (20 000) |
| 2 | Total exactement égal à la somme des sous-totaux |
| 3 | Chaque sous-total journalier exact (6 500 / 11 000 / 2 500 / 0) |
| 4 | Aucune ligne d'un autre processus n'entre dans le total (voisin à 99 999) |
| 5 | 8 lignes saisies, 8 lignes rendues, aucun doublon d'identifiant |
| 6 | Journées triées par date, statut et compte de lignes renseignés |
| 7 | Chaque ligne porte bénéficiaire, nature, session, montant (CT-11) |
| 8 | Bénéficiaires distincts comptés une fois (3, pas 8) |
| 9 | Journée ouverte sans ligne : présente, sous-total 0 |
| 10 | Unité et période reprises des fiches |
| 11 | Montant figé rendu tel quel (7 777, qu'aucune grille ne produirait) |
| 12 | Aucune dépendance vers un client de résolution de montant |
| 13-14 | Aucun flottant : composants `fcfa` en entiers, aucun `double`/`BigDecimal` |
| 15 | Processus sans fiche : état vide, total 0 |
| 16 | Portée vérifiée même sur état vide |
| 17 | Utilisateur hors portée : refus |
| 18 | Unité déclarée non concordante : refus |

**`ConsolidationControllerIT` — 13 tests**, chaîne jeton → sécurité → contrôleur.

| # | Objet |
|---|---|
| 1 | Sans jeton : `401`, service jamais appelé |
| 2-4 | Les trois rôles du circuit : `200` |
| 5 | Rôle hors circuit (DRH) : `403 ACCES_REFUSE` |
| 6 | `codeUnite` absent : `400 REQUETE_INVALIDE` |
| 7 | `codeUnite` transmis tel quel au service |
| 8 | Hors portée : `403 UTILISATEUR_NON_HABILITE` |
| 9 | Unité non concordante : `403 UNITE_NON_CONCORDANTE` |
| 10-11 | JSON : totaux, sous-totaux, détail des lignes |
| 12 | Montants sérialisés en entiers, sans décimale |
| 13 | Processus vide : `200`, total 0 |

---

## Vérifications de l'étape 5 (cartographie) — écarts relevés, non corrigés

| # | Écart | Gravité | Suite |
|---|---|---|---|
| E-01 | **`FicheResponse.depuis` (Sprint 3.3) calcule un sous-total hors de `ConsolidationService`.** C'est un sous-total *journalier* pour l'écran de saisie, pas un montant mensuel, et il n'alimente pas RG-08 — mais c'est bien un montant sommé ailleurs. | Faible | À laisser ou à faire recopier depuis le service consolidé, au choix. Aucun impact sur l'aiguillage. |
| E-02 | **Le `curl` de vérification du guide §8 est incomplet** : `http://localhost:8082/saisie/processus/1/etat` sans `?codeUnite=` répondra `400`. Conséquence directe de la décision n°2. | Documentaire | **Confirmé à la vérification manuelle** : sans le paramètre, la réponse est bien `400 REQUETE_INVALIDE`. Commande corrigée dans `docs/appel-consolidation.md`. |
| E-03 | **`BouchonVerificationProcessus` toujours actif** (dispositif provisoire du Sprint 3.3, profil `bouchon-workflow`). | Connu, déjà tracé | Suppression obligatoire au Sprint 4 — `docs/dispositifs_provisoires.md`, actions B-01 à B-04. |
| E-04 | **Fiches antérieures à la migration V3 (`code_unite` nul) non recoupables.** Une valeur absente ne peut pas contredire la déclaration ; le fait est journalisé en `WARN` nommant le processus. | Transitoire | Ne concerne que les lignes créées lors des essais manuels des Sprints 3.1/3.2. Toute fiche ouverte depuis le 3.3 porte la valeur. |
| E-05 | `graphify` signale 2 fichiers sans nœud (`realm-afb-rations-dev.json`, `realm-export.json`). | Outillage | Préexistant, sans rapport avec ce sprint. |
| E-06 | **Message de `PublicateurAuditKafka` sur échec d'envoi** : se termine par « L'operation metier a abouti », inexact pour un événement de refus. N'apparaît que Kafka éteint. | Cosmétique | Dans `rations-audit-commun`, périmètre verrouillé. À traiter lors d'une évolution de ce module. Voir « Chaîne d'audit » ci-dessous. |

**Les trois vérifications demandées sont vertes :**

- *Aucune dépendance hors appel d'API* : seule dépendance interne
  `rations-audit-commun` (+ `lombok`) ; aucun import vers `identite`, `grilles`,
  `workflow`, `reporting`, `transmission` ou `audit`. Les échanges passent par
  trois clients HTTP (`infrastructure/{grilles,identite,workflow}`).
- *Cinq endpoints publics + un interne* : vérifié par grep sur les mappings.
- *Aucun montant calculé ailleurs* : une seule exception, E-01 ci-dessus.

---

## Fichiers

**Créés (9)**

| Chemin | Objet |
|---|---|
| `application/ConsolidationService.java` | RG-06 côté Saisie ; **seul lieu de calcul du montant mensuel** |
| `application/EtatConsolide.java` | Type applicatif de sortie, avec `JourneeConsolidee` imbriqué |
| `api/dto/EtatConsolideResponse.java` | DTO de sortie, recopie sans recalculer |
| `api/dto/JourneeConsolideeResponse.java` | Détail par journée |
| `api/ConsolidationController.java` | L'unique endpoint interne |
| `domaine/exception/UniteNonConcordanteException.java` | Refus de déclaration mensongère |
| `test/…/ConsolidationServiceTest.java` | 18 tests, vraie base |
| `test/…/api/ConsolidationControllerIT.java` | 13 tests, chaîne HTTP |
| `docs/appel-consolidation.md` | Convention d'appel pour Workflow |
| `docs/decisions/2026-08-31-endpoint-interne-de-consolidation.md` | Les six décisions du sous-sprint |

**Modifiés (4)**

| Chemin | Modification |
|---|---|
| `infrastructure/LignePrestationRepository.java` | `findByIdFicheJournaliereIn` — lot de fiches en une requête, sans jointure |
| `application/EtatModifiableService.java` | `exigerHabilitationSurUnite` — portée sur un code unité explicite |
| `api/GestionnaireErreursApi.java` | Traduction `403 UNITE_NON_CONCORDANTE`, publiée en audit |
| `CLAUDE.md` | §11 (endpoint interne) et §17 (8 lignes de décisions) |

---

## Points ouverts créés par ce sous-sprint

| # | Point | Pour qui |
|---|---|---|
| **C-02** | Workflow doit adopter `503 SERVICE_SAISIE_INDISPONIBLE` en refus conservateur si Saisie est muet, et **ne jamais** enregistrer un `montant_total` partiel, à zéro, ou repris d'une lecture antérieure. | Sprint 4 |
| **C-03** | Workflow doit refuser la soumission d'un état vide (`nombreLignes == 0`) : Saisie rend `200`, la règle appartient à Workflow. | Sprint 4 |
| **C-04** | `code_unite` nul sur les fiches pré-V3 : nettoyage ou renoncement explicite, selon ce que le métier veut faire des essais des Sprints 3.1/3.2. | Sprint 4 ou nettoyage de données |

---

## Vérification manuelle réelle — trois services démarrés, jeton Keycloak réel

Faite par l'assistant à la demande de l'utilisateur (« realise toi mm les étapes
de la vérification visuelle »). PostgreSQL et `dottel-keycloak` étaient déjà
actifs. Les trois services — Identité (8081), Grilles (8083), Saisie (8082,
profils `dev,bouchon-workflow`) — ont tourné réellement, avec des jetons obtenus
par grant `password` auprès du realm `afb-rations-dev`.

### Le jeu de données saisi, et son total calculé à la main

Sept lignes créées par l'API sur le processus 740, unité `00002`, avec les
**vraies grilles tarifaires** du service Grilles (RATION/JOUR 1 500, RATION/SOIR
2 000, TRANSPORT/JOUR 1 000, TRANSPORT/SOIR 1 500) :

```
20 août   MBALLA   RATION    JOUR   1 500     21 août   MBALLA    RATION    SOIR   2 000
          MBALLA   TRANSPORT JOUR   1 000               NKOULOU   RATION    SOIR   2 000
          NKOULOU  RATION    JOUR   1 500               ATANGANA  TRANSPORT SOIR   1 500
                          -------                                         -------
               sous-total    4 000                           sous-total    5 500

22 août   ATANGANA RATION    JOUR   1 500     18 août (fiche 111)  aucune ligne      0
                          -------             2020-01-01 (fiche 112) aucune ligne    0
               sous-total    1 500                                        =======
                                                             TOTAL MOIS   11 000
```

### Les 14 scénarios

| # | Scénario | Résultat obtenu | Conforme |
|---|---|---|---|
| 1 | `GET /saisie/processus/740/etat?codeUnite=00002` (agent) | `200`, **`montantTotalFcfa: 11000`** | ✅ égal au calcul à la main |
| 2 | Somme des sous-totaux vs total annoncé | 11 000 = 11 000 | ✅ |
| 3 | Recoupement SQL direct (`GROUP BY date_jour`) | 0 / 0 / 4 000 / 5 500 / 1 500, `SUM` global **11 000**, 7 lignes | ✅ identique à l'endpoint |
| 4 | Contrôle « aucune ligne d'un autre processus » (`GROUP BY id_processus`) | une seule ligne : processus 740, 7 lignes, 11 000 | ✅ |
| 5 | Journées triées par date croissante | `2020-01-01, 2026-08-18, 08-20, 08-21, 08-22` | ✅ |
| 6 | Journées ouvertes sans ligne | présentes, `sousTotalFcfa: 0` | ✅ |
| 7 | Détail des lignes | nom, prénom, **compte courant**, **code agence**, nature, session, montant, `idGrille` réel (1, 2, 3, 4) | ✅ US-06 / CT-11 |
| 8 | Décimales dans le JSON | **aucune** — `11000`, `4000`, `5500`, `1500`, `0` | ✅ aucun flottant |
| 9 | `CHEF_UNITE_DA` (paul_essama, 00002) | `200`, total 11 000 | ✅ il lit l'état qu'il validera |
| 10 | `DIRECTEUR_RESEAU_DR` (sylvie_atangana, portée nationale) | `200`, total 11 000 | ✅ |
| 11 | `DRH` (hors circuit) | `403 ACCES_REFUSE` | ✅ |
| 12 | Sans jeton | `401` | ✅ |
| 13 | Sans `codeUnite` | `400 REQUETE_INVALIDE` | ✅ aucun repli silencieux |
| 14 | Processus 99999 inexistant | `200`, `journees: []`, total `0` | ✅ jamais `404` |

### Les deux preuves qui comptent le plus

**Le recoupement du code unité fonctionne, et il fallait le bon rôle pour le
voir.** Avec le jeton de l'agent, `?codeUnite=00007` rend `403
UTILISATEUR_NON_HABILITE` : le **premier** verrou (portée d'accès) l'arrête avant
le recoupement. Il a fallu le Directeur Réseau, à portée nationale, pour franchir
ce premier verrou et atteindre le second :

```
403 UNITE_NON_CONCORDANTE
"Le processus 740 ne releve pas de l'unite 00007 declaree, mais de 00002.
 L'etat consolide ne peut pas etre rendu."
```

C'est exactement le trou décrit en décision n°3 — un utilisateur habilité sur
l'unité qu'il déclare, mais sur un processus qui n'en relève pas — refusé et
nommé.

**Les montants sont réellement figés : le service Grilles a été éteint.** Avec le
port 8083 fermé, la consolidation rend une réponse **strictement identique**
(total 11 000, comparaison JSON à JSON : `True`). Dans le même état, une
**écriture** est refusée en `503 SERVICE_GRILLES_INDISPONIBLE`. Lecture qui
fonctionne sans Grilles, écriture qui refuse : la démonstration que la
consolidation ne rerésout jamais de grille ne dépend d'aucune lecture de code.

### Swagger — six opérations, ni plus ni moins

```
POST    /saisie/fiches                 ┐
GET     /saisie/fiches/{id}/lignes     │ 5 endpoints publics du contrat
POST    /saisie/lignes                 │
PUT     /saisie/lignes/{id}            │
DELETE  /saisie/lignes/{id}            ┘
GET     /saisie/processus/{id}/etat    ← 1 endpoint interne
```

Aucun endpoint créé par anticipation pour le Sprint 4.

### Chaîne d'audit (producteur) — vérifiée avec Kafka démarré

Premier passage sans Kafka : les événements `ACCES_REFUSE` tombaient en
`AUDIT PERDU` (dégradation voulue, doctrine 1.3). À la demande de l'utilisateur,
la vérification a été reprise avec l'infrastructure complète :
`docker compose up -d kafka`, topics créés par `infra/docker/kafka-topics.sh`,
un `kafka-console-consumer` branché sur `rations.audit.evenement`, puis les trois
scénarios de refus rejoués.

| Contrôle | Résultat |
|---|---|
| `AUDIT PERDU` sur identite / grilles / saisie | **0 / 0 / 0** |
| Événements `service-saisie / ACCES_REFUSE` sur le topic | **3**, un par scénario |
| Consultation autorisée (`200`) | **n'émet aucun événement** — voulu : une lecture autorisée n'est pas une action sensible |

Les trois `detailJson` reçus sur le topic, complets et conformes à CT-04 :

| Scénario | `motif` | `detail` |
|---|---|---|
| DRH hors circuit | `ROLE_INSUFFISANT` | « Le role de l'utilisateur ne permet pas cette action. » |
| Agent, unité déclarée hors portée | `HABILITATION_ABSENTE` | « Vous n'avez pas de droit sur l'unite 00007… » |
| DR, unité déclarée ≠ unité des fiches | `UNITE_NON_CONCORDANTE` | « Le processus 740 ne releve pas de l'unite 00007 declaree, mais de 00002. » |

Chaque événement porte `serviceEmetteur`, `action`, `entiteCible: acces`,
`dateAction`, `adresseIp`, et `login` dans le `detailJson`. `idUtilisateur` est
`null` — décision Sprint 3.3 (`docs/decisions/2026-08-31-idutilisateur-non-renseigne-en-saisie.md`),
`GET /identite/habilitation` ne rend qu'un `login`.

**Ce que cette vérification ne couvre pas.** La chaîne s'arrête au topic : le
consommateur qui écrit dans `audit_log` (`service-audit`) n'est encore qu'un
squelette (`ServiceAuditApplication`, `SecurityConfig`, `RoleJwtConverter`,
`RoleEnum` — dernier commit `sprint-0.6`). La trace de bout en bout
`événement → audit_log` sera vérifiable au sprint qui construit ce consommateur.

**E-06 (cosmétique, non corrigé).** Le message de `PublicateurAuditKafka` sur le
chemin d'échec se termine par « L'operation metier a abouti », inexact pour un
événement de refus. Il n'apparaît que Kafka éteint ; avec Kafka démarré, il ne
sort pas. Dans `rations-audit-commun`, au périmètre verrouillé — à traiter lors
d'une évolution de ce module.

### Nettoyage

Les trois services applicatifs ont été arrêtés après vérification. `rations-kafka`
a été **ré-arrêté** pour restaurer l'état antérieur à la session (il n'était pas
lancé) — le relancer :
`docker compose -f infra/docker/docker-compose.yml up -d kafka`. `rations-postgres`
et `dottel-keycloak` laissés actifs.

---

## Suite

**Sprint 4.1 — domaine du workflow.** Le service Saisie est complet et clos ; il
n'attend plus rien de lui-même. Les trois obligations qui pèsent sur le Sprint 4
sont C-02, C-03 et la suppression du bouchon (E-03 / B-01 à B-04).
