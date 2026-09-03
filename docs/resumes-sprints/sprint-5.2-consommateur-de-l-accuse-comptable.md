# Résumé Sprint 5.2 — Consommateur de l'accusé comptable

**Services :** service-transmission (consommateur Kafka), service-workflow (écriture du statut)
**Date :** 2–3 septembre 2026
**Config :** **Opus / effort élevé sur l'intégralité du sous-sprint**, conformément au
guide §1 — « un consommateur mal conçu traite deux fois le même message, ou perd un accusé
sans que rien ne le signale ». Aucun changement de modèle en cours de route.

**Statut :** livré, **tests au vert**, **vérification manuelle réelle faite et conforme**
(deux services démarrés, broker réel, 16 messages publiés sur le topic — voir la section
dédiée en fin de document). **Un défaut trouvé par cette vérification et corrigé**, avec son
test de non-régression.

`mvn -pl service-transmission test` → **BUILD SUCCESS, 79 tests, 0 échec** (33 au Sprint 5.1,
**+46**).
`mvn -pl service-workflow test` → **BUILD SUCCESS, 282 tests, 0 échec** (244 au Sprint 5.1,
**+38**).
Backend complet `mvn test` → **BUILD SUCCESS, 604 tests, 0 échec** (520 au Sprint 5.1,
**+84**), aucune régression.

**Aucune migration.** Les quatre colonnes utilisées (`statut_integration`,
`reference_comptable`, `date_traitement`, `motif_integration`) existent depuis la migration
V4 du Sprint 5.1.

---

## Le cœur du sous-sprint

Le module publiait l'état validé. **Il est désormais producteur ET consommateur.**

Le module de comptabilisation renvoie un accusé sur `rations.etat.accuse` ; le service
Transmission l'écoute, le contrôle, et fait écrire le statut d'intégration sur
`processus_mensuel` **par l'API du service Workflow, jamais par sa base** (AR04). Sans ce
consommateur, un état apparaissait indéfiniment comme transmis sans qu'on sache s'il avait
été traité, rejeté, ou perdu : le suivi (US-15) restait aveugle sur le sort de ce qui part
en paiement.

**Aucune écriture comptable n'est produite**, et **aucune référence n'est fabriquée** : elle
est recopiée de l'accusé (CLAUDE.md §8).

---

## Les arbitrages du sous-sprint

### 1. Les cinq situations anormales, tranchées avant tout code

| Situation | Traitement | Motif |
| --- | --- | --- |
| **Message illisible** | `ACCUSE ILLISIBLE`, audit, **message avancé** | Aucun rejeu ne le rendra lisible, et s'obstiner bloquerait tous les accusés suivants. |
| **Processus inconnu** | `ACCUSE ORPHELIN`, audit, message avancé | Rejouer ne fera pas naître le processus. |
| **Processus jamais transmis** | `ACCUSE INCOHERENT`, audit, **drapeau jamais posé** | Le poser ferait croire à un envoi qui n'a pas eu lieu ; RG-13 refuserait ensuite le vrai comme un doublon, et l'état resterait impayé à jamais. |
| **Même accusé deux fois** | *No-op* : aucune écriture, **aucune trace d'audit** | Une seconde ligne ferait croire à un second traitement comptable. |
| **Accusé contradictoire** | `ACCUSE CONTRADICTOIRE`, base inchangée | Le module **refuse et signale, il n'arbitre jamais** — doctrine `INCOHERENCE_GRILLE` (2.4). |

Les cinq sont **définitives** : aucune ne s'arrange en réessayant. Le seul échec
**temporaire** est un service Workflow injoignable, et c'est le seul cas qui fait rejouer.

### 2. La table des transitions du statut d'intégration

Portée par `TransitionIntegration`, et **par elle seule** : le mutateur de
`ProcessusMensuel` est en visibilité paquet, comme `appliquerStatut` — le compilateur
garantit qu'aucun service applicatif ne la contourne.

| Statut courant | Accusé identique en tout point | Accusé différent |
| --- | --- | --- |
| `NULL`, état **non transmis** | ⛔ `NonTransmis` | ⛔ `NonTransmis` |
| `NULL`, état transmis (défensif) | ↩︎ `DejaApplique` | ✅ `Appliquer` |
| `EN_ATTENTE` | ↩︎ `DejaApplique` | ✅ `Appliquer` |
| `INTEGRE` | ↩︎ `DejaApplique` | ⛔ `Contradiction` |
| `REJETE` | ↩︎ `DejaApplique` | ⛔ `Contradiction` |

**Quatre principes.** (1) `INTEGRE` et `REJETE` sont **définitifs** ; `NULL` et `EN_ATTENTE`
sont ouverts — `EN_ATTENTE` parce que c'est le module lui-même qui l'a posé à la
publication, sans référence ni date, et qu'un premier accusé l'enrichit sans le contredire.
(2) **Aucune régression.** (3) Le rejeu à l'identique n'est pas une contradiction. (4) Même
statut, contenu différent, sur un état définitif = contradiction — deux `INTEGRE` de
références différentes ne sont pas le même accusé.

> **Question posée à l'arbitrage, et sa réponse.** *« Que se passe-t-il si un `REJETE`
> arrive après un `INTEGRE` déjà traité ? Un accusé peut-il jamais faire régresser un
> statut ? »* → Le `REJETE` est **refusé comme contradictoire**, rien n'est écrit, et le
> refus nomme les **deux** statuts. Et **non : jamais.**

> **Affinement relevé pendant l'implémentation, signalé avant le code.** Une première
> formulation faisait de « même statut, contenu différent » une contradiction *en toutes
> circonstances*. Elle aurait refusé le cas le plus normal : un premier accusé `EN_ATTENTE`
> portant une référence, alors que le module avait posé `EN_ATTENTE` sans référence. La
> règle « ouvert / définitif » corrige cela sans toucher aux quatre principes.

### 3. Comment l'idempotence est garantie

Elle ne repose **pas** sur une mémoire du service Transmission : il n'a pas de base, et un
cache disparaîtrait au premier redémarrage — au moment précis où un rejeu est le plus
probable.

Elle repose sur la **seule source de vérité**, `processus_mensuel`. Le service Workflow
compare et décide **dans la même transaction que l'écriture**, et `StatutIntegrationClient`
n'offre **aucune méthode de lecture**, délibérément : lire puis décider puis écrire en deux
appels laisserait entre les deux une fenêtre où un second accusé pourrait s'intercaler.

Et parce qu'aucun accusé ne fait régresser un statut, la garantie tient **même si les
messages arrivent dans le désordre** — ce qui compte, la clé de partition des accusés étant
posée par un producteur que l'équipe ne contrôle pas.

### 4. Le statut et la date voyagent en chaîne

`statutIntegration` est un `String` dans `AccuseComptableEvent`, pas l'énumération ;
`dateTraitement` aussi. Même raison que `nature` et `session` au 5.1 : une valeur inconnue
ferait **échouer la désérialisation** au lieu d'être vue.

La conséquence est concrète. Un statut `"BIDON"` tomberait dans « message illisible » —
diagnostic pauvre, indiscernable d'un JSON tronqué — alors qu'il mérite son refus nommé
`STATUT_INCONNU`. **C'est la leçon du défaut 2 du Sprint 5.1**, où le comportement était
juste mais le diagnostic perdu.

**Le motif accepte trois noms** (`motif`, `motifRejet`, `motifIntegration`, via
`@JsonAlias`) : le contrat §7.2 décrit le champ en toutes lettres mais ne le nomme pas dans
son exemple JSON. Un désaccord de nom ferait refuser **tous** les accusés de rejet pour
`MOTIF_REJET_ABSENT`, et le motif du refus comptable serait perdu.

**Une date sans décalage horaire est refusée** : `"2026-08-18T02:15:00"` est ambigu, et lui
supposer un fuseau reviendrait à inventer une information sur un traitement de paiement.

### 5. Acquittement, réessai, parallélisme

**Acquittement après traitement** (`AckMode.RECORD`, `enable.auto.commit=false`). C'est un
fonctionnement « au moins une fois », donc un doublon est possible — **acceptable
uniquement parce que le traitement est idempotent**.

**Réessai borné** : deux tentatives, deux secondes, puis abandon tracé `ACCUSE ABANDONNE`.
Un rejeu illimité tiendrait la partition bloquée tant que le Workflow ne répond pas. Ce qui
est perdu est une mise à jour de **suivi**, pas un paiement.

> Ce réessai est **l'inverse** de celui du 5.1. Là, rejouer risquait un **double paiement**,
> donc c'était réservé aux échecs antérieurs à tout envoi. Ici, on rejoue une **écriture
> idempotente** : la répéter ne produit aucune écriture comptable. Même principe de
> prudence, deux opérations de natures opposées, deux règles opposées.

**Un seul fil**, un seul groupe. **Mais la garantie d'ordre est une supposition** : elle
suppose que le module de comptabilisation partitionne par `idProcessus`, ce que l'équipe ne
contrôle pas. **Le vrai filet est le refus strict de contradiction**, pas l'ordre Kafka.
Point consigné (D-11), à confirmer avec la DFT.

---

## ⚠️ L'arbitrage décisif de l'utilisateur — l'authentification du consommateur

**Le problème, qui n'existait pas jusqu'ici.** Toute la chaîne du 5.1 relaie le jeton de
l'utilisateur final (doctrine 1.3). **Un message Kafka n'a pas d'utilisateur derrière lui.**
L'accusé arrive des minutes ou des heures après la clôture ; aucun jeton n'existe, et le
realm ne porte **aucun compte de service**. La doctrine du relais ne s'applique donc pas —
non par exception, mais **faute d'utilisateur final à relayer**.

**Trois options présentées, l'utilisateur a tranché : secret partagé.** `permitAll` a été
écarté — c'est une **écriture sur le statut de paiement d'un état**, pas une lecture de
documentation ; quiconque atteindrait le port 8084 pourrait déclarer n'importe quel état
intégré ou rejeté. Le compte de service Keycloak est la voie propre à terme, mais impossible
aujourd'hui.

### L'exigence ajoutée par l'utilisateur, et ce qu'elle a changé

> *« Ce secret ne doit jamais apparaître dans un journal, même par accident. Le module a
> déjà pris l'habitude de logger des motifs détaillés (`AUDIT PERDU`,
> `TRANSMISSION MANQUEE`, `SEUIL INDISPONIBLE`) — exige que Claude confirme explicitement
> que la valeur est exclue de tout message d'erreur ou de trace, sinon ce garde-fou
> fuiterait par le canal même censé le surveiller. »*

L'exigence a été **rendue vérifiable plutôt que promise** :

| Garantie | Où |
| --- | --- |
| Jamais dans un journal, une exception, un événement d'audit, un corps de réponse | `FiltreCleInterne`, `StatutIntegrationHttpClient` |
| **Ni longueur, ni préfixe** — un préfixe est déjà une fuite | idem |
| Le refus **ne distingue pas** « absent » de « invalide » | `FiltreCleInterne` |
| Comparaison à **temps constant** (`MessageDigest.isEqual`) | `FiltreCleInterne` |
| **Deux tests de garde relisent les sources**, un par service | `CleInterneJamaisJournaliseeTest` × 2 |
| Un test vérifie que le corps du refus ne contient ni la clé, ni sa longueur | `IntegrationComptableIT` |

**Vérifié en réel** : zéro occurrence de la clé dans les deux journaux, après trois appels
refusés.

### Les deux autres exigences de l'utilisateur

> *« Exige que Claude énumère explicitement, avant d'écrire le code, quelles transitions
> sont autorisées et lesquelles sont refusées — sinon cette règle sera improvisée au fil du
> code plutôt que décidée une fois pour toutes. »*

→ Table arrêtée et présentée **avant tout code**, puis portée par
`TransitionIntegration` et éprouvée exhaustivement par `TransitionIntegrationTest`.

> *« Le vrai filet n'est pas l'ordre Kafka, c'est le refus strict de contradiction. Exige
> que Claude consigne ce point dans `docs/points-en-attente.md`, à côté de M-03 : il faut
> confirmer avec la DFT que leur système partitionne bien par `idProcessus`, sinon la
> garantie d'ordre n'est qu'une supposition. »*

→ Consigné (point **D-11**), avec l'explication de ce que la réponse change en exploitation :
si la clé vaut `idProcessus`, une trace `ACCUSE CONTRADICTOIRE` signale une vraie anomalie
comptable ; sinon, elle peut n'être qu'un désordre de transport.

---

## ⚠️ La dette technique datée — `spring-kafka`

**Le test bout en bout du guide (test 10) n'est pas automatisable en l'état.**
`@EmbeddedKafka` échoue sur `NoClassDefFoundError: kafka/testkit/KafkaClusterTestKit` : le
pom parent épingle `spring-kafka` en **3.3.0** (Sprint 0.2) alors que les `kafka-clients`
sont en **4.2.1**, et Kafka 4 a déplacé cette classe.

**Le point qui a changé la question :** Spring Boot 4.1.0, parent du projet, **gère déjà
`spring-kafka` en 4.1.0**. L'épinglage le rétrograde de deux versions majeures.

**L'utilisateur a tranché : ne pas y toucher maintenant**, avec une exigence explicite —

> *« Ajoute une ligne dans `docs/points-en-attente.md` : montée de spring-kafka vers 4.1.0
> à traiter dans un sprint technique dédié, touchant les six producteurs, testée broker
> éteint pour chacun — pas juste "un jour", sinon cette dette reste invisible jusqu'à ce
> qu'un incident la révèle. »*

→ Consigné (point **T-01**) avec les cinq étapes du sprint technique, dont la **vérification
broker éteint pour chacun des six producteurs**. Motif inscrit : c'est elle, et elle seule,
qui a révélé les deux défauts du 5.1 ; une montée qui passerait les 604 tests sans ce
contrôle laisserait le même angle mort.

**Ce qui a été livré à la place** : un **test de câblage** (`CablageConsommateurAccuseTest`,
`ApplicationContextRunner`) — c'est ce type de test qui avait attrapé le défaut de démarrage
du 5.1 — plus le bout en bout **réel** à la vérification manuelle, sur le conteneur
`rations-kafka`, comme le guide §8 le prescrit déjà.

---

## Ce qui a été livré

### service-transmission

| Fichier | Rôle |
| --- | --- |
| `domaine/AccuseComptableEvent.java` | Charge reçue (contrat §7.2), tolerant reader, statut et date en chaîne, motif à trois alias |
| `domaine/CodeAnomalieAccuseEnum.java` | Les huit refus nommés, distincts de ceux de la charge sortante |
| `application/ValidationAccuseService.java` | Contrôle de la charge, **aucune exception n'en sort** |
| `application/ResultatValidationAccuse.java` | Type scellé — la conversion vers l'énumération n'a lieu qu'ici |
| `application/AnomalieAccuse.java` | Couple `(code, message)`, forme d'`AnomalieCharge` |
| `application/TraitementAccuseService.java` | Orchestration, traces, distinction définitif / temporaire |
| `application/ResultatTraitementAccuse.java` | Quatre issues — le consommateur ne lit qu'une chose : faut-il rejouer ? |
| `application/StatutIntegrationClient.java` | Port de remontée. **Aucune méthode de lecture**, délibérément |
| `application/ResultatMiseAJourIntegration.java` | Six issues scellées, une seule temporaire |
| `infrastructure/AccuseComptableConsumer.java` | Écoute, conversion sous capture, **ne lève que sur échec temporaire** |
| `infrastructure/config/ConfigurationConsommateurAccuse.java` | `@EnableKafka`, chaîne, `AckMode.RECORD`, 1 fil, réessai borné |
| `infrastructure/workflow/StatutIntegrationHttpClient.java` | Appel `PUT`, secret en en-tête, traduction des refus |

### service-workflow

| Fichier | Rôle |
| --- | --- |
| `domaine/TransitionIntegration.java` | **La table des transitions**, décide et écrit en un seul geste |
| `domaine/ProcessusMensuel.java` | `appliquerAccuseComptable()`, **visibilité paquet** |
| `domaine/exception/` (3) | `ProcessusNonTransmis`, `AccuseContradictoire`, `CleInterneInvalide` |
| `application/IntegrationComptableService.java` | Transaction unique, conversion de date, trace conditionnelle |
| `application/ResultatIntegrationComptable.java` | Photographie du moment de la décision |
| `api/dto/IntegrationComptableRequest/Response.java` | Corps et réponse, champ `resultat` |
| `api/ProcessusController.java` | `PUT /processus/{id}/integration`, endpoint interne |
| `api/GestionnaireErreursApi.java` | `422 PROCESSUS_NON_TRANSMIS`, `409 ACCUSE_CONTRADICTOIRE` |
| `infrastructure/config/FiltreCleInterne.java` | Secret partagé, temps constant, **ni `@Component` ni `@Bean`** |
| `infrastructure/config/SecurityConfig.java` | **Chaîne dédiée** `@Order(HIGHEST_PRECEDENCE)` |

### Tests

| Fichier | Ce qu'il prouve |
| --- | --- |
| `ValidationAccuseServiceTest` (14) | Refus nommés, les trois alias du motif **par Jackson**, date ambiguë refusée, toutes les anomalies rapportées |
| `TraitementAccuseServiceTest` (11) | Les six issues, **une seule trace pour deux réceptions**, l'échec temporaire n'est pas un refus |
| `AccuseComptableConsumerTest` (9) | **Sept formes d'illisibilité absorbées**, ne lève que sur échec temporaire, message énorme tronqué |
| `CablageConsommateurAccuseTest` (8) | Assemblage, désérialisation en chaîne, `AckMode`, 1 fil, réessai borné, **écoute enregistrée** |
| `CleInterneJamaisJournaliseeTest` × 2 (8) | **Le secret ne fuit par aucun canal** — gardes qui relisent les sources |
| `TransitionIntegrationTest` (12) | **La table, case par case** : aucune régression, rejeu inoffensif, contradictions refusées |
| `IntegrationComptableServiceTest` (11) | Dix rejeux → une écriture, une trace ; conversion déterministe ; même instant en deux fuseaux reconnu |
| `IntegrationComptableIT` (10) | Secret, **la chaîne dédiée ne déborde pas**, les trois codes de refus |

---

## Documentation mise à jour

- `CLAUDE.md` §9.1 (le consommateur, la table, les cinq anomalies), §11 (endpoint interne,
  trois codes), §15 (**sept erreurs interdites ajoutées**), §17 (**dix décisions**).
- `docs/decisions/2026-09-02-consommation-accuse-comptable.md` — les arbitrages, les
  exigences de l'utilisateur, le défaut trouvé.
- `docs/dispositifs_provisoires.md` §3bis — le secret partagé, et **le remplacement en trois
  fichiers** le jour où la DSI ouvre un compte de service.
- `docs/points-en-attente.md` — **trois points ajoutés** : D-10 (compte de service), D-11
  (clé de partition, DFT), T-01 (montée `spring-kafka`, sprint technique daté).

---

## Ce que ce sous-sprint ne fait pas

- **Le contrôle d'unicité de RG-13** — Sprint 5.3. Son point d'accroche reste marqué dans
  `TransmissionService`.
- **`GET /transmission/processus/{id}`** — Sprint 5.3.
- **La reprise d'une transmission manquée** — toujours impossible sans compte de service.
  Les états 1318, 1319 et 1320 restent en base pour le 5.3.

---

## Critères de validation du guide (§11)

| Critère | Statut | Preuve |
| --- | --- | --- |
| Cinq situations anormales énumérées et arbitrées | **Fait** | Arbitrage avant tout code, décision §1 |
| Charge de l'accusé conforme au contrat d'API | **Vérifié** | `ValidationAccuseServiceTest`, quatre champs + motif |
| Consommateur résistant aux messages illisibles | **Vérifié** | 7 formes absorbées en test, **1 en réel sans blocage du suivant** |
| Décisions sur l'acquittement et le parallélisme tranchées | **Fait** | Arbitrage utilisateur, `CablageConsommateurAccuseTest` |
| Statut d'intégration remonté au processus | **Vérifié en réel** | 1316 `INTEGRE`, 1317 `REJETE`, 1321 `INTEGRE` en base |
| Motif de rejet conservé et visible | **Vérifié en réel** | 1317 porte son motif en base |
| Traitement idempotent, prouvé par test | **Vérifié** | 10 rejeux → 1 écriture (test) ; **3 rejeux → audit inchangé (réel)** |
| Remontée par appel d'API uniquement | **Vérifié** | Aucune datasource vers `rations_workflow` dans service-transmission |
| Dix tests passants | **Vérifié** | Les 10 cas du guide couverts ; **604 tests au total, 0 échec** |

**Écart assumé et arbitré :** le test 10 (bout en bout `@EmbeddedKafka`) est remplacé par un
test de câblage plus la vérification manuelle sur broker réel. Voir la dette T-01.

---

## Vérification manuelle — faite le 3 septembre 2026

PostgreSQL, `rations-kafka` et `dottel-keycloak` démarrés. **Deux services réellement
lancés** : Workflow (8084) et Transmission (8086). Flyway : *Successfully validated 4
migrations, current version 4*. Consommateur : *Subscribed to topic(s):
rations.etat.accuse*, puis *partitions assigned: [rations.etat.accuse-0]*.

Jeu d'essai laissé par le Sprint 5.1 : **1316, 1317, 1321** transmis (`EN_ATTENTE`) ;
**1318, 1319, 1320** clôturés mais **non transmis**.

### ⚠️ Le défaut trouvé — et pourquoi aucun test ne pouvait le voir

#### `@EnableKafka` manquait : le service démarrait et n'écoutait rien

À la première tentative, le service Transmission démarrait **normalement** — tous les beans
présents, sonde de santé à `200`, aucune erreur. Et **pas une seule ligne de souscription
dans le journal.**

**Cause.** Sans `@EnableKafka`, l'annotation `@KafkaListener` n'est jamais traitée : la
méthode d'écoute reste une méthode ordinaire que personne n'appelle. Le projet déclare
`spring-kafka` directement, sans `spring-boot-starter-kafka`, donc l'auto-configuration qui
poserait cette annotation n'est pas entraînée.

**Pourquoi personne ne l'avait rencontrée.** C'est **le premier `@KafkaListener` du
module**. Les six services n'ont eu jusqu'ici que des *producteurs*, qui n'en ont pas besoin.

**Pourquoi les 78 tests ne pouvaient rien voir**, y compris `CablageConsommateurAccuseTest`
écrit précisément pour attraper les défauts de câblage : il vérifiait que les **beans
existent**, pas que l'**écoute est enregistrée**. Même famille que le bean `ObjectMapper`
absent au 5.1 — un câblage qui ne se voit qu'en assemblant, et ici même qu'en **démarrant**.

**Correction.** `@EnableKafka` sur `ConfigurationConsommateurAccuse`. **Test de
non-régression** : `ecouteEffectivementEnregistree`, qui interroge le
`KafkaListenerEndpointRegistry` — bean qui n'existe que par cette annotation — et exige que
la liste des conteneurs d'écoute ne soit pas vide.

**La leçon**, à ranger à côté de celles des Sprints 2.3, 4.2 et 5.1 : un test de câblage qui
compte les beans ne prouve pas que le service *fonctionne*. Pour un consommateur, la
question est « l'écoute est-elle enregistrée ? ».

### Traitement nominal

| # | Scénario | Résultat obtenu | Conforme |
|---|---|---|---|
| 1 | Accusé `INTEGRE` pour 1316 | `statut_integration = INTEGRE`, `reference_comptable = CPT-2027-03-000512` | ✅ |
| 2 | Date `2027-04-05T02:15:00Z` | Stockée `2027-04-05 03:15:00` — conversion au fuseau système | ✅ **déterministe** |
| 3 | Accusé `REJETE` motivé pour 1317 | `REJETE` + *« Compte 00002000123456 clos depuis le 12/04. »* | ✅ **motif conservé (US-15)** |
| 4 | Journal du service Transmission | *« Accuse comptable applique a l'etat 1316 : statut d'integration INTEGRE, reference CPT-2027-03-000512 »* | ✅ |

### ⚠️ Le scénario redouté — un message illisible ne doit pas bloquer les suivants

| # | Scénario | Résultat obtenu | Conforme |
|---|---|---|---|
| 5 | Publication de `{ceci nest pas du JSON` (offset 2) | `ACCUSE ILLISIBLE : … Unexpected character ('c' …)`, **le service reste debout** | ✅ |
| 6 | Accusé **valide** publié juste après (offset 3) | *« Accuse comptable applique a l'etat 1321 »* | ✅ **la partition n'est pas bloquée** |

### Les quatre autres anomalies

| # | Scénario | Résultat obtenu | Conforme |
|---|---|---|---|
| 7 | Accusé pour l'état inexistant `999999` | `ACCUSE ORPHELIN : … Le service Workflow ne connait pas l'etat 999999` | ✅ |
| 8 | Accusé pour **1318, jamais transmis** | `ACCUSE INCOHERENT`, réponse `422 PROCESSUS_NON_TRANSMIS` | ✅ |
| 9 | **1318 après ce refus** | `transmis_comptabilite = f`, `statut_integration` **NULL** | ✅ **le drapeau n'est jamais posé** |
| 10 | Rejet **sans motif** | `ACCUSE INVALIDE : MOTIF_REJET_ABSENT`, **le Workflow n'est jamais appelé** | ✅ |
| 11 | Statut `"BIDON"` | `ACCUSE INVALIDE : STATUT_INCONNU : statutIntegration vaut "BIDON", hors des valeurs du contrat` | ✅ **refus nommé, pas « illisible »** |

### ⚠️ L'idempotence, éprouvée en réel

| # | Scénario | Résultat obtenu | Conforme |
|---|---|---|---|
| 12 | **Trois rejeux** de l'accusé identique de 1316 | 3 × *« rejeu sans effet, aucune ecriture ni trace produite »* | ✅ |
| 13 | Base après les trois rejeux | **strictement inchangée** | ✅ |
| 14 | **Événements d'audit pour 1316** | **2 avant, 2 après** — aucun ajouté | ✅ **la preuve visible de l'idempotence** |

### Les contradictions

| # | Scénario | Résultat obtenu | Conforme |
|---|---|---|---|
| 15 | `REJETE` après `INTEGRE` (1316) | `409 ACCUSE_CONTRADICTOIRE`, message nommant **les deux** statuts | ✅ *la question posée à l'arbitrage* |
| 16 | Régression vers `EN_ATTENTE` (1317) | `409`, message : *« L'etat 1317 porte deja REJETE … et l'accuse recu porte EN_ATTENTE »* | ✅ **aucune régression** |
| 17 | Même statut, **référence différente** (1316) | `409` : *« reference CPT-2027-03-000512 … l'accuse recu porte INTEGRE (reference CPT-AUTRE-999) »* | ✅ |
| 18 | Base après les trois contradictions | **strictement inchangée** | ✅ **refuse et signale, n'arbitre jamais** |

### Le secret partagé

| # | Scénario | Résultat obtenu | Conforme |
|---|---|---|---|
| 19 | `PUT` **sans** en-tête | `401 CLE_INTERNE_INVALIDE` | ✅ |
| 20 | `PUT` avec **mauvaise clé** | `401`, **message identique** au précédent | ✅ *« absent » et « invalide » indistinguables* |
| 21 | `PUT` avec un jeton `Bearer` mais sans la clé | `401` | ✅ la route ne passe pas par OAuth2 |
| 22 | `GET /processus/1321` sans jeton | `401` **au corps vide**, sans code métier | ✅ **la chaîne dédiée ne déborde pas** |
| 23 | Base après ces quatre appels | inchangée | ✅ |
| 24 | **Fuite du secret dans les journaux** | `changeme-in-development` : **0**, `changeme` : **0**, valeur présentée : **0** | ✅ **exigence de l'utilisateur tenue** |
| 25 | Ce que le Workflow journalise | *« Appel refuse sur /processus/1321/integration : en-tete X-Cle-Interne absent ou invalide. »* | ✅ le nom, jamais la valeur |

### Vivacité et journal d'audit

| # | Scénario | Résultat obtenu | Conforme |
|---|---|---|---|
| 26 | Consommateur après **16 messages dont 8 anomalies** | toujours actif, traite le 16ᵉ | ✅ |
| 27 | **Décalage de consommation** | `CURRENT-OFFSET 15 / LOG-END-OFFSET 15`, **LAG = 0** | ✅ **aucun message bloqué** |
| 28 | Répartition des événements d'audit | **3 `ACCUSE_COMPTABLE_APPLIQUE`**, **3 `INTEGRATION_COMPTABLE`**, **8 `ACCUSE_COMPTABLE_REFUSE`** | ✅ 3 états mis à jour, 8 anomalies |
| 29 | Delta d'audit du Workflow | `statutIntegration: {avant: "EN_ATTENTE", apres: "INTEGRE"}`, `referenceComptable: {avant: null, apres: "CPT-…"}` | ✅ |
| 30 | Format des dates en audit | `"dateTraitement":"2027-04-05T02:15Z"` — ISO 8601 | ✅ le correctif du 2.2 tient |
| 31 | Contrainte `ck_processus_integration_apres_transmission` | `0` ligne avec statut d'intégration sans transmission | ✅ |

### États laissés en base

| Processus | Période | Montant | Transmis | Statut d'intégration | Rôle |
|---|---|---|---|---|---|
| 1316 | 03/2027 | 8 000 | ✅ | `INTEGRE` + référence | Accusé nominal |
| 1317 | 04/2027 | 105 000 | ✅ | `REJETE` + motif | Rejet motivé |
| 1318, 1319, 1320 | 05, 06, 07/2027 | 1 500 / 2 000 / 2 000 | ❌ | NULL | **Jeu d'essai du Sprint 5.3** |
| 1321 | 08/2027 | 1 000 | ✅ | `INTEGRE` + référence | Accusé après message illisible |

Les trois états non transmis sont **conservés volontairement** : ils serviront à éprouver le
verrou d'unicité et la reprise du sous-sprint 5.3.

---

## Seconde passe de vérification — les six étapes rejouées avant le commit

Conduite à la demande de l'utilisateur, sur services redémarrés et broker réel. **Les six
étapes passent.**

| Étape | Contrôle | Résultat |
|---|---|---|
| 1 | Souscription du consommateur | *Subscribed to topic(s): rations.etat.accuse* puis *partitions assigned: [rations.etat.accuse-0]* |
| 2 | Accusé sur **1319, jamais transmis** | `ACCUSE INCOHERENT`, refus `422 PROCESSUS_NON_TRANSMIS`, **drapeau toujours à `f`, statut `NULL`** |
| 3 | Message empoisonné (offset 16) puis accusé valide | `ACCUSE ILLISIBLE`, **sonde à `200`**, l'accusé suivant traité |
| 4 | `REJETE` sur 1316 déjà `INTEGRE` | `409`, message nommant **les deux** statuts et **les deux** références, base strictement identique |
| 5a | `PUT` sans en-tête | `401 CLE_INTERNE_INVALIDE` |
| 5b | `PUT` avec la **bonne** clé | `200` + `"resultat":"DEJA_APPLIQUE"` — **l'idempotence se lit dans la réponse HTTP** |
| 5c | Fuite du secret dans les deux journaux | `changeme-in-development` : **0**, `changeme` : **0** |
| 6 | Décalage de consommation | `19 / 19`, **LAG = 0** |
| + | `GET /processus/1316` et `POST /…/validation` sans jeton | `401` **au corps vide** — la chaîne dédiée ne déborde pas |
| + | Contrainte `ck_processus_integration_apres_transmission` | `0` ligne en infraction |

**Aucune anomalie nouvelle.** L'état final de la base est celui reporté dans le tableau
ci-dessus, inchangé par cette seconde passe hormis les rejeux, qui n'ont rien écrit.
