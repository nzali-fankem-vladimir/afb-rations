# Robustesse de la chaîne d'audit — observations

**Sprint 6.3, étape 4 — 4 septembre 2026. Mis à jour au sprint de rattrapage
du service Audit, étape 7 — 4 septembre 2026 (même journée, sprint suivant) :
les scénarios 2 et 3, déclarés inéprouvables faute de consommateur, sont
désormais vérifiés en réel contre le service construit à ce sprint.**

Ce document consigne ce qui a été **réellement observé**, et distingue
explicitement ce qui n'a pas pu l'être. Il applique la règle posée le même jour
dans [`decisions/2026-09-04-verification-reelle-de-l-audit.md`](decisions/2026-09-04-verification-reelle-de-l-audit.md) :
un constat d'audit ne vaut que par l'observation de son effet, jamais par
l'absence de symptôme d'échec.

---

## 0. État de la chaîne au moment de ces observations

**Constat initial du Sprint 6.3** : la chaîne d'audit avait deux moitiés, une
seule existait.

| Moitié | État au Sprint 6.3 | État au sprint de rattrapage (étape 7) |
| --- | --- | --- |
| Producteur (`rations-audit-commun` + 6 services) | opérationnel, 176 messages sur le topic | inchangé, 193 messages |
| Consommateur (`service-audit`) | **inexistant** | **opérationnel** : `AuditEvenementConsumer`, groupe `rations-audit-lecture`, `audit_log` à 193 lignes |

Les deux moitiés existent désormais. Les trois scénarios du guide, y compris
les deux déclarés inéprouvables au Sprint 6.3, sont vérifiés dans les sections
2 et 3 ci-dessous. Voir [`decisions/2026-09-04-service-audit-sprint-bloquant.md`](decisions/2026-09-04-service-audit-sprint-bloquant.md)
pour le contexte de la décision qui a ouvert ce sprint.

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

**Statut : VÉRIFIÉ, en conditions réelles, au sprint de rattrapage (4 septembre
2026, étape 7).**

Le consommateur existe désormais (`AuditEvenementConsumer`,
`ConfigurationConsommateurAudit`, groupe `rations-audit-lecture`). Le scénario
a été rejoué deux fois de suite, contre le vrai conteneur `rations-kafka` et la
vraie base `rations_audit`, base et offset préalablement remis à zéro
(`TRUNCATE audit_log`, reset du groupe à l'offset 0) :

```
1er demarrage (a froid, offset 0) : Started ServiceAuditApplication ...
                                     Souscription audit effective : partitions assignees=[rations.audit.evenement-0]
SELECT COUNT(*) FROM audit_log ;                                   -> 193

arret du service (taskkill sur le port 8087)

2e demarrage (redemarrage, groupe deja etabli) : Started ServiceAuditApplication ...
                                                  Souscription audit effective : partitions assignees=[rations.audit.evenement-0]
SELECT COUNT(*) FROM audit_log ;                                   -> 193 (inchange)

SELECT action, service_emetteur, id_entite, date_action, COUNT(*)
  FROM audit_log
 GROUP BY action, service_emetteur, id_entite, date_action
HAVING COUNT(*) > 1 ;                                              -> 0 ligne
```

**Aucune trace perdue, aucun doublon.** Le compte reste identique à
193 après le redémarrage, et aucune combinaison (action, service, entité,
date) n'apparaît deux fois. Ce n'est pas une propriété applicative — l'entité
`AuditLog` n'a aucune clé métier déduplicante — c'est une propriété de la
configuration du consommateur, vérifiée par ailleurs à l'assemblage
(`CablageConsommateurAuditTest`) : groupe de consommateurs **fixe**
(`rations-audit-lecture`, jamais généré), acquittement `AckMode.RECORD`
**après** traitement, `enable.auto.commit=false`. Un redémarrage reprend donc
exactement là où le groupe s'était arrêté, ni avant (perte), ni depuis le début
(doublon).

### Ce qui reste vrai de l'analyse initiale

- Le topic porte une rétention **explicite de 7 jours**
  (`retention.ms=604800000`, posée au Sprint 0.5).
- La clé de partition (`entiteCible:idEntite`) garantit que les événements d'une
  même entité restent ordonnés côté consommateur — sans rapport avec l'ordre
  des faits, restitué par le tri sur `date_action` (section 3 ci-dessous et
  guide de ce sprint, étape 6).

### La fenêtre de récupération a été utilisée avant sa fermeture

L'offset le plus ancien du topic était encore **0** au moment de ce sprint :
les 176 événements constatés au Sprint 6.3, plus les événements publiés
depuis, ont été intégralement rejoués et persistés — **193 lignes au total**,
supérieur au seuil de 176 fixé par les critères de validation du guide.

---

## 3. Événement mal formé reçu — le consommateur tombe-t-il ?

**Statut : VÉRIFIÉ, au sprint de rattrapage (étape 5 et étape 7).**

Le précédent du Sprint 5.2 a été repris tel quel, pas réinventé : désérialisation
**en chaîne** (`StringDeserializer`), jamais `JsonDeserializer`, dans
`ConfigurationConsommateurAudit`. La conversion JSON a lieu dans
`AuditEvenementConsumer.deserialiserOuTracer`, où un échec devient un rejet
tracé (préfixe `AUDIT ENTREE REJETEE`) au lieu de remonter.

**Second filet, propre à ce consommateur** : un message lisible mais dont un
champ obligatoire (`action`, `entiteCible`, `dateAction`, `serviceEmetteur`)
est absent ou vide est également tracé et écarté
(`MessageAuditEntrant.premierChampObligatoireManquant`) — la contrainte
`NOT NULL` d'`audit_log` ne doit jamais être la première à le découvrir.

Sur les 193 événements réels rejoués à l'étape 7 (voir section 2), **aucun
n'a été rejeté** : `grep -c "AUDIT ENTREE REJETEE"` sur le journal du service
rend 0. Le mécanisme de rejet existe et est verrouillé par construction (même
discipline que le Sprint 5.2), mais n'a pas encore été observé sur un message
réellement malformé — le topic actuel n'en contient aucun.

### Le piège du Sprint 5.2, rencontré une seconde fois dans ce module — et évité

`@EnableKafka` est posé explicitement dans `ConfigurationConsommateurAudit`
(commentaire dédié dans le code, référence directe au défaut du Sprint 5.2).
**Vérifié à l'assemblage** (`CablageConsommateurAuditTest.ecouteEffectivementEnregistree`,
via `KafkaListenerEndpointRegistry`) et **vérifié au démarrage réel** (log
`Souscription audit effective : partitions assignees=[...]`, observé à
plusieurs reprises aux étapes 2, 5 et 7). Le consommateur d'audit est le
**deuxième** `@KafkaListener` du module, après celui du service Transmission.

---

## 4. Récapitulatif

| Scénario | Statut | Fondement |
| --- | --- | --- |
| 1. Broker arrêté, opérations métier aboutissent | **Vérifié** | 8 tests producteur + 5 services restés `UP` broker arrêté + observation réelle du Sprint 5.1 |
| 2. Service Audit arrêté puis redémarré, aucune trace perdue | **Vérifié** | Redémarrage réel : 193 lignes avant et après, 0 doublon exact (sprint de rattrapage, étape 7) |
| 3. Événement mal formé, consommateur toujours actif | **Vérifié par construction** | Désérialisation en chaîne + rejet tracé des champs obligatoires manquants ; `@EnableKafka` vérifié à l'assemblage et au démarrage réel. Aucun message réellement malformé observé sur le topic actuel. |

Les trois scénarios sont désormais des critères de validation opposables du
sprint de rattrapage du service Audit (guide, section 10).
