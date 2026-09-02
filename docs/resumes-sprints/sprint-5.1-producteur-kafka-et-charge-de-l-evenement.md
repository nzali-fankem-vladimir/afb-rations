# Résumé Sprint 5.1 — Producteur Kafka et charge de l'événement

**Services :** service-transmission (premier code métier), service-workflow (déclenchement)
**Date :** 2 septembre 2026
**Config :** **Opus / effort élevé sur l'intégralité du sous-sprint**, conformément au
guide §1 — « la charge publiée part vers la comptabilité : une donnée manquante ou fausse
produit un paiement erroné, et le module n'a aucun moyen de le rattraper une fois
l'événement parti ». Aucun changement de modèle en cours de route.

**Statut :** livré, **tests au vert**, **vérification manuelle réelle faite et conforme**
(cinq services démarrés, jetons Keycloak réels, topic Kafka lu, broker arrêté puis relancé —
voir la section dédiée en fin de document). **Deux défauts trouvés par cette vérification et
corrigés**, chacun avec son test de non-régression.

`mvn -pl service-transmission test` → **BUILD SUCCESS, 33 tests, 0 échec** (0 auparavant).
`mvn -pl service-workflow test` → **BUILD SUCCESS, 244 tests, 0 échec** (231 au Sprint 4.4,
**+13**).
Backend complet `mvn test` → **BUILD SUCCESS, 520 tests, 0 échec** (474 au Sprint 4.4,
**+46**), aucune régression sur les autres services.

Migration **`V4__processus_statut_integration.sql`** appliquée : *Successfully applied 1
migration to schema « public », now at version v4*.

---

## Le cœur du sous-sprint

Le circuit de validation s'achevait sur une clôture. Il met désormais l'état validé à la
disposition de la comptabilité.

Une clôture — **aux deux points où elle survient** — déclenche `POST /transmission/processus/{id}`.
Le service Transmission relit l'en-tête auprès du Workflow, **vérifie que l'état est bien
`CLOTURE`**, va chercher le détail des lignes auprès de la Saisie, assemble la charge du
contrat §7.1, **la contrôle**, puis la publie sur `rations.etat.valide` et **attend
l'accusé du broker**. C'est cet accusé, et lui seul, qui autorise le Workflow à poser
`transmis_comptabilite` (RG-13).

**Aucune écriture comptable n'est produite.** Ni compte général, ni sens, ni journal, ni
pièce, ni appel au CBS. Vérifié par recherche sur les deux services : aucune classe de ce
type n'existe.

---

## Les cinq arbitrages du sous-sprint

### 1. Où vit le statut d'intégration — colonnes sur `processus_mensuel`

Le contrat expose un statut à **trois valeurs** plus une référence et une date ; le
dictionnaire ne prévoit qu'un **booléen**, et le service Transmission n'a pas de base.

Retenu : quatre colonnes additionnelles sur `processus_mensuel`, écrites par l'API du
Workflow et jamais par sa base. Motif principal : `transmis_comptabilite` vit déjà sur
cette ligne et décrit **le même fait** ; les séparer dans deux bases laisserait deux
moitiés de vérité sans transaction commune pour les tenir d'accord.

**Les quatre colonnes sont nullables.** Un état jamais transmis n'a *aucun* statut
d'intégration — pas `EN_ATTENTE`, qui signifie « publié, la comptabilité n'a pas encore
répondu ». Une contrainte en base interdit d'ailleurs un statut d'intégration sans
transmission.

> **Écart au guide, signalé avant tout codage.** Le guide annonce
> `V3__statut_integration.sql`. **V3 est déjà pris** par `piece_jointe.nombre_signatures`
> (Sprint 4.2). C'est donc **V4**.

### 2. Qui rassemble la charge — le service Transmission

Le Workflow n'envoie que l'identifiant. Trois raisons, dont une qui n'était pas dans le
guide et qui a emporté la décision : **le service peut alors vérifier lui-même ce qu'il
publie**. Un endpoint interne qui publierait vers la comptabilité sur la seule foi d'un
corps fourni par l'appelant enverrait en paiement n'importe quel état, y compris un état
encore en saisie.

S'y ajoutent : le Workflow n'a rien à savoir du contrat comptable §7.1, et le détail ne
traverse le réseau qu'**une** fois au lieu de deux.

### 3. Clé de partition et acquittement

**Clé = identifiant du processus** : Kafka ne garantit l'ordre qu'à l'intérieur d'une
partition. **`acks=all`** : avec `acks=1`, un leader qui tombe avant la recopie perdrait un
message que le producteur a cru reçu — un état figé, réputé transmis, jamais payé.

**Sérialisation en chaîne, pas `JsonSerializer`** : celui-ci ajoute des en-têtes portant le
**nom de classe Java**, que le module de comptabilisation n'a aucune raison de connaître.
Un test le vérifie : aucun `cm.afrilandfirstbank` ne fuit dans le message.

### 4. Échec de transmission après clôture réussie

**La clôture n'est jamais annulée.** Le document porte déjà le visa, écrit sur disque hors
transaction (Sprint 4.2) ; l'annuler laisserait un document signé pour une validation
inexistante. Et Kafka ne partage aucune transaction avec PostgreSQL.

L'échec est signalé **trois fois** : journal au préfixe `TRANSMISSION MANQUEE`, événement
d'audit du même nom dans une base qu'aucun service métier ne peut réécrire, et **champ
`transmission` rendu au valideur**.

Ce dernier point est le plus important : **aucune reprise automatique n'est possible**. Le
realm `afb-rations-dev` n'a qu'un client public, `serviceAccountsEnabled: false` — vérifié
dans le fichier de realm. Une tâche programmée n'aurait aucun jeton à relayer. La personne
qui clôture est donc, aujourd'hui, la **seule** à pouvoir apprendre qu'un état n'est pas
parti.

### 5. Le déclenchement branché sur le statut, pas sur le niveau

Le circuit clôture à deux endroits. Le déclenchement n'est **pas** écrit deux fois : il est
branché sur ce qui les définit tous les deux — `statut == CLOTURE`. Deux appels séparés se
seraient ressemblés à s'y méprendre, et en oublier un aurait laissé une moitié des états
sans jamais partir en paiement, **sans aucune erreur visible**.

---

## ⚠️ Les trois corrections décisives apportées par l'utilisateur

**Sur les trois points, la proposition initiale était fausse ou incomplète, et la
correction a modifié la conception.**

### Correction 1 — le budget de temps était faux d'un facteur trente

L'option présentée annonçait « 2 nouvelles tentatives espacées de quelques secondes ».
L'utilisateur a exigé le **vrai** pire cas, chiffré. Calcul fait :

| | Écrit initialement | Après correction |
| --- | --- | --- |
| Kafka `delivery.timeout` / attente d'accusé | 20 s / **25 s** | 6 s / **7 s** |
| Une tentative | **35 s** | **17 s** (les trois dépendances à leur limite) |
| Trois tentatives + pauses | **109 s** | — |
| Tentatives retenues | 3 | **2** |
| **Pire cas d'un fil HTTP bloqué** | **109 s** | **17 s** |

L'utilisateur avait par ailleurs raison sur les 30 s : c'est le `delivery.timeout` du
producteur **d'audit** (`AuditProprietes`). Il le peut — il publie sans attendre, personne
ne patiente derrière lui. Le producteur de l'échange comptable est un bean distinct, et
**quelqu'un attend derrière lui**. Reprendre 30 s aurait bloqué ce fil dix fois trop
longtemps.

### Correction 2 — le risque de double paiement, et pourquoi l'idempotence ne suffit pas

L'utilisateur a signalé qu'un réessai après une confirmation perdue republierait le même
état, et a demandé de **vérifier l'idempotence avant de réessayer, ou de documenter le
risque**.

Vérification faite : **`enable.idempotence=true` ne couvre pas ce cas.** Elle protège des
réessais **internes** du client Kafka à l'intérieur d'un seul `send()` — numéro de séquence
par partition et par session. Un second `send()` **applicatif** est, pour le producteur, un
message neuf.

La réponse n'a donc pas été de documenter le risque, mais de **le supprimer** :

| Échec | Un message a-t-il pu atteindre le broker ? | Réessai |
| --- | --- | --- |
| Service Workflow indisponible | non — aucun `send()` n'a eu lieu | **oui** |
| Service Saisie indisponible | non — aucun `send()` n'a eu lieu | **oui** |
| Charge incomplète | non, et déterministe | non |
| **Publication échouée** | **peut-être** | **non** |
| Délai HTTP dépassé, code inconnu, réponse illisible | on ne sait pas | **non** |

La règle est portée par le **type scellé** `ResultatDemandeTransmission`, pas par un
commentaire : le `switch` qui décide est exhaustif, et une quatrième issue ajoutée plus
tard ferait échouer la compilation au lieu de tomber dans une branche par défaut qui
réessaierait. Le classement par défaut est le prudent — **ne pas savoir n'est pas savoir
que non**.

Le risque résiduel, qui ne peut pas être fermé de l'intérieur, est consigné dans
`docs/points-en-attente.md` à côté de M-03 : nul ne sait si le module de comptabilisation
dédoublonne par `idProcessus`.

### Correction 3 — le pool dédié, et un défaut qu'il a révélé

L'utilisateur a exigé un pool séparé du trafic HTTP, sur le modèle de l'audit (Sprint 1.3),
pour qu'une panne de Kafka en fin de mois ne sature pas les fils de Tomcat.

Deux points en sont sortis :

- **La file du pool doit être nulle**, contre l'intuition. Une file aggraverait le
  problème : quatre fils occupés 17 s et cinquante tâches en attente feraient patienter la
  dernière plus de trois minutes — et son fil HTTP avec elle. La file nulle refuse
  immédiatement et rend la main.
- **Le handler de rejet doit journaliser *puis lever*.** La première version, muette par
  analogie avec l'audit, laissait la tâche disparaître sans que personne ne l'attende
  jamais : le fil appelant aurait patienté jusqu'à sa borne défensive, et la file nulle
  aurait produit exactement l'attente inutile qu'elle était censée supprimer. La différence
  avec l'audit est nette : là, personne n'attend le résultat ; ici, quelqu'un attend et doit
  savoir.

L'utilisateur a enfin demandé un **préfixe de journal dédié** :
`TRANSMISSION REJETEE POOL SATURE`, distinct de `TRANSMISSION MANQUEE`. En supervision,
« Kafka est en panne » et « le pool a été saturé par un pic de fin de mois » appellent deux
réactions différentes et se confondraient sous un préfixe unique.

---

## Le contrôle de complétude — le dernier filet

Quatorze contrôles en trois familles. Une fois l'événement publié, **rien ne le rattrape**.

**Racine** — identifiant, période 1-12, code unité, type, au moins une ligne, montant total
strictement positif.
**Lignes** — bénéficiaire identifiable, numéro de compte courant, code agence, montant
exploitable, nature dans `RATION` / `TRANSPORT`, session dans `JOUR` / `SOIR`.
**Cohérence** — total égal à la somme des lignes, et concordance des deux sources.

Trois principes :

- **Trois témoins pour un seul montant** : celui enregistré à la soumission, celui
  recalculé par la Saisie, et la somme des lignes de la charge. Deux suffiraient à détecter
  un écart ; la troisième dit *de quel côté* il est. **Aucun n'est ajusté sur les autres.**
- **Construire d'abord, contrôler ensuite** — le contrôle porte alors sur exactement ce qui
  partirait. Une inversion des deux codes échapperait à un contrôle des seules entrées.
- **Une anomalie par contrôle**, jamais une par ligne fautive : cent lignes sans code agence
  produisent une anomalie qui en nomme cinq et compte le reste.

Refus en **`500 CHARGE_INCOMPLETE`** et non `422` : l'état est clôturé, donc figé, et
l'appelant n'a rien à corriger. Même parti qu'`INCOHERENCE_GRILLE` (2.4) et
`SEUIL_INDISPONIBLE` (4.3) — le service refuse et signale, il n'arbitre jamais.

---

## Les deux codes, à leur place

`codeUnite` est à la **racine** (unité qui supporte la charge, ligne de débit).
`codeAgence` est sur **chaque ligne** (agence de domiciliation du compte crédité, ligne de
crédit). Ils viennent de deux objets différents et vont à deux endroits différents ; les
intervertir demanderait de croiser deux sources qui ne se rencontrent nulle part dans le
code.

Quatre tests le vérifient, dont un où un bénéficiaire domicilié en `00047` est servi par
l'unité `00002` : son code agence n'est **pas** aligné sur le code unité.

---

## Ce qui a été livré

### service-transmission — premier code métier du service

| Fichier | Rôle |
| --- | --- |
| `domaine/StatutIntegrationEnum.java` | Trois valeurs du contrat §7.2 |
| `domaine/CodeAnomalieEnum.java` | Les quatorze contrôles nommés |
| `domaine/EtatValideEvent.java` | La charge du contrat §7.1 |
| `domaine/exception/` (6) | `ProcessusIntrouvable`, `EtatNonCloture`, `ChargeIncomplete`, `ServiceWorkflowIndisponible`, `ServiceSaisieIndisponible`, `PublicationEchouee` |
| `application/ConstructionChargeService.java` | Assemblage + contrôle |
| `application/TransmissionService.java` | Orchestration, ordre des refus |
| `application/` ports et résultats scellés (10) | `ProcessusClient`, `ConsolidationClient`, `PublicateurEtatValide`, et leurs résultats |
| `infrastructure/EtatValideProducer.java` | Producteur Kafka, attente d'accusé |
| `infrastructure/config/ConfigurationProducteurEtatValide.java` | `acks=all`, idempotence, bornes |
| `infrastructure/config/ConfigurationAppelsSortants.java` | 2 s / 3 s, aucun réessai |
| `infrastructure/workflow/ProcessusHttpClient.java` | Lecture de l'en-tête |
| `infrastructure/saisie/ConsolidationHttpClient.java` | Lecture du détail |
| `api/TransmissionController.java` | `POST /transmission/processus/{id}`, endpoint interne |
| `api/GestionnaireErreursApi.java` + `ErreurApiDto.java` | Format d'erreur uniforme, `ACCES_REFUSE` tracé (CT-04) |

### service-workflow — déclenchement

| Fichier | Rôle |
| --- | --- |
| `db/migration/V4__processus_statut_integration.sql` | Quatre colonnes + deux contraintes |
| `domaine/StatutIntegrationEnum.java` | Copie assumée |
| `domaine/ProcessusMensuel.java` | `constaterTransmissionComptable()`, refuse un second passage |
| `application/TransmissionClient.java` + `ResultatDemandeTransmission.java` | Port, et **la règle de réessai portée par le type** |
| `application/DeclenchementTransmission.java` | Pool, réessai, signalement |
| `application/EnregistrementTransmission.java` | Pose du drapeau, transaction propre `REQUIRES_NEW` |
| `application/ResultatTransmissionCloture.java` | Ce qui remonte au valideur |
| `infrastructure/transmission/TransmissionHttpClient.java` | Traduction des réponses en issues réessayables ou non |
| `infrastructure/config/ConfigurationTransmission.java` | Pool 4 fils file nulle, client HTTP 20 s |
| `ValidationService`, `ValidationResponse`, `ResultatValidation` | Branchement sur `statut == CLOTURE`, champ additif |

### Tests

| Fichier | Ce qu'il prouve |
| --- | --- |
| `ConstructionChargeServiceTest` (23) | Conformité §7.1 champ par champ, les deux codes non intervertis, total = somme sur 2 journées × 2 bénéficiaires, un franc d'écart refusé, détail tronqué refusé, une anomalie par contrôle |
| `EtatValideProducerTest` (6) | Le JSON publié est celui du contrat, aucune fuite de nom de classe Java, clé = idProcessus, échec sans exception |
| `DeclenchementTransmissionTest` (11) | **La règle de réessai** : un échec ambigu n'est jamais rejoué, un échec antérieur l'est, au plus 2 tentatives, aucun drapeau sans accusé, pool saturé sans attente |
| `ValidationServiceTest` (+3) | Les deux points de clôture transmettent, une montée au DR ne transmet pas, un échec n'annule pas la clôture |
| `CircuitCompletIT` (mis à jour) | Les deux branches du seuil posent le drapeau |

---

## Documentation mise à jour

- `CLAUDE.md` §4 (colonnes), §5 (`StatutIntegrationEnum`), §9.1 (rôle du service),
  §11 (endpoint interne, champ `transmission`), §15 (**sept erreurs interdites
  ajoutées**), §17 (**dix décisions**).
- `docs/decisions/2026-09-02-transmission-comptable-et-reprise.md` — les cinq arbitrages,
  les corrections de l'utilisateur, le budget chiffré.
- `docs/points-en-attente.md` — **deux points ajoutés** : dédoublonnage côté comptabilité
  (DFT, à côté de M-03) et reprise impossible sans compte de service Keycloak (DSI).

---

## Ce que ce sous-sprint ne fait pas

- **Le consommateur de l'accusé comptable** — Sprint 5.2.
- **Le contrôle d'unicité de RG-13**, résistant à la concurrence — Sprint 5.3. Son point
  d'accroche est marqué à sa place exacte dans `TransmissionService`, après la lecture de
  l'en-tête et avant tout autre appel, comme l'a été celui de RG-12 au Sprint 4.3. Un
  contrôle en deux temps, lecture puis écriture, ne résisterait pas à deux instances
  traitant la même clôture : un demi-verrou donnerait l'illusion de la protection.
- **`GET /transmission/processus/{id}`** — Sprint 5.3.
- **La reprise d'une transmission manquée** — impossible sans compte de service Keycloak.

---

## Critères de validation du guide (§11)

| Critère | Statut | Preuve |
| --- | --- | --- |
| Écart contrat / dictionnaire arbitré | **Fait** | Migration V4, décision §1 |
| Origine des données de la charge tranchée | **Fait** | Décision §2 |
| Charge conforme au contrat, deux codes bien placés | **Vérifié** | `ConstructionChargeServiceTest`, `EtatValideProducerTest` |
| Contrôle de cohérence du montant total | **Vérifié** | Trois témoins, un franc d'écart refusé |
| Clé de partition et acquittement arbitrés | **Fait** | Décision §3, `EtatValideProducerTest` |
| Publication observée sur le topic | **Vérifié en réel** | Trois messages lus sur `rations.etat.valide` |
| Déclenchement aux deux points de clôture | **Vérifié** | `ValidationServiceTest` 3 et 9, `CircuitCompletIT` |
| Comportement en cas d'échec après clôture tranché | **Fait** | Décision §4, `DeclenchementTransmissionTest` |
| Aucune écriture comptable produite | **Vérifié** | Recherche sur les deux services : aucune classe |
| Aucun accès direct entre bases | **Vérifié** | Aucune datasource ni JPA dans service-transmission |
| `mvn -pl service-transmission test` | **BUILD SUCCESS** | 29 tests |

---

## Vérification manuelle — faite le 2 septembre 2026

Conduite par l'assistant à la demande de l'utilisateur (« fait les vérifications visuelles
toi-même »).

PostgreSQL, `rations-kafka` et `dottel-keycloak` démarrés. **Cinq services réellement
lancés** : Identité (8081), Saisie (8082), Grilles (8083), Workflow (8084), Transmission
(8086). Jetons obtenus par grant `password` sur le realm `afb-rations-dev` :
`jean_mbarga` (AGENT_UNITE / 00002), `paul_essama` (CHEF_UNITE_DA / 00002),
`sylvie_atangana` (DIRECTEUR_RESEAU_DR). Seuil lu en base : **100 000**.

### ⚠️ Deux défauts trouvés — et pourquoi aucun test ne pouvait les voir

#### Défaut 1 — le service Transmission ne démarrait pas

À la première tentative de lancement :

> `Parameter 1 of constructor in EtatValideProducer required a bean of type
> 'com.fasterxml.jackson.databind.ObjectMapper' that could not be found.`

**Spring Boot 4 n'auto-configure aucun bean de ce type.** La classe est bien au classpath,
tirée par des dépendances tierces, mais aucun bean n'existe.

Les 29 tests ne pouvaient rien voir : ils construisent leur propre convertisseur et
appellent le producteur directement, **sans jamais demander à Spring de l'assembler**. Un
défaut de câblage ne se voit qu'en assemblant.

**Correction.** Un bean `convertisseurChargeComptable` construit explicitement, avec
`JavaTimeModule` et `WRITE_DATES_AS_TIMESTAMPS` désactivé — le correctif du Sprint 2.2 sur
`DeltaAudit`, transposé. Au-delà de la cause immédiate, le motif de fond est le même que
pour `acks` : **la forme du message qui part en comptabilité ne doit pas dépendre d'un
`spring.jackson.*` posé dans un fichier de déploiement.**

**Test de non-régression** : `CablageProducteurEtatValideTest`, trois cas — l'assemblage
aboutit, les dates sortent en ISO 8601, et **les deux `KafkaTemplate` coexistent sans
ambiguïté**. Bâti sur `ApplicationContextRunner` et non `@SpringBootTest`, pour ne pas
dépendre du realm Keycloak au démarrage.

#### Défaut 2 — le mauvais code d'erreur quand le broker est arrêté

Broker arrêté, la réponse portait **`500 ERREUR_INTERNE`** au lieu du `503
PUBLICATION_ECHOUEE` prévu, et le préfixe de supervision n'était pas écrit.

Cause : **`kafkaTemplate.send()` ne rend pas toujours un futur.** Broker injoignable, il
**lève dès l'appel** un `KafkaException` enveloppant le dépassement de `max.block.ms`
(*« Topic rations.etat.valide not present in metadata after 3000 ms »*). Mes trois captures
ne visaient que les exceptions du futur, et la classe promettait pourtant « aucune exception
ne sort d'ici ».

**Gravité mesurée** : le classement restait **prudent** — l'échec tombait dans
`EchecApresTentative`, donc **aucun risque de double paiement**, et `tentatives` valait bien
1. Ce qui était perdu, c'est le diagnostic : le message ne nommait plus la cause et la
supervision n'avait pas son préfixe.

**Correction** : capture de `RuntimeException` ramenée en `PublicationEchouee`, avec le
préfixe `PUBLICATION ETAT VALIDE EN ECHEC`. **Test de non-régression** :
`echecSynchroneSansException`.

### Le premier point de clôture — sous le seuil (processus 1316)

État de **8 000 FCFA**, 4 lignes, deux bénéficiaires — dont **NKOLO Alice, domiciliée en
agence `00047`, dans une unité `00002`**. C'est le cas qui distingue les deux codes.

| # | Scénario | Résultat obtenu | Conforme |
|---|---|---|---|
| 1 | `POST /processus/1316/validation` (jeton DA) | `200`, `CLOTURE`, `aiguillage: SOUS_SEUIL_CLOTURE_DIRECTE`, `seuilApplique: 100000`, **`transmission: { transmis: true, motif: null, tentatives: 1 }`** | ✅ champ additif rendu au valideur |
| 2 | Durée de la requête | **~1 seconde** | ✅ conforme au budget (~150 ms nominal) |
| 3 | Message lu sur `rations.etat.valide` | `codeUnite: "00002"` **à la racine** ; `codeAgence: "00002"` puis **`"00047"`** selon la ligne | ✅ **les deux codes ne sont pas confondus, sur données réelles** |
| 4 | Cohérence du montant | `montantTotal: 8000` = 1500 + 2000 + 1000 + 3500 | ✅ |
| 5 | Fuite de type Java | aucun `cm.afrilandfirstbank` dans le message | ✅ |
| 6 | Base `processus_mensuel` 1316 | `CLOTURE` / 8000 / **`transmis_comptabilite = t`** / **`statut_integration = EN_ATTENTE`** | ✅ |
| 7 | Journal du service Transmission | *« Etat valide 1316 publie sur rations.etat.valide (partition 0, offset 0), 4 ligne(s), 8000 FCFA »* | ✅ |

### Le second point de clôture — au-dessus du seuil (processus 1317)

État de **105 000 FCFA**, 30 lignes, 30 bénéficiaires distincts.

| # | Scénario | Résultat obtenu | Conforme |
|---|---|---|---|
| 8 | Validation DA (montant > seuil) | `200`, **`EN_ATTENTE_DR`**, `aiguillage: ENVOI_DIRECTEUR_RESEAU`, **`transmission: null`** | ✅ un état non clôturé ne transmet rien |
| 9 | Topic après cette validation | **toujours 1 message** | ✅ rien n'est parti |
| 10 | Validation DR | `200`, `CLOTURE`, `aiguillage: null`, `seuilApplique: null`, **`transmission: { transmis: true }`** | ✅ **les deux branches transmettent** |
| 11 | Message publié | `montantTotal: 105000` = somme des **30** lignes | ✅ le contrôle des trois témoins tient sur un volume réel |

### ⚠️ Le scénario dangereux — clôturer avec Kafka arrêté (processus 1318, 1319, 1320)

`docker stop rations-kafka`, puis trois clôtures.

| # | Scénario | Résultat obtenu | Conforme |
|---|---|---|---|
| 12 | Validation, broker arrêté | `200`, **`statut: CLOTURE`** | ✅ **la clôture n'est jamais annulée** |
| 13 | Champ `transmission` (après correctif) | `transmis: false`, motif : *« reponse 503 PUBLICATION_ECHOUEE … L'etat 1320 … reste CLOTURE et NON TRANSMIS : il devra etre retransmis, faute de quoi ses beneficiaires ne seront pas payes »* | ✅ le valideur est averti, en clair |
| 14 | **`tentatives`** | **`1`, et non 2** | ✅ **la preuve visible que l'échec de publication n'a pas été rejoué** — la règle anti-double-paiement, observée en réel |
| 15 | Durée de la requête | **3 à 4 secondes** | ✅ très en deçà des 17 s du pire cas |
| 16 | Base, les trois états | `CLOTURE` / **`transmis_comptabilite = f`** / `statut_integration` **NULL** | ✅ retrouvables par requête |
| 17 | Journal du Workflow | `TRANSMISSION MANQUEE : l'etat 1320 de l'unite 00002 est CLOTURE mais n'a pas ete transmis…` | ✅ préfixe de supervision |
| 18 | Fil d'exécution du journal | **`[ transmission-4]`** | ✅ **le pool dédié est bien en service** : ce n'est pas un fil HTTP de Tomcat |
| 19 | Journal du Transmission | `PUBLICATION ETAT VALIDE EN ECHEC : l'envoi de l'etat 1320 … a echoue des l'appel` | ✅ second préfixe, après correctif |
| 20 | Contenu du topic | **les trois états échoués en sont absents** | ✅ rien n'est parti, le drapeau dit vrai |

### Retour à la normale (processus 1321)

| # | Scénario | Résultat obtenu | Conforme |
|---|---|---|---|
| 21 | `docker start rations-kafka`, puis clôture | `200`, `CLOTURE`, `transmission: { transmis: true, tentatives: 1 }` | ✅ aucun dommage durable |
| 22 | Lecture complète du topic | **3 messages** — 1316, 1317, 1321 — tous avec `total = somme des lignes` | ✅ exactement les états dont le drapeau est à vrai |
| 23 | Agences vues sur les lignes | `00002`, `00047`, `00050` — alors que l'unité est toujours `00002` | ✅ |

### Journal d'audit

| # | Scénario | Résultat obtenu | Conforme |
|---|---|---|---|
| 24 | `rations.audit.evenement` | `TRANSMISSION_ETAT_VALIDE` (émis par service-transmission) **et** `TRANSMISSION_COMPTABLE` (émis par service-workflow) pour 1316, 1317, 1321, avec topic, partition et offset | ✅ deux traces distinctes, deux questions différentes |
| 25 | Delta d'audit du Workflow | `transmisComptabilite: {avant: false, apres: true}`, `statutIntegration: {avant: null, apres: "EN_ATTENTE"}` | ✅ |
| 26 | **`TRANSMISSION_MANQUEE`** | **absent** | ⚠️ **voir ci-dessous** |

### ⚠️ Constat honnête : quand c'est Kafka qui tombe, l'audit de l'échec tombe aussi

Les événements `TRANSMISSION_MANQUEE` des états 1318, 1319 et 1320 **ne sont jamais
arrivés**. Ce n'est pas un défaut, c'est une conséquence : le journal d'audit passe lui
aussi par Kafka, en publication non bloquante (doctrine Sprint 1.3). Quand la cause de
l'échec de transmission **est** l'indisponibilité du broker, la trace de cet échec ne peut
pas davantage partir.

Sur les trois filets prévus, **deux seulement survivent à une panne de Kafka** : le journal
au préfixe `TRANSMISSION MANQUEE`, et le drapeau à faux en base. La réponse au valideur
survit également, mais elle est éphémère.

**Conséquence pour l'exploitation** : la supervision ne doit **pas** s'appuyer sur le
journal d'audit pour détecter les transmissions manquées. C'est le préfixe de log et la
requête `WHERE statut = 'CLOTURE' AND transmis_comptabilite = false` qui font foi. Consigné
dans `docs/points-en-attente.md`, et argument de plus en faveur de l'outbox transactionnel
déjà ouvert depuis le Sprint 1.3.

### États laissés en base

| Processus | Période | Montant | Transmis |
|---|---|---|---|
| 1316 | 03/2027 | 8 000 | ✅ |
| 1317 | 04/2027 | 105 000 | ✅ |
| 1318, 1319, 1320 | 05, 06, 07/2027 | 1 500 / 2 000 / 2 000 | ❌ **jeu d'essai naturel pour le Sprint 5.3** |
| 1321 | 08/2027 | 1 000 | ✅ |

Les trois états non transmis sont conservés volontairement : ils serviront à éprouver le
verrou d'unicité et la reprise du sous-sprint 5.3.
