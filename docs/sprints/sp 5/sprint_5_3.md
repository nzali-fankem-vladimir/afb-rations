# SPRINT 5.3

## Unicité de transmission et clôture du Sprint 5

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Garantir qu'un état n'est transmis qu'une seule fois et exposer le statut d'intégration |
| **Livrable** | Contrôle RG-13, endpoint de consultation, clôture du Sprint 5 |
| **Durée** | Une journée |
| **Prérequis** | Sprint 5.2 validé et commité |
| **Sprint suivant** | 6.1, service Reporting |

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Contrôle d'unicité (étapes 2-3) | Opus | Élevé |
| Endpoint et tests (étapes 4-5) | Opus | Élevé |
| Clôture du sprint (étapes 6-7) | Sonnet | Moyen |

**Changement manuel à l'étape 6.** Une fois RG-13 implémentée et testée, revenir en Sonnet effort moyen pour la vérification et la mise à jour documentaire.

## 2. Outil de cartographie

Utilisation obligatoire à l'étape 6, pour la clôture du Sprint 5.

```
py -3.14 -m graphify update .
```

## 3. Contexte

Dernier sous-sprint du service Transmission. La publication et la réception fonctionnent ; il reste à poser le verrou.

**RG-13 dit qu'un état validé n'est transmis qu'une seule fois.** La conséquence d'une violation est directe : la comptabilité recevrait deux fois le même état et produirait deux jeux d'écritures, donc un double paiement des mêmes bénéficiaires. C'est l'une des dix erreurs interdites du CLAUDE.md.

Le risque n'est pas théorique. Plusieurs chemins mènent à une double transmission : un rejeu de la clôture, un appel manuel de l'endpoint interne, deux instances du service traitant la même clôture, ou une reprise après incident du Sprint 5.1. Le contrôle doit résister à tous.

Ce sous-sprint clôt le Sprint 5.

## 4. Objectifs

- Contrôle d'unicité de transmission, résistant à la concurrence
- `GET /transmission/processus/{id}` : consultation du statut d'intégration
- Vérification de bout en bout de la chaîne complète
- Clôture du Sprint 5 et mise à jour de CLAUDE.md

## 5. Règles et stories concernées

| Référence | Objet |
|---|---|
| RG-13 | Transmission unique à la comptabilité |
| US-12 | Un même état n'est transmis qu'une seule fois |
| CT-22 | Seconde transmission du même état : empêchée |

## 6. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis le fichier CLAUDE.md a la racine, section 6, regle
RG-13, et section 15 sur les erreurs interdites. Confirme en 3 lignes
ce qu'impose RG-13 et la consequence d'une violation.

CONTEXTE DE CETTE SESSION : Sprint 5.3, dernier sous-sprint du
service Transmission. La publication et la reception existent depuis
les sous-sprints 5.1 et 5.2. On pose maintenant le verrou d'unicite.
SERVICE CONCERNE : service-transmission, avec le service Workflow
pour le porteur de l'indicateur.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Chaque methode publique vient avec ses tests.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : avant tout code, enumere avec moi tous les chemins
par lesquels une seconde transmission du meme etat pourrait survenir :
rejeu de la cloture, appel manuel de l'endpoint interne, deux
instances du service traitant la meme cloture, reprise apres
incident, autre chemin que tu identifies.

Pour chacun, dis-moi si le controle que tu envisages le bloque
effectivement. Ne code rien avant que nous ayons couvert la liste.
```

### Étape 2. Contrôle d'unicité

```
Une fois les chemins couverts, implemente le controle :

1. Avant toute publication, verifier l'indicateur de transmission du
   processus, a l'emplacement arbitre au Sprint 5.1.
2. Si l'etat a deja ete transmis, ne pas publier et retourner une
   reponse explicite, distincte d'une erreur technique. Une seconde
   demande n'est pas forcement une anomalie : elle peut venir d'un
   rejeu legitime.
3. Si l'etat n'a pas ete transmis, publier puis positionner
   l'indicateur.

L'ordre entre publication et positionnement de l'indicateur pose une
question que je veux que tu me presentes : positionner avant publier
risque de marquer transmis un etat dont la publication echoue ;
publier avant positionner risque de publier deux fois si le
positionnement echoue. Quelle option retiens-tu, et comment limites-tu
le risque residuel ?
```

### Étape 3. Résistance à la concurrence

```
Deux instances du service peuvent traiter la meme cloture
simultanement. Un controle en deux temps, lecture puis ecriture, ne
suffit pas : les deux instances liraient un indicateur a faux avant
que l'une ait pu le positionner.

Propose un mecanisme qui resiste a ce cas. Verrou optimiste sur le
processus, mise a jour conditionnelle en base, ou autre solution que
tu identifies.

Montre-moi le mecanisme retenu et le test qui le prouve, avant de
generaliser.
```

### Étape 4. Consultation du statut

```
Cree l'endpoint GET /transmission/processus/{id}, conforme au contrat
d'api section 7 :

Il retourne le statut d'integration, la reference comptable et la
date de traitement quand ils existent, ainsi que le motif en cas de
rejet.

Accessible aux roles ARH et aux acteurs du circuit, avec verification
de la portee d'acces.

Cas a traiter : processus non encore transmis, processus transmis
sans accuse recu, processus integre, processus rejete. Chacun doit
produire une reponse comprehensible, jamais un champ vide sans
explication.

Montre le service puis le controleur.
```

### Étape 5. Tests

```
Ecris les tests de ce sous-sprint.

Unicite :
1. premiere transmission : publication effectuee, indicateur
   positionne
2. seconde demande sur le meme etat : aucune publication, reponse
   explicite
3. le topic ne recoit qu'un seul message apres deux demandes
4. deux demandes concurrentes sur le meme etat : un seul message
   publie
5. echec de publication : indicateur non positionne, une reprise
   reste possible

Les tests 3 et 4 sont les plus importants du sous-sprint. Le test 3
verifie le resultat observable cote comptabilite, pas seulement
l'etat interne.

Consultation :
6. processus non transmis : reponse explicite
7. processus transmis sans accuse : statut en attente
8. processus integre : reference comptable retournee
9. processus rejete : motif retourne
10. consultation hors portee : 403

Bout en bout, chaine complete :
11. declenchement, saisie, soumission, validation, cloture,
    publication observee sur le topic, accuse publie manuellement,
    statut remonte, consultation retournant integre

Le test 11 valide toute la chaine du module, du premier sprint au
cinquieme.

Montre les fichiers de test.
```

### Étape 6. Vérification et clôture

**Retour en Sonnet, effort moyen.**

```
Lance la cartographie et verifie :
- que service-transmission n'accede a la base d'aucun autre service
- qu'aucune classe d'ecriture comptable n'existe nulle part dans le
  projet
- que l'endpoint de consultation du contrat d'api existe
- qu'aucun autre endpoint n'a ete cree par anticipation

Recherche aussi dans tout le projet les termes lies aux ecritures
comptables et au CBS : ils ne doivent apparaitre que dans des
commentaires ou de la documentation, jamais dans du code actif.

Liste les ecarts sans les corriger.
```

### Étape 7. Mise à jour documentaire

```
Mets a jour CLAUDE.md avec les decisions du Sprint 5 :
1. L'emplacement retenu du statut d'integration et de la reference
   comptable (Sprint 5.1).
2. L'origine des donnees de la charge (Sprint 5.1).
3. La cle de partition et le niveau d'acquittement du producteur
   (Sprint 5.1).
4. Le comportement en cas d'echec de transmission apres cloture
   (Sprint 5.1).
5. Le traitement des cinq situations anormales du consommateur
   (Sprint 5.2).
6. Le moment d'acquittement et le mecanisme d'idempotence
   (Sprint 5.2).
7. L'ordre entre publication et positionnement de l'indicateur, et le
   mecanisme de resistance a la concurrence (Sprint 5.3).

Si la repartition des tables a change au Sprint 5.1, mets aussi a
jour la section correspondante.

Propose les ajouts section par section.
```

## 7. Fichiers à créer ou modifier

| Chemin | Nature |
|---|---|
| `service-transmission/.../application/UniciteTransmissionService.java` | RG-13 |
| `service-transmission/.../application/TransmissionService.java` | Modification, contrôle avant publication |
| `service-transmission/.../api/dto/StatutTransmissionResponse.java` | DTO de sortie |
| `service-transmission/.../api/TransmissionController.java` | Endpoint de consultation |
| `service-workflow/.../application/ProcessusService.java` | Mise à jour conditionnelle de l'indicateur |
| `service-transmission/src/test/...` | Tests d'unicité et de bout en bout |
| `CLAUDE.md` | Décisions du Sprint 5 |

## 8. Commandes terminal

```bash
cd afb-rations/backend

mvn -pl service-transmission test
mvn -pl service-transmission spring-boot:run
```

Vérification de CT-22, deux demandes successives sur le même état :

```bash
curl -X POST -H "Authorization: Bearer <jeton>" \
  http://localhost:8086/transmission/processus/1

curl -X POST -H "Authorization: Bearer <jeton>" \
  http://localhost:8086/transmission/processus/1
```

Comptage des messages sur le topic :

```bash
docker exec -it kafka-rations /opt/kafka/bin/kafka-console-consumer.sh \
  --topic rations.etat.valide \
  --from-beginning --timeout-ms 5000 \
  --bootstrap-server localhost:9092 | wc -l
```

Attendu : un seul message pour ce processus.

Consultation du statut :

```bash
curl -H "Authorization: Bearer <jeton>" \
  http://localhost:8086/transmission/processus/1
```

Recherche d'écritures comptables dans le projet :

```bash
grep -rni "ecriture\|debit\|credit\|cbs" backend/*/src/main/java --include=*.java
```

Attendu : aucun résultat en code actif.

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| `mvn -pl service-transmission test` | BUILD SUCCESS |
| Onze tests du sous-sprint | Tous passants |
| Deux demandes successives | Un seul message sur le topic |
| Deux demandes concurrentes | Un seul message sur le topic |
| Échec de publication | Indicateur non positionné, reprise possible |
| Consultation, quatre situations | Réponses compréhensibles |
| Chaîne complète de bout en bout | Statut final intégré |
| Aucune écriture comptable en code actif | Vérifié |
| Aucun accès direct entre bases | Vérifié |

## 10. Points de vigilance

- **RG-13 protège d'un double paiement réel.** Ce n'est pas une contrainte technique de confort : deux transmissions produisent deux jeux d'écritures, et les bénéficiaires sont payés deux fois. Le test 3, qui compte les messages sur le topic, vérifie le résultat observable côté comptabilité et pas seulement l'état interne du module.
- Le contrôle en deux temps ne résiste pas à la concurrence. Deux instances liraient un indicateur à faux avant que l'une l'ait positionné. Le mécanisme de l'étape 3 est indispensable.
- Une seconde demande n'est pas toujours une anomalie. Un rejeu légitime doit recevoir une réponse explicite, pas une erreur technique qui ferait croire à une panne.
- L'ordre entre publication et positionnement de l'indicateur laisse toujours un risque résiduel. L'important est de savoir lequel on choisit et pourquoi, et de rendre la reprise possible.
- La recherche de termes comptables dans le code est un contrôle de périmètre, pas une formalité. Le module ne doit rien produire qui ressemble à une écriture.
- Le Sprint 5 se clôt ici. Vérifier qu'aucun endpoint de reporting n'a été créé par anticipation.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Chemins de double transmission énumérés et couverts | Fait |
| Ordre publication et indicateur arbitré | Fait |
| Mécanisme de résistance à la concurrence en place | Vérifié |
| Un seul message publié après deux demandes | Vérifié |
| Un seul message publié en cas de concurrence | Vérifié |
| Reprise possible après échec de publication | Vérifié |
| Endpoint de consultation conforme au contrat | Vérifié |
| Quatre situations de consultation traitées | Vérifié |
| Chaîne complète validée de bout en bout | Vérifié |
| Aucune écriture comptable dans le projet | Vérifié |
| CLAUDE.md complété des sept décisions du Sprint 5 | Fait |

## 12. Commit

```bash
git add .
git commit -m "sprint-5.3: unicite de transmission et statut d'integration

- Controle rg-13 resistant au rejeu et a la concurrence
- Un seul message publie par etat, verifie sur le topic
- Consultation du statut d'integration comptable
- Cloture du sprint 5

Refs: RG-13, US-12, CT-22"
```

---

**Fin du Sprint 5.3 et du Sprint 5.** L'échange avec la comptabilité est complet, en publication comme en réception. En attente de validation avant le Sprint 6, service Reporting.
