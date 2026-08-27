# SPRINT 6.1

## Suivi des demandes et recherche multicritère

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Exposer le suivi des demandes avec recherche multicritère et historique des validations |
| **Livrable** | Deux endpoints de suivi, agrégation multi-services, tests |
| **Durée** | Une journée |
| **Prérequis** | Sprint 5.3 validé et commité |
| **Sprint suivant** | 6.2, rapports et exports |

## 0. Héritage du Sprint 1.3 — à faire avant toute autre étape

Deux obligations transverses, décidées au Sprint 1.3 et **non facultatives**.
Elles sont inscrites ici parce qu'aucune session ne relit les documents de
décision d'elle-même : ne pas compter sur la mémoire d'une session précédente.
Voir CLAUDE.md section 17.

### 0.1 Dépendance au module de publication d'audit

Ajouter au `pom.xml` de **service-reporting** :

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
| Stratégie d'agrégation (étapes 2-3) | Opus | Élevé |
| Endpoints et tests (étapes 4-6) | Sonnet | Moyen |

**Deux changements manuels.** Passer en Opus pour l'arbitrage d'agrégation, revenir en Sonnet à l'étape 4.

## 2. Outil de cartographie

Utilisation recommandée en fin de sous-sprint. Le service Reporting appelle plusieurs services : la cartographie doit confirmer qu'il n'accède à aucune base.

```
py -3.14 -m graphify update .
```

## 3. Contexte

Premier sous-sprint du service Reporting. Sa particularité est structurelle : **il n'a pas de base propre.** Toutes ses données vivent ailleurs, dans les bases des services Saisie, Grilles et Workflow. Il ne fait que lire, agréger et présenter.

Cela pose une difficulté que ce sous-sprint doit arbitrer. La recherche multicritère du cahier des charges porte sur la période, l'unité, la session, la nature et le bénéficiaire. Or la période et l'unité vivent dans le service Workflow, tandis que la session, la nature et le bénéficiaire vivent dans le service Saisie. Une recherche combinant les deux suppose donc de croiser deux sources, sans jointure SQL possible.

Le document maître fixe une cible de trois secondes en moyenne pour les opérations courantes : la stratégie retenue doit la tenir.

## 4. Objectifs

- Stratégie d'agrégation multi-services arbitrée
- `GET /reporting/demandes` : recherche multicritère paginée
- `GET /reporting/processus/{id}/historique` : historique des validations et retours
- Portée d'accès appliquée à la lecture
- Tests couvrant les combinaisons de filtres

## 5. Règles et stories concernées

| Référence | Objet |
|---|---|
| US-15 | Consultation du statut des demandes, recherche multicritère, historique |
| CT-30 | Recherche par période, unité, session, nature et bénéficiaire |
| CT-31 | Historique des validations et rejets affiché |

## 6. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis le fichier CLAUDE.md a la racine, sections 3 et 11.
Confirme en 3 lignes le role du service Reporting et ses quatre
endpoints.

CONTEXTE DE CETTE SESSION : Sprint 6.1, suivi des demandes. Le
service Reporting n'a PAS de base propre : il lit les donnees des
autres services par leur api.
SERVICE CONCERNE : service-reporting, avec appels vers Saisie et
Workflow.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Aucun acces direct a la base d'un autre service.
- Chaque methode publique vient avec ses tests.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : un arbitrage avant tout code. La recherche
multicritere porte sur la periode, l'unite, la session, la nature et
le beneficiaire. Les deux premiers criteres vivent dans le service
Workflow, les trois autres dans le service Saisie. Aucune jointure
SQL n'est possible entre les deux bases.

Presente-moi les strategies possibles pour combiner ces criteres, avec
leurs consequences sur le temps de reponse, dont la cible est de
3 secondes en moyenne. Precise pour chacune le comportement quand un
filtre porte sur les deux sources a la fois. Attends ma decision.
```

### Étape 2. Clients de lecture

```
Une fois la strategie arbitree, cree les clients de lecture vers les
services concernes, isoles derriere des interfaces comme les clients
precedents.

Chaque client expose uniquement ce dont le reporting a besoin, pas
l'integralite de l'api du service appele.

Applique la propagation du jeton decidee au Sprint 1.3 : la portee
d'acces doit continuer de s'appliquer, un utilisateur ne devant voir
que les unites qui lui sont accessibles.

Montre les interfaces puis les implementations.
```

### Étape 3. Service d'agrégation

```
Cree le service d'agregation mettant en œuvre la strategie arbitree.

Il doit gerer trois situations :
1. filtres portant uniquement sur le Workflow
2. filtres portant uniquement sur la Saisie
3. filtres portant sur les deux

Ajoute un delai d'attente sur chaque appel et un comportement defini
quand un service est injoignable : le reporting doit-il echouer, ou
retourner un resultat partiel signale comme tel ? Presente les deux,
j'arbitre.

Montre le service.
```

### Étape 4. Endpoint de suivi

```
Cree l'endpoint GET /reporting/demandes :

- Filtres optionnels et combinables : periode, unite, session, nature,
  beneficiaire.
- Pagination au format de reference du Sprint 1.2.
- Reponse : pour chaque demande, la periode, l'unite, le montant
  total, le statut d'avancement et le statut d'integration comptable.
- Accessible aux roles ARH, AGENT_UNITE, CHEF_UNITE_DA et
  DIRECTEUR_RESEAU_DR, avec portee d'acces appliquee.

Montre le service puis le controleur.
```

### Étape 5. Historique

```
Cree l'endpoint GET /reporting/processus/{id}/historique :

Il retourne la suite des etapes du processus, dans l'ordre : niveau,
acteur, decision, motif de retour eventuel, date et heure.

Un dossier passe plusieurs fois par le meme niveau apres un retour :
l'historique doit montrer cette chronologie complete, pas seulement
le dernier passage.

Verifie la portee d'acces. Montre le service puis le controleur.
```

### Étape 6. Tests

```
Ecris les tests de ce sous-sprint.

Recherche :
1. sans filtre : toutes les demandes accessibles a l'utilisateur
2. filtre sur la periode seule
3. filtre sur la nature seule
4. filtres combines periode et beneficiaire, croisant les deux
   sources
5. aucun resultat : reponse vide explicite, pas une erreur
6. utilisateur a portee locale : ne voit que son unite
7. utilisateur a portee nationale : voit toutes les unites
8. service injoignable : comportement conforme a la decision de
   l'etape 3

Historique :
9. processus valide sans retour : etapes dans l'ordre
10. processus ayant subi un retour puis une resoumission : toutes les
    etapes visibles, y compris les passages successifs au meme niveau
11. consultation hors portee : 403

Le test 10 verifie que l'historique n'ecrase pas les passages
anterieurs, ce qui priverait le controle interne de sa valeur.

Montre les fichiers de test.
```

## 7. Fichiers à créer

| Chemin | Nature |
|---|---|
| `service-reporting/.../application/SaisieLectureClient.java` | Interface |
| `service-reporting/.../application/WorkflowLectureClient.java` | Interface |
| `service-reporting/.../infrastructure/*HttpClient.java` | Implémentations |
| `service-reporting/.../application/AgregationService.java` | Stratégie d'agrégation |
| `service-reporting/.../application/SuiviService.java` | Recherche et historique |
| `service-reporting/.../api/dto/DemandeResponse.java` | DTO de sortie |
| `service-reporting/.../api/dto/EtapeHistoriqueResponse.java` | DTO de sortie |
| `service-reporting/.../api/ReportingController.java` | Deux endpoints |
| `service-reporting/src/test/...` | Tests |

## 8. Commandes terminal

Les services Identité, Saisie, Workflow et Reporting doivent tourner.

```bash
cd afb-rations/backend

mvn -pl service-reporting test
mvn -pl service-reporting spring-boot:run
```

```bash
curl -H "Authorization: Bearer <jeton_arh>" \
  "http://localhost:8085/reporting/demandes?periode=2026-08&codeUnite=00002&page=0&size=10"

curl -H "Authorization: Bearer <jeton_arh>" \
  "http://localhost:8085/reporting/demandes?nature=RATION&session=JOUR"

curl -H "Authorization: Bearer <jeton_arh>" \
  http://localhost:8085/reporting/processus/1/historique
```

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| `mvn -pl service-reporting test` | BUILD SUCCESS |
| Onze tests du sous-sprint | Tous passants |
| Filtres combinés inter-sources | Résultats cohérents |
| Aucun résultat | Réponse vide explicite |
| Portée locale | Seules les unités accessibles |
| Service injoignable | Comportement conforme à la décision |
| Historique après retour | Tous les passages visibles |
| Temps de réponse | Sous la cible de trois secondes |
| Aucun accès direct à une base | Vérifié par cartographie |

## 10. Points de vigilance

- **Le service Reporting n'a pas de base.** Si l'assistant propose une entité JPA ou une datasource, arrêter : c'est une lecture pure par API.
- L'agrégation inter-services est le point de performance du module. Une stratégie naïve qui appellerait un service par ligne de résultat ferait exploser le temps de réponse.
- La portée d'accès doit s'appliquer au reporting comme ailleurs. Un utilisateur qui verrait les états d'une autre unité par le suivi contournerait le cloisonnement.
- L'historique doit montrer tous les passages, y compris les répétitions au même niveau après retour. Un historique qui n'afficherait que le dernier passage priverait le contrôle interne de sa valeur.
- Une recherche sans résultat n'est pas une erreur. Retourner un 404 obligerait l'interface à traiter un cas normal comme une panne.
- Ne pas implémenter les rapports ni les exports ici : c'est le sous-sprint 6.2.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Stratégie d'agrégation arbitrée et documentée | Fait |
| Comportement en cas de service injoignable tranché | Fait |
| Clients de lecture isolés derrière des interfaces | Fait |
| Recherche multicritère fonctionnelle sur les deux sources | Vérifié |
| Portée d'accès appliquée | Vérifié |
| Historique complet, passages successifs visibles | Vérifié |
| Cible de trois secondes tenue | Vérifié |
| Onze tests passants | Vérifié |
| Aucune base ni entité JPA dans ce service | Vérifié |

## 12. Commit

```bash
git add .
git commit -m "sprint-6.1: suivi des demandes et recherche multicritere

- Agregation par lecture des api saisie et workflow, sans base propre
- Recherche combinant des criteres de deux sources
- Historique complet des validations et retours
- Portee d'acces appliquee a la lecture

Refs: US-15, CT-30, CT-31"
```

---

**Fin du Sprint 6.1** — en attente de validation avant le Sprint 6.2
