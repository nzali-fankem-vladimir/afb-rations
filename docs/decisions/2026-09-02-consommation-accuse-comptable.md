# Consommation de l'accusé comptable — arbitrages du Sprint 5.2

**Date :** 2 septembre 2026
**Services :** service-transmission (consommateur), service-workflow (écriture)
**Références :** contrat d'API §7.2, US-12, US-15, CLAUDE.md §9.1, guide Sprint 5.2

---

## Le problème

Le Sprint 5.1 a rendu le module **producteur**. Il publie l'état validé sur
`rations.etat.valide`, pose `transmis_comptabilite` et `statut_integration = EN_ATTENTE`.
Là s'arrêtait l'échange : un état apparaissait indéfiniment comme transmis, sans que
personne sache s'il avait été **traité, rejeté, ou perdu**. Le suivi (US-15) était aveugle
sur le sort de ce qui part en paiement.

Ce sous-sprint construit la seconde moitié : le module devient **consommateur**.

---

## 1. Les cinq situations anormales, arbitrées avant tout code

| Situation | Traitement | Motif |
| --- | --- | --- |
| **Message illisible** | Journal `ACCUSE ILLISIBLE`, audit, **message avancé** | Aucun rejeu ne le rendra lisible, et s'obstiner bloquerait tous les accusés suivants. |
| **Processus inconnu** | Journal `ACCUSE ORPHELIN`, audit, message avancé | Rejouer ne fera pas naître le processus. Erreur d'identifiant, ou accusé destiné à un autre module. |
| **Processus jamais transmis** | Journal `ACCUSE INCOHERENT`, audit, message avancé, **`transmis_comptabilite` jamais posé** | Le poser ferait croire à un envoi qui n'a pas eu lieu ; RG-13 refuserait ensuite le vrai comme un doublon, et l'état resterait impayé à jamais. |
| **Même accusé deux fois** | *No-op* : aucune écriture, **aucune trace d'audit** | C'est l'idempotence. Une seconde ligne ferait croire à un second traitement comptable. |
| **Accusé contradictoire** | Journal `ACCUSE CONTRADICTOIRE`, audit, base inchangée | Le module **refuse et signale, il n'arbitre jamais** — doctrine `INCOHERENCE_GRILLE` (2.4). |

Toutes ces anomalies sont **définitives** : aucune ne s'arrange en réessayant. Elles font
donc avancer le message après trace. Le seul échec **temporaire** est un service Workflow
injoignable, et c'est le seul cas qui fait rejouer.

---

## 2. La table des transitions du statut d'intégration

Portée par `TransitionIntegration` (service-workflow, paquet `domaine`), et **par elle
seule** : le mutateur de `ProcessusMensuel` est en visibilité paquet, le compilateur
garantit qu'aucun service applicatif ne la contourne.

| Statut courant | Accusé identique en tout point | Accusé différent |
| --- | --- | --- |
| `NULL`, état **non transmis** | ⛔ `NonTransmis` | ⛔ `NonTransmis` |
| `NULL`, état transmis (défensif) | ↩︎ `DejaApplique` | ✅ `Appliquer` |
| `EN_ATTENTE` | ↩︎ `DejaApplique` | ✅ `Appliquer` |
| `INTEGRE` | ↩︎ `DejaApplique` | ⛔ `Contradiction` |
| `REJETE` | ↩︎ `DejaApplique` | ⛔ `Contradiction` |

### Les quatre principes

1. **`INTEGRE` et `REJETE` sont définitifs.** Seuls `NULL` et `EN_ATTENTE` sont ouverts à
   l'écriture. `EN_ATTENTE` l'est parce que c'est **le module lui-même** qui l'a posé à la
   publication, sans référence ni date : le premier accusé venu de la comptabilité
   l'enrichit, il ne le contredit pas.
2. **Aucune régression.** Depuis un verdict définitif, un accusé `EN_ATTENTE` diffère donc
   est refusé.
3. **Le rejeu à l'identique n'est pas une contradiction.** Même statut, même référence,
   même date, même motif : rien n'est écrit, rien n'est tracé.
4. **Même statut, contenu différent, sur un état définitif = contradiction.** Deux
   `INTEGRE` de références différentes ne sont pas le même accusé.

> **Affinement relevé pendant l'implémentation.** Une première formulation faisait de
> « même statut, contenu différent » une contradiction **en toutes circonstances**. Elle
> aurait refusé le cas le plus normal : un premier accusé `EN_ATTENTE` portant une
> référence, alors que le module avait posé `EN_ATTENTE` sans référence. La règle
> « ouvert / définitif » corrige cela sans toucher aux quatre principes.

### Réponse à la question posée à l'arbitrage

> *« Que se passe-t-il si un accusé `REJETE` arrive après un `INTEGRE` déjà traité ? Un
> accusé peut-il jamais faire régresser un statut ? »*

Le `REJETE` est **refusé comme contradictoire**, rien n'est écrit, et le refus est tracé
avec les **deux** statuts nommés — sans quoi personne ne pourrait lever la contradiction
avec la comptabilité. Et **non : un accusé ne fait jamais régresser un statut.**

---

## 3. Comment l'idempotence est garantie

Elle ne repose **pas** sur une mémoire du service Transmission : il n'a pas de base, et un
cache en mémoire disparaîtrait au premier redémarrage — c'est-à-dire au moment précis où un
rejeu est le plus probable.

Elle repose sur la **seule source de vérité**, `processus_mensuel`, qui porte déjà le
résultat de tout accusé antérieur. Le service Workflow compare et décide **dans la même
transaction que l'écriture** :

- lecture, décision et écriture sont **un seul geste** — un appel HTTP, une transaction ;
- l'interface `StatutIntegrationClient` n'offre **aucune méthode de lecture**, délibérément :
  lire puis décider puis écrire en deux appels laisserait entre les deux une fenêtre où un
  second accusé pourrait s'intercaler, et l'idempotence ne serait qu'une apparence ;
- `DEJA_APPLIQUE` est rendu à l'appelant, qui ne publie alors **aucune trace d'audit**.

Recevoir deux fois le même accusé produit donc **le même état final et une seule ligne
d'audit**. Prouvé par `IntegrationComptableServiceTest` (dix rejeux, une écriture) et
`TraitementAccuseServiceTest`.

Et parce qu'aucun accusé ne fait régresser un statut, la garantie tient **même si les
messages arrivent dans le désordre**.

---

## 4. L'authentification du consommateur — secret partagé, dispositif provisoire

### Le problème, qui n'existait pas jusqu'ici

Toute la chaîne du Sprint 5.1 relaie le jeton de l'utilisateur final (doctrine 1.3).
**Un message Kafka n'a pas d'utilisateur derrière lui.** L'accusé arrive des minutes ou des
heures après la clôture ; aucun jeton n'existe, et le realm `afb-rations-dev` ne porte
**aucun compte de service** (`serviceAccountsEnabled: false`). La doctrine du relais ne
s'applique donc pas — non par exception, mais **faute d'utilisateur final à relayer**.

### Ce qui a été écarté

- **`permitAll`, comme Swagger.** Écarté : ce n'est pas une lecture de documentation, c'est
  une **écriture sur le statut de paiement d'un état**. Quiconque atteindrait le port 8084
  pourrait déclarer n'importe quel état intégré ou rejeté.
- **Compte de service Keycloak.** La voie propre à terme, impossible aujourd'hui — c'est
  précisément le point en attente DSI ouvert au Sprint 5.1.

### Ce qui a été retenu

Un **secret partagé** en en-tête `X-Cle-Interne`, exigé par la seule route
`PUT /processus/{id}/integration`, portée par une **chaîne de sécurité dédiée** qui ne passe
pas par OAuth2. Une chaîne séparée plutôt qu'une exception dans la chaîne principale : les
six endpoints du contrat restent tous protégés par OAuth2 **sans exception à lire entre les
lignes**.

### Le secret ne fuit par aucun canal — exigence explicite, rendue vérifiable

Le module a l'habitude de journaliser des motifs détaillés (`AUDIT PERDU`,
`TRANSMISSION MANQUEE`, `SEUIL INDISPONIBLE`). C'est une bonne habitude, et c'est
précisément ce qui rend la fuite probable : il suffirait qu'un jour quelqu'un ajoute la clé
reçue au message « pour faciliter le diagnostic ».

| Garantie | Où |
| --- | --- |
| Valeur lue d'une variable d'environnement, repli `changeme-in-development` | `application-dev.yml` des deux services |
| Jamais dans un journal, une exception, un événement d'audit, un corps de réponse | `FiltreCleInterne`, `StatutIntegrationHttpClient` |
| **Ni longueur, ni préfixe** — un préfixe est déjà une fuite | idem |
| Le refus ne distingue pas « absent » de « invalide » | `FiltreCleInterne` |
| Comparaison à **temps constant** (`MessageDigest.isEqual`) | `FiltreCleInterne` |
| **Deux tests de garde relisent les sources**, un par service | `CleInterneJamaisJournaliseeTest` × 2 |
| Un test vérifie que le corps du refus ne contient ni la clé, ni sa longueur | `IntegrationComptableIT` |

---

## 5. Acquittement et parallélisme

**Acquittement après traitement** (`AckMode.RECORD`, `enable.auto.commit=false`). Un
acquittement avant traitement perdrait silencieusement un accusé si le Workflow tombait
entre les deux.

C'est un fonctionnement « au moins une fois », donc un doublon est possible — **acceptable
uniquement parce que le traitement est idempotent**. Sans elle, ce réglage corromprait les
statuts à chaque rejeu.

**Le réessai est borné** : deux nouvelles tentatives, deux secondes de pause, puis abandon
avec trace `ACCUSE ABANDONNE`. Un rejeu illimité tiendrait la partition bloquée tant que le
Workflow ne répond pas — plus aucun accusé ne serait traité, y compris ceux d'états sans
rapport. Ce qui est perdu est une mise à jour de **suivi**, pas un paiement : l'état reste
`EN_ATTENTE` et retrouvable par requête.

> Ce réessai n'a rien de commun avec celui de la publication (5.1). Là, rejouer risquait un
> **double paiement**, et c'était donc réservé aux échecs antérieurs à tout envoi. Ici, on
> rejoue une lecture suivie d'une **écriture idempotente** : la répéter ne produit aucune
> écriture comptable. Le même principe de prudence, appliqué à deux opérations de natures
> opposées, donne deux règles opposées.

**Un seul fil** (`concurrency = 1`), un seul groupe. Kafka garantit qu'une partition n'est
lue que par un consommateur du groupe.

> **Limite consignée, et c'est le point le plus important de cette section.** Cette garantie
> d'ordre **suppose** que le module de comptabilisation partitionne par `idProcessus`. Ce
> producteur n'est pas le nôtre et l'équipe n'y a pas accès. **Le vrai filet n'est donc pas
> l'ordre Kafka, c'est le refus strict de contradiction du §2.** Point à confirmer avec la
> DFT, consigné dans `docs/points-en-attente.md`.

---

## 6. Le statut et la date voyagent en chaîne, pas en type

`AccuseComptableEvent.statutIntegration` est un `String`, pas l'énumération ; `dateTraitement`
aussi. Même raison que `nature` et `session` dans `EtatValideEvent` (5.1) : une valeur
inconnue ferait **échouer la désérialisation** au lieu d'être vue.

La conséquence est concrète. Un statut `"BIDON"` tomberait dans « message illisible » —
diagnostic pauvre, indiscernable d'un JSON tronqué — alors qu'il mérite son propre refus
nommé, `STATUT_INCONNU`, qui dit exactement ce qui ne va pas. C'est la leçon du défaut 2 du
Sprint 5.1, où le comportement était juste mais le diagnostic perdu.

La conversion a lieu **une seule fois**, dans `ValidationAccuseService`, et le type scellé
`ResultatValidationAccuse` fait que rien en aval ne voit un statut non vérifié.

**Le motif accepte trois noms** (`motif`, `motifRejet`, `motifIntegration`, via
`@JsonAlias`) : le contrat §7.2 décrit le champ en toutes lettres mais ne le nomme pas dans
son exemple JSON, qui ne montre qu'un accusé d'intégration. Un désaccord de nom ferait
refuser **tous** les accusés de rejet pour `MOTIF_REJET_ABSENT`, et le motif du refus
comptable serait perdu. Trois alias coûtent une annotation.

**Une date sans décalage horaire est refusée.** `"2026-08-18T02:15:00"` est ambigu, et lui
supposer un fuseau reviendrait à inventer une information sur un traitement de paiement.

---

## 7. Trois codes HTTP, trois gestes différents

| Code | Situation | Pourquoi ce code |
| --- | --- | --- |
| `404 PROCESSUS_INTROUVABLE` | Identifiant inconnu | Code existant du contrat. |
| `422 PROCESSUS_NON_TRANSMIS` | État jamais transmis | **Rien n'est dupliqué, c'est une règle de gestion qui refuse** — distinction du Sprint 2.3 (`TRANSITION_INTERDITE`) et 3.3 (`ETAT_NON_MODIFIABLE`). |
| `409 ACCUSE_CONTRADICTOIRE` | Contredit un statut définitif | **Deux affirmations concurrentes sur la même ressource : c'est la définition d'un conflit** — distinction du Sprint 4.1 entre `PROCESSUS_EXISTANT` (409) et `FONCTIONNALITE_NON_OUVERTE` (422). |
| `401 CLE_INTERNE_INVALIDE` | Secret absent ou faux | L'authentification manque, ce n'est pas un droit refusé. Traité comme une **indisponibilité** par l'appelant, donc rejoué : un secret mal configuré est une panne de déploiement, pas un accusé fautif. |

**`PUT` et non `POST`** : l'opération est idempotente, et le verbe le dit avant tout
commentaire. `200` dans les deux cas d'acceptation, le champ `resultat` distinguant
`APPLIQUE` de `DEJA_APPLIQUE` — l'appelant en a besoin pour ne pas tracer deux fois.

---

## 8. Le test bout en bout du guide (test 10) n'est pas automatisable — et pourquoi

`@EmbeddedKafka` échoue : `NoClassDefFoundError: kafka/testkit/KafkaClusterTestKit`. Le pom
parent épingle `spring-kafka` en **3.3.0** (décision Sprint 0.2) alors que les
`kafka-clients` tirés sont en **4.2.1**, et Kafka 4 a déplacé cette classe.

Spring Boot 4.1, parent du projet, **gère déjà `spring-kafka` en 4.1.0** : l'épinglage le
rétrograde de deux versions majeures. La montée touche les producteurs des **six** services
— dont celui de l'audit et ses cinq couches de protection — et relève d'un sprint technique
dédié, consigné dans `docs/points-en-attente.md`.

**Ce qui a été fait à la place**, arbitré avec l'utilisateur :

- un **test de câblage** (`CablageConsommateurAccuseTest`, `ApplicationContextRunner`) —
  c'est exactement ce type de test qui avait attrapé le défaut de démarrage du Sprint 5.1,
  et il vérifie en outre les trois réglages qui portent les décisions du sous-sprint ;
- le **bout en bout réel à la vérification manuelle**, sur le conteneur `rations-kafka`
  avec `kafka-console-producer.sh`, comme le guide §8 le prescrit déjà.

---

## 9. Le défaut trouvé par la vérification manuelle - `@EnableKafka`

**Le service démarrait normalement et n'écoutait rien.** Pas une ligne de journal, pas une
erreur, tous les beans en place, la sonde de santé à `200`. Seule l'absence de toute trace
de souscription dans le journal l'a révélé.

**La cause.** Sans `@EnableKafka`, l'annotation `@KafkaListener` n'est jamais traitée : la
méthode d'écoute reste une méthode ordinaire que personne n'appelle. Le projet déclare
`spring-kafka` directement, sans `spring-boot-starter-kafka`, donc l'auto-configuration qui
poserait cette annotation n'est pas entraînée.

**Pourquoi personne ne l'avait rencontrée.** C'est **le premier `@KafkaListener` du
module**. Les six services n'ont eu jusqu'ici que des *producteurs* - celui de l'audit
depuis le Sprint 1.3, celui de l'échange comptable depuis le 5.1 - et un producteur n'a pas
besoin de cette annotation.

**Pourquoi les 78 tests ne pouvaient rien voir.** Y compris
`CablageConsommateurAccuseTest`, écrit précisément pour attraper les défauts de câblage : il
vérifiait que les **beans existent**, pas que l'**écoute est enregistrée**. C'est la même
famille de défaut que le bean `ObjectMapper` absent au Sprint 5.1 - un câblage qui ne se
voit qu'en assemblant, et ici même qu'en **démarrant**.

**La correction et sa garde.** `@EnableKafka` sur `ConfigurationConsommateurAccuse`, et un
test `ecouteEffectivementEnregistree` qui interroge le `KafkaListenerEndpointRegistry` : ce
bean n'existe que par cette annotation, et le test exige en outre que la liste des
conteneurs d'écoute ne soit pas vide.

**La leçon, à ranger à côté de celles des Sprints 2.3, 4.2 et 5.1.** Un test de câblage qui
se contente de compter les beans ne prouve pas que le service *fonctionne*. Pour un
consommateur, la question à poser est « l'écoute est-elle enregistrée ? », pas « les beans
sont-ils là ? ».

---

## Ce que ce sous-sprint ne fait pas

- **Le contrôle d'unicité de RG-13** — Sprint 5.3. Son point d'accroche reste marqué dans
  `TransmissionService`.
- **`GET /transmission/processus/{id}`** — Sprint 5.3.
- **La reprise d'une transmission manquée** — toujours impossible sans compte de service.
- **Aucune écriture comptable**, aucune référence fabriquée : la référence est recopiée de
  l'accusé, jamais produite (CLAUDE.md §8).
