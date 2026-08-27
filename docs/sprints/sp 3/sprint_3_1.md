# SPRINT 3.1

## Domaine de la saisie : bénéficiaire, fiche et ligne

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Créer les trois entités du service Saisie et clarifier leur rattachement au processus mensuel |
| **Livrable** | Entités, repositories, tests, convention de rattachement au processus |
| **Durée** | Une journée |
| **Prérequis** | Sprint 2.4 validé et commité |
| **Sprint suivant** | 3.2, valorisation et contrôle des doublons |

## 0. Héritage du Sprint 1.3 — à faire avant toute autre étape

Deux obligations transverses, décidées au Sprint 1.3 et **non facultatives**.
Elles sont inscrites ici parce qu'aucune session ne relit les documents de
décision d'elle-même : ne pas compter sur la mémoire d'une session précédente.
Voir CLAUDE.md section 17.

### 0.1 Dépendance au module de publication d'audit

Ajouter au `pom.xml` de **service-saisie** :

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
| Entités et repositories (étapes 2-4) | Sonnet | Moyen |
| Rattachement au processus (étape 5) | Opus | Élevé |

**Changement manuel à l'étape 5.** Le rattachement au processus mensuel pose une question d'architecture entre deux services : basculer en Opus effort élevé pour cette étape, puis revenir.

## 2. Outil de cartographie

Recommandée en début de sous-sprint. Le service Saisie est vide, mais la cartographie confirmera qu'il ne contracte aucune dépendance en cours de route.

```
py -3.14 -m graphify update .
```

## 3. Contexte

Premier sous-sprint du service Saisie, cœur métier du module. C'est ici que l'agent d'unité enregistre, jour après jour, les agents de garde servis en ration ou en transport.

Une particularité forte de ce processus, déjà relevée pendant la phase documentaire : **il n'y a pas d'enrôlement**. Contrairement à d'autres modules du programme, aucun référentiel de bénéficiaires n'existe en amont. Le bénéficiaire est créé au moment de la première saisie qui le concerne. Il ne faut donc ni référentiel préalable, ni import, ni liste de bénéficiaires attendus.

Un point d'architecture demande un arbitrage dans ce sous-sprint. La table `fiche_journaliere` porte `id_processus`, qui désigne un processus mensuel vivant dans une autre base, celle du service Workflow. Aucune clé étrangère n'est possible entre deux bases : c'est une référence logique inter-services, et sa gestion doit être décidée maintenant.

## 4. Objectifs

- Entités `Beneficiaire`, `FicheJournaliere` et `LignePrestation`, conformes au dictionnaire
- Repositories avec les recherches nécessaires aux sous-sprints suivants
- Recherche ou création du bénéficiaire au fil de la saisie
- Convention de rattachement au processus mensuel arrêtée et documentée
- Tests JUnit du domaine

## 5. Règles et stories concernées

| Référence | Objet |
|---|---|
| RG-01 | Nature RATION ou TRANSPORT exclusivement |
| RG-02 | Session JOUR ou SOIR exclusivement |
| RG-05 | Fiche vierge à chaque nouveau jour |
| US-03 | Saisie des bénéficiaires jour par jour |

RG-03, RG-04 et RG-06 relèvent des sous-sprints suivants.

## 6. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis le fichier CLAUDE.md a la racine, section 4 pour les
tables beneficiaires, fiche_journaliere et ligne_prestation. Confirme
en 3 lignes ce que tu y as trouve, en citant notamment la distinction
entre code_agence et code_unite.

CONTEXTE DE CETTE SESSION : Sprint 3.1, domaine de la saisie. Les
trois tables existent en base depuis le Sprint 0.5. On cree le code
qui les exploite. Aucun endpoint dans ce sous-sprint.

Rappel important : il n'y a AUCUN enrolement dans ce processus. Le
beneficiaire est cree au moment de la premiere saisie qui le
concerne. Ne cree ni referentiel prealable, ni import, ni liste de
beneficiaires attendus.
SERVICE CONCERNE : service-saisie uniquement.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Chaque methode publique vient avec ses tests JUnit.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : cree l'entite Beneficiaire, strictement conforme au
dictionnaire : id, nom, prenom, num_compte_courant, code_agence,
actif, date_creation.

Attention : code_agence est l'agence de domiciliation du compte du
beneficiaire. Le code_unite, qui designe l'unite supportant la
charge, n'a rien a faire dans cette table. Montre le fichier.
```

### Étape 2. Fiche journalière et ligne de prestation

```
Cree les deux entites restantes, conformes au dictionnaire :

FicheJournaliere : id, id_processus, date_jour, statut.
LignePrestation : id, id_fiche_journaliere, id_beneficiaire, nature,
session, montant_applique.

nature, session et statut sont types par leurs enumerations.

id_processus n'est PAS une association JPA : le processus vit dans
une autre base, celle du service Workflow. C'est un identifiant
simple. Ne cree aucune relation vers une entite ProcessusMensuel qui
n'existe pas dans ce service.

Montre les deux fichiers.
```

### Étape 3. Repositories

```
Cree les trois repositories.

BeneficiaireRepository :
1. recherche par nom, prenom et numero de compte courant, pour
   retrouver un beneficiaire deja saisi
2. recherche paginee par nom partiel

FicheJournaliereRepository :
3. findByIdProcessusAndDateJour, qui portera RG-05
4. findByIdProcessus, pour la consolidation du sous-sprint 3.4

LignePrestationRepository :
5. findByIdFicheJournaliere
6. la recherche qui portera RG-04 au sous-sprint 3.2 : existence
   d'une ligne pour un beneficiaire, une fiche, une nature et une
   session donnes

Montre les fichiers.
```

### Étape 4. Recherche ou création du bénéficiaire

```
Cree le service qui resout un beneficiaire au moment de la saisie :

Entree : nom, prenom, numero de compte courant, code agence.
Comportement : si un beneficiaire correspondant existe deja, le
retourner ; sinon, le creer.

Deux questions a trancher avant d'ecrire, ne decide pas seul :
1. Sur quels champs identifie-t-on un beneficiaire deja connu ? Le
   numero de compte courant seul, ou la combinaison nom, prenom et
   compte ? Un meme agent ressaisi avec une faute de frappe dans son
   nom creerait un doublon de beneficiaire, ce qui fausserait RG-04.
2. Que faire si le numero de compte correspond a un beneficiaire
   existant mais avec un nom different ? Correction de saisie ou
   erreur de compte, les consequences ne sont pas les memes.

Presente les options, attends ma decision.
```

### Étape 5. Rattachement au processus mensuel

**Étape en Opus, effort élevé.**

```
Point d'architecture a arbitrer.

fiche_journaliere.id_processus designe un processus mensuel qui vit
dans la base du service Workflow. Aucune cle etrangere n'est possible
entre deux bases.

La saisie ne peut pas commencer sans processus : une fiche doit etre
rattachee a un processus mensuel existant, pour une unite et une
periode donnees.

Presente-moi les options, avec leurs consequences sur le couplage,
sur la coherence des donnees et sur l'experience de l'agent :

1. L'agent declenche d'abord le processus via le service Workflow,
   puis saisit. La saisie recoit un identifiant de processus deja
   valide.
2. Le service Saisie appelle le service Workflow pour obtenir ou
   creer le processus du mois, de facon transparente pour l'agent.
3. Une autre option que tu identifies.

Pour chacune : que se passe-t-il si le service Workflow est
injoignable ? Comment garantir qu'aucune fiche ne reference un
processus inexistant ? Comment la saisie sait-elle qu'un processus
est encore modifiable, alors que le statut vit dans l'autre service ?

Ne code rien. Attends ma decision, puis redige-la dans docs/ et
signale-moi qu'elle doit rejoindre CLAUDE.md a la cloture du Sprint 3.
```

### Étape 6. Tests

```
Ecris les tests du domaine :

1. creation d'un beneficiaire inconnu : cree et retourne
2. resolution d'un beneficiaire deja connu : retourne l'existant,
   sans doublon
3. comportement conforme aux deux decisions de l'etape 4
4. recherche de fiche par processus et date : retrouve la bonne fiche
5. recherche de fiche pour une date sans fiche : ne retourne rien
6. la recherche d'existence de ligne retourne vrai sur une
   combinaison presente
7. elle retourne faux quand la session differe
8. elle retourne faux quand la nature differe

Les tests 7 et 8 sont importants : ils prouvent que RG-04 portera
bien sur la combinaison complete et non sur le seul beneficiaire.

Donnees camerounaises, codes guichets reels (00002 pour Douala
Bonanjo). Montre les fichiers de test.
```

## 7. Fichiers à créer

| Chemin | Nature |
|---|---|
| `service-saisie/.../domaine/Beneficiaire.java` | Entité |
| `service-saisie/.../domaine/FicheJournaliere.java` | Entité |
| `service-saisie/.../domaine/LignePrestation.java` | Entité |
| `service-saisie/.../domaine/StatutFicheEnum.java` | Énumération |
| `service-saisie/.../infrastructure/BeneficiaireRepository.java` | Repository |
| `service-saisie/.../infrastructure/FicheJournaliereRepository.java` | Repository |
| `service-saisie/.../infrastructure/LignePrestationRepository.java` | Repository |
| `service-saisie/.../application/ResolutionBeneficiaireService.java` | Recherche ou création |
| `service-saisie/src/test/...` | Tests du domaine |
| `docs/rattachement-processus.md` | Décision de l'étape 5 |

## 8. Commandes terminal

```bash
cd afb-rations/backend

mvn -pl service-saisie test
mvn -pl service-saisie spring-boot:run
```

Contrôle du schéma :

```sql
\c rations_saisie
\d beneficiaires
\d fiche_journaliere
\d ligne_prestation
```

Attendu : `code_agence` sur `beneficiaires`, aucun `code_unite` dans ce schéma.

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| `mvn -pl service-saisie test` | BUILD SUCCESS |
| Huit tests du sous-sprint | Tous passants |
| Entités conformes au dictionnaire | Comparaison champ par champ |
| `code_agence` présent, `code_unite` absent | Vérifié |
| Aucune association JPA vers un processus | Vérifié |
| Bénéficiaire connu non dupliqué | Vérifié |
| Recherche d'existence sensible à la nature et à la session | Vérifié |
| Aucun endpoint créé | Vérifié |
| Aucune dépendance vers un autre service | Vérifié |

## 10. Points de vigilance

- **Aucun enrôlement.** Si l'assistant propose un référentiel de bénéficiaires, un import ou une liste préalable, arrêter : c'est un réflexe hérité d'autres modules, explicitement écarté ici.
- `id_processus` n'est pas une association JPA. Une entité `ProcessusMensuel` créée dans le service Saisie serait une duplication du domaine d'un autre service, et une faute d'architecture.
- Le critère d'identification d'un bénéficiaire conditionne la fiabilité de RG-04. Un critère trop lâche crée des doublons de bénéficiaires, un critère trop strict empêche de retrouver un agent déjà saisi.
- `code_agence` appartient au bénéficiaire, `code_unite` au processus. Les confondre fausserait le sens comptable : l'un porte le crédit, l'autre le débit.
- La décision de l'étape 5 engage tout le Sprint 4. La prendre à la légère se paierait au moment du workflow.
- Ne créer aucun endpoint dans ce sous-sprint.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Trois entités conformes au dictionnaire | Vérifié |
| Aucune entité de processus créée dans ce service | Vérifié |
| Trois repositories avec leurs six recherches | Fait |
| Critère d'identification du bénéficiaire tranché | Fait |
| Comportement en cas de compte connu avec nom différent tranché | Fait |
| Convention de rattachement au processus arrêtée et documentée | Fait |
| Huit tests passants | Vérifié |
| Aucun référentiel ni import de bénéficiaires | Vérifié |
| Aucune dépendance vers un autre service | Vérifié |

## 12. Commit

```bash
git add .
git commit -m "sprint-3.1: domaine de la saisie

- Trois entites conformes au dictionnaire de donnees
- Resolution du beneficiaire au fil de la saisie, sans enrolement
- Convention de rattachement au processus mensuel arretee
- Tests du domaine

Refs: RG-01, RG-02, RG-05, US-03"
```

---

**Fin du Sprint 3.1** — en attente de validation avant le Sprint 3.2
