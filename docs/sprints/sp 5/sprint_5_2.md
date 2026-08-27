# SPRINT 5.2

## Consommateur de l'accusé comptable

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Consommer l'accusé de prise en charge renvoyé par la comptabilité et remonter le statut d'intégration |
| **Livrable** | Consommateur Kafka, mise à jour du statut d'intégration, gestion des cas d'erreur |
| **Durée** | Une journée |
| **Prérequis** | Sprint 5.1 validé et commité |
| **Sprint suivant** | 5.3, unicité de transmission et clôture du sprint |

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Consommateur et désérialisation (étapes 2-3) | Opus | Élevé |
| Mise à jour du statut et cas d'erreur (étapes 4-6) | Opus | Élevé |

Maintenir Opus effort élevé. Un consommateur mal conçu traite deux fois le même message, ou perd un accusé sans que rien ne le signale.

## 2. Outil de cartographie

Facultatif ici. Le périmètre reste circonscrit au service Transmission et à son appel déjà établi vers le Workflow.

## 3. Contexte

Le module publie l'état validé. Ce sous-sprint construit la seconde moitié de l'échange : **le module est producteur et consommateur.**

C'est un point qui a été explicitement corrigé pendant la phase documentaire, après une première version qui ne prévoyait qu'une publication. Le module de comptabilisation renvoie un accusé de prise en charge sur un second topic, que le service consomme pour remonter le statut d'intégration jusque dans le suivi. Sans ce consommateur, un état apparaîtrait indéfiniment comme transmis sans qu'on sache s'il a été traité, rejeté, ou perdu.

La charge de l'accusé est décrite au contrat d'API, section 7.2 : identifiant du processus, statut d'intégration, référence comptable et date de traitement. En cas de rejet, un motif accompagne l'accusé.

## 4. Objectifs

- Consommateur Kafka écoutant le topic de l'accusé comptable
- Désérialisation et validation de la charge reçue
- Mise à jour du statut d'intégration du processus concerné
- Traitement des accusés de rejet, avec conservation du motif
- Gestion des messages inexploitables et des accusés en double

## 5. Règles et stories concernées

| Référence | Objet |
|---|---|
| US-12 | Clôture et mise à disposition, dont le suivi du traitement aval |
| US-15 | Suivi du statut des demandes, alimenté par ce statut d'intégration |
| CLAUDE.md section 9 | Le module est producteur et consommateur |

## 6. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis le fichier CLAUDE.md a la racine, section 9. Confirme
en 3 lignes le role du module sur Kafka et le contenu de l'accuse
comptable.

CONTEXTE DE CETTE SESSION : Sprint 5.2, consommateur de l'accuse. La
publication existe depuis le Sprint 5.1. On construit maintenant la
reception de l'accuse de prise en charge renvoye par la comptabilite.

Rappel : le module est producteur ET consommateur. Sans ce
consommateur, le statut d'integration ne remonte jamais et le suivi
reste aveugle sur le sort des etats transmis.
SERVICE CONCERNE : service-transmission, avec remontee vers le
service Workflow.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Chaque methode publique vient avec ses tests.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : avant tout code, enumere avec moi les situations
anormales qu'un consommateur doit traiter, et ce que le module doit
faire dans chacune : message illisible, accuse pour un processus
inconnu, accuse pour un processus non transmis, accuse recu deux fois
pour le meme processus, accuse contredisant un statut deja recu.
Propose un traitement pour chacune, j'arbitre.
```

### Étape 2. Charge de l'accusé

```
Cree la classe representant l'accuse recu, conforme au contrat d'api
section 7.2 : idProcessus, statutIntegration, referenceComptable,
dateTraitement, et le motif present en cas de rejet.

Ajoute la validation de la charge recue : identifiant present, statut
appartenant a l'enumeration du Sprint 5.1, motif present quand le
statut vaut rejete.

Un message qui ne passe pas cette validation ne doit pas faire tomber
le consommateur. Montre la classe et sa validation.
```

### Étape 3. Consommateur Kafka

```
Cree le consommateur :

1. Configuration dans application.yml : brokers, identifiant de
   groupe, deserialisation JSON, tous lus depuis des variables
   d'environnement.
2. Ecoute du topic de l'accuse comptable, dont le nom est celui
   arrete au Sprint 0.5.
3. Appel du service de traitement pour chaque message recu.

Deux questions a trancher :
- A quel moment le message est-il acquitte ? Un acquittement avant
  traitement perd le message si le traitement echoue ; un
  acquittement apres traitement peut provoquer un retraitement en cas
  de panne.
- Combien d'instances du service peuvent consommer en parallele sans
  risque de traitement concurrent du meme processus ?

Presente les options, attends ma decision. Montre ensuite le
consommateur.
```

### Étape 4. Traitement de l'accusé

```
Cree le service de traitement :

1. Valide la charge recue.
2. Verifie que le processus existe et qu'il a bien ete transmis. Un
   accuse pour un processus jamais transmis est une anomalie : traite
   selon la decision de l'etape 1.
3. Met a jour le statut d'integration, la reference comptable et la
   date de traitement, selon l'emplacement arbitre au Sprint 5.1.
4. En cas de rejet, conserve le motif et rend le statut visible dans
   le suivi.
5. Trace le traitement dans le journal d'audit.

La mise a jour du processus se fait par appel a l'api du service
Workflow, jamais par acces direct a sa base, conformement au
diagramme AR04.

Montre le service.
```

### Étape 5. Idempotence

```
Un accuse peut arriver deux fois : reessai du module comptable,
retraitement apres panne, ou rejeu manuel.

Rends le traitement idempotent : recevoir deux fois le meme accuse
pour le meme processus doit produire le meme resultat qu'une seule
reception, sans erreur ni double ecriture d'audit.

Traite aussi le cas d'un accuse contredisant un statut deja recu,
selon la decision de l'etape 1 : un processus deja marque integre qui
recevrait ensuite un rejet, par exemple.

Montre le code et explique-moi comment tu garantis l'idempotence.
```

### Étape 6. Tests

```
Ecris les tests de ce sous-sprint.

Traitement nominal :
1. accuse d'integration : statut mis a jour, reference comptable
   enregistree, date de traitement conservee
2. accuse de rejet avec motif : statut rejete, motif conserve et
   visible

Cas anormaux, chacun conforme a la decision de l'etape 1 :
3. message illisible : le consommateur ne tombe pas, l'anomalie est
   tracee
4. accuse pour un processus inconnu
5. accuse pour un processus jamais transmis
6. accuse de rejet sans motif : refuse
7. statut d'integration non reconnu : refuse

Idempotence :
8. meme accuse recu deux fois : resultat identique, une seule trace
   d'audit
9. accuse contredisant un statut deja recu : comportement conforme a
   la decision

Bout en bout, avec un Kafka de test :
10. publication d'un accuse sur le topic, verification que le statut
    remonte jusqu'au processus

Le test 8 est le plus important : sans idempotence, un rejeu de
topic corromprait les statuts de tous les processus concernes.

Montre les fichiers de test.
```

## 7. Fichiers à créer

| Chemin | Nature |
|---|---|
| `service-transmission/.../domaine/AccuseComptableEvent.java` | Charge reçue |
| `service-transmission/.../infrastructure/AccuseComptableConsumer.java` | Consommateur Kafka |
| `service-transmission/.../application/TraitementAccuseService.java` | Traitement et idempotence |
| `service-transmission/.../application/StatutIntegrationClient.java` | Interface de remontée |
| `service-transmission/.../infrastructure/StatutIntegrationHttpClient.java` | Implémentation |
| `service-workflow/.../api/ProcessusController.java` | Endpoint interne de mise à jour du statut |
| `service-transmission/src/test/...` | Tests |

## 8. Commandes terminal

```bash
cd afb-rations/backend

mvn -pl service-transmission test
mvn -pl service-transmission spring-boot:run
```

Simulation d'un accusé, en jouant le rôle du module comptable :

```bash
docker exec -it kafka-rations /opt/kafka/bin/kafka-console-producer.sh \
  --topic rations.etat.accuse \
  --bootstrap-server localhost:9092
```

Puis coller la charge :

```json
{"idProcessus":1,"statutIntegration":"INTEGRE","referenceComptable":"CPT-2026-08-000512","dateTraitement":"2026-08-19T02:15:00Z"}
```

Contrôle de la remontée :

```sql
\c rations_workflow
SELECT id, statut, transmis_comptabilite FROM processus_mensuel WHERE id = 1;
```

Le contrôle exact dépend de l'emplacement du statut arbitré au Sprint 5.1.

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| `mvn -pl service-transmission test` | BUILD SUCCESS |
| Dix tests du sous-sprint | Tous passants |
| Accusé d'intégration | Statut et référence enregistrés |
| Accusé de rejet | Motif conservé et visible |
| Message illisible | Consommateur toujours actif, anomalie tracée |
| Accusé pour processus inconnu | Comportement conforme à la décision |
| Même accusé reçu deux fois | Résultat identique, une seule trace |
| Accusé publié manuellement | Statut remonté jusqu'au processus |
| Mise à jour par appel d'API | Aucun accès direct à la base du Workflow |

## 10. Points de vigilance

- **Un consommateur ne doit jamais tomber sur un message illisible.** Un message mal formé bloquerait la consommation de tous les suivants, y compris les accusés valides.
- **L'idempotence n'est pas optionnelle.** Un rejeu de topic, opération courante en exploitation, retraiterait tous les accusés. Sans idempotence, les statuts et le journal d'audit seraient corrompus.
- Le moment de l'acquittement détermine si le système perd des messages ou en retraite. Les deux comportements sont acceptables si l'idempotence est garantie ; sans elle, seul l'acquittement après traitement est envisageable, et encore avec précaution.
- Un accusé pour un processus jamais transmis signale une incohérence sérieuse, côté module ou côté comptabilité. L'ignorer silencieusement priverait d'un signal utile.
- La mise à jour du processus passe par l'API du service Workflow. Une écriture directe dans sa base serait une faute d'architecture, et le contrôle de cartographie du sous-sprint 5.3 la détecterait.
- Ne pas implémenter le contrôle d'unicité de transmission ici : c'est RG-13, au sous-sprint 5.3.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Cinq situations anormales énumérées et arbitrées | Fait |
| Charge de l'accusé conforme au contrat d'API | Vérifié |
| Consommateur résistant aux messages illisibles | Vérifié |
| Décisions sur l'acquittement et le parallélisme tranchées | Fait |
| Statut d'intégration remonté au processus | Vérifié |
| Motif de rejet conservé et visible | Vérifié |
| Traitement idempotent, prouvé par test | Vérifié |
| Remontée par appel d'API uniquement | Vérifié |
| Dix tests passants | Vérifié |

## 12. Commit

```bash
git add .
git commit -m "sprint-5.2: consommateur de l'accuse comptable

- Ecoute du topic d'accuse et validation de la charge recue
- Remontee du statut d'integration au processus par appel d'api
- Traitement idempotent, resistant au rejeu et aux messages illisibles
- Conservation du motif en cas de rejet comptable

Refs: US-12, US-15, contrat d'api section 7.2"
```

---

**Fin du Sprint 5.2** — en attente de validation avant le Sprint 5.3
