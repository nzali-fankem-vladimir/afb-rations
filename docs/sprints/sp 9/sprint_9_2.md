# SPRINT 9.2

## Recette fonctionnelle

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Dérouler les quarante cas de test du document de scénarios et consigner les résultats |
| **Livrable** | Cahier de recette renseigné, anomalies qualifiées et corrigées |
| **Durée** | Une à deux journées |
| **Prérequis** | Sprint 9.1 validé et commité |
| **Sprint suivant** | 9.3, performance, sécurité et robustesse |

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Déroulé de la recette (étapes 2-4) | Sonnet | Moyen |
| Qualification et correction des anomalies (étape 5) | Opus | Élevé |

**Changement manuel à l'étape 5**, si des anomalies de règle métier apparaissent.

## 2. Outil de cartographie

Utile seulement en cas d'anomalie touchant plusieurs services.

## 3. Contexte

Les tests d'intégration vérifient que le module fonctionne. La recette vérifie qu'il **fait ce qui a été demandé**. Ce n'est pas la même chose : un module peut fonctionner parfaitement et s'écarter du besoin exprimé.

Le document de scénarios définit dix scénarios et quarante cas de test, chacun avec son action, son résultat attendu et son type. Ce sous-sprint les déroule tous, en conditions réelles, à travers l'interface, avec les six rôles.

Une partie a déjà été couverte automatiquement au sous-sprint 9.1. Ces cas se revérifient malgré tout à l'écran : un comportement correct en API peut être mal restitué à l'interface.

## 4. Objectifs

- Cahier de recette rédigé, reprenant les quarante cas
- Déroulé complet, avec les six rôles, à travers l'interface
- Anomalies consignées, qualifiées et corrigées
- Écarts entre le comportement observé et les spécifications tranchés

## 5. Règles et stories concernées

Les quinze règles, les dix-neuf stories et les quarante cas de test du document de scénarios.

## 6. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis le document de scenarios, les dix scenarios et les
quarante cas de test. Confirme en 3 lignes leur repartition par type.

CONTEXTE DE CETTE SESSION : Sprint 9.2, recette fonctionnelle. Les
tests d'integration du Sprint 9.1 verifient que le module fonctionne ;
la recette verifie qu'il fait ce qui a ete demande.
SERVICE CONCERNE : tous, en verification.

METHODE DE TRAVAIL :
- Je deroule les cas, je te remonte les resultats.
- Tu m'aides a qualifier les ecarts : anomalie, ecart de
  specification, ou comportement attendu mal compris.
- Tu ne corriges rien avant que nous ayons qualifie l'ecart.

PREMIERE ACTION : produis le cahier de recette a partir du document
de scenarios. Un tableau par scenario, avec pour chaque cas : son
identifiant, l'action, le resultat attendu, le role a utiliser, et
une colonne de resultat a renseigner. Ajoute une colonne indiquant si
le cas est deja couvert par un test automatise du Sprint 9.1.
```

### Étape 2. Recette des parcours de saisie

```
Deroule les scenarios SC-01 a SC-03, en te connectant avec les
utilisateurs de test correspondants.

SC-01, authentification et habilitations : cinq cas.
SC-02, saisie journaliere : cinq cas.
SC-03, consolidation et soumission : trois cas.

Renseigne le cahier au fur et a mesure. Pour chaque ecart, note
precisement ce que tu as observe, pas seulement que ca a echoue.
```

### Étape 3. Recette des parcours de validation

```
Deroule les scenarios SC-04 a SC-06.

SC-04, validation du Chef d'Unite et aiguillage : cinq cas, dont la
verification du seuil et la modification du parametre.
SC-05, validation du Directeur Reseau : quatre cas, dont le detail
transmis a la comptabilite et le refus de seconde transmission.
SC-06, traitement d'un etat retourne : deux cas.

Le cas CT-18, modification du seuil, demande d'agir en base entre
deux validations : prepare-le avant de commencer.
```

### Étape 4. Recette des parcours transverses

```
Deroule les scenarios SC-07 a SC-10.

SC-07, grilles tarifaires : cinq cas.
SC-08, suivi et reporting : quatre cas, dont la coherence des
exports.
SC-09, etat complementaire : quatre cas, a sauter si le Sprint 6bis
n'a pas ete realise.
SC-10, administration : trois cas.

Pour SC-08, ouvre reellement les fichiers exportes et compare les
totaux a ceux affiches : la coherence exigee par CT-32 ne se verifie
pas autrement.
```

### Étape 5. Qualification et correction

**Étape en Opus, effort élevé si des règles métier sont concernées.**

```
Reprenons ensemble les ecarts consignes.

Pour chacun, qualifions :
1. Anomalie : le comportement s'ecarte de la specification. A
   corriger.
2. Ecart de specification : la specification est ambigue ou
   incomplete. A trancher avec le metier, pas a corriger seul.
3. Comportement attendu mal compris : le module est conforme, le cas
   de test etait mal formule. A corriger dans le cahier.

Traite les anomalies par ordre de gravite : d'abord celles touchant
une regle de gestion, ensuite celles touchant l'ergonomie.

Un commit par anomalie corrigee, avec le numero du cas de test en
reference.
```

### Étape 6. Nouveau déroulé

```
Apres correction, redeoule les cas concernes et les cas voisins :
une correction peut en casser un autre.

Renseigne le cahier definitif et fais-m'en la synthese : nombre de
cas passants, anomalies corrigees, ecarts de specification restant a
trancher avec le metier.
```

## 7. Fichiers à créer ou modifier

| Chemin | Nature |
|---|---|
| `docs/cahier-recette.md` | Cahier renseigné |
| Fichiers en anomalie | Corrections, un commit par anomalie |
| `docs/ecarts-specification.md` | Écarts à trancher avec le métier |

## 8. Commandes terminal

Environnement complet, en conteneurs :

```bash
cd afb-rations/infra/docker
docker compose up -d
docker compose ps
```

Préparation du cas CT-18, entre deux validations :

```sql
\c rations_workflow
UPDATE parametre_systeme SET valeur = '50000' WHERE code = 'SEUIL_AIGUILLAGE_DR';
```

Après recette, remise à la valeur initiale.

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| Quarante cas déroulés | Cahier complet |
| Cas de type erreur | Refus obtenus, messages compréhensibles |
| Cas d'aiguillage à la borne | Comportement exact |
| Cohérence des exports | Totaux identiques |
| Six rôles utilisés | Chacun sur son parcours |
| Anomalies corrigées | Cas concernés repassés |
| Cas voisins après correction | Aucune régression |
| Écarts de spécification | Consignés, non corrigés unilatéralement |

## 10. Points de vigilance

- **Consigner ce qui a été observé, pas seulement l'échec.** « CT-14 échoue » ne permet pas de diagnostiquer ; « le dossier passe au Directeur Réseau alors que le montant est de 98 000 » le permet.
- Un écart de spécification ne se corrige pas seul. Si la spécification est ambiguë, la trancher relève du métier, et le corriger dans le code figerait une interprétation non validée.
- Les cas déjà couverts au Sprint 9.1 se revérifient à l'écran. Un comportement correct côté API peut être mal restitué par l'interface, et c'est l'interface que l'utilisateur voit.
- Après correction, rejouer les cas voisins. Une correction sur l'aiguillage peut affecter la clôture, une correction sur la saisie peut affecter la consolidation.
- Les messages d'erreur font partie de la recette. Un refus correct assorti d'un message incompréhensible est une anomalie d'ergonomie, pas un succès.
- Un commit par anomalie, avec le numéro du cas en référence. Un commit global rendrait impossible de retracer une correction.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Cahier de recette produit et renseigné | Fait |
| Quarante cas déroulés avec les six rôles | Vérifié |
| Écarts qualifiés en trois catégories | Fait |
| Anomalies corrigées, un commit chacune | Fait |
| Cas corrigés et voisins repassés | Vérifié |
| Écarts de spécification consignés sans correction unilatérale | Fait |
| Synthèse de recette produite | Fait |

## 12. Commit

```bash
git add .
git commit -m "sprint-9.2: recette fonctionnelle

- Quarante cas de test deroules avec les six roles
- Anomalies qualifiees et corrigees, cas voisins repasses
- Ecarts de specification consignes pour arbitrage metier
- Cahier de recette renseigne

Refs: document de scenarios, SC-01 a SC-10"
```

---

**Fin du Sprint 9.2** — en attente de validation avant le Sprint 9.3
