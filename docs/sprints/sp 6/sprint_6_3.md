# SPRINT 6.3

## Journal d'audit : couverture et consultation

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Vérifier la couverture de publication d'audit sur les sept services et exposer la consultation du journal |
| **Livrable** | Couverture vérifiée, consultation du journal, clôture du sprint |
| **Durée** | Une journée |
| **Prérequis** | Sprint 6.2 validé et commité |
| **Sprint suivant** | 6bis.1, état complémentaire |

## Révision de ce guide

Cette version tient compte de la décision du Sprint 0.2 : le journal d'audit vit dans un **service Audit dédié**, alimenté par le topic `rations.audit.evenement`, et développé juste après le Sprint 1 selon l'ordre de CLAUDE.md section 14.

Deux conséquences par rapport à la version précédente. L'infrastructure d'audit n'est plus à construire ici : elle existe depuis le service Audit. Et la consultation du journal ne relève plus du service Reporting mais du **service Audit lui-même**, qui expose deux endpoints de lecture.

Ce sous-sprint devient donc un sprint de **vérification de couverture** et de **finalisation de la consultation**, pas de construction.

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Audit de couverture (étapes 2-4) | Opus | Élevé |
| Consultation et clôture (étapes 5-7) | Sonnet | Moyen |

**Changement manuel à l'étape 5.**

## 2. Outil de cartographie

Utilisation obligatoire dès l'étape 2. C'est elle qui permet de repérer les services qui écrivent en base sans publier d'événement d'audit.

```
py -3.14 -m graphify update .
```

## 3. Contexte

Dernier sous-sprint du service Reporting, et clôture du cœur fonctionnel du module.

Le producteur d'événements d'audit a été construit au Sprint 1.3, le service Audit qui les consomme juste après, et les services suivants ont publié leurs événements au fil de leur développement. Ce sous-sprint vérifie qu'ils l'ont fait **partout où ils devaient le faire**.

Les exigences de contrôle interne du cahier des charges sont explicites : historisation complète des modifications, journaux générés automatiquement, chaque opération associée à un utilisateur, une date et une heure.

Une omission de publication ne provoque aucune erreur : le code fonctionne, les tests passent, et personne ne s'en aperçoit avant qu'un contrôle demande la trace d'une opération qui n'a jamais été enregistrée. D'où un sous-sprint dédié à cette vérification.

## 4. Objectifs

- Inventaire des écritures de chaque service, confronté aux événements publiés
- Correction des omissions constatées
- Consultation du journal d'audit, exposée par le service Audit
- Vérification de l'immuabilité du journal
- Clôture du Sprint 6 et mise à jour de CLAUDE.md

## 5. Règles et stories concernées

| Référence | Objet |
|---|---|
| RG-09 | Signature horodatée, dont la trace complète l'audit |
| US-02 | Action non autorisée refusée et tracée |
| CT-04 | Refus d'accès tracé |
| CT-40 | Consultation du journal d'audit d'un processus |
| Document maître 7.3 | La journalisation vient après la modification d'état |

## 6. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis le fichier CLAUDE.md a la racine, sections 3, 4, 9.2
et 11. Confirme en 3 lignes ou vit le journal d'audit, comment il est
alimente, et quels endpoints le service Audit expose.

CONTEXTE DE CETTE SESSION : Sprint 6.3, couverture et consultation du
journal d'audit. Le producteur existe depuis le Sprint 1.3, le
service Audit consomme le topic depuis son propre sprint. On verifie
maintenant que la publication a bien lieu PARTOUT.
SERVICE CONCERNE : les sept services, en verification, puis
service-audit pour la consultation.

METHODE DE TRAVAIL :
- Tu listes d'abord toutes les omissions, sans en corriger aucune.
- Je decide ensuite lesquelles corriger et dans quel ordre.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : a partir de la cartographie, dresse l'inventaire de
toutes les methodes qui modifient un etat persistant, service par
service. Pour chacune, indique si elle publie un evenement d'audit.
Presente le resultat sous forme de tableau : service, methode,
publication oui ou non. Ne corrige rien.
```

### Étape 2. Inventaire de couverture

Périmètre attendu de la vérification, service par service :

| Service | Opérations devant publier un événement |
|---|---|
| Identité | Attribution de rôle, modification de profil, refus d'accès |
| Grilles | Création, soumission, validation, rejet, fermeture |
| Saisie | Création, modification et suppression de ligne, ouverture de fiche |
| Workflow | Déclenchement, soumission, validation, retour, clôture, ouverture d'état complémentaire |
| Transmission | Publication de l'état, réception d'accusé, mise à jour du statut |
| Reporting | Génération et export de rapport |
| Audit | Consultation du journal |

```
Confronte ton inventaire a ce tableau. Signale-moi :
- les operations publiant un evenement mais absentes du tableau
- les operations du tableau ne publiant rien
- les evenements incomplets : auteur manquant, service_emetteur
  absent, delta avant-apres absent sur une modification, entite
  ciblee imprecise

Le champ service_emetteur merite une attention particuliere : sans
lui, une trace centralisee ne dit plus d'ou elle vient. Verifie qu'il
est renseigne par chacun des sept services.

Presente les ecarts, sans les corriger.
```

### Étape 3. Correction des omissions

```
Une fois les ecarts arbitres, corrige-les un par un, service par
service.

Respecte l'ordre du document maitre section 7.3 : la publication
intervient apres la modification d'etat, jamais avant.

Verifie aussi qu'aucune correction n'introduit d'appel REST vers le
service Audit : la publication passe exclusivement par le topic
rations.audit.evenement.

Un commit par service corrige.
```

### Étape 4. Robustesse de la chaîne d'audit

```
Verifie le comportement de la chaine complete en situation degradee :

1. Broker Kafka arrete : les operations metier aboutissent-elles
   toujours ? Aucune ne doit echouer.
2. Service Audit arrete, broker disponible : les evenements
   s'accumulent-ils dans le topic, et sont-ils traites au redemarrage
   du service ? Aucune trace ne doit etre perdue.
3. Evenement mal forme recu par le service Audit : le consommateur
   tombe-t-il, bloquant le traitement des suivants ?

Le point 2 est la contrepartie du choix asynchrone : il faut prouver
qu'une panne du service Audit differe les traces sans les perdre.

Consigne les observations.
```

### Étape 5. Consultation du journal

**Passage en Sonnet, effort moyen.**

```
Finalise la consultation du journal, exposee par le service Audit
selon le contrat d'api section 11 :

- GET /audit/entrees : recherche filtrable par periode, utilisateur,
  service emetteur, entite ciblee, identifiant d'entite, type
  d'action. Paginee au format de reference du Sprint 1.2.
- GET /audit/processus/{id} : toutes les actions relatives a un
  processus donne, quel que soit le service qui les a produites.

Le second est le cas d'usage principal du controle interne, decrit
par CT-40. Le journal etant centralise, une seule lecture suffit :
verifie que les index poses au Sprint 0.5 rendent cette requete
rapide sur un volume realiste.

Reserve la consultation aux roles habilites.

AUCUN endpoint d'ecriture, de modification ou de suppression.
Montre les services puis le controleur.
```

### Étape 6. Immuabilité

```
Verifie que le journal est bien immuable :

1. Aucune methode de modification ou de suppression n'est exposee par
   le service Audit.
2. Aucun endpoint ne permet d'alterer une entree existante.
3. Le repository n'expose pas les methodes de suppression heritees
   par defaut de JpaRepository.
4. Aucun autre service ne possede de connexion vers la base
   rations_audit.

Le point 4 est le benefice architectural qui a motive la creation du
service Audit au Sprint 0.2 : verifie qu'il tient effectivement.

Le point 3 est souvent oublie : un repository JPA standard expose des
methodes de suppression qui n'ont jamais ete ecrites mais sont bien
disponibles.

Ecris les tests qui le prouvent. Montre le code et les tests.
```

### Étape 7. Clôture du Sprint 6

```
Lance la cartographie et verifie :
- que les quatre endpoints du contrat d'api du service Reporting
  existent, ni plus ni moins
- que les deux endpoints du service Audit existent, en lecture seule
- que service-reporting n'a ni entite JPA ni datasource
- qu'aucun service autre que service-audit ne pointe vers
  rations_audit
- que toutes les operations du tableau de l'etape 2 publient un
  evenement

Puis mets a jour CLAUDE.md avec les decisions du Sprint 6 :
1. La strategie d'agregation multi-services retenue (Sprint 6.1).
2. Le comportement en cas de service injoignable pendant une lecture
   (Sprint 6.1).
3. La structure du rapport arbitree (Sprint 6.2).
4. La convention de nommage des fichiers exportes (Sprint 6.2).
5. Le perimetre definitif des operations publiant un evenement
   d'audit, et les corrections apportees (Sprint 6.3).

Propose les ajouts section par section.
```

## 7. Fichiers à créer ou modifier

| Chemin | Nature |
|---|---|
| Services divers | Ajout des publications d'audit manquantes |
| `service-audit/.../application/ConsultationAuditService.java` | Consultation |
| `service-audit/.../api/dto/EntreeAuditResponse.java` | DTO de sortie |
| `service-audit/.../api/AuditController.java` | Deux endpoints de lecture |
| `service-audit/src/test/.../ImmutabiliteAuditTest.java` | Test d'immuabilité |
| `docs/robustesse-audit.md` | Observations de l'étape 4 |
| `CLAUDE.md` | Décisions du Sprint 6 |

## 8. Commandes terminal

```bash
py -3.14 -m graphify update .

cd afb-rations/backend
mvn clean test
```

Recherche des méthodes de suppression exposées sur le journal :

```bash
grep -rn "delete\|remove" backend/service-audit/src/main/java --include=*.java
```

Recherche d'une connexion à la base d'audit depuis un autre service :

```bash
grep -rn "rations_audit" backend/ --include=*.yml | grep -v "service-audit"
```

Attendu : aucun résultat.

Contrôle du contenu du journal après un parcours complet :

```bash
curl -H "Authorization: Bearer <jeton_arh>" \
  http://localhost:8087/audit/processus/1
```

Attendu : déclenchement, soumission, validation, clôture et transmission tous présents, chacun avec son service émetteur.

Test de robustesse :

```bash
docker compose stop kafka-rations
# rejouer une operation metier : elle doit aboutir

docker compose start kafka-rations
docker compose stop rations-audit
# rejouer une operation : l'evenement s'accumule dans le topic

docker compose start rations-audit
# verifier que la trace apparait apres redemarrage
```

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| `mvn clean test` sur l'ensemble | BUILD SUCCESS, aucune régression |
| Inventaire de couverture | Aucune opération du tableau sans publication |
| Événements complets | Auteur, service émetteur, date, entité, delta présents |
| Ordre modification puis publication | Vérifié |
| Broker arrêté | Opérations métier aboutissent |
| Service Audit arrêté puis redémarré | Aucune trace perdue |
| Événement mal formé | Consommateur toujours actif |
| Consultation par processus | Toutes les actions du parcours visibles |
| Journal immuable | Aucune méthode de suppression exposée |
| Base d'audit isolée | Aucun autre service n'y accède |
| Deux endpoints du service Audit, en lecture seule | Vérifié |
| Quatre endpoints du service Reporting | Ni plus ni moins |

## 10. Points de vigilance

- **Une omission de publication ne provoque aucune erreur.** Le code fonctionne, les tests passent, et le manque n'apparaît qu'au moment où un contrôle réclame une trace inexistante. D'où l'inventaire systématique.
- Le champ `service_emetteur` doit être renseigné partout. Sur un journal centralisé, c'est lui qui remplace l'information implicite que donnait un journal réparti.
- **Aucun service autre que le service Audit ne doit accéder à `rations_audit`.** C'est le bénéfice qui a motivé la création du service dédié au Sprint 0.2 : si un service métier y accède directement, ce bénéfice disparaît.
- Un repository JPA standard expose des méthodes de suppression que personne n'a écrites. L'immuabilité ne se décrète pas, elle se vérifie.
- La panne du service Audit ne doit pas perdre de traces : les événements restent dans le topic et sont traités au redémarrage. C'est la contrepartie du choix asynchrone, et elle doit être prouvée.
- Le refus d'accès doit être tracé, comme l'exige CT-04.
- Corriger les omissions service par service, avec un commit par service.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Inventaire de couverture réalisé sur les sept services | Fait |
| Écarts listés puis arbitrés | Fait |
| Toutes les opérations du tableau publient un événement | Vérifié |
| `service_emetteur` renseigné par les sept services | Vérifié |
| Ordre modification puis publication respecté | Vérifié |
| Robustesse éprouvée sur les trois scénarios | Fait |
| Aucune trace perdue après panne du service Audit | Vérifié |
| Consultation par processus fonctionnelle | Vérifié |
| Journal immuable, prouvé par test | Vérifié |
| Base d'audit inaccessible aux autres services | Vérifié |
| Refus d'accès tracés | Vérifié |
| CLAUDE.md complété des cinq décisions du Sprint 6 | Fait |

## 12. Commit

```bash
git add .
git commit -m "sprint-6.3: couverture et consultation du journal d'audit

- Inventaire de couverture sur les sept services et correction des omissions
- Consultation filtrable exposee par le service audit, en lecture seule
- Immuabilite verifiee, base d'audit isolee des services metier
- Robustesse eprouvee broker et service audit arretes
- Cloture du sprint 6

Refs: US-02, CT-04, CT-40, CLAUDE.md section 9.2"
```

---

**Fin du Sprint 6.3 et du Sprint 6.** Le cœur fonctionnel du module est complet. En attente de validation avant le Sprint 6bis.
