# SPRINT 4.1

## Domaine du workflow : processus et étapes

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Créer le processus mensuel, les étapes de workflow et la machine à états du circuit |
| **Livrable** | Entités, repositories, machine à états ET01, endpoints de déclenchement et de consultation |
| **Durée** | Une à deux journées |
| **Prérequis** | Sprint 3.4 validé et commité |
| **Sprint suivant** | 4.2, soumission et pièce jointe |

## 0. Héritage du Sprint 1.3 — à faire avant toute autre étape

Deux obligations transverses, décidées au Sprint 1.3 et **non facultatives**.
Elles sont inscrites ici parce qu'aucune session ne relit les documents de
décision d'elle-même : ne pas compter sur la mémoire d'une session précédente.
Voir CLAUDE.md section 17.

### 0.1 Dépendance au module de publication d'audit

Ajouter au `pom.xml` de **service-workflow** :

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

**Ce service est le premier consommateur reel de `/identite/habilitation`** (RG-12) : la section 0.2 ci-dessous le concerne directement, pas de facon theorique.

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
| Entités et repositories (étapes 2-3) | Sonnet | Moyen |
| Machine à états ET01 (étapes 4-5) | Opus | Élevé |
| Endpoints et tests (étapes 6-7) | Opus | Élevé |

**Changement manuel à l'étape 4.** La machine à états porte RG-07 : basculer en Opus effort élevé et l'y maintenir jusqu'à la fin du sous-sprint.

## 2. Outil de cartographie

Recommandée en début et en fin de sous-sprint. Le service Workflow appellera le service Saisie : la cartographie doit confirmer que l'appel passe par l'API.

```
py -3.14 -m graphify update .
```

## 3. Contexte

Premier sous-sprint du service Workflow, pièce centrale du module. Le processus mensuel est l'objet autour duquel tout s'organise : il porte la période, l'unité, le statut d'avancement, le montant total et, à terme, l'indicateur de transmission comptable.

Sa machine à états est décrite par le diagramme ET01 du document de conception. Elle est plus riche que celle de la grille tarifaire : six statuts, un aiguillage conditionnel selon le montant, et un retour qui ramène toujours à l'agent quel que soit le niveau d'origine.

Ce sous-sprint construit le socle : entités, machine à états, déclenchement et consultation. La soumission, la validation et le retour viennent aux sous-sprints suivants.

Un point de périmètre : `processus_mensuel` porte `type_processus`, avec les valeurs NORMAL et COMPLEMENTAIRE. **Seul le type NORMAL est traité dans le Sprint 4.** L'état complémentaire est le Sprint 6bis, sous réserve de confirmation métier.

## 4. Objectifs

- Entités `ProcessusMensuel`, `EtapeWorkflow` et `ParametreSysteme`
- Repositories avec les recherches nécessaires au circuit
- Machine à états conforme à ET01, transitions autorisées et interdites
- `POST /processus` : déclenchement d'un processus normal
- `GET /processus/{id}` : détail et statut
- `GET /processus/{id}/etat` : état consolidé, par appel au service Saisie

## 5. Règles et stories concernées

| Référence | Objet |
|---|---|
| RG-07 | Validation séquentielle, aucun saut de niveau |
| RG-06 | Consolidation mensuelle, moitié portée par le Workflow |
| US-06 | État consolidé consultable |
| ET01 | Diagramme d'état-transition du document de conception |

Transitions autorisées, d'après ET01 :

| Depuis | Vers | Déclencheur |
|---|---|---|
| (création) | EN_COURS_SAISIE | Déclenchement par l'agent |
| EN_COURS_SAISIE | SOUMIS | Soumission, informations complètes |
| SOUMIS | EN_ATTENTE_DA | Signature agent apposée, transfert au DA |
| EN_ATTENTE_DA | CLOTURE | Validation DA, montant au plus 100 000 XAF |
| EN_ATTENTE_DA | EN_ATTENTE_DR | Validation DA, montant supérieur au seuil |
| EN_ATTENTE_DA | RETOURNE | Retour DA avec motif |
| EN_ATTENTE_DR | CLOTURE | Validation DR |
| EN_ATTENTE_DR | RETOURNE | Retour DR avec motif |
| RETOURNE | EN_COURS_SAISIE | Reprise par l'agent |

CLOTURE est terminal : aucune transition n'en repart.

## 6. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis le fichier CLAUDE.md a la racine, sections 4, 6 et 7.
Confirme en 3 lignes la table processus_mensuel, la regle RG-07 et le
schema du workflow.

CONTEXTE DE CETTE SESSION : Sprint 4.1, domaine du workflow. Les
quatre tables existent en base depuis le Sprint 0.5. On cree les
entites, la machine a etats et les endpoints de declenchement et de
consultation. La soumission, la validation et le retour sont les
sous-sprints suivants.

Point de perimetre : seul le type de processus NORMAL est traite
ici. Le type COMPLEMENTAIRE est le Sprint 6bis, sous reserve de
confirmation metier. Ne l'implemente pas.
SERVICE CONCERNE : service-workflow uniquement.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Chaque methode publique vient avec ses tests JUnit.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : cree l'entite ProcessusMensuel, strictement conforme
au dictionnaire : id, mois_paiement, annee_paiement, code_unite,
type_processus, id_processus_origine, motif_ouverture, montant_total,
statut, transmis_comptabilite, date_declenchement, date_cloture,
id_createur.

code_unite designe l'unite qui supporte la charge. id_processus_origine
est une auto-reference, nulle pour un processus normal. Montre le
fichier.
```

### Étape 2. Étapes et paramètres

```
Cree les deux entites restantes :

EtapeWorkflow : id, id_processus, id_acteur, ordre_etape, nom_etape,
statut_etape, date_action, motif_retour, signature_numerique.

ParametreSysteme : id, code, libelle, valeur, actif,
date_modification.

id_acteur designe un utilisateur qui vit dans la base du service
Identite : c'est un identifiant simple, pas une association JPA.

Cree aussi les enumerations StatutEnum, TypeProcessusEnum,
NomEtapeEnum et StatutEtapeEnum, conformes au dictionnaire.

Montre les fichiers.
```

### Étape 3. Repositories

```
Cree les trois repositories.

ProcessusMensuelRepository :
1. recherche par code_unite, mois et annee, pour le controle
   d'unicite du processus normal
2. recherche paginee par statut et code_unite
3. findById

EtapeWorkflowRepository :
4. findByIdProcessusOrderByOrdreEtape
5. recherche de la derniere etape d'un processus
6. recherche des etapes realisees par un acteur donne sur un
   processus, qui portera RG-12 au sous-sprint 4.4

ParametreSystemeRepository :
7. findByCodeAndActifTrue

Montre les fichiers.
```

### Étape 4. Machine à états

**Étape en Opus, effort élevé.**

```
Cree la machine a etats du processus, selon le tableau ET01 du guide
de ce sous-sprint.

Expose :
1. une methode indiquant si une transition est autorisee
2. les methodes de transition correspondant aux neuf transitions du
   tableau

Exigences :
- Toute transition non listee leve une erreur explicite.
- CLOTURE est terminal : aucune transition n'en repart, y compris
  vers RETOURNE.
- Le passage de EN_ATTENTE_DA vers CLOTURE ou vers EN_ATTENTE_DR
  depend du montant : la machine expose les deux transitions, le
  choix entre elles releve du service d'aiguillage au sous-sprint
  4.3. Ne code pas la comparaison de montant ici.

Montre le fichier.
```

### Étape 5. Tests de la machine à états

```
Ecris les tests, une methode par transition.

Transitions valides, les neuf du tableau ET01.

Transitions interdites, au minimum :
10. EN_COURS_SAISIE vers EN_ATTENTE_DA, en sautant la soumission
11. SOUMIS vers CLOTURE, en sautant les validations
12. EN_ATTENTE_DA vers EN_ATTENTE_DA
13. CLOTURE vers RETOURNE
14. CLOTURE vers EN_COURS_SAISIE
15. RETOURNE vers EN_ATTENTE_DA, en sautant la resoumission

Les tests 13 et 14 protegent le caractere definitif de la cloture,
qui conditionne l'unicite de la transmission comptable au Sprint 5.

Montre le fichier de test.
```

### Étape 6. Déclenchement et consultation

```
Cree les services et les endpoints :

POST /processus :
1. Verifie l'habilitation : role AGENT_UNITE, et portee d'acces sur
   le code unite demande.
2. Verifie qu'aucun processus NORMAL n'existe deja pour ce couple
   unite et periode. Si oui, refuse avec le code de conflit du
   contrat d'api.
3. Cree le processus au statut EN_COURS_SAISIE, type NORMAL.
4. Trace le declenchement.

Le type COMPLEMENTAIRE, s'il est transmis, est refuse pour l'instant
avec un message indiquant que la fonctionnalite n'est pas encore
ouverte.

GET /processus/{id} : detail et statut, portee d'acces verifiee.

Montre les services puis le controleur.
```

### Étape 7. État consolidé

```
Cree l'endpoint GET /processus/{id}/etat.

Il s'appuie sur l'appel au service Saisie decrit dans
docs/appel-consolidation.md, redige au Sprint 3.4. Isole cet appel
derriere une interface, comme le client de resolution du montant du
Sprint 3.2.

Applique le comportement prevu par la convention quand le service
Saisie est injoignable, et quand le processus n'a aucune fiche.

Montre l'interface, son implementation, puis le controleur.
```

## 7. Fichiers à créer

| Chemin | Nature |
|---|---|
| `service-workflow/.../domaine/ProcessusMensuel.java` | Entité |
| `service-workflow/.../domaine/EtapeWorkflow.java` | Entité |
| `service-workflow/.../domaine/ParametreSysteme.java` | Entité |
| `service-workflow/.../domaine/TransitionProcessus.java` | Machine à états |
| `service-workflow/.../domaine/*Enum.java` | Quatre énumérations |
| `service-workflow/.../infrastructure/*Repository.java` | Trois repositories |
| `service-workflow/.../application/ConsolidationClient.java` | Interface d'appel |
| `service-workflow/.../infrastructure/ConsolidationHttpClient.java` | Implémentation |
| `service-workflow/.../application/ProcessusService.java` | Déclenchement, consultation |
| `service-workflow/.../api/ProcessusController.java` | Trois endpoints |
| `service-workflow/src/test/...` | Tests |

## 8. Commandes terminal

Les services Identité, Saisie et Workflow doivent tourner.

```bash
cd afb-rations/backend

mvn -pl service-workflow test
mvn -pl service-workflow spring-boot:run
```

```bash
curl -X POST -H "Authorization: Bearer <jeton_agent>" \
  -H "Content-Type: application/json" \
  -d '{"moisPaiement":8,"anneePaiement":2026,"codeUnite":"00002"}' \
  http://localhost:8084/processus
```

Rejouer le même appel : attendu 409.

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| `mvn -pl service-workflow test` | BUILD SUCCESS |
| Quinze tests de la machine à états | Tous passants |
| Transitions interdites | Erreur explicite |
| CLOTURE terminal | Aucune transition sortante |
| Déclenchement nominal | 201, statut EN_COURS_SAISIE |
| Second déclenchement même unité et période | 409 |
| Type COMPLEMENTAIRE demandé | Refusé avec message explicite |
| Déclenchement hors portée | 403 |
| État consolidé | Données provenant du service Saisie |
| Service Saisie injoignable | Comportement conforme à la convention |
| Aucun accès à la base du service Saisie | Vérifié par cartographie |

## 10. Points de vigilance

- **CLOTURE est terminal.** Autoriser une sortie de cet état, même par commodité de correction, ouvrirait la porte à une seconde transmission comptable et donc à un double paiement. Les tests 13 et 14 le verrouillent.
- La machine à états n'arbitre pas le montant. Elle expose les deux transitions possibles depuis EN_ATTENTE_DA ; le choix relève du service d'aiguillage, au sous-sprint 4.3. Mélanger les deux rendrait la machine dépendante du seuil.
- `id_acteur` n'est pas une association JPA. L'utilisateur vit dans le service Identité.
- L'unicité du processus normal est garantie en base par l'index partiel du Sprint 0.5, mais le refus doit venir du service, avec un message compréhensible.
- Le type COMPLEMENTAIRE existe dans le modèle mais n'est pas ouvert. L'accepter dès maintenant contournerait la confirmation métier attendue.
- Ne pas implémenter la soumission, la validation ni le retour dans ce sous-sprint.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Trois entités conformes au dictionnaire | Vérifié |
| Quatre énumérations conformes | Vérifié |
| Machine à états couvrant les neuf transitions d'ET01 | Fait |
| Six transitions interdites levant une erreur | Vérifié |
| CLOTURE verrouillé | Vérifié |
| Unicité du processus normal contrôlée par le service | Vérifié |
| Type COMPLEMENTAIRE refusé | Vérifié |
| État consolidé obtenu par appel d'API | Vérifié |
| Portée d'accès appliquée | Vérifié |
| Aucun endpoint de soumission ou de validation | Vérifié |

## 12. Commit

```bash
git add .
git commit -m "sprint-4.1: domaine du workflow

- Entites processus, etapes et parametres systeme
- Machine a etats conforme a ET01, cloture terminale
- Declenchement, consultation et etat consolide
- Tests des transitions valides et interdites

Refs: RG-06, RG-07, ET01"
```

---

**Fin du Sprint 4.1** — en attente de validation avant le Sprint 4.2
