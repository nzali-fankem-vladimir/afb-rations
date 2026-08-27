# SPRINT 1.3

## Habilitations inter-services et publication d'audit

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Rendre la vérification d'habilitation consommable par les autres services et mettre en place la publication d'audit |
| **Livrable** | API d'habilitation, producteur d'événements d'audit réutilisable, documentation Swagger du service |
| **Durée** | Une journée |
| **Prérequis** | Sprint 1.2 validé et commité |
| **Sprint suivant** | Service Audit, puis Sprint 2, service Grilles tarifaires |

## Révision de ce guide

Cette version tient compte de la décision du Sprint 0.2 : **`audit_log` est géré par un service Audit dédié**, alimenté par le topic Kafka `rations.audit.evenement`.

La version précédente laissait ouvert l'emplacement du journal et demandait de construire l'infrastructure d'audit dans le service Identité. Ce n'est plus le cas : ce sous-sprint construit uniquement le **côté producteur**, c'est-à-dire la publication d'événements, réutilisable par les six services métier. Le côté consommateur et la table `audit_log` relèvent du service Audit, dont le développement suit immédiatement ce sprint selon l'ordre d'implémentation de CLAUDE.md section 14.

**L'appel REST synchrone vers le service Audit est interdit.** Il réintroduirait une dépendance de disponibilité sur le chemin critique de chaque opération métier, ce que le choix du topic élimine précisément.

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| API d'habilitation inter-services (étapes 2-3) | Sonnet | Moyen |
| Producteur d'audit (étapes 4-5) | Opus | Élevé |
| Clôture du Sprint 1 (étapes 6-7) | Sonnet | Faible |

**Changement manuel à l'étape 4.** Le producteur d'audit sera repris par les six services : sa conception mérite un effort élevé, une reprise ultérieure coûterait six modifications.

## 2. Outil de cartographie

Utilisation recommandée à l'étape 6, pour vérifier que le service Identité reste sans dépendance vers un autre service, et que le producteur d'audit ne crée aucun couplage.

```
py -3.14 -m graphify update .
```

## 3. Contexte

Dernier sous-sprint du Sprint 1. Les deux précédents ont produit un service Identité qui fonctionne pour lui-même. Celui-ci le rend utile aux autres, sur deux plans.

**L'habilitation consommable à distance.** Au Sprint 4, le service Workflow devra vérifier qu'un validateur a le droit d'agir sur un dossier, sans accéder à la base du service Identité.

**La publication d'audit.** Les six services métier doivent tracer leurs actions. Ils le font en publiant un événement sur `rations.audit.evenement`, que le service Audit consomme et persiste. Le mécanisme de publication se construit ici une fois pour toutes.

Ce sous-sprint clôt le Sprint 1 : il inclut la vérification de bout en bout du service et la mise à jour de CLAUDE.md.

## 4. Objectifs

- Endpoint interne de vérification d'habilitation, consommable par les autres services
- Convention d'appel documentée pour les services consommateurs
- Producteur d'événements d'audit réutilisable, sans couplage vers le service Identité
- Publication effective sur les actions du Sprint 1.2
- Documentation Swagger complète du service Identité
- Clôture du Sprint 1

## 5. Règles et stories concernées

| Référence | Objet |
|---|---|
| US-02 | Accès limité aux fonctions autorisées, action non autorisée refusée et tracée |
| RG-12 | Séparation des tâches, dont la vérification s'appuiera sur cette API au Sprint 4 |
| CT-04 | Action hors périmètre refusée et tracée |
| Document maître 7.3 | Ordre des opérations : la journalisation vient après la modification d'état |

## 6. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis le fichier CLAUDE.md a la racine, sections 3, 9.2 et
15. Confirme en 3 lignes ou vit le journal d'audit, comment les
services l'alimentent, et ce qui est interdit en la matiere.

CONTEXTE DE CETTE SESSION : Sprint 1.3, dernier sous-sprint du
Sprint 1. On rend la verification d'habilitation consommable par les
autres services, et on construit le producteur d'evenements d'audit
que les six services metier utiliseront.

Rappel : le journal d'audit vit dans le service Audit, base
rations_audit, alimente par le topic rations.audit.evenement. Un
appel REST synchrone vers le service Audit est INTERDIT.
SERVICE CONCERNE : service-identite, plus la convention d'appel des
services consommateurs.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Aucun service ne doit acceder a la base d'un autre.
- Chaque methode publique vient avec ses tests.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : propose la forme de l'api de verification
d'habilitation. Deux questions a trancher avec moi avant tout code :
faut-il un endpoint dedie, ou les services consommateurs doivent-ils
se contenter du profil retourne par GET /identite/moi et decider
eux-memes ? Et si un endpoint dedie est retenu, comment est-il
protege, sachant que l'appelant est un service et non un
utilisateur ? Presente les options.
```

### Étape 2. API d'habilitation

```
Une fois la forme validee, implemente la solution retenue.

Si un endpoint dedie a ete choisi, il doit au minimum permettre de
repondre a : cet utilisateur a-t-il le droit d'agir sur un dossier
rattache a ce code unite ?

Il s'appuie sur le service de portee d'acces du Sprint 1.1, sans le
dupliquer.

Montre le service puis le controleur.
```

### Étape 3. Convention d'appel côté consommateurs

```
Documente la maniere dont les autres services consommeront cette
verification, sans encore l'implementer chez eux.

Produis :
1. Une note dans docs/ decrivant l'appel : chemin, en-tetes, forme de
   la reponse, comportement attendu en cas d'indisponibilite du
   service Identite.
2. Le point de vigilance sur la propagation du jeton : un service
   appelant doit-il transmettre le jeton de l'utilisateur, ou
   s'authentifier lui-meme ? Pose-moi la question si CLAUDE.md ne
   tranche pas.

N'ecris aucun client dans les autres services : ils n'ont pas encore
de code metier.
```

### Étape 4. Producteur d'événements d'audit

**Étape en Opus, effort élevé.**

```
Construis le producteur d'evenements d'audit, destine a etre repris
par les six services metier.

L'evenement publie porte : identifiant utilisateur, service
emetteur, action, entite ciblee, identifiant d'entite, horodatage,
adresse ip, et un delta avant/apres en json.

Quatre exigences :

1. La publication est ASYNCHRONE. Le service metier publie et
   poursuit son traitement : il n'attend aucune reponse et ne verifie
   pas que le message a ete consomme.

2. L'echec de publication ne doit JAMAIS faire echouer l'operation
   metier. Montre-moi comment tu le garantis : que se passe-t-il
   exactement si le broker est indisponible au moment de la
   publication ?

3. Le mecanisme doit etre reutilisable par les six services sans
   creer de dependance Maven vers service-identite. Presente-moi les
   options : duplication assumee du producteur dans chaque service,
   module commun partage, ou autre solution. Un module commun
   introduit un couplage a l'echelle du build : dis-moi si tu le
   juges acceptable ici et pourquoi.

4. AUCUN appel REST vers le service Audit. Si tu es tente d'en
   ajouter un, meme en repli, tu t'arretes et tu me le signales.

Attends ma decision sur le point 3 avant d'ecrire le code.
```

### Étape 5. Publication sur les actions du Sprint 1.2

```
Le Sprint 1.2 a trace les modifications de profil avec le strict
minimum. Reprends ces appels pour qu'ils utilisent le producteur de
l'etape 4.

Verifie que le delta avant/apres est complet sur l'attribution de
role : ancien role et ancien code unite, nouveau role et nouveau code
unite.

Ajoute la publication sur les refus d'acces : une tentative d'action
hors perimetre doit produire un evenement d'audit, conformement au
critere CT-04.

Respecte l'ordre du document maitre section 7.3 : la publication
intervient APRES la modification d'etat, jamais avant. Un evenement
publie avant une modification qui echoue enregistrerait une operation
qui n'a pas eu lieu.

Montre les modifications.
```

### Étape 6. Documentation et vérification du service

```
Complete la documentation Springdoc du service Identite : chaque
endpoint documente avec son role requis, ses codes de retour
possibles et un exemple de reponse conforme au contrat d'api.

Puis lance la cartographie et verifie :
- que service-identite ne depend d'aucun autre service
- que le producteur d'audit n'introduit pas de dependance vers
  service-audit
- qu'aucun appel REST vers le service Audit n'existe
- que les trois endpoints du contrat d'api existent, ni plus ni moins

Liste les ecarts sans les corriger.
```

### Étape 7. Clôture du Sprint 1

```
Mets a jour CLAUDE.md avec les decisions prises pendant le Sprint 1 :
1. La definition retenue pour la portee du Directeur Reseau
   (Sprint 1.1).
2. Les trois decisions d'administration du Sprint 1.2 : modification
   de son propre role, dernier administrateur, invalidation des
   sessions.
3. La forme retenue pour la verification d'habilitation
   inter-services et la propagation du jeton.
4. Le format de pagination de reference.
5. Le mode de partage du producteur d'audit entre les six services
   (decision de l'etape 4, point 3).

Propose les ajouts section par section, sans reecrire le fichier
entier.
```

## 7. Fichiers à créer ou modifier

| Chemin | Nature |
|---|---|
| `service-identite/.../application/HabilitationService.java` | Création |
| `service-identite/.../api/HabilitationController.java` | Création, si endpoint dédié retenu |
| `.../domaine/AuditEvenement.java` | Charge de l'événement publié |
| `.../infrastructure/AuditEvenementProducer.java` | Producteur Kafka |
| `service-identite/.../application/UtilisateurAdminService.java` | Modification, publication d'audit |
| `service-identite/src/test/...` | Tests de l'habilitation et de la publication |
| `docs/appel-habilitation.md` | Note de convention pour les services consommateurs |
| `docs/publication-audit.md` | Convention de publication pour les six services |
| `CLAUDE.md` | Ajout des décisions du Sprint 1 |

L'emplacement exact du producteur dépend de la décision prise à l'étape 4, point 3.

## 8. Commandes terminal

```bash
cd afb-rations/backend

mvn -pl service-identite test
mvn -pl service-identite spring-boot:run
```

Vérification de l'habilitation, avec un jeton d'agent rattaché à l'unité 00002 :

```bash
curl -H "Authorization: Bearer <jeton_agent>" \
  "http://localhost:8081/identite/habilitation?codeUnite=00002"

curl -H "Authorization: Bearer <jeton_agent>" \
  "http://localhost:8081/identite/habilitation?codeUnite=00003"
```

Attendu : autorisé sur sa propre unité, refusé sur une autre.

Vérification de la publication d'audit :

```bash
docker exec -it kafka-rations /opt/kafka/bin/kafka-console-consumer.sh \
  --topic rations.audit.evenement \
  --from-beginning \
  --bootstrap-server localhost:9092
```

Déclencher ensuite une attribution de rôle et observer l'événement publié.

Vérification de la robustesse :

```bash
docker compose stop kafka-rations
```

Puis rejouer une attribution de rôle : attendu, l'opération métier aboutit malgré tout.

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| `mvn -pl service-identite test` | BUILD SUCCESS, aucune régression |
| Habilitation sur sa propre unité | Autorisée |
| Habilitation sur une autre unité, rôle local | Refusée |
| Habilitation, rôle à portée nationale | Autorisée sur toute unité |
| Attribution de rôle | Événement publié sur le topic |
| Delta avant/après | Complet dans l'événement |
| Refus d'accès | Événement publié |
| Broker Kafka arrêté | L'opération métier aboutit malgré tout |
| Aucun appel REST vers le service Audit | Vérifié |
| Aucune dépendance vers `service-audit` | Vérifié par cartographie |
| Swagger du service | Trois endpoints documentés |

## 10. Points de vigilance

- **L'audit ne doit jamais faire échouer le métier.** C'est la raison même du choix asynchrone. La vérification broker arrêté n'est pas facultative : elle prouve que le principe tient en conditions réelles.
- **Aucun appel REST vers le service Audit.** Même en repli, même en cas d'échec de publication. Cet appel réintroduirait la dépendance que le topic élimine, et il est listé parmi les erreurs interdites de CLAUDE.md.
- Le mode de partage du producteur entre six services est un vrai arbitrage. Un module commun évite la duplication mais crée un couplage de build : une modification du producteur oblige à recompiler les six services. La duplication assumée est plus verbeuse mais préserve l'indépendance. Ni l'un ni l'autre n'est évident, d'où la question.
- La publication vient après la modification d'état. L'ordre inverse enregistrerait des opérations qui ont échoué.
- Le champ `service_emetteur` doit être renseigné par chaque service publiant. Sans lui, une trace centralisée ne dit plus d'où elle vient.
- Le refus d'accès doit être publié, comme l'exige CT-04. Une tentative hors périmètre est une information de sécurité, pas un simple rejet à ignorer.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Forme de la vérification d'habilitation tranchée et implémentée | Fait |
| Question de la propagation du jeton tranchée et consignée | Fait |
| Mode de partage du producteur d'audit tranché | Fait |
| Publication asynchrone, sans attente de réponse | Vérifié |
| L'échec de publication ne fait pas échouer le métier | Vérifié broker arrêté |
| Aucun appel REST vers le service Audit | Vérifié |
| Aucune dépendance Maven vers `service-audit` | Vérifié |
| Actions du Sprint 1.2 publiées avec delta complet | Vérifié |
| Refus d'accès publiés | Vérifié |
| Documentation Swagger complète et exacte | Vérifié |
| Trois endpoints du contrat, ni plus ni moins | Vérifié |
| CLAUDE.md complété des décisions du Sprint 1 | Fait |
| Deux notes de convention rédigées | Fait |

## 12. Commit

```bash
git add .
git commit -m "sprint-1.3: habilitations inter-services et publication d'audit

- Verification d'habilitation consommable par les autres services
- Producteur d'evenements d'audit asynchrone, sans appel rest
- Publication sur les modifications de profil et les refus d'acces
- Robustesse verifiee broker arrete
- Cloture du sprint 1

Refs: US-02, RG-12, CT-04, CLAUDE.md section 9.2"
```

---

**Fin du Sprint 1.3 et du Sprint 1.** Le service Identité est complet. Le sprint suivant construit le **service Audit**, consommateur du topic, avant le service Grilles : voir CLAUDE.md section 14 pour l'ordre d'implémentation.
