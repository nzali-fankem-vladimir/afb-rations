# Robustesse de la chaîne d'audit — observations

**Sprint 6.3, étape 4 — 4 septembre 2026.**

Ce document consigne ce qui a été **réellement observé**, et distingue
explicitement ce qui n'a pas pu l'être. Il applique la règle posée le même jour
dans [`decisions/2026-09-04-verification-reelle-de-l-audit.md`](decisions/2026-09-04-verification-reelle-de-l-audit.md) :
un constat d'audit ne vaut que par l'observation de son effet, jamais par
l'absence de symptôme d'échec.

---

## 0. État de la chaîne au moment de ces observations

La chaîne d'audit a deux moitiés, et une seule existe :

| Moitié | État | Preuve |
| --- | --- | --- |
| Producteur (`rations-audit-commun` + 5 services) | opérationnel | 176 messages sur le topic |
| Consommateur (`service-audit`) | **inexistant** | aucun `@KafkaListener`, aucun groupe de consommateurs sur le broker |

Deux des trois scénarios du guide portent sur le consommateur. Ils sont donc
**inéprouvables en l'état**, et sont consignés comme tels plutôt que déclarés
vérifiés. Voir [`decisions/2026-09-04-service-audit-sprint-bloquant.md`](decisions/2026-09-04-service-audit-sprint-bloquant.md).

---

## 1. Broker Kafka arrêté — les opérations métier aboutissent-elles ?

**Statut : vérifié**, à trois niveaux convergents.

### 1.1 Au niveau du producteur (tests automatisés)

`PublicateurAuditKafkaTest` couvre les cinq couches de `PublicateurAuditKafka`
par huit tests, dont quatre portent directement sur ce scénario :

- *broker indisponible : l'envoi lève, l'opération métier n'en sait rien* ;
- *livraison échouée de manière asynchrone : rien ne remonte* ;
- *le producteur n'attend jamais le futur : aucun `get()`, aucun `join()`* ;
- *émission impossible : `publier()` ne remonte rien non plus*.

La borne `max.block.ms` est le point dur : sans elle, `KafkaProducer.send()`
bloque le thread appelant jusqu'à 60 secondes par défaut, et une opération
métier gèlerait une minute sur un broker éteint.

### 1.2 En conditions réelles, ce sprint

Le broker a été arrêté (`docker stop rations-kafka`) pendant que cinq services
tournaient — Identité (8081), Saisie (8082), Workflow (8084), Reporting (8085),
Transmission (8086). **Les cinq sont restés `UP`** : leur sonde
`/actuator/health` a continué de répondre `200 {"status":"UP"}` pendant tout
l'arrêt. Aucun n'a chuté, aucun n'a exigé le broker pour rester opérationnel.

Le broker a ensuite été redémarré et **le topic était intact** :

```
avant l'arrêt : rations.audit.evenement:0:176
après le redémarrage : rations.audit.evenement:0:176
```

### 1.3 Observation antérieure, en production locale

Le Sprint 5.1 avait déjà constaté le comportement sur une opération métier
réelle : trois états ont été clôturés broker éteint, la clôture a abouti dans
les trois cas, et seules les traces d'audit ont été perdues (`docs/points-en-attente.md`,
section « trois filets, mais deux seulement quand c'est Kafka qui tombe »).

### 1.4 Ce que ce scénario coûte, et qui est assumé

L'opération métier aboutit, **et sa trace disparaît**. Le journal applicatif
porte alors un `WARN AUDIT PERDU`, et rien d'autre. C'est le risque résiduel
accepté au Sprint 1.3 : une trace manquante est plus difficile à détecter qu'une
trace fausse. La réponse de fond reste l'outbox transactionnel, ouverte depuis
le Sprint 1.3 (`docs/publication-audit.md` §5, `docs/points-en-attente.md`).

---

## 2. Service Audit arrêté, broker disponible — les traces sont-elles rattrapées au redémarrage ?

**Statut : INÉPROUVABLE. Non vérifié, et déclaré non vérifié.**

Ce scénario suppose un consommateur qui s'arrête puis reprend au dernier offset
acquitté. Il n'existe aucun consommateur : `service-audit/src` ne contient aucun
`@KafkaListener`, et `service-audit/pom.xml` ne déclare pas `spring-kafka`. Le
seul groupe de consommateurs enregistré sur le broker est
`rations-transmission-accuse`, qui écoute `rations.etat.accuse`.

Le guide qualifie ce point de « contrepartie du choix asynchrone : il faut
prouver qu'une panne du service Audit diffère les traces sans les perdre ». La
preuve ne peut pas être produite ici.

### Ce qui est en place et jouera en sa faveur

- Le topic porte une rétention **explicite de 7 jours**
  (`retention.ms=604800000`, posée au Sprint 0.5), précisément dimensionnée pour
  qu'une panne prolongée du service Audit ne perde rien.
- La clé de partition (`entiteCible:idEntite`) garantit que les événements d'une
  même entité restent ordonnés côté consommateur.

### Un fait favorable, mesuré, et à ne pas prendre pour une garantie

L'offset le plus ancien du topic est encore **0** : les 176 événements sont
intégralement présents, y compris ceux du 27 août — plus vieux que la rétention.
Kafka purge par segment entier et ne supprime jamais le segment actif ; à ce
volume, tout y tient encore.

C'est une fenêtre, pas une garantie. Au premier basculement de segment, les plus
anciens deviennent éligibles à la suppression. **Le sprint de construction du
service Audit doit donc reprendre le topic depuis l'offset 0**, et le faire tant
que cette fenêtre est ouverte.

---

## 3. Événement mal formé reçu — le consommateur tombe-t-il ?

**Statut : INÉPROUVABLE côté service Audit. Précédent applicable disponible.**

Aucun consommateur d'audit n'existe. En revanche, le module a déjà tranché ce
problème exact au Sprint 5.2, sur l'autre consommateur, et la solution est
directement transposable — elle doit être reprise, pas réinventée :

> Désérialisation **en chaîne** (`StringDeserializer`), jamais `JsonDeserializer` :
> ce dernier convertit *avant* que le code d'écoute ne soit appelé. Un message
> malformé échouerait donc **dans le conteneur Kafka, hors de portée de toute
> capture**, et serait rejoué sans fin en bloquant tous les messages suivants,
> valides compris.

`AccuseComptableConsumerTest` (9 tests) et le test de câblage (8 tests)
verrouillent ce comportement côté Transmission. Le consommateur d'audit devra
suivre la même discipline, avec en plus la contrainte du *tolerant reader* :
`service-audit` ne dépend pas de `rations-audit-commun` et lit le topic avec son
propre type, pour que le schéma puisse évoluer sans pas cadencé à sept services.

### Un piège à ne pas retrouver

Le Sprint 5.2 a découvert, en démarrant le service et non en exécutant des
tests, que **`@EnableKafka` manquait** : le service démarrait normalement, tous
ses beans existaient, et aucun consommateur ne s'abonnait — pas une ligne de
journal, pas une erreur. Le projet déclare `spring-kafka` sans le starter, donc
l'auto-configuration qui poserait cette annotation n'est pas entraînée. Le
consommateur d'audit sera le **deuxième** `@KafkaListener` du module et tombera
dans le même piège s'il l'oublie. La vérification se fait par le
`KafkaListenerEndpointRegistry`, pas par la présence des beans.

---

## 4. Récapitulatif

| Scénario | Statut | Fondement |
| --- | --- | --- |
| 1. Broker arrêté, opérations métier aboutissent | **Vérifié** | 8 tests producteur + 5 services restés `UP` broker arrêté + observation réelle du Sprint 5.1 |
| 2. Service Audit arrêté puis redémarré, aucune trace perdue | **Inéprouvable** | Aucun consommateur n'existe |
| 3. Événement mal formé, consommateur toujours actif | **Inéprouvable** | Aucun consommateur n'existe ; précédent du Sprint 5.2 à reprendre |

Les scénarios 2 et 3 sont à reprendre **tels quels** dans le sprint de
construction du service Audit, où ils deviendront des critères de validation
opposables.
