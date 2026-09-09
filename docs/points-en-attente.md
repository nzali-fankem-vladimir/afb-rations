# Points en attente

Questions ouvertes, à trancher avec la DSI ou le métier. Ne pas figer de
comportement définitif ailleurs dans le code tant qu'un point reste ici.

## D-07 — Nommage définitif des topics Kafka en environnement partagé

Les noms de développement (`rations.etat.valide`, `rations.etat.accuse`,
`rations.audit.evenement`, Sprint 0.5) ne sont pas arrêtés pour
l'environnement partagé. Ne pas les figer ailleurs que dans
`infra/docker/kafka-topics.sh` (CLAUDE.md section 9).

## Garantie d'exhaustivité du journal d'audit — outbox transactionnel

Sprint 1.3, décision du 27 août 2026 (`docs/publication-audit.md` section 5).

La publication d'audit a lieu **après le commit** de la transaction métier, de
façon asynchrone. Cela écarte les traces **fausses** — un rollback ne laisse
jamais derrière lui la trace d'une opération annulée — mais conserve un risque de
trace **manquante** : broker durablement indisponible, file d'attente saturée,
ou arrêt de la JVM entre le commit et l'envoi.

**Ce risque est le plus difficile à détecter des deux.** Une trace fausse se
repère par recoupement avec l'état réel (`utilisateurs.role` contredit
`audit_log`) ; une trace manquante ne se repère pas du tout, rien ne signalant
une absence. Le compromis retenu est donc un compromis de coût pour ce sprint,
pas un état correct.

La réponse de fond est un **outbox transactionnel** : écrire l'événement dans
une table locale, dans la même transaction que la modification, puis le relayer
vers Kafka par un processus séparé. Ni trace fausse, ni trace manquante. Coût :
une table et un relais supervisé par service métier.

**À arbitrer avec le contrôle interne / l'audit interne** : le niveau de perte
d'événements acceptable est-il nul ? Si oui, l'outbox devient obligatoire pour
les six services, et le module `rations-audit-commun` est le seul endroit à
reprendre.

## Révocation immédiate d'un jeton Keycloak après changement de rôle

Sprint 1.2, décision du 26 août 2026
(`docs/decisions/2026-08-26-attribution-role-administrateur.md`) : une
modification de rôle applicatif s'applique dès la requête suivante, le
module étant stateless et relisant le profil local à chaque appel. Mais le
jeton Keycloak déjà émis à l'utilisateur reste valide jusqu'à son expiration
naturelle : ce module n'a aucun moyen de le révoquer immédiatement.

Si le métier exige qu'un changement de rôle coupe l'accès sur-le-champ (cas
d'un incident de sécurité, par exemple), la réponse est à chercher côté
configuration du realm Keycloak (durée de vie courte des jetons,
introspection systématique) — pas dans ce service, qui ne stocke ni
n'émet de jeton.

## Intégration au service de signature électronique de la banque

Sprint 4.2, décision du 1er septembre 2026 (`SignatureService`,
`docs/controles-completude.md`). **À arbitrer avec la DSI**, pas avec le métier :
la question n'est pas de savoir si le métier veut une signature, mais de quelle
infrastructure de confiance la banque dispose et à quelles conditions ce module
peut s'y raccorder.

**Ce que le module fait aujourd'hui.** RG-09 est tenue par une **mention signée
horodatée** imprimée sur la pièce jointe (login, rôle figé au moment de l'acte,
date et heure), doublée d'une **empreinte SHA-256** du fichier enregistrée dans
`etape_workflow.signature_numerique`.

**Ce que cela prouve.** Que le document archivé n'a pas été altéré depuis la
dernière signature : on recalcule l'empreinte du fichier et on la compare.

**Ce que cela ne prouve pas, et qu'il ne faut pas laisser croire.**

- Ce n'est **pas une signature électronique au sens juridique**. Aucune clé,
  aucun certificat, aucune autorité de certification, aucun horodatage qualifié.
- L'empreinte vit **dans la même base** que le reste du module : elle ne protège
  pas de quelqu'un qui peut y écrire. Elle détecte une altération du fichier, pas
  une falsification coordonnée.
- Seule la **dernière** empreinte reste vérifiable contre le fichier. Le document
  étant enrichi à chaque validation, les empreintes intermédiaires documentent ce
  qu'était le document à leur étape sans pouvoir être recontrôlées. C'est
  l'empreinte finale, après clôture, qui scelle le justificatif archivé.

**La question posée à la DSI.** La banque dispose-t-elle d'un service de
signature électronique — autorité de certification interne, HSM, horodatage
qualifié — auquel ce module devrait se raccorder ? Et si oui, sous quelle forme :

| Variante | Ce qu'elle signifie | Ce qu'elle suppose |
| --- | --- | --- |
| **Cachet serveur** | Une clé unique du module scelle le document. Prouve que *le module* a produit et scellé la pièce, **pas** qu'une personne l'a signée. | Un certificat de service et sa garde. |
| **Signature personnelle** | Chaque agent, chef d'unité et directeur réseau signe avec sa propre clé. Seule variante qui honore vraiment « une signature par validation ». | Un certificat par acteur, une conservation des clés (HSM ou carte), une gestion de la révocation. |

**Le point de tension à signaler.** Les spécifications parlent d'une signature
numérique « **automatique** ». Ce mot exclut la variante *signature personnelle*,
qui suppose par nature un geste de la personne. Si le contrôle interne exige une
valeur probante opposable, c'est donc la spécification elle-même qu'il faut
rouvrir, pas seulement l'implémentation.

**Coût du report : faible.** Le passage à une signature PAdES est un
enrichissement du document, comme l'est déjà l'apposition des mentions ; la
géométrie de la page des visas, la convention de nommage et la discipline
d'écriture confirmée ne sont pas à reprendre. C'est la chaîne de confiance qui
manque, pas le code.

---

## `SEUIL_AIGUILLAGE_DR` — point de défaillance unique du circuit de validation

**Ouvert au Sprint 4.3. À surveiller en priorité en production.**

**La décision prise.** Si le paramètre `SEUIL_AIGUILLAGE_DR` est absent, désactivé
ou porte une valeur illisible, le service Workflow **refuse la validation**
(`500 SEUIL_INDISPONIBLE`) au lieu d'appliquer une valeur de repli. Une valeur par
défaut dans le code réintroduirait exactement ce que RG-08 interdit, et un
aiguillage sur un seuil inventé serait invisible : le circuit continuerait de
tourner en appliquant un niveau d'approbation que personne n'a décidé.

**Le compromis assumé, et sa conséquence.** Une seule ligne de
`parametre_systeme` conditionne **tout** le circuit de validation du module. Une
suppression accidentelle, une désactivation, une valeur mal saisie — `100 000` avec
une espace, une valeur avec décimale — bloque toutes les validations de toutes les
unités, immédiatement et en même temps. Ce n'est pas un défaut de conception : c'est
le prix explicitement accepté pour qu'une erreur de configuration soit visible tout
de suite plutôt que de produire des aiguillages faux pendant des semaines. Mais
c'est un point de fragilité qu'il faut connaître avant de le découvrir.

**Ce qui est demandé à l'exploitation.**

| Mesure | Pourquoi |
| --- | --- |
| **Supervision du log au préfixe `SEUIL INDISPONIBLE`** | C'est le signal unique et immédiat. Chaque échec de lecture le journalise en `error` avec la valeur trouvée. Une alerte sur ce préfixe transforme une panne de circuit en incident détecté en quelques secondes. |
| **Contrôle de la ligne au déploiement et après toute migration** | La ligne vient de la migration V2. Un rejeu de base, une restauration partielle ou une intervention manuelle peuvent la faire disparaître sans que rien ne le signale tant que personne ne valide. |
| **Restreindre l'écriture sur `parametre_systeme`** | La table n'a aujourd'hui aucun endpoint d'administration : elle se modifie en SQL direct. Tant qu'il en est ainsi, l'accès en écriture à cette table est un accès au niveau d'approbation requis par la banque, et devrait être tracé au même titre. |
| **Journaliser toute modification du seuil** | Le module trace le seuil **appliqué** à chaque validation (audit `VALIDATION_PROCESSUS`, champ `seuilApplique`), donc l'effet du changement. Il ne trace pas le changement lui-même : `parametre_systeme` n'a ni horodatage ni auteur (CLAUDE.md §4). Un contrôle interne qui voudrait savoir *qui* a abaissé le seuil et *quand* ne le trouvera nulle part dans ce module. |

**Piste, si le métier le demande un jour.** Un endpoint d'administration du seuil,
réservé à `ADMIN` ou à la DRH, publiant un événement d'audit — ce qui fermerait la
dernière ligne du tableau. Hors périmètre du module tel que spécifié : il n'existe
aucun endpoint `/parametres` au contrat d'API.

---

## Séparation des tâches dans une unité à un seul valideur (Sprint 4.4)

**Le point.** RG-12 interdit à une même personne d'agir deux fois sur la version d'un
dossier qui est dans le circuit. La portée d'un chef d'unité est limitée à sa propre
unité (décision Sprint 1.1), et rien n'oblige une unité à compter plus d'un DA.

**Ce qui est déjà réglé.** Le contrôle porte sur le **cycle courant** et non sur toute
la vie du processus : un retour clôt un cycle, et le DA qui a retourné un état peut
valider la version corrigée. Sans ce découpage, le premier retour de chaque unité à un
seul DA aurait produit un blocage définitif. Voir
`docs/decisions/2026-09-01-separation-des-taches-et-cycle-de-validation.md`.

**Ce qui reste ouvert.** Le cas où la **même personne** soumet puis doit valider — un
agent promu chef d'unité qui reprend ses propres dossiers, ou une unité où le chef fait
lui-même la saisie. Le module refuse (`403 SEPARATION_TACHES`) et le déblocage est
organisationnel : un suppléant habilité sur l'unité, ou une réattribution par
l'administrateur. Le module ne peut pas faire mieux : il ne connaît pas l'annuaire des
valideurs d'une unité, `GET /identite/habilitation` ne répondant qu'à « cette personne
a-t-elle droit sur cette unité ? », jamais « qui d'autre ? ».

**Ce qui est demandé au métier.**

| Question | Pourquoi elle se pose |
| --- | --- |
| Chaque unité dispose-t-elle d'au moins un valideur distinct de l'agent qui saisit ? | Sans cela, le circuit est bloqué dès la première soumission de cette unité, et le refus paraîtra arbitraire à l'utilisateur. |
| Qui valide quand le chef d'unité est absent ? | La question existait avant le module ; la digitalisation la rend simplement visible et bloquante là où le papier laissait passer. |
| Un « suppléant » doit-il exister comme habilitation, ou l'administrateur réattribue-t-il au cas par cas ? | La première option demanderait une évolution du service Identité ; la seconde tient avec l'existant. |

**Surveillance.** Les refus `SEPARATION_TACHES` sont tracés en audit avec leur propre
motif, distinct de `HABILITATION_ABSENTE` et de `ROLE_INSUFFISANT`. Un comptage par
unité dira si le cas est théorique ou quotidien — et c'est cette mesure, pas une
hypothèse, qui devra décider d'une éventuelle évolution.

---

## Dédoublonnage de l'événement `rations.etat.valide` côté comptabilité (Sprint 5.1)

**À poser à la DFT, à côté de M-03** (« Position sur une seconde transmission
comptable », `docs/dispositifs_provisoires.md`), dont c'est le versant technique.

**La question.** Si le module publiait deux fois l'événement du même
`idProcessus` sur `rations.etat.valide`, le module de comptabilisation
produirait-il deux jeux d'écritures — donc un double paiement des mêmes
bénéficiaires — ou écarterait-il le doublon ? **Personne dans l'équipe ne le
sait**, et le module de comptabilisation n'est pas accessible.

**Ce qui est déjà fait pour que le cas ne se présente pas.**

| Mesure | Portée |
| --- | --- |
| `enable.idempotence=true` sur le producteur | Couvre les réessais **internes** du client Kafka à l'intérieur d'un seul `send()` : le broker écarte le doublon par numéro de séquence. |
| **Aucun réessai applicatif après une tentative de publication** | Un second `send()` applicatif serait, pour le producteur, un message neuf : l'idempotence ne le couvre pas. Le type `ResultatDemandeTransmission` sépare structurellement l'échec **antérieur** à toute publication — réessayable — de l'échec **ambigu**, qui ne l'est jamais. Éprouvé par `DeclenchementTransmissionTest`. |
| Drapeau `transmis_comptabilite` posé **après accuse** | Empêche qu'un état publié soit republié par un chemin ultérieur. |
| Contrôle d'unicité résistant à la concurrence | **Sprint 5.3.** Il fermera les chemins que le 5.1 laisse ouverts : rejeu de la clôture, appel manuel de l'endpoint interne, deux instances du service. |

**Le risque résiduel, qui ne peut pas être fermé de l'intérieur.** Une publication
peut aboutir sur le broker et son accusé se perdre en chemin. Le module classe alors
l'échec comme ambigu et ne rejoue rien — l'état reste `CLOTURE` et non transmis, donc
à reprendre à la main. Si quelqu'un le reprend et que le premier message était bien
passé, la comptabilité reçoit deux fois le même état. **Le module ne peut pas le
détecter** : il n'a aucun consommateur sur `rations.etat.valide`, et rien ne lui dit
ce que le receveur a déjà vu.

**Ce qui est demandé.** Que la DFT indique si le module de comptabilisation
dédoublonne par `idProcessus`. Si oui, la reprise manuelle d'un état ambigu devient
sans danger. Si non, elle exige une vérification humaine préalable côté comptabilité,
et il faut le dire dans la procédure d'exploitation.

---

## Reprise d'une transmission manquée — aucun compte de service au realm (Sprint 5.1)

**Le point.** Un état `CLOTURE` est **figé** : plus personne ne peut le corriger ni
le rouvrir. S'il n'a pas été transmis, ses bénéficiaires ne sont pas payés. Il n'existe
aujourd'hui **aucune reprise automatique**.

**Pourquoi elle est impossible, et non simplement absente.** Toute la chaîne de
transmission relaie le jeton de l'utilisateur final (doctrine Sprint 1.3) :
Workflow → Transmission, puis Transmission → Workflow et Transmission → Saisie. Le
realm `afb-rations-dev` ne porte **qu'un client public**, `serviceAccountsEnabled:
false` — vérifié dans `infra/keycloak/realm-afb-rations-dev.json`. Une tâche
programmée n'aurait donc aucun jeton à présenter. Ce n'est pas un oubli de code : il
manque une identité machine, et sa création est une décision Keycloak / DSI.

**Ce qui tient lieu de filet aujourd'hui.**

| Dispositif | Ce qu'il apporte |
| --- | --- |
| **Un réessai immédiat**, sur les seuls échecs antérieurs à toute publication | Couvre le cas le plus fréquent : un service dépendant qui redémarre. |
| **Le résultat rendu au valideur** (champ `transmission` de `ValidationResponse`) | **Le seul signal qu'un humain reçoit**, au moment même de la clôture. |
| **Journal au préfixe `TRANSMISSION MANQUEE`** | Supervision. Distinct de `TRANSMISSION REJETEE POOL SATURE`, qui désigne un pic de charge et non une panne : les deux appellent des réactions différentes. |
| **Événement d'audit `TRANSMISSION_MANQUEE`** | Trace dans une base qu'aucun service métier ne peut réécrire, avec le motif exact. |
| **`transmis_comptabilite` reste à faux** | L'état reste retrouvable : `SELECT ... WHERE statut = 'CLOTURE' AND transmis_comptabilite = false`. |

**Ce qui est demandé.**

1. **À la DSI** : créer un client confidentiel avec compte de service sur le realm AFB,
   ou statuer que la reprise restera un geste manuel. Sans lui, aucune reprise
   programmée ne peut être écrite.
2. **À l'exploitation** : superviser le préfixe `TRANSMISSION MANQUEE`, et contrôler
   périodiquement la requête ci-dessus — un état qui y figure durablement est un
   paiement en attente.
3. **Au métier / DFT** : la reprise manuelle est subordonnée à la réponse sur le
   dédoublonnage (point précédent).

**Ce que le Sprint 5.3 ne réglera pas.** Il pose le verrou d'unicité, qui empêche une
transmission de partir deux fois. Il ne fait pas partir celle qui n'est jamais partie.

### Constat de la vérification manuelle du 2 septembre 2026 : trois filets, mais deux seulement quand c'est Kafka qui tombe

Le scénario a été rejoué en réel, broker arrêté. Les trois états clôturés pendant la
panne (1318, 1319, 1320) sont restés `CLOTURE` avec `transmis_comptabilite = false` et
`statut_integration` nul, et **aucun message n'est parti** — vérifié sur le topic. La
réponse au valideur portait bien `transmis: false` avec le motif exact, et le journal du
service Workflow le préfixe `TRANSMISSION MANQUEE`.

**En revanche, l'événement d'audit `TRANSMISSION_MANQUEE` n'est jamais arrivé.** C'est
logique et non un défaut : le journal d'audit passe lui aussi par Kafka, en publication
non bloquante (doctrine Sprint 1.3). Quand la cause de l'échec de transmission **est**
l'indisponibilité du broker, la trace de cet échec ne peut pas davantage partir.

**Conséquence pratique.** Sur les trois filets prévus, seuls **deux** survivent à une
panne de Kafka :

| Filet | Panne de Kafka | Autre panne (Saisie, Workflow, charge incomplète) |
| --- | --- | --- |
| Journal `TRANSMISSION MANQUEE` | **survit** | survit |
| `transmis_comptabilite = false` en base | **survit** | survit |
| Réponse au valideur | **survit** | survit |
| Audit `TRANSMISSION_MANQUEE` | **perdu** | arrive |

La supervision ne doit donc **pas** s'appuyer sur le journal d'audit pour détecter les
transmissions manquées : c'est le préfixe de log et la requête
`WHERE statut = 'CLOTURE' AND transmis_comptabilite = false` qui font foi. C'est un
argument de plus en faveur de l'outbox transactionnel déjà consigné plus haut.

---

## Partitionnement des accusés sur `rations.etat.accuse` — clé posée par la comptabilité (Sprint 5.2)

**À poser à la DFT, à côté du dédoublonnage ci-dessus et de M-03**, dont c'est le
versant symétrique : celui-là porte sur ce que la comptabilité fait de ce qu'on lui
envoie, celui-ci sur la forme de ce qu'elle nous renvoie.

**La question.** Le module de comptabilisation publie-t-il ses accusés sur
`rations.etat.accuse` avec **`idProcessus` en clé de partition** ?

**Pourquoi elle compte.** Le consommateur du Sprint 5.2 tourne à un seul fil par
instance (`concurrency = 1`), et Kafka garantit qu'une partition n'est lue que par un
consommateur du groupe. Si les accusés d'un même état portent la même clé, ils tombent
donc dans la même partition et sont traités **en série et dans l'ordre**.

**Mais cette garantie est une supposition, pas un fait vérifié.** Le module de
comptabilisation n'est pas accessible à l'équipe et **nous ne contrôlons pas son
producteur**. S'il partitionne par autre chose — un identifiant technique de lot
d'envoi, par exemple, ce qui serait un choix parfaitement naturel de son côté — deux
accusés portant sur le même état peuvent atterrir sur deux partitions différentes et
arriver **dans le désordre**, malgré `concurrency = 1`.

**Ce qui protège aujourd'hui, et qui ne dépend d'aucune supposition.** Le vrai filet
n'est pas l'ordre Kafka, c'est le **refus strict de contradiction** arbitré au Sprint
5.2 : `INTEGRE` et `REJETE` sont définitifs, aucun accusé ne fait régresser un statut,
et un accusé qui contredit un statut déjà reçu est refusé, tracé, et n'écrit rien. Un
rejeu désordonné ne peut donc pas corrompre un statut — au pire il produit une trace
`ACCUSE CONTRADICTOIRE` qu'un humain examine.

**Ce qui est demandé.**

1. **À la DFT** : confirmer la clé de partition des accusés. Si elle vaut
   `idProcessus`, l'ordre est garanti et la trace `ACCUSE CONTRADICTOIRE` devient le
   signal d'une vraie anomalie comptable. Si elle vaut autre chose, cette même trace
   peut n'être qu'un désordre de transport, et son interprétation en exploitation
   change du tout au tout.
2. **À l'exploitation** : surveiller le préfixe `ACCUSE CONTRADICTOIRE`. Sa
   signification exacte dépend de la réponse au point 1.

---

## Montée de `spring-kafka` vers 4.1.0 — dette technique datée (Sprint 5.2)

**Le point.** Le pom parent épingle `spring-kafka.version` à **3.3.0** (décision Sprint 0.2,
« versions hors Spring Boot en `dependencyManagement` »). Or **Spring Boot 4.1.0, parent du
projet, gère déjà `spring-kafka` en 4.1.0** : l'épinglage rétrograde la bibliothèque de deux
versions majeures, alors que les `kafka-clients` effectivement tirés sont en **4.2.1**.

**Comment il s'est révélé.** Au Sprint 5.2, `@EmbeddedKafka` échoue avec
`NoClassDefFoundError: kafka/testkit/KafkaClusterTestKit` : Kafka 4 a déplacé cette classe,
que spring-kafka 3.3.0 référence encore. **Le test bout en bout du guide (test 10) n'est
donc pas automatisable en l'état.** Le sous-sprint a livré à la place un test de câblage
(`CablageConsommateurAccuseTest`) et fait le bout en bout à la vérification manuelle, sur le
conteneur réel.

**Pourquoi ce n'est pas seulement une gêne de test.** Un client Kafka en retard de deux
versions majeures sur son serveur n'est pas une configuration qu'on souhaite emmener en
production. Le décalage est aujourd'hui silencieux ; il ne l'a été rendu visible que par un
test.

**Ce qui est demandé — sprint technique dédié, pas « un jour ».**

| Étape | Portée |
| --- | --- |
| Retirer `<spring-kafka.version>` du pom parent, laisser Boot 4.1 gérer | 1 ligne, effet sur tout le réacteur |
| Rejouer la suite complète du backend | 603 tests au 2 septembre 2026 |
| **Éprouver chacun des six producteurs, broker éteint** | `rations-audit-commun` (les cinq couches qui garantissent que l'audit ne fait jamais échouer le métier, `max.block.ms` en tête) et le producteur comptable du Sprint 5.1 (`acks=all`, idempotence, budget 6 s / 7 s) |
| Éprouver le consommateur d'accusé, broker éteint puis relancé | Sprint 5.2 |
| Réactiver le test bout en bout `@EmbeddedKafka` | Test 10 du guide 5.2, aujourd'hui remplacé |

**Pourquoi la vérification broker éteint est explicitement demandée.** C'est elle, et elle
seule, qui a révélé les deux défauts du Sprint 5.1 — le bean `ObjectMapper` absent et le
`KafkaException` levé dès l'appel de `send()`. Une montée de version qui passerait les 603
tests sans ce contrôle laisserait exactement le même angle mort.

**Ce que ce report coûte aujourd'hui.** Un test automatisé de moins (le bout en bout Kafka),
compensé par un test de câblage et par la vérification manuelle. Rien en fonctionnement.

---

## Publication d'issue incertaine — un état réputé transmis sans l'être (Sprint 5.3)

**Statut :** risque résiduel assumé, **en surveillance prioritaire**
**Origine :** décision d'ordre du Sprint 5.3, `docs/decisions/2026-09-03-unicite-de-transmission-et-verrou.md`

### Le fait

Le verrou de RG-13 réserve la transmission **avant** de publier. Quand la publication se
termine de façon **ambiguë** — délai d'accusé dépassé, échec de livraison —, la réservation
**reste posée** : l'événement a pu être écrit sur le topic avant que l'accusé ne se perde, et
republier produirait un second jeu d'écritures comptables pour les mêmes bénéficiaires.

L'état est alors marqué transmis sans qu'on sache s'il l'est. C'est le risque résiduel de
l'ordre retenu, et il a été préféré à l'autre — republier — parce qu'il **se voit et se
répare**, alors qu'un double paiement est irréversible.

### Comment le détecter

```sql
SELECT id, code_unite, mois_paiement, annee_paiement, date_reservation_transmission
  FROM processus_mensuel
 WHERE statut = 'CLOTURE'
   AND transmis_comptabilite = TRUE
   AND statut_integration IS NULL
   AND date_reservation_transmission < NOW() - INTERVAL '15 minutes';
```

L'ancienneté est ce qui distingue l'incident du trafic normal : la même signature en base,
quelques centaines de millisecondes après une clôture, est simplement un envoi en cours.
C'est la raison d'être de la colonne `date_reservation_transmission` (migration V5).

Deux préfixes de journal l'accompagnent :

| Préfixe | Ce qu'il dit |
|---|---|
| `TRANSMISSION ISSUE INCERTAINE` | l'état a peut-être été publié ; le verrou reste posé, il ne sera pas rejoué |
| `CONFIRMATION TRANSMISSION MANQUEE` | l'état **est** publié, mais le verrou n'a pas pu être confirmé : fausse alerte à venir sur la requête ci-dessus |

Comme pour les transmissions manquées du Sprint 5.1, **le journal d'audit ne peut pas
servir de filet quand la panne est Kafka lui-même** : la trace passe par le même broker.
La requête SQL fait foi.

### Ce qu'il faut faire quand la requête remonte un état

1. **Vérifier le topic** `rations.etat.valide` : l'événement du processus y est-il ?
2. **S'il y est** : rien à faire côté transmission ; l'accusé comptable finira d'aligner le
   statut d'intégration, ou sera à réclamer à la DFT.
3. **S'il n'y est pas** : lever la réservation à la main, ce qui rend une reprise possible.
   Aucune reprise automatique n'existe — le realm n'a pas de compte de service, point déjà
   ouvert plus haut.

**Ne jamais lever une réservation sans avoir vérifié le topic.** C'est le seul geste du
module qui puisse produire un double paiement.

### À arrêter avec la DSI

* Le seuil d'alerte (15 minutes ci-dessus est une valeur de départ, à caler sur la latence
  réelle du broker en environnement partagé).
* Le rattachement de cette requête à la supervision, à côté de
  `statut = 'CLOTURE' AND transmis_comptabilite = false`.
* La levée manuelle : aujourd'hui une écriture directe en base, faute d'endpoint
  d'administration — à revoir quand un compte de service existera.

---

## Borne de volume de la recherche du Reporting (Sprint 6.1)

**Date de la decision :** 3 septembre 2026
**Date de revue exigee :** **3 septembre 2027**, puis chaque annee
**Voir :** `docs/decisions/2026-09-03-agregation-multi-services-du-reporting.md` section 4

### Ce qui est en place

Le service Reporting n'a pas de base : il croise en memoire les en-tetes rendus par le
service Workflow et les identifiants rendus par le service Saisie. La pagination se fait
donc **apres** le croisement, ce qui suppose de ramener toute la portee de l'utilisateur
avant de la decouper.

Un garde-fou borne ce volume : `app.reporting.limite-resultats`, **defaut 5000**. Au-dela,
la recherche est refusee en `422 RECHERCHE_TROP_LARGE`, avec un message nommant le nombre
trouve, la borne et l'action attendue.

### Pourquoi ce point est ouvert

La borne est calibree sur les volumes de 2026 : environ 50 unites, un etat par unite et
par mois, soit ~600 etats par an, donc ~8 ans de donnees nationales avant qu'elle ne
morde. **Cette marge se consomme toute seule**, sans qu'aucun evenement ne la signale
avant le premier refus en production.

Trois choses peuvent la consommer plus vite que prevu : l'ouverture du module a de
nouvelles unites, l'ouverture des etats COMPLEMENTAIRE (aujourd'hui fermes par le drapeau
`RATTRAPAGE_ACTIF`), et une reprise d'historique anterieur au module.

### Ce qu'il faut faire a la revue

| Geste | Pourquoi |
| --- | --- |
| Compter les lignes de `processus_mensuel` en production | C'est exactement le volume que la borne mesure. |
| Comparer a 5000 | Au-dela de la moitie, la borne mordra dans les cinq ans. |
| Relever la borne par variable d'environnement si besoin | Correctif immediat, sans redeploiement de code. |
| **Programmer la vraie correction** si la borne est relevee deux fois | Descendre la pagination dans les bases (strategie C de la decision) : le Reporting transmet au second service la liste des identifiants retenus par le premier, et la base pagine. |

**Relever la borne n'est pas une solution, c'est un report.** Elle protege la cible de
trois secondes ; la repousser indefiniment finit par la faire perdre en silence, ce qui est
pire qu'un refus explicite.

---

## A-01 — Une recherche « toutes les actions de cette personne » ne verra pas 21 traces sur 30 (Sprint 6.3)

**Ouvert le 4 septembre 2026, à l'étape 2 du Sprint 6.3. TRANCHÉ le 4 septembre
2026, à l'étape 3 du sprint de rattrapage du service Audit : Option B retenue
— aucun champ `login_acteur` ajouté au schéma. Voir
`docs/decisions/2026-09-04-arbitrage-point-a-01-login-acteur.md` et la section
« Décision tranchée au sprint de rattrapage » ci-dessous. Ce point n'est plus
ouvert en tant que tel ; les deux défauts de producteur qu'il a mis au jour
restent consignés ci-dessous comme travail pour un futur sprint producteur.**

### Le fait

`audit_log` porte une colonne `id_utilisateur` et un index dédié
(`idx_audit_log_utilisateur`), posés au Sprint 0.5 précisément pour permettre au
contrôle interne de retrouver toutes les actions d'une personne.

Or, sur les **30 points de publication** du backend, **21 passent un `null`
littéral en `idUtilisateur`** — ce ne sont pas des cas de bord, ce sont des
`null` écrits en dur :

| Service | Traces avec `idUtilisateur` | Traces sans |
| --- | --- | --- |
| Identité | `ATTRIBUTION_ROLE`, `LIAISON_COMPTE_KEYCLOAK` | `ACCES_REFUSE` |
| Grilles | les 5 traces de grille | `ACCES_REFUSE` |
| Saisie | — | les 6 (décision Sprint 3.3) |
| Workflow | `SOUMISSION`, `VALIDATION`, `RETOUR` | les 6 autres |
| Transmission | — | les 7 |
| Reporting | — | les 2 (Sprint 6.3) |

### Pourquoi c'est un piège et pas seulement un manque

L'information n'est pas absente : le `login` figure dans `detail_json` pour une
partie de ces traces, et `detail_json` est de type **JSONB**, donc
interrogeable. Le problème est ailleurs.

**Un contrôle interne écrit `WHERE id_utilisateur = …`**, obtient un résultat qui
a l'air complet, et n'a aucun moyen de savoir qu'il manque des pans entiers.
« Trouvable par un chemin que personne n'empruntera » est plus dangereux
qu'« absent » : l'absence se remarque, le résultat partiel non. C'est exactement
le type de trace « présente mais introuvable » que le module a évité partout
ailleurs.

### Requête de contournement, en attendant

```sql
-- Toutes les actions attribuables a jean_mbarga, par les deux chemins.
SELECT action, service_emetteur, entite_cible, id_entite, date_action, adresse_ip
  FROM audit_log
 WHERE id_utilisateur = (SELECT id FROM ... /* base rations_identite, hors jointure */)
    OR detail_json ->> 'login' = 'jean_mbarga'
 ORDER BY date_action DESC;
```

Un index d'appoint la rend rapide si le besoin devient courant :

```sql
CREATE INDEX idx_audit_log_login_json ON audit_log ((detail_json ->> 'login'));
```

**Cette requête reste un pansement.** Elle suppose que celui qui la rédige
connaisse le piège, ce qui est précisément ce sur quoi on ne peut pas compter.

### Pourquoi ce n'est pas corrigé au Sprint 6.3

Un champ dédié et indexable (`login_acteur`) a été envisagé et **écarté à ce
sprint**, sur arbitrage de l'utilisateur. Le motif n'est pas le coût du champ
lui-même mais son emplacement dans le temps :

- il toucherait **21 points dans 5 services**, transformant un sprint d'une
  journée de vérification en modification active de presque tout le backend ;
- il déciderait de la **forme de l'écriture avant que la lecture n'ait été
  conçue**. Le sprint de construction du service Audit doit arrêter l'entité JPA,
  les index et la structure de consultation ; s'il découvre alors qu'il faut
  aussi le **rôle** de l'acteur, ou une autre forme, il faudra une V3 de toute
  façon. L'argument « ça évite une migration » ne tient que si le design est
  parfait aujourd'hui, ce qui n'est jamais garanti avant d'avoir conçu la lecture.

Même raisonnement qu'au Sprint 5.1 pour la montée de version Kafka : une bonne
idée mal placée dans le temps.

### Ce qu'il faut faire au sprint de construction du service Audit

| Geste | Pourquoi |
| --- | --- |
| Trancher la forme du champ d'acteur — login seul, ou login + rôle | Le rôle figé au moment de l'action est ce qui rend une trace justifiable des années plus tard, et `utilisateurs.role` change dans le temps. |
| L'ajouter à `EvenementAudit` de façon **additive** | Le contrat de fil le prévoit ; les messages déjà sur le topic restent lisibles. |
| Le renseigner depuis le `SecurityContextHolder`, **sans appel réseau** | C'est ce que fait déjà `GestionnaireErreursApi` dans quatre services. Le quatrième appel HTTP refusé au Sprint 3.3 n'a pas à être rouvert. |
| Le laisser nul là où il n'y a pas d'utilisateur | Un message Kafka n'a personne derrière lui : `AccuseComptableConsumer` et `TraitementAccuseService` resteront légitimement sans acteur. |
| Poser l'index dans la même migration que l'entité | Une seule passe, un seul schéma. |

### Décision tranchée au sprint de rattrapage du service Audit (4 septembre 2026)

**Option B retenue avec l'utilisateur : aucun champ `login_acteur` ajouté au
schéma de `audit_log` à ce sprint.** Motif de fond, confirmé par inspection
réelle des 193 messages présents sur le topic (`kafka-console-consumer.sh
--from-beginning`), et non plus seulement supposé : un champ alimenté par
extraction de `detail_json` n'aurait de toute façon pas résolu la majorité des
cas, pour deux raisons distinctes qu'il faut désormais traiter séparément —
d'où les deux points ci-dessous, à ne pas fusionner.

**Correctif à une affirmation provisoire faite pendant l'arbitrage** : il avait
été avancé une incohérence « `login` contre `loginCible` ». Vérification faite,
c'est inexact — `loginCible` n'apparaît que sur `ATTRIBUTION_ROLE`, action dont
`id_utilisateur` **n'est pas nul** (c'est l'identifiant de l'administrateur
auteur du geste ; `loginCible` désigne la personne dont le rôle change, pas
l'auteur). Cette action n'appartient donc pas au périmètre du point A-01. La
vraie incohérence, ci-dessous, oppose `login` à `auteur`.

**1. Service Saisie : aucune capture d'acteur à la source, sur cinq actions.**
`CREATION_LIGNE_PRESTATION`, `OUVERTURE_FICHE_JOURNALIERE`,
`SUPPRESSION_LIGNE_PRESTATION`, `MODIFICATION_LIGNE_PRESTATION` et
`CREATION_BENEFICIAIRE` ne portent **aucun identifiant d'acteur, sous aucune
forme** — ni `id_utilisateur`, ni un login quelconque dans `detail_json`.
Vérifié dans le code (`CreationLigneService.tracer`) : `idUtilisateur` y est un
`null` littéral, écrit en dur. Ce n'est pas une limite de schéma ni un défaut
de nommage, c'est une absence totale de capture à la source — 45 événements
`CREATION_LIGNE_PRESTATION` sur les 193 inspectés, la plus grosse part des
traces sans acteur. **Défaut à corriger dans un futur sprint qui touche aux
producteurs** (service Saisie), hors périmètre du sprint de construction du
service Audit, qui ne touche que le consommateur et la lecture.

**2. Nommage incohérent de la clé de login, quand un login est bien capturé.**
Trois actions au moins capturent un login d'acteur dans `detail_json` malgré
un `id_utilisateur` nul, mais pas sous la même clé selon le service émetteur :

| Action | Service émetteur | `id_utilisateur` | Clé du login dans `detail_json` |
| --- | --- | --- | --- |
| `ACCES_REFUSE` | identite / grilles / saisie / transmission / workflow | nul | `login` |
| `DECLENCHEMENT_PROCESSUS` | workflow | nul | `auteur` |
| `GENERATION_RAPPORT` | reporting | nul | `login` |
| `EXPORT_RAPPORT` | reporting | nul | `login` |

Table complète des 15 actions à `id_utilisateur` toujours nul, constatée sur
les 193 messages inspectés le 4 septembre 2026 (nullité déterministe par
action : 0 % ou 100 %, jamais mixte) :

| Action | Service émetteur | Acteur capturé ? | Clé |
| --- | --- | --- | --- |
| `CREATION_LIGNE_PRESTATION` | saisie | non | — |
| `OUVERTURE_FICHE_JOURNALIERE` | saisie | non | — |
| `SUPPRESSION_LIGNE_PRESTATION` | saisie | non | — |
| `MODIFICATION_LIGNE_PRESTATION` | saisie | non | — |
| `CREATION_BENEFICIAIRE` | saisie | non | — |
| `ACCES_REFUSE` | identite, grilles, saisie, transmission, workflow | oui | `login` |
| `DECLENCHEMENT_PROCESSUS` | workflow | oui | `auteur` |
| `GENERATION_RAPPORT` | reporting | oui | `login` |
| `EXPORT_RAPPORT` | reporting | oui | `login` |
| `ACCUSE_COMPTABLE_REFUSE` | transmission | non (événement système, sans acteur humain) | — |
| `TRANSMISSION_ETAT_VALIDE` | transmission | non (système) | — |
| `TRANSMISSION_COMPTABLE` | workflow | non (système) | — |
| `TRANSMISSION_DOUBLON_REFUSEE` | workflow | non (système) | — |
| `INTEGRATION_COMPTABLE` | workflow | non (système, accusé Kafka) | — |
| `ACCUSE_COMPTABLE_APPLIQUE` | transmission | non (système) | — |

Les six dernières lignes (préfixe transmission/intégration) sont normales et
non fautives : ce sont des événements déclenchés par un message Kafka, sans
utilisateur final derrière eux (doctrine 1.3, section 9.1) — à ne pas confondre
avec les cinq lignes Saisie, où un agent humain a bien agi mais n'a pas été
capturé.

**But du futur sprint producteur** : uniformiser sur une seule clé (`login`,
déjà majoritaire) pour toute action qui capture un acteur sans
`id_utilisateur`, et ajouter la capture manquante côté Saisie — pour que ce
travail d'archéologie sur le contenu réel du topic n'ait pas à être refait.

---

## Accès du Directeur Réseau au journal d'audit — écarté tant que sa portée nationale reste provisoire

**Ouvert au sprint de rattrapage du service Audit (4 septembre 2026), étape 6.**
Décision tranchée avec l'utilisateur : `GET /audit/entrees` et
`GET /audit/processus/{id}` sont réservés à `ARH`, `DRH` et `ADMIN` — pas à
`DIRECTEUR_RESEAU_DR`.

**Pourquoi ce n'est pas simplement « le DR a déjà une portée nationale »**.
Le Sprint 1.1 a bien retenu une portée nationale par défaut pour le DR, comme
pour ARH/DRH/ADMIN (`docs/decisions/2026-08-26-portee-acces-directeur-reseau.md`)
— mais cette portée est **provisoire**, faute de découpage en réseaux défini
par le métier, et **réversible à coût faible**. Ouvrir au DR la lecture
intégrale du journal d'audit — toutes les unités, tous les services, tous les
refus d'accès — sur la foi d'une portée qui peut encore changer ferait
dépendre un accès de contrôle interne d'un arbitrage qui n'est pas stabilisé.

**Ce qui rouvrirait la question.** Pas la clarification du découpage en
réseaux à elle seule : même un DR à portée strictement régionale n'aurait pas
nécessairement vocation à lire le journal d'audit complet du module, qui n'est
pas un outil de suivi de dossier mais un instrument de contrôle interne. La
question à poser au métier, distincte de celle des réseaux, est : *le DR a-t-il
besoin d'un accès de suivi qui lui soit propre* — par exemple limité à son
périmètre, dans un format différent des deux endpoints actuels — et non
simplement d'un ajout de rôle sur les endpoints existants d'ARH/DRH/ADMIN.

**À réexaminer** seulement si le métier formule explicitement ce besoin, pas
au moment où le découpage en réseaux sera arrêté.

---

## `GET /audit/processus/{id}` ne couvre que le niveau workflow, pas le détail de saisie

**Ouvert au sprint de rattrapage du service Audit (4 septembre 2026), étape 6.**
Décision tranchée avec l'utilisateur : le filtre reste
`entite_cible = 'processus_mensuel' AND id_entite = {id}`, et non un filtre
plus large sur `id_entite` seul.

**Ce que cela couvre.** Tous les événements que le service Workflow publie
avec `entiteCible = "processus_mensuel"` — `DECLENCHEMENT_PROCESSUS`,
`SOUMISSION_PROCESSUS`, `VALIDATION_PROCESSUS`, `RETOUR_PROCESSUS`,
`INTEGRATION_COMPTABLE`, `TRANSMISSION_COMPTABLE`,
`TRANSMISSION_DOUBLON_REFUSEE`, `TRANSMISSION_RESERVATION_LIBEREE`.

**Ce que cela ne couvre pas.** Les événements du service Saisie
(`CREATION_LIGNE_PRESTATION`, `MODIFICATION_LIGNE_PRESTATION`,
`SUPPRESSION_LIGNE_PRESTATION`, `OUVERTURE_FICHE_JOURNALIERE`,
`CREATION_BENEFICIAIRE`, `INCOHERENCE_BENEFICIAIRE`) ne portent **pas**
`idProcessus` en `idEntite` : ils portent l'identifiant de la ligne ou de la
fiche elle-même, dans un espace d'identifiants distinct de celui des
processus. Les inclure par un filtre élargi sur `id_entite` seul créerait un
risque de collision numérique — une ligne de prestation n°109 confondue avec
un processus n°109 — donc une fausse trace dans le journal d'un dossier. C'est
pour cette raison que le filtre reste restreint à `processus_mensuel`, pas par
choix de périmètre arbitraire.

**Ce qu'il faudrait pour un journal vraiment complet (workflow + saisie) par
dossier.** Enrichir les événements publiés par le service Saisie d'un champ
`idProcessus`, en plus de l'identifiant de la ligne ou de la fiche — évolution
additive du contrat de fil (`EvenementAudit` porte déjà `entiteCible` et
`idEntite`, il faudrait un champ supplémentaire ou le porter dans
`detail_json`). C'est un défaut de producteur, comme celui déjà consigné plus
haut sur l'absence de capture d'acteur côté Saisie : à traiter dans un futur
sprint qui touche aux producteurs, pas dans ce sprint-ci, qui ne construit que
la moitié aval de la chaîne (consommateur, persistance, lecture).

**Documenté dans le contrat d'API**, section 8 (`GET /audit/processus/{id}`) :
la portée exacte de l'endpoint — niveau workflow uniquement — y est explicite,
pour qu'un lecteur du contrat ne suppose pas à tort un journal complet du
dossier.

---

## M-04 — Rythme de paiement : mensuel ou hebdomadaire (sprint d'ajustement métier)

**Ouvert le 9 septembre 2026**, entre le sous-sprint 6bis.1 et le sous-sprint
6bis.2. **Le métier a répondu le même jour : lecture (B), tout le cycle devient
hebdomadaire**, et les trois questions non techniques ont été tranchées dans la
foulée. Voir
`docs/decisions/2026-09-09-rythme-de-paiement-et-maille-de-la-periode.md`.

**Ce qui est tranché :** le rythme (lecture B), la valeur du seuil RG-08
(maintenue à 100 000 XAF), la forme de la période (intervalle de dates), et le
rattachement d'une semaine à cheval (sans objet, la notion de mois disparaissant
de l'identité de la période).

**Ce qui reste ouvert, et pour quoi ce point n'est pas clos :** la position de
la **DFT** sur la forme de la période dans le contrat Kafka publié à la
comptabilité. Le module ne peut pas la décider seul — la charge est consommée
par une équipe qui ne fait pas partie de ce projet.

Point voisin : **W-02** (rien n'interdit d'ouvrir un état sur une période
future), déjà ouvert vers le métier et portant sur la même table — les deux
questions peuvent partir dans le même échange. *Note de tenue de registre :
W-02 n'a jamais été porté dans le tableau de `docs/dispositifs_provisoires.md`
section 3 ; il ne vit que dans les tableaux de questions ouvertes des résumés
des Sprints 4.1 et 4.2.*

### Le fait

Le métier a indiqué que le paiement des frais de ration et de transport de la
garde armée se fait **de façon hebdomadaire**, et non mensuelle.

Tout le module est bâti sur l'hypothèse inverse, et cette hypothèse n'a jamais
été discutée : aucun document du projet — cahier des charges, user stories,
contrat d'API, guides de sprint, résumés, décisions — ne contient une seule
occurrence de « hebdomadaire » ou de « semaine » au sens d'un rythme de
paiement. Le mensuel est un postulat implicite, hérité de la description du
processus papier (« consolidation mensuelle à la main »), jamais remis en cause
depuis le Sprint 0.

**L'information est arrivée au meilleur moment possible, malgré les apparences.**
Le sous-sprint 6bis.2 doit implémenter RG-15, dont l'énoncé est « aucune ligne
ne peut reproduire une combinaison déjà présente dans un autre état de la même
unité et de la **même période** ». C'est la première règle du module qui fige en
code la définition de la période. Une semaine plus tard, elle aurait été écrite,
testée, commitée — et il aurait fallu la défaire.

### Les trois lectures, et celle qui a été retenue

Trois lectures se cachaient derrière le même mot. Elles ont été posées au métier
dans ces termes, sans qu'aucune ne soit présentée comme acquise.

**(A) Seul le décaissement est hebdomadaire, le dossier reste mensuel.** L'agent
touche son argent chaque semaine, mais le module ne change pas : un dossier par
mois et par unité, la banque découpant ensuite le versement hors du module.
Impact : quasi nul — le décaissement est hors périmètre (CLAUDE.md section 8).

**(B) Tout le cycle devient hebdomadaire.** Saisie, consolidation, soumission,
validation, clôture et transmission comptable ont lieu chaque semaine. Impact
majeur : la maille de la période change dans deux bases, dans le contrat publié
à la comptabilité, et la valeur du seuil d'approbation de la banque est à
reconsidérer.

**(C) Cas mixte : validation hebdomadaire, transmission comptable regroupée
mensuellement.** Le plus lourd des trois : il ajoute un niveau d'agrégation qui
n'existe nulle part aujourd'hui, entre l'état validé et l'événement publié.

**Valeur provisoire retenue à l'ouverture du point : (A).** Ce n'était pas de la
paresse : c'était la seule lecture ne demandant aucune modification, donc la
seule ne fabriquant aucune dette si elle se révélait fausse. Basculer le module
en hebdomadaire sur une phrase entendue en réunion, puis découvrir que seul le
décaissement l'était, aurait coûté une reprise complète pour rien.

**Réponse du métier, 9 septembre 2026 : lecture (B).** La valeur provisoire (A)
est donc écartée. Elle reste consignée ici parce qu'elle explique pourquoi rien
n'a été écrit avant la réponse.

### Ce qui est gelé, et ce qui ne l'est pas

**Le sous-sprint 6bis.2 est gelé.** Il implémente RG-15 sur « la même période ».
Écrit aujourd'hui, il le serait sur le couple `(mois_paiement, annee_paiement)`
recopié sur `fiche_journaliere` par la migration V3 du service Saisie, et sur
l'index `idx_fiche_journaliere_unite_periode` qui l'accompagne. C'est
précisément la définition que la réponse (B) invalide.

**Motif du gel, révisé deux fois le 9 septembre 2026.** Le guide du sprint
d'ajustement prévoyait que 6bis.2 « reprenne dès que le métier a répondu, quelle
que soit sa réponse ». Cette phrase supposait la lecture (A). La réponse étant
(B), le gel a d'abord été maintenu dans l'attente de l'arbitrage sur la forme de
la période. **Cet arbitrage est désormais rendu — intervalle de dates — et le
gel reste néanmoins en vigueur, pour une raison qui n'est plus une inconnue mais
un ordre de travaux :** la forme est décidée, la migration qui la porte n'est
pas écrite. RG-15 porte sur « la même période » ; tant que `processus_mensuel`
et `fiche_journaliere` portent `(mois_paiement, annee_paiement)`, l'écrire
reviendrait à écrire faux en connaissance de cause.

**6bis.2 reprend derrière les migrations de la maille**, et non derrière une
réponse à obtenir. La différence compte pour le planning : ce n'est plus une
attente, c'est une dépendance. Le découpage de ces travaux — sprint dédié ou
travaux rattachés à 6bis.2 — reste à arrêter.

**Rien d'autre n'est gelé.** Les sous-sprints 7F.1 (socle et composants), 7F.2
(layout et navigation), 7F.3 (authentification), 8.1 et 8.2 ne touchent pas à la
période et peuvent avancer. Vérifié : le répertoire `frontend/src` ne contient
**aucune** occurrence de `mois`, `periode` ou `annee`. Les sous-sprints 7F.4
(écrans de saisie), 7F.5 (écrans de validation) et 7F.7 (suivi et reporting)
dépendent en revanche de la maille — ils ne sont pas gelés parce qu'ils viennent
après 7F.1 à 7F.3 et que l'arbitrage de la forme aura eu lieu d'ici là. Si ce
n'était pas le cas, il faudrait les geler à leur tour.

Geler tout le projet pour une question ouverte serait aussi faux que de
l'ignorer.

### Inventaire d'impact — ce qui CASSE (le code cesse d'être correct)

Établi par balayage du backend le 9 septembre 2026. **Le tableau des points de
rupture annoncé par le guide du sprint (« section 8 ») n'existait pas dans ce
guide** — sa section 8 est « Commandes terminal » ; l'inventaire ci-dessous a
donc été établi de première main, pas recopié.

| Service | Point de rupture | Nature |
| --- | --- | --- |
| Saisie | `fiche_journaliere.mois_paiement` / `annee_paiement`, recopiés et **figés** à l'ouverture (migration V3) ; index `idx_fiche_journaliere_unite_periode` | Migration |
| Workflow | `processus_mensuel.mois_paiement` / `annee_paiement` ; index unique `ux_processus_normal_par_periode` (V1) — c'est lui qui interdit deux états NORMAL sur la même période | Migration |
| Workflow | `DeclenchementProcessusRequest` : `@Min(1) @Max(12)` sur `moisPaiement`, `@Min(2000) @Max(2100)` sur l'année. Une semaine 13 serait refusée en 400 | Contrat d'entrée |
| Workflow | `CompletudeService.dansLaPeriode` : `dateJour.getMonthValue() == mois && dateJour.getYear() == annee`. C'est le contrôle LIGNE_HORS_PERIODE de la soumission (CLAUDE.md section 6) | Règle de gestion |
| Workflow | `NommageDocument` : `{annee}/{mois}/etat-rations-{unite}-{annee}{mois}-p{id}.pdf`. **12 documents déjà archivés** sous cette forme | Convention + artefact existant |
| Workflow | `DocumentService` : tableau `MOIS[]`, libellé imprimé `TOTAL DU MOIS`, en-tête « mois année » | Document signé |
| Workflow | `ValidationService.libellePeriode` et `RetourService.libellePeriode` (`%02d/%d`) : « L'état 09/2026 de l'unité 00002 ». Aucune décision n'en dépend, mais en hebdomadaire quatre états porteraient le **même** intitulé dans les messages de refus | Rupture mineure, cosmétique mais réelle |
| Transmission | Charge publiée sur `rations.etat.valide` : `"periode": { "mois", "annee" }` (contrat d'API section 7.1, ligne 290). **Contrat inter-applicatif**, consommé par un module que cette équipe ne maintient pas | Contrat externe |
| Transmission | `ConstructionChargeService` : contrôle du mois hors de l'intervalle 1–12 (refus `CHARGE_INCOMPLETE`), et contrôle de concordance de période entre l'en-tête et le détail | Règle de gestion |
| Reporting | Filtres et réponses portant mois et année (`SuiviService`, `DemandeResponse`, `HistoriqueResponse`, `ReponseWorkflow`, `WorkflowLectureHttpClient`) ; nommage d'export `rapport-rations-<agence>-<AAAAMM>` | Contrat exposé + écran |
| Reporting | Borne `app.reporting.limite-resultats: 5000`. Calibrée sur ~600 états/an (~8 ans de marge). En hebdomadaire : ~2 600/an, soit **moins de deux ans** avant que `422 RECHERCHE_TROP_LARGE` ne morde en production | Volumétrie |
| RG-08 | La **valeur** du seuil (100 000 XAF) est calibrée sur un cumul mensuel. Voir la question portée à la DRH et à la DFT ci-dessous | Gouvernance, pas code |

Volume de données concerné, mesuré le 9 septembre 2026 : **15 états** portant une
période mensuelle (11 `CLOTURE`, 1 `EN_COURS_SAISIE`, 1 `RETOURNE`, 2
`COMPLEMENTAIRE`), de 2026-09 à 2027-09 ; **18 fiches sur 18** portant la période
recopiée figée, sur 14 couples unité + période ; **7 états déjà transmis** à la
comptabilité sous la forme mensuelle du contrat, donc **non rejouables** ; **12
pièces jointes** archivées sous `{annee}/{mois}`.

### Inventaire d'impact — ce qui RESTE CORRECT mais dont la justification vieillit

À ne pas confondre avec ce qui précède : ici le code est juste, seule sa
justification écrite date. Les mélanger ferait passer une relecture de javadoc
pour une migration.

Quatre décisions sont justifiées par la formule « c'est un geste mensuel » :

| Où | Ce qui est écrit | Verdict après vérification |
| --- | --- | --- |
| Sprint 4.2 | Soumission à trois appels sortants, 15 s au pire cas, « accepté, geste mensuel » (CLAUDE.md, résumé 4.2) | **Tient.** ~50 unités × 52 semaines ≈ 2 600 soumissions/an, soit ~10 par jour ouvré. Trivial |
| Sprint 4.3 | `SeuilService` javadoc : le `SELECT` du seuil est « invisible dans un geste mensuel qui fait déjà plusieurs secondes d'appels réseau » | **Tient.** Trois lignes indexées, ×4 en fréquence |
| Sprint 5.1 | `ConfigurationProducteurEtatValide` javadoc : les microsecondes gagnées « n'ont aucun sens dans un geste mensuel » (justifie `acks=all`) | **Tient.** `acks=all` reste le bon réglage à 2 600 messages/an |
| Sprint 5.1 | Arbitrage de latence contre la doctrine « aucun réessai » du Sprint 3.2 | **Tient.** Le motif du 3.2 était la latence d'un geste *par ligne*, inchangé |

**Aucun de ces quatre arguments ne tombe.** Seule leur formulation est à reprendre
le jour de la passe documentaire.

### Inventaire d'impact — ce qui n'est PAS impacté

Cette liste vaut autant que les autres : elle borne le chantier. Un inventaire
qui ne liste que les dégâts fait croire que tout est à refaire, et cette croyance
coûte plus cher que le travail réel. **Chaque point a été vérifié dans le code le
9 septembre 2026, pas recopié.**

- **Tout le grain journalier.** `uk_fiche_journaliere_processus_jour` porte sur
  `(id_processus, date_jour)` (V1 Saisie) ; l'index RG-04
  `ux_ligne_par_fiche_beneficiaire_nature_session` porte sur
  `(id_fiche_journaliere, id_beneficiaire, nature, session)` — la fiche *est*
  l'identité de la journée, aucune période n'y figure. RG-05 ouvre une fiche
  vierge par jour. **Un agent saisira exactement comme aujourd'hui.**
- **Le service Grilles en entier.** `ResolutionMontantService.resoudre(nature,
  session, LocalDate date)` résout à la **date de la journée**, et le service
  Saisie lui passe la date de prestation en paramètre de requête. Les 19
  occurrences du mot « période » dans ce service désignent les **périodes de
  validité des grilles**, qui ont leurs propres bornes, sans rapport avec le
  rythme de paiement.
- **Le service Identité, la passerelle, `rations-audit-commun` et le service
  Audit : zéro occurrence** de `mois`, `periode` ou `semaine` dans
  `src/main/java`. Compté, pas supposé.
- **L'accusé comptable.** `AccuseComptableEvent` porte cinq champs —
  `idProcessus`, `statutIntegration`, `referenceComptable`, `dateTraitement`,
  `motif`. Le rapprochement se fait par `idProcessus` **seul** : aucune période,
  donc insensible au changement de maille.
- **Le circuit de validation, dans ses décisions.** RG-07 et RG-09 à RG-13 sont
  formulées sur l'état, jamais sur sa durée. `SeparationTachesService` (RG-12) ne
  contient aucune occurrence de période. **Deux nuances, contre l'affirmation
  initiale du guide** : `AiguillageService` lit bien le type de processus — un
  état `COMPLEMENTAIRE` monte au Directeur Réseau sans que le seuil soit lu
  (Sprint 6bis.1) —, mais `NORMAL`/`COMPLEMENTAIRE` n'est pas une période et
  cette lecture est indifférente au rythme ; et `ValidationService` /
  `RetourService` lisent bien mois et année, pour composer un libellé de message
  — classé en rupture mineure ci-dessus, pas ici.
- **Le frontend existant.** `frontend/src` ne contient aucune occurrence de
  `mois`, `periode` ou `annee`. Le socle du Sprint 0.3 est indemne ; l'incidence
  « puis dans tout le frontend » portée au registre est **prospective**, pas un
  dégât constaté.

### Les trois questions non techniques, portées le 9 septembre 2026

Elles ne se résolvent pas en code et n'appartiennent pas à l'équipe technique.

**1. La valeur du seuil RG-08 — devant la DRH et la DFT.** Les 100 000 XAF sont
calibrés sur le cumul d'un **mois**. Appliqués tels quels à une semaine, ils
laissent le Chef d'Unité clôturer directement des dossiers qui, ramenés au mois,
valent plus de 400 000 XAF : le Directeur Réseau sortirait de fait du circuit,
sans qu'aucune erreur ne soit visible. **Ce n'est pas un réglage technique, c'est
un changement du niveau d'approbation requis pour engager la banque.** Bonne
nouvelle à dire au métier : la valeur vit dans `parametre_systeme` et se change
**sans redéploiement** (RG-08, Sprint 4.3). C'est la *décision* qui manque, pas le
moyen de l'appliquer. **À porter en premier : c'est celle qui mettra le plus de
temps à revenir.**

> **Tranchée le 9 septembre 2026 — le seuil est maintenu à 100 000 XAF.**
> Conséquence mesurée sur les 15 états en base (données de test) : **1 seul**
> dépassait le seuil en mensuel — l'état 1317, 105 000 XAF, monté au Directeur
> Réseau ; ramené à la semaine il vaut ~24 249 XAF et se clôturerait chez le
> Chef d'Unité. **Aucun** des quinze n'atteindrait le Directeur Réseau. Le seuil
> maintenu divise donc par ~4,33 la fréquence à laquelle un dossier lui parvient.
> La décision est celle du métier et est appliquée telle quelle ; elle est
> **réversible sans redéploiement**, la valeur étant relue en base à chaque
> validation et jamais mise en cache (Sprint 4.3).

**2. Le contrat Kafka — devant la DFT.** La charge publiée sur
`rations.etat.valide` porte `"periode": { "mois", "annee" }` (contrat d'API
section 7.1). Elle est consommée par le module de comptabilisation, **qui n'est
pas maintenu par cette équipe**. Toute autre forme de période est une
modification de contrat inter-applicatif : la modifier unilatéralement casserait
un flux de paiement chez quelqu'un d'autre, sans erreur visible de ce côté-ci. À
poser dans le **même échange que M-03** (seconde transmission sur une période
déjà traitée) et **D-11** (clé de partition des accusés) : ce sont les trois
questions ouvertes avec le même interlocuteur. Rappel de contexte utile à
l'échange : **7 états ont déjà été transmis** sous la forme mensuelle actuelle,
et ne sont pas rejouables.

> **Position du module arrêtée le 9 septembre 2026 : l'intervalle de dates**,
> par cohérence avec la forme retenue en interne. **Mais la question reste
> ouverte au registre**, parce que la charge est consommée par une équipe
> extérieure au projet : porter cette forme sur le fil suppose que le module de
> comptabilisation l'accepte et adapte son côté. Modifier la charge sans cet
> accord casserait un flux de paiement chez quelqu'un d'autre, sans erreur
> visible de ce côté-ci (CLAUDE.md section 15). **C'est le seul point qui
> maintient M-04 ouvert.**

**3. Le chevauchement de mois — devant le métier.** Une semaine tombe à cheval
sur deux mois : du 29 septembre au 5 octobre. Ce cas n'existe pas aujourd'hui —
mesuré : **zéro** journée concernée en base — et le module n'a **aucun moyen de
le représenter**, `fiche_journaliere` recopiant et **figeant** le mois et l'année
du processus (migration V3, Sprint 3.1). Question à poser en clair : *une semaine
à cheval appartient-elle au mois de son premier jour, à celui de son dernier, ou
la notion de mois disparaît-elle du module ?* **C'est cette
réponse qui commande la forme technique, pas l'inverse.**

> **Tranchée le 9 septembre 2026 — la question est sans objet.** Le choix de
> l'intervalle de dates (forme 2) supprime le champ à remplir : la semaine du 29
> septembre au 5 octobre est `[2026-09-29, 2026-10-05]`, sans mois
> d'appartenance à déterminer. **La notion de mois disparaît de l'identité de la
> période** — pas du module : un rapport pourra toujours porter sur une plage de
> dates couvrant un mois. Ce qui disparaît, c'est le mois comme **attribut porté
> par un état et par une fiche**.

### Forme technique de la période — TRANCHÉE le 9 septembre 2026

La réponse étant (B), la question était : par quoi remplacer le couple
`(mois_paiement, annee_paiement)` ? Deux formes ont été présentées, la seconde
recommandée. **La seconde est retenue.**

- Forme 1 — numéro de semaine ISO + année ISO (`semaine`, `annee_iso`).
- **Forme 2 — intervalle de dates (`date_debut`, `date_fin`). RETENUE.**

Décision consignée dans
`docs/decisions/2026-09-09-rythme-de-paiement-et-maille-de-la-periode.md`
section 4. Les quatre motifs qui l'ont emportée :

1. Le contrôle LIGNE_HORS_PERIODE devient « la date de la journée est-elle entre
   les deux bornes », plus simple **et** plus robuste que l'égalité mois + année
   d'aujourd'hui (`CompletudeService.dansLaPeriode`).
2. Le chevauchement de mois **disparaît en tant que problème**, au lieu d'être
   traité cas par cas en six endroits.
3. La forme survit à tout changement ultérieur de cadence — quinzaine, décade,
   mois de nouveau — **sans nouvelle migration**. Le métier vient de changer
   d'avis une fois ; rien ne dit qu'il ne le fera pas deux.
4. Elle évite les pièges de la semaine ISO : l'existence d'une **semaine 53**, et
   une année ISO qui diffère de l'année calendaire aux premiers jours de janvier.
   Elle évite aussi que la borne haute de `moisPaiement` sur
   `DeclenchementProcessusRequest` et le contrôle équivalent de
   `ConstructionChargeService` refusent une semaine 13.

### Passe documentaire à prévoir — liste préparée, rien de corrigé

Préparée pour que la correction soit une **liste** et non une chasse. Elle
s'exécute après l'arbitrage de la forme, pas avant.

**Famille 1 — ce qui devient faux.**

- `docs/resumes-sprints/sprint-3.4-consolidation-mensuelle.md` — à commencer par
  son **nom de fichier** et son titre.
- `docs/resumes-sprints/sprint-6.1-suivi-des-demandes-et-recherche-multicritere.md`
  : « ~50 unités × un état par mois = ~600/an ; 5 000 représente ~8 ans »
  (ligne 98). En hebdomadaire : ~2 600/an, **moins de deux ans**.
- `docs/resumes-sprints/sprint-4.1-domaine-du-workflow.md` : « Second
  déclenchement même unité et période » (lignes 100 et 137).
- **RG-06 elle-même**, dont le libellé porte le mot « mensuelle » (CLAUDE.md
  section 6), ainsi que US-06, US-07 et US-16.
- Le nom même de la table `processus_mensuel` et de la classe `ProcessusMensuel`.

**Famille 2 — ce qui reste vrai mais dont la justification vieillit.** Les quatre
décisions du tableau ci-dessus. **Reformulation seulement.**

**Famille 3 — artefacts physiques déjà produits.** La convention
`{annee}/{mois}/etat-rations-{unite}-{annee}{mois}-p{id}.pdf` et le libellé
`TOTAL DU MOIS` imprimé dans les documents signés. **12 fichiers existent déjà**
sous cette forme, dont 7 rattachés à des états transmis à la comptabilité. La
question n'est pas seulement de changer la convention, mais de décider **ce qu'on
fait des documents déjà archivés** — les laisser en place sous l'ancienne forme
est probablement la seule réponse acceptable, un document signé ne se
réécrivant pas.

### Ce qu'il faut pour fermer M-04

| | Condition | État au 9 septembre 2026 |
| --- | --- | --- |
| 1 | Lecture retenue du rythme de paiement | **Tranchée** — lecture (B), tout le cycle devient hebdomadaire |
| 2 | Décision sur la **valeur** du seuil RG-08 | **Tranchée** — maintenue à 100 000 XAF |
| 3 | Réponse sur le **rattachement d'une semaine à cheval** | **Sans objet** — conséquence de la condition 4 |
| 4 | Arbitrage de la **forme technique** de la période | **Tranché** — intervalle de dates (`date_debut`, `date_fin`) |
| 5 | Position de la **DFT sur le contrat Kafka** | **En attente** — seul point restant |

**M-04 se ferme sur la seule condition 5.** Les quatre autres sont acquises et
consignées dans
`docs/decisions/2026-09-09-rythme-de-paiement-et-maille-de-la-periode.md`.

Le sous-sprint 6bis.2 ne dépend pas de la condition 5 : il reprend derrière les
migrations qui portent la nouvelle maille, lesquelles peuvent être écrites sans
attendre la DFT — le contrat Kafka est un point de sortie du module, pas sa
représentation interne. La condition 5 bloque la **mise en production** de la
bascule, pas l'écriture de RG-15.
