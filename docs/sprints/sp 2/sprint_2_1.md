# SPRINT 2.1

## Domaine Grille tarifaire et cycle de statuts

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Créer l'entité Grille tarifaire, son repository et la mécanique de ses statuts |
| **Livrable** | Entité, repository, machine à états des statuts, tests JUnit |
| **Durée** | Une journée |
| **Prérequis** | Sprint 1.3 validé et commité |
| **Sprint suivant** | 2.2, cycle de vie côté Analyste RH |

## 0. Héritage du Sprint 1.3 — à faire avant toute autre étape

Deux obligations transverses, décidées au Sprint 1.3 et **non facultatives**.
Elles sont inscrites ici parce qu'aucune session ne relit les documents de
décision d'elle-même : ne pas compter sur la mémoire d'une session précédente.
Voir CLAUDE.md section 17.

### 0.1 Dépendance au module de publication d'audit

Ajouter au `pom.xml` de **service-grilles** :

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
| Entité et repository (étapes 2-3) | Sonnet | Moyen |
| Machine à états et tests (étapes 4-5) | Sonnet | Moyen |

**Changement manuel à prévoir au sous-sprint suivant.** Le 2.2 passe en Opus effort élevé : c'est là que RG-14 commence à s'appliquer. Ce sous-sprint reste en Sonnet, il ne porte que la structure.

## 2. Outil de cartographie

Facultatif. Le service Grilles est vide, seul son squelette existe depuis le Sprint 0.2.

## 3. Contexte

Premier sous-sprint du service Grilles. Ce service conditionne toute la chaîne : sans grille active, aucune ligne de prestation ne peut être valorisée, donc aucune saisie n'est possible au Sprint 3.

La table `grille_tarifaire` et son index partiel existent déjà en base depuis le Sprint 0.5, avec quatre grilles actives chargées en données de référence. Ce sous-sprint ne crée pas le schéma : il crée le code qui l'exploite.

L'entité porte un cycle de statuts à quatre états, décrit par le diagramme d'état-transition ET02 du document de conception. Ce sous-sprint construit la mécanique de ce cycle, sans encore l'exposer : les endpoints viennent aux sous-sprints 2.2 et 2.3.

## 4. Objectifs

- Entité `GrilleTarifaire` conforme au dictionnaire de données
- Repository avec les recherches dont les sous-sprints suivants auront besoin
- Machine à états des quatre statuts, avec les transitions autorisées et interdites
- Tests JUnit couvrant chaque transition, valide comme invalide

## 5. Règles et stories concernées

| Référence | Objet |
|---|---|
| RG-14 | Cycle de validation, une seule grille active par couple nature et session |
| US-13 | Paramétrage des grilles par l'Analyste RH |
| ET02 | Diagramme d'état-transition du document de conception |

Rappel des transitions autorisées, d'après ET02 :

| Depuis | Vers | Déclencheur |
|---|---|---|
| (création) | BROUILLON | Création par l'ARH |
| BROUILLON | BROUILLON | Ajustement avant soumission |
| BROUILLON | EN_ATTENTE_DRH | Soumission à la DRH |
| EN_ATTENTE_DRH | ACTIVE | Validation DRH |
| EN_ATTENTE_DRH | REJETEE | Rejet DRH avec motif |
| ACTIVE | (fermée) | Activation d'une remplaçante ou désactivation |

Toute autre transition est interdite. Une grille REJETEE ne revient pas en BROUILLON, elle est conservée pour l'historique.

## 6. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis le fichier CLAUDE.md a la racine, section 4 pour la
table grille_tarifaire et section 6 pour RG-14. Confirme en 3 lignes
ce que tu y as trouve.

CONTEXTE DE CETTE SESSION : Sprint 2.1, domaine Grille tarifaire. La
table et son index partiel existent deja en base depuis le Sprint
0.5, avec quatre grilles actives en donnees de reference. On cree le
code qui les exploite : entite, repository, machine a etats. Aucun
endpoint dans ce sous-sprint.
SERVICE CONCERNE : service-grilles uniquement.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Chaque methode publique vient avec ses tests JUnit.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question
  plutot que de supposer.

PREMIERE ACTION : cree l'entite GrilleTarifaire dans le package
domaine, strictement conforme au dictionnaire de donnees : id,
nature, session, montant_fcfa, date_debut, date_fin,
statut_validation, id_createur, id_validateur, date_creation,
date_validation, motif_rejet.

nature, session et statut_validation sont types par leurs
enumerations, pas par des chaines libres. Montre le fichier.
```

### Étape 2. Énumérations

```
Cree les enumerations du service, conformes au dictionnaire :
NatureEnum (RATION, TRANSPORT), SessionEnum (JOUR, SOIR) et
StatutGrilleEnum (BROUILLON, EN_ATTENTE_DRH, ACTIVE, REJETEE).

Question avant d'ecrire : NatureEnum et SessionEnum seront aussi
utilisees par service-saisie au Sprint 3. Comment eviter la
duplication sans creer de dependance entre services ? Presente-moi
les options et leurs consequences, je tranche.
```

### Étape 3. Repository

```
Cree GrilleTarifaireRepository avec :

1. findByStatutValidation(StatutGrilleEnum statut) : List
2. la recherche de la grille active pour un couple nature et session
   a une date donnee (statut ACTIVE, date_debut inferieure ou egale
   a la date, date_fin nulle ou posterieure)
3. la verification d'existence d'une grille active pour un couple
   nature et session
4. une methode paginee avec filtre optionnel sur le statut

Pour la deuxieme, montre-moi ta requete avant de l'ecrire : c'est
elle qui portera RG-03 au sous-sprint 2.4, elle doit etre exacte sur
les bornes de dates.
```

### Étape 4. Machine à états

```
Cree la mecanique des transitions de statut, selon le tableau ET02
du guide de ce sous-sprint.

Expose au minimum :
1. une methode indiquant si une transition est autorisee depuis un
   statut vers un autre
2. les methodes de transition : soumettre, valider, rejeter avec
   motif, fermer

Chaque transition interdite leve une erreur explicite, pas une
erreur generique. Le motif est obligatoire au rejet.

Ne place pas la logique de fermeture de l'ancienne grille ici :
elle releve du service de validation, au sous-sprint 2.3. Ici, on
ne gere que le statut d'une grille prise isolement.

Montre le fichier.
```

### Étape 5. Tests JUnit

```
Ecris les tests de la machine a etats :

Transitions valides :
1. creation vers BROUILLON
2. BROUILLON vers EN_ATTENTE_DRH
3. EN_ATTENTE_DRH vers ACTIVE
4. EN_ATTENTE_DRH vers REJETEE avec motif
5. ACTIVE vers fermee

Transitions invalides, chacune levant une erreur explicite :
6. BROUILLON vers ACTIVE, en sautant la validation
7. REJETEE vers BROUILLON
8. ACTIVE vers EN_ATTENTE_DRH
9. rejet sans motif

Tests du repository, sur les donnees de reference du Sprint 0.5 :
10. la recherche de grille active retourne bien une grille pour
    RATION et JOUR
11. la recherche a une date anterieure a date_debut ne retourne rien

Donnees camerounaises si des noms sont necessaires.
Montre les fichiers de test.
```

## 7. Fichiers à créer

| Chemin | Nature |
|---|---|
| `service-grilles/.../domaine/GrilleTarifaire.java` | Entité |
| `service-grilles/.../domaine/NatureEnum.java` | Énumération |
| `service-grilles/.../domaine/SessionEnum.java` | Énumération |
| `service-grilles/.../domaine/StatutGrilleEnum.java` | Énumération |
| `service-grilles/.../domaine/TransitionGrille.java` | Machine à états |
| `service-grilles/.../infrastructure/GrilleTarifaireRepository.java` | Repository |
| `service-grilles/src/test/.../TransitionGrilleTest.java` | Tests des transitions |
| `service-grilles/src/test/.../GrilleTarifaireRepositoryTest.java` | Tests du repository |

## 8. Commandes terminal

```bash
cd afb-rations/backend

mvn -pl service-grilles test
mvn -pl service-grilles spring-boot:run
```

Vérification des données de référence en base :

```sql
\c rations_grilles
SELECT nature, session, montant_fcfa, statut_validation, date_debut, date_fin
FROM grille_tarifaire;
```

Attendu : quatre lignes ACTIVE, `date_fin` nulle, une par combinaison.

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| `mvn -pl service-grilles test` | BUILD SUCCESS |
| Onze tests du sous-sprint | Tous passants |
| Entité conforme au dictionnaire | Comparaison champ par champ |
| Types énumérés, pas de chaîne libre | Vérifié |
| Transitions interdites | Erreur explicite, jamais silencieuse |
| Rejet sans motif | Refusé |
| Recherche de grille active | Retourne la bonne grille |
| Recherche à une date hors validité | Ne retourne rien |
| Démarrage du service | Sonde de santé en UP |

## 10. Points de vigilance

- **Ne pas recréer la table.** Elle existe depuis le Sprint 0.5, avec son index partiel. L'entité doit s'y conformer, pas l'inverse. Si Hibernate propose de modifier le schéma au démarrage, vérifier que la validation de schéma est bien configurée et non la génération automatique.
- La requête de grille active est la pièce maîtresse de RG-03. Une borne de date mal posée produirait un montant faux, ou aucun montant. Elle mérite d'être relue attentivement.
- `date_fin` nulle signifie grille courante, pas grille sans fin de validité. La confusion est fréquente et fausse la requête.
- Les transitions interdites doivent lever une erreur, jamais retourner un booléen ignoré par l'appelant. Une transition silencieusement refusée laisserait une grille dans un état incohérent.
- Ne pas implémenter la fermeture de l'ancienne grille ici. Cette opération touche deux lignes et relève du sous-sprint 2.3, où elle sera transactionnelle.
- Ne créer aucun endpoint dans ce sous-sprint.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Entité conforme au dictionnaire | Vérifié |
| Trois énumérations créées, question de partage tranchée | Fait |
| Repository avec les quatre recherches | Fait |
| Requête de grille active relue et validée | Fait |
| Machine à états couvrant les cinq transitions valides | Fait |
| Quatre transitions invalides levant une erreur explicite | Vérifié |
| Onze tests passants | Vérifié |
| Aucun endpoint créé | Vérifié |
| Aucune modification du schéma de base | Vérifié |

## 12. Commit

```bash
git add .
git commit -m "sprint-2.1: domaine grille tarifaire et cycle de statuts

- Entite conforme au dictionnaire de donnees
- Repository avec recherche de la grille active par date
- Machine a etats des quatre statuts
- Tests des transitions valides et invalides

Refs: RG-14, US-13, ET02"
```

---

**Fin du Sprint 2.1** — en attente de validation avant le Sprint 2.2
