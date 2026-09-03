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
