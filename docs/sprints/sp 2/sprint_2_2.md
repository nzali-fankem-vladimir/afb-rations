# SPRINT 2.2

## Cycle de vie côté Analyste RH

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Permettre à l'Analyste RH de créer, ajuster, soumettre et consulter les grilles tarifaires |
| **Livrable** | Endpoints de création, de soumission et de consultation, avec le contrôle d'unicité |
| **Durée** | Une journée |
| **Prérequis** | Sprint 2.1 validé et commité |
| **Sprint suivant** | 2.3, décision de la Directrice RH |

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Contrôle d'unicité et création (étapes 2-4) | Opus | Élevé |
| Consultation et tests (étapes 5-6) | Opus | Élevé |

**Changement manuel requis.** Le sous-sprint 2.1 tournait en Sonnet. Basculer en Opus effort élevé avant de coller le premier prompt : RG-14 commence à s'appliquer ici, et le contrôle d'unicité conditionne la justesse de tous les montants payés.

## 2. Outil de cartographie

Recommandée avant l'étape 6, pour vérifier que le service Grilles reste sans dépendance vers un autre service.

```
py -3.14 -m graphify update .
```

## 3. Contexte

Le domaine et la machine à états existent. Ce sous-sprint les expose du côté de l'Analyste RH, qui propose les grilles sans jamais les rendre applicables lui-même.

Le point de fond est le **contrôle d'unicité de RG-14** : une seule grille active par couple nature et session. L'index partiel posé au Sprint 0.5 en est le garde-fou de dernier recours, mais le refus doit intervenir en amont, dans le service, avec un message compréhensible. Une contrainte de base violée produirait une erreur technique illisible pour l'utilisateur.

Deuxième point : une grille soumise **reste sans effet sur les saisies** tant que la DRH ne l'a pas validée. C'est ce qui protège la chaîne de paiement d'un montant erroné introduit par inadvertance.

## 4. Objectifs

- `POST /grilles` : création d'une grille et soumission à la DRH
- `GET /grilles` : liste filtrable par statut, pour l'ARH et la DRH
- Contrôle d'unicité avant enregistrement, avec un message explicite
- Traçabilité de la création et de la soumission dans le journal d'audit
- Tests unitaires et d'intégration couvrant les cas nominaux et de conflit

## 5. Règles et stories concernées

| Référence | Objet |
|---|---|
| RG-14 | Une seule grille active par couple nature et session, passage obligatoire par EN_ATTENTE_DRH |
| US-13 | Paramétrage des grilles par l'Analyste RH |
| CT-25 | Grille créée et soumise, sans effet sur les saisies |
| CT-26 | Seconde grille active sur le même couple : refusée |

## 6. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis le fichier CLAUDE.md a la racine, section 6, regle
RG-14. Confirme en 3 lignes ce qu'elle impose.

CONTEXTE DE CETTE SESSION : Sprint 2.2, cycle de vie cote Analyste
RH. Le domaine, la machine a etats et le repository existent depuis
le Sprint 2.1. On expose la creation, la soumission et la
consultation. La DRH est le sous-sprint suivant : aucun endpoint de
validation ici.
SERVICE CONCERNE : service-grilles uniquement.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Aucune entite JPA exposee en api : DTO en entree comme en sortie.
- Chaque methode publique vient avec ses tests.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : avant tout code, reponds a une question de fond que
les specifications ne tranchent pas explicitement. US-13 parle
d'ajouter, modifier ou desactiver une grille. Quand l'ARH modifie une
grille ACTIVE, cree-t-on une nouvelle ligne qui remplacera l'ancienne
apres validation, ou modifie-t-on la ligne existante ?

Presente-moi les deux options avec leurs consequences sur
l'historique, sur les lignes de prestation deja saisies et sur
l'index partiel. Attends ma decision.
```

### Étape 2. DTO

```
Une fois la decision prise, ecris les DTO :

1. Entree de creation : nature, session, montantFcfa, dateDebut.
   Validation : nature et session obligatoires et appartenant aux
   enumerations, montant strictement positif, dateDebut obligatoire.
2. Sortie : id, nature, session, montantFcfa, dateDebut, dateFin,
   statutValidation, createur, validateur, dateCreation,
   dateValidation, motifRejet.

Pour le createur et le validateur, expose un libelle lisible plutot
qu'un identifiant technique. Le service Grilles ne connait pas les
utilisateurs : dis-moi comment tu comptes obtenir ce libelle sans
creer de dependance vers service-identite. Presente les options.

Montre les fichiers.
```

### Étape 3. Contrôle d'unicité

```
Cree le service de controle d'unicite, cœur de RG-14 :

Avant tout enregistrement, verifier qu'aucune grille ACTIVE ne couvre
deja le meme couple nature et session sur la periode visee. Verifier
aussi qu'aucune grille n'est deja EN_ATTENTE_DRH pour ce couple :
deux propositions concurrentes sur le meme couple creeraient une
ambiguite au moment de la validation.

En cas de conflit, lever une erreur portant le code du contrat d'api
et un message explicite citant la nature et la session concernees.

Montre le service puis ses tests.
```

### Étape 4. Création et soumission

```
Cree le service et l'endpoint POST /grilles :

1. Verifie l'habilitation : role ARH.
2. Applique le controle d'unicite de l'etape 3.
3. Cree la grille au statut BROUILLON.
4. La soumet immediatement, statut EN_ATTENTE_DRH.
5. Trace la creation et la soumission dans le journal d'audit.
6. Retourne la grille creee, avec son statut.

Respecte l'ordre des operations du document maitre section 7.3 :
habilitation, chargement, regles, modification d'etat, audit,
notification.

Question a trancher avant d'ecrire : la creation et la soumission
sont-elles un seul appel, ou l'ARH doit-il pouvoir garder une grille
en BROUILLON avant de la soumettre ? Le contrat d'api ne prevoit
qu'un endpoint POST /grilles. Presente-moi les consequences des deux
lectures.

Montre le service puis le controleur.
```

### Étape 5. Consultation

```
Cree l'endpoint GET /grilles :

- Liste paginee, au format de reference fixe au Sprint 1.2.
- Filtre optionnel sur le statut.
- Tri par defaut : nature, puis session, puis date de debut
  decroissante.
- Accessible aux roles ARH et DRH.

Montre le service puis le controleur.
```

### Étape 6. Tests

```
Ecris les tests de ce sous-sprint.

Tests unitaires, avec Mockito :
1. creation nominale sur un couple sans grille active : acceptee, au
   statut EN_ATTENTE_DRH
2. creation sur un couple deja couvert par une grille ACTIVE :
   refusee avec le code de conflit
3. creation sur un couple deja EN_ATTENTE_DRH : refusee
4. montant negatif ou nul : refuse
5. nature ou session absente : refusee
6. comportement conforme aux deux decisions prises aux etapes 1 et 4

Tests d'integration :
7. POST /grilles sans jeton : 401
8. POST /grilles avec un jeton DRH : 403, la creation est reservee a
   l'ARH
9. POST /grilles avec un jeton ARH, couple libre : 201, statut
   EN_ATTENTE_DRH
10. POST /grilles, couple deja actif : 409
11. GET /grilles avec un jeton ARH : 200, pagination correcte
12. GET /grilles filtre sur EN_ATTENTE_DRH : ne retourne que les
    grilles en attente

Verification complementaire, essentielle : apres la creation d'une
grille EN_ATTENTE_DRH sur un couple deja actif ailleurs, la
recherche de grille active du Sprint 2.1 doit toujours retourner
l'ancienne grille, pas la nouvelle.

Montre les fichiers de test.
```

## 7. Fichiers à créer

| Chemin | Nature |
|---|---|
| `service-grilles/.../api/dto/CreationGrilleRequest.java` | DTO d'entrée |
| `service-grilles/.../api/dto/GrilleResponse.java` | DTO de sortie |
| `service-grilles/.../application/UniciteGrilleService.java` | Contrôle RG-14 |
| `service-grilles/.../application/GrilleService.java` | Création, soumission, consultation |
| `service-grilles/.../api/GrilleController.java` | Endpoints |
| `service-grilles/src/test/.../UniciteGrilleServiceTest.java` | Tests du contrôle |
| `service-grilles/src/test/.../GrilleServiceTest.java` | Tests du service |
| `service-grilles/src/test/.../GrilleControllerIT.java` | Tests d'intégration |

## 8. Commandes terminal

```bash
cd afb-rations/backend

mvn -pl service-grilles test
mvn -pl service-grilles spring-boot:run
```

Vérifications manuelles, avec un jeton ARH :

```bash
curl -X POST -H "Authorization: Bearer <jeton_arh>" \
  -H "Content-Type: application/json" \
  -d '{"nature":"TRANSPORT","session":"SOIR","montantFcfa":3000,"dateDebut":"2026-09-01"}' \
  http://localhost:8083/grilles
```

Attendu : 201, statut `EN_ATTENTE_DRH`.

Rejouer le même appel : attendu 409, conflit.

```bash
curl -H "Authorization: Bearer <jeton_arh>" \
  "http://localhost:8083/grilles?statut=EN_ATTENTE_DRH&page=0&size=10"
```

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| `mvn -pl service-grilles test` | BUILD SUCCESS, aucune régression |
| Douze tests du sous-sprint | Tous passants |
| Création sur couple libre | 201, statut EN_ATTENTE_DRH |
| Création sur couple déjà actif | 409, message citant nature et session |
| Création sur couple déjà en attente | 409 |
| Montant nul ou négatif | 400 |
| Création par un rôle non ARH | 403 |
| Grille en attente sans effet sur la recherche de grille active | Vérifié |
| Trace de création dans le journal | Ligne présente |
| Format de pagination conforme au Sprint 1.2 | Vérifié |

## 10. Points de vigilance

- **Le refus doit venir du service, pas de la base.** L'index partiel du Sprint 0.5 est un garde-fou de dernier recours ; s'il se déclenche, l'utilisateur reçoit une erreur technique illisible. Le contrôle applicatif doit passer avant.
- Deux grilles simultanément EN_ATTENTE_DRH sur le même couple créeraient une ambiguïté au moment de la validation : laquelle ferme l'ancienne ? Le contrôle d'unicité doit couvrir ce cas, pas seulement les grilles actives.
- Une grille en attente ne doit strictement rien changer aux montants appliqués. La vérification complémentaire de l'étape 6 n'est pas facultative : c'est elle qui prouve que la chaîne de paiement est protégée.
- Le service Grilles ne connaît pas les utilisateurs. Récupérer un libellé de créateur ne doit pas se traduire par un accès à la base du service Identité.
- Le montant est un entier, en FCFA. Pas de décimale, pas de type flottant : un arrondi sur un montant payé serait une anomalie comptable.
- Ne pas créer d'endpoint de validation ni de rejet dans ce sous-sprint.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Décision sur la modification d'une grille active tranchée | Fait |
| Décision sur création et soumission en un ou deux temps tranchée | Fait |
| Question du libellé créateur résolue sans dépendance croisée | Fait |
| Contrôle d'unicité couvrant les grilles actives et en attente | Vérifié |
| `POST /grilles` conforme au contrat d'API | Vérifié |
| `GET /grilles` conforme, avec pagination de référence | Vérifié |
| Conflit refusé par le service avec message explicite | Vérifié |
| Grille en attente sans effet sur la résolution du montant | Vérifié |
| Douze tests passants | Vérifié |
| Aucun endpoint de validation créé | Vérifié |

## 12. Commit

```bash
git add .
git commit -m "sprint-2.2: creation et soumission des grilles par l'arh

- Controle d'unicite couvrant les grilles actives et en attente
- Creation et soumission a la drh, sans effet sur les saisies
- Consultation paginee et filtrable
- Tests unitaires et d'integration

Refs: RG-14, US-13, CT-25, CT-26"
```

---

**Fin du Sprint 2.2** — en attente de validation avant le Sprint 2.3
