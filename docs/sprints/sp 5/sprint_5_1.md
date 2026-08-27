# SPRINT 5.1

## Producteur Kafka et charge de l'événement

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Publier l'état validé sur le topic comptable à la clôture du processus |
| **Livrable** | Producteur Kafka, construction de la charge, déclenchement depuis le service Workflow |
| **Durée** | Une à deux journées |
| **Prérequis** | Sprint 4.4 validé et commité |
| **Sprint suivant** | 5.2, consommateur de l'accusé comptable |

## 0. Héritage du Sprint 1.3 — à faire avant toute autre étape

Deux obligations transverses, décidées au Sprint 1.3 et **non facultatives**.
Elles sont inscrites ici parce qu'aucune session ne relit les documents de
décision d'elle-même : ne pas compter sur la mémoire d'une session précédente.
Voir CLAUDE.md section 17.

### 0.1 Dépendance au module de publication d'audit

Ajouter au `pom.xml` de **service-transmission** :

```xml
<dependency>
    <groupId>cm.afrilandfirstbank.rations</groupId>
    <artifactId>rations-audit-commun</artifactId>
    <version>${project.version}</version>
</dependency>
```

Puis, dans `application-dev.yml` :

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
```

Le port `PublicateurAudit` devient injectable. Ne **jamais** réécrire un
producteur d'audit local, ni surcharger les réglages du producteur du module
(`max.block.ms` en tête) : ce sont eux qui garantissent que l'audit ne fait
jamais échouer le métier. Ne **jamais** appeler le service Audit en REST
(CLAUDE.md section 15).

**Attention, propre a ce service :** il possede deja son propre `KafkaTemplate` pour `rations.etat.valide`. Celui de l'audit est un bean distinct, qualifie `kafkaTemplateAudit`. Injecter le mauvais publierait les evenements d'audit sur le mauvais topic.

### 0.2 Publication obligatoire des refus d'accès

Tout service qui consomme `GET /identite/habilitation` doit publier un
événement `ACCES_REFUSE` quand le verdict est négatif, **et** quand le service
Identité est injoignable (refus conservateur, `docs/appel-habilitation.md`).

Le service Identité ne trace pas ces refus : il répond à une question, il ne
refuse pas l'action. **Si le consommateur ne publie pas, le refus n'est tracé
nulle part** — exigence CT-04 non tenue, en silence.

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Emplacement du statut d'intégration (étapes 2-3) | Opus | Élevé |
| Construction de la charge (étapes 4-5) | Opus | Élevé |
| Publication et déclenchement (étapes 6-7) | Opus | Élevé |

Maintenir Opus effort élevé sur l'ensemble. La charge publiée part vers la comptabilité : une donnée manquante ou fausse produit un paiement erroné, et le module n'a aucun moyen de le rattraper une fois l'événement parti.

## 2. Outil de cartographie

Utilisation obligatoire en fin de sous-sprint. Le service Transmission dialogue avec le service Workflow : la cartographie doit confirmer que l'échange passe par l'API et non par la base.

```
py -3.14 -m graphify update .
```

## 3. Contexte

Premier sous-sprint du service Transmission, dernier maillon de la chaîne interne. Le circuit de validation s'achève sur une clôture ; ce service met alors l'état validé à la disposition de la comptabilité.

Le point de périmètre est net et a été posé dès la phase documentaire : **le module ne produit aucune écriture comptable.** Il publie les données de l'état validé ; le module de comptabilisation, auquel l'équipe n'a pas accès, fabrique les écritures et gère l'impact CBS. Le schéma comptable figurant en annexe des spécifications est informatif : il ne se code pas ici.

La charge publiée est décrite au contrat d'API, section 7.1. Elle contient notamment le **code agence** de chaque bénéficiaire, qui alimentera la ligne de crédit, et le **code unité** du processus, qui alimentera la ligne de débit. Ces deux codes partagent le même format et le même référentiel, mais désignent des choses différentes : les confondre fausserait le sens comptable de l'opération.

## 4. Objectifs

- Emplacement du statut d'intégration arbitré et implémenté
- Service de construction de la charge, conforme au contrat d'API
- Producteur Kafka publiant sur le topic de l'état validé
- Déclenchement depuis le service Workflow à la clôture
- Tests couvrant la complétude de la charge et la publication

## 5. Règles et stories concernées

| Référence | Objet |
|---|---|
| RG-13 | Transmission unique, dont le contrôle est le sous-sprint 5.3 |
| US-12 | Clôture et mise à disposition pour la comptabilité |
| CT-21 | Détail transmis : bénéficiaires, natures, sessions, montants, comptes courants, code agence, période, unité, code unité |

## 6. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis le fichier CLAUDE.md a la racine, sections 8 et 9.
Confirme en 3 lignes le perimetre du module vis-a-vis de la
comptabilite et le role du service Transmission sur Kafka.

CONTEXTE DE CETTE SESSION : Sprint 5.1, producteur Kafka. Le circuit
de validation s'acheve sur une cloture depuis le Sprint 4.4. On
publie maintenant l'etat valide vers la comptabilite.

Rappel de perimetre : ce module ne produit AUCUNE ecriture comptable.
Il publie les donnees de l'etat valide. Si tu es tente de creer une
classe d'ecriture, un schema debit-credit ou un appel au CBS, tu
t'arretes et tu me le signales.
SERVICE CONCERNE : service-transmission, avec appel au service
Workflow.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Chaque methode publique vient avec ses tests.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : un ecart entre deux documents doit etre arbitre
avant tout code. Le contrat d'api prevoit un statut d'integration
comptable a trois valeurs et une reference comptable, exposes par
GET /transmission/processus/{id}. Or le dictionnaire de donnees ne
prevoit qu'un booleen transmis_comptabilite sur processus_mensuel, et
le service Transmission n'a pas de base propre.

Presente-moi les options pour resoudre cet ecart, avec leurs
consequences sur l'isolation des services et sur les migrations
existantes. Ne code rien, attends ma decision.
```

### Étape 2. Emplacement du statut d'intégration

```
Une fois l'option retenue, implemente-la :

- Si elle suppose des colonnes supplementaires sur processus_mensuel,
  ecris la migration Flyway correspondante dans service-workflow, en
  version V3, sans toucher aux migrations existantes.
- Si elle suppose une base propre au service Transmission, ecris la
  migration et signale-moi que la repartition des tables consignee
  dans CLAUDE.md doit etre mise a jour.

Cree l'enumeration du statut d'integration, avec les trois valeurs du
contrat d'api : en attente, integre, rejete.

Montre la migration puis l'enumeration.
```

### Étape 3. Origine des données de la charge

```
La charge publiee contient le detail des lignes, qui vit dans la base
du service Saisie, et les donnees de periode et d'unite, qui vivent
dans celle du service Workflow.

Question a trancher : le service Transmission obtient-il ces donnees
en appelant lui-meme le service Saisie, ou le service Workflow les
lui transmet-il dans sa demande de transmission ?

La premiere option multiplie les appels mais garde le service
Workflow simple. La seconde reduit les appels mais fait transiter une
charge volumineuse entre deux services. Precise aussi laquelle
resiste le mieux a une indisponibilite du service Saisie au moment de
la cloture.

Presente les deux, attends ma decision.
```

### Étape 4. Construction de la charge

```
Cree le service de construction de la charge, strictement conforme au
contrat d'api section 7.1 :

Racine : idProcessus, periode avec mois et annee, codeUnite,
typeProcessus, montantTotal.
Pour chaque ligne : nom, prenom, numCompteCourant, codeAgence,
nature, session, montant.

Attention a la distinction des deux codes :
- codeUnite est au niveau de la racine : c'est l'unite qui supporte
  la charge.
- codeAgence est au niveau de chaque ligne : c'est l'agence de
  domiciliation du compte du beneficiaire.

Ne les inverse pas et n'en omets aucun : la comptabilite ne peut pas
reconstituer l'ecriture sans les deux.

Ajoute un controle de completude de la charge avant publication :
aucun champ obligatoire vide, aucune ligne sans montant, montant
total egal a la somme des lignes. En cas d'echec, la publication
n'a pas lieu et l'anomalie est signalee.

Montre le service puis ses tests.
```

### Étape 5. Contrôle de cohérence de la charge

```
Ecris le test qui verifie que le montant total de la racine est bien
egal a la somme des montants de lignes, sur un jeu de donnees couvrant
plusieurs journees et plusieurs beneficiaires.

Ce controle est le dernier filet avant la comptabilite : une fois
l'evenement publie, le module n'a aucun moyen de le rattraper.

Ajoute aussi un test verifiant qu'aucune ligne ne part sans code
agence, et qu'aucune charge ne part sans code unite.

Montre le fichier de test.
```

### Étape 6. Producteur Kafka

```
Cree le producteur Kafka :

1. Configuration du producteur dans application.yml, brokers lus
   depuis une variable d'environnement.
2. Service de publication sur le topic de l'etat valide, dont le nom
   est celui arrete au Sprint 0.5 et rappele au contrat d'api.
3. Serialisation JSON de la charge.

Deux questions a trancher :
- Quelle cle de partition utiliser ? L'identifiant du processus
  garantit l'ordre des messages relatifs a un meme etat.
- Quel niveau d'acquittement exiger du broker ? Un acquittement
  faible ameliore la latence mais peut perdre un message, ce qui
  signifierait un etat cloture jamais paye.

Presente les options, attends ma decision. Montre ensuite le service.
```

### Étape 7. Déclenchement depuis le Workflow

```
Branche la transmission sur la cloture du processus, aux deux points
ou elle survient : validation du Chef d'Unite sous le seuil, et
validation du Directeur Reseau.

Le service Workflow appelle le service Transmission par son api,
conformement au diagramme AR04. Isole cet appel derriere une
interface, comme les clients precedents.

Question importante : que se passe-t-il si la transmission echoue
alors que la cloture a reussi ? L'etat serait cloture mais jamais
transmis, donc jamais paye, sans que personne ne s'en apercoive.
Presente-moi les options : echec de la cloture entiere, cloture
maintenue avec reprise ulterieure, autre mecanisme.

Ne code pas avant ma decision.
```

## 7. Fichiers à créer

| Chemin | Nature |
|---|---|
| `service-workflow/.../db/migration/V3__statut_integration.sql` | Migration, selon l'arbitrage |
| `service-transmission/.../domaine/StatutIntegrationEnum.java` | Énumération |
| `service-transmission/.../domaine/EtatValideEvent.java` | Charge de l'événement |
| `service-transmission/.../application/ConstructionChargeService.java` | Construction et contrôle |
| `service-transmission/.../infrastructure/EtatValideProducer.java` | Producteur Kafka |
| `service-transmission/.../api/TransmissionController.java` | Endpoint de déclenchement interne |
| `service-workflow/.../application/TransmissionClient.java` | Interface d'appel |
| `service-workflow/.../infrastructure/TransmissionHttpClient.java` | Implémentation |
| `service-transmission/src/test/...` | Tests |

## 8. Commandes terminal

Kafka, Workflow et Transmission doivent tourner.

```bash
cd afb-rations/backend

mvn -pl service-transmission test
mvn -pl service-transmission spring-boot:run
```

Observation du topic pendant un test de clôture :

```bash
docker exec -it kafka-rations /opt/kafka/bin/kafka-console-consumer.sh \
  --topic rations.etat.valide \
  --from-beginning \
  --bootstrap-server localhost:9092
```

Déclencher ensuite une clôture depuis le service Workflow et observer le message publié.

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| `mvn -pl service-transmission test` | BUILD SUCCESS |
| Charge conforme au contrat d'API | Champ par champ |
| Code unité présent à la racine | Vérifié |
| Code agence présent sur chaque ligne | Vérifié |
| Les deux codes non inversés | Vérifié |
| Montant total égal à la somme des lignes | Vérifié |
| Charge incomplète | Publication empêchée, anomalie signalée |
| Message observé sur le topic | Contenu identique à la charge construite |
| Clôture sous le seuil | Publication déclenchée |
| Clôture après validation DR | Publication déclenchée |
| Aucune classe d'écriture comptable | Vérifié |
| Aucun accès à la base d'un autre service | Vérifié |

## 10. Points de vigilance

- **Aucune écriture comptable dans ce module.** Si l'assistant propose une classe d'écriture, un schéma débit-crédit ou un appel au CBS, arrêter la session. C'est l'un des écarts interdits du CLAUDE.md.
- Le code unité et le code agence ne se trouvent pas au même niveau de la charge : l'un est à la racine, l'autre sur chaque ligne. Les inverser ou en omettre un rendrait l'écriture irréconstituable côté comptabilité.
- Une fois l'événement publié, il n'y a pas de retour en arrière. Le contrôle de complétude et de cohérence de la charge est le dernier filet.
- L'échec de transmission après une clôture réussie est le scénario le plus dangereux du sprint : un état clôturé, donc figé, mais jamais transmis, donc jamais payé, et rien ne le signale. La décision de l'étape 7 doit traiter ce cas explicitement.
- Le niveau d'acquittement du producteur engage la fiabilité. Un message perdu équivaut à un paiement non effectué.
- Ne pas implémenter le consommateur ni le contrôle d'unicité ici : ce sont les sous-sprints 5.2 et 5.3.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Écart entre contrat d'API et dictionnaire arbitré | Fait |
| Origine des données de la charge tranchée | Fait |
| Charge conforme au contrat, deux codes bien placés | Vérifié |
| Contrôle de cohérence du montant total | Vérifié |
| Clé de partition et acquittement arbitrés | Fait |
| Publication observée sur le topic | Vérifié |
| Déclenchement aux deux points de clôture | Vérifié |
| Comportement en cas d'échec après clôture tranché | Fait |
| Aucune écriture comptable produite | Vérifié |
| Aucun accès direct entre bases | Vérifié |

## 12. Commit

```bash
git add .
git commit -m "sprint-5.1: producteur kafka et charge de l'evenement

- Charge conforme au contrat d'api, code agence et code unite distincts
- Controle de completude et de coherence avant publication
- Publication sur le topic de l'etat valide
- Declenchement aux deux points de cloture du circuit

Refs: US-12, CT-21, contrat d'api section 7.1"
```

---

**Fin du Sprint 5.1** — en attente de validation avant le Sprint 5.2
