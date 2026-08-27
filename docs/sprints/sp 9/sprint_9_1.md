# SPRINT 9.1

## Tests d'intégration du flux complet

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Vérifier automatiquement le parcours de bout en bout, à travers les six services |
| **Livrable** | Suite de tests d'intégration, environnement de test reproductible |
| **Durée** | Une à deux journées |
| **Prérequis** | Sprint 8.3 validé et commité |
| **Sprint suivant** | 9.2, recette fonctionnelle |

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Environnement de test (étapes 2-3) | Opus | Élevé |
| Scénarios de bout en bout (étapes 4-6) | Opus | Élevé |

Maintenir Opus effort élevé. Les tests d'intégration traversent six services et un bus : leur conception détermine ce qu'on saura vraiment du module.

## 2. Outil de cartographie

```
py -3.14 -m graphify update .
```

## 3. Contexte

Chaque service a ses tests unitaires et ses tests d'intégration locaux. Aucun test ne vérifie encore le parcours **complet**, celui qui traverse les six services, la passerelle et le bus Kafka.

C'est pourtant là que se logent les erreurs qui ont échappé aux sprints précédents : une donnée perdue entre deux services, un contrat d'appel divergent, un statut qui ne remonte pas. Chaque service fonctionne correctement isolément, et l'ensemble échoue.

Le document de scénarios décrit dix scénarios et quarante cas de test. Ce sous-sprint automatise les parcours structurants ; le sous-sprint 9.2 déroulera la recette complète, y compris ce qui ne s'automatise pas.

## 4. Objectifs

- Environnement de test reproductible, avec les dépendances externes
- Jeu de données de test réaliste et rejouable
- Test du parcours complet sous le seuil
- Test du parcours complet au-dessus du seuil
- Test du parcours avec retour et resoumission
- Test du parcours de régularisation, si le Sprint 6bis a été réalisé

## 5. Règles et stories concernées

L'ensemble des quinze règles est traversé. Les cas de test du document de scénarios servent de référence, en particulier les scénarios SC-02 à SC-06 et SC-09.

## 6. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis CLAUDE.md integralement, et le document de scenarios
pour les dix scenarios de test. Confirme en 3 lignes le parcours
complet d'un etat mensuel.

CONTEXTE DE CETTE SESSION : Sprint 9.1, tests d'integration. Chaque
service a ses tests locaux ; aucun ne verifie encore le parcours
complet a travers les six services et le bus.
SERVICE CONCERNE : tous, en integration.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Un test d'integration doit etre rejouable : deux executions
  successives donnent le meme resultat.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : propose la strategie d'environnement de test. Les
tests ont besoin de PostgreSQL, de Kafka et d'un fournisseur
d'identite. Presente-moi les options : conteneurs ephemeres pilotes
par les tests, environnement partage prealablement demarre, ou
mixte. Precise pour chacune la reproductibilite et le temps
d'execution. Attends ma decision.
```

### Étape 2. Environnement de test

```
Une fois la strategie arbitree, mets-la en place.

Pour l'authentification, deux options : un fournisseur d'identite
demarre pour les tests, ou des jetons forges valides par une cle de
test. La seconde est plus rapide mais s'ecarte du fonctionnement
reel.

Presente les deux, en precisant laquelle detecterait une erreur de
configuration de la validation du jeton. J'arbitre.

Montre la configuration.
```

### Étape 3. Jeu de données de test

```
Cree le jeu de donnees de test :

- Six utilisateurs, un par role, avec les codes unite du referentiel.
- Les quatre grilles actives couvrant les combinaisons nature et
  session.
- Un parametre de seuil.

Donnees camerounaises, codes guichets reels, conformement au document
maitre section 7.5.

Le jeu doit etre remis a l'etat initial entre deux executions : un
test qui depend du resultat du precedent devient impossible a
diagnostiquer quand il echoue.

Montre les fichiers.
```

### Étape 4. Parcours sous le seuil

```
Ecris le premier test de bout en bout, parcours nominal sous le
seuil :

1. Declenchement d'un processus par l'agent.
2. Ouverture d'une fiche et saisie de plusieurs lignes, sur
   plusieurs journees.
3. Verification du montant resolu depuis la grille.
4. Consultation de l'etat consolide, verification du total.
5. Soumission, verification de la signature et du statut.
6. Validation par le Chef d'Unite.
7. Verification de l'aiguillage : cloture directe, montant sous le
   seuil.
8. Verification de la publication sur le topic de l'etat valide.
9. Publication d'un accuse d'integration.
10. Verification de la remontee du statut d'integration.
11. Consultation du suivi, verification de l'etat final.

Chaque etape verifie un resultat observable, pas seulement l'absence
d'erreur.

Montre le fichier de test.
```

### Étape 5. Parcours au-dessus du seuil et retour

```
Ecris les deux tests suivants :

Parcours au-dessus du seuil : identique au precedent, avec des
montants portant le total au-dela du seuil, et une etape
supplementaire de validation par le Directeur Reseau. Verifie que le
dossier passe bien par EN_ATTENTE_DR.

Parcours avec retour : soumission, retour motive par le Directeur
Reseau, verification que l'etat revient a l'agent et non au Chef
d'Unite, correction, resoumission, validation complete, cloture.

Le second verifie RG-11 dans les conditions reelles, a travers les
services.

Montre les fichiers.
```

### Étape 6. Parcours de régularisation

**À sauter si le Sprint 6bis n'a pas été réalisé.**

```
Ecris le test du parcours de regularisation :

1. Un etat normal cloture, avec un beneficiaire paye le 10 juillet.
2. Ouverture d'un etat complementaire sur juillet.
3. Tentative de ressaisie du 10 juillet pour ce beneficiaire :
   refusee.
4. Saisie du 15 juillet pour ce meme beneficiaire : acceptee.
5. Soumission, validation, cloture.
6. Verification qu'une seconde transmission a bien eu lieu, distincte
   de la premiere.
7. Verification que l'etat d'origine est reste inchange du debut a la
   fin.

Ce test rejoue les cas CT-34 a CT-37 du document de scenarios.

Montre le fichier.
```

## 7. Fichiers à créer

| Chemin | Nature |
|---|---|
| `backend/integration-tests/` | Module ou dossier de tests d'intégration |
| `.../ParcoursSousSeuil IT` | Test de bout en bout |
| `.../ParcoursAuDessusSeuil IT` | Test de bout en bout |
| `.../ParcoursAvecRetour IT` | Test de bout en bout |
| `.../ParcoursRegularisation IT` | Test de bout en bout, conditionnel |
| `.../donnees-test/` | Jeu de données initial |
| `docs/environnement-tests.md` | Stratégie retenue |

## 8. Commandes terminal

```bash
cd afb-rations/backend

mvn verify -P integration-tests
```

Exécution répétée, pour vérifier la reproductibilité :

```bash
mvn verify -P integration-tests
mvn verify -P integration-tests
```

Attendu : résultat identique aux deux exécutions.

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| Parcours sous le seuil | Clôture directe, transmission, statut remonté |
| Parcours au-dessus du seuil | Passage par EN_ATTENTE_DR, puis clôture |
| Retour du Directeur Réseau | Retour à l'agent, jamais au Chef d'Unité |
| Resoumission après correction | Circuit repris depuis le début |
| Régularisation | Ressaisie refusée, journée nouvelle acceptée |
| État d'origine après régularisation | Inchangé |
| Deux exécutions successives | Résultat identique |
| Montants vérifiés à chaque étape | Cohérents de bout en bout |

## 10. Points de vigilance

- **Un test d'intégration doit être rejouable.** S'il dépend de l'état laissé par l'exécution précédente, son échec devient impossible à diagnostiquer, et il finit par être ignoré.
- Vérifier des résultats observables, pas seulement l'absence d'erreur. Un test qui se contente de vérifier qu'aucune exception n'a été levée passe alors même que les montants sont faux.
- Le test avec retour est celui qui vérifie RG-11 dans les conditions réelles. C'était le piège identifié au Sprint 4.4 : ici, on le confirme à travers les services.
- La vérification de la publication Kafka est indispensable. Un parcours qui se termine sur une clôture sans contrôler le message publié laisserait passer une transmission défaillante.
- Le choix du fournisseur d'identité en test a une conséquence : des jetons forgés vont plus vite mais ne détecteraient pas une erreur de configuration de la validation.
- Ces tests seront longs. Les isoler dans un profil dédié évite de ralentir la compilation quotidienne.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Stratégie d'environnement arbitrée et documentée | Fait |
| Choix du fournisseur d'identité en test tranché | Fait |
| Jeu de données réaliste et réinitialisable | Fait |
| Trois parcours de bout en bout automatisés | Vérifié |
| Parcours de régularisation, si applicable | Vérifié |
| Publication Kafka vérifiée dans les tests | Vérifié |
| Reproductibilité sur deux exécutions | Vérifié |
| Tests isolés dans un profil dédié | Fait |

## 12. Commit

```bash
git add .
git commit -m "sprint-9.1: tests d'integration du flux complet

- Environnement de test reproductible et jeu de donnees reinitialisable
- Parcours sous seuil, au-dessus du seuil et avec retour
- Verification de la publication kafka et de la remontee du statut
- Parcours de regularisation

Refs: document de scenarios, SC-02 a SC-06 et SC-09"
```

---

**Fin du Sprint 9.1** — en attente de validation avant le Sprint 9.2
