# SPRINT 3.3

## Endpoints de saisie

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Exposer l'ouverture d'une fiche journalière et la gestion des lignes de prestation |
| **Livrable** | Les cinq endpoints du service Saisie, avec leurs DTO et leurs tests d'intégration |
| **Durée** | Une journée |
| **Prérequis** | Sprint 3.2 validé et commité |
| **Sprint suivant** | 3.4, consolidation mensuelle et clôture du sprint |

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Ouverture de fiche et RG-05 (étapes 2-3) | Opus | Élevé |
| Gestion des lignes et tests (étapes 4-6) | Sonnet | Moyen |

**Changement manuel à l'étape 4.** Les deux premières étapes portent RG-05 et la protection contre la modification d'un état déjà soumis. À partir de la gestion des lignes, revenir en Sonnet effort moyen.

## 2. Outil de cartographie

Recommandée à l'étape 6, pour confirmer que les endpoints exposés correspondent exactement au contrat d'API.

```
py -3.14 -m graphify update .
```

## 3. Contexte

Les règles sont posées, il reste à les exposer. Ce sous-sprint produit les cinq endpoints du service Saisie décrits au contrat d'API, section 3.

Deux points de fond s'y jouent. **RG-05, la fiche vierge par jour** : chaque nouveau jour sélectionné ouvre une fiche neuve, ce qui empêche de reprendre par inadvertance les lignes de la veille. Concrètement, l'ouverture d'une fiche est idempotente : ouvrir deux fois le même jour retourne la même fiche, elle ne se duplique pas et ne se vide pas.

Second point, moins visible : **une ligne n'est modifiable qu'avant soumission**. Or le statut du processus vit dans le service Workflow. La vérification suppose donc un appel inter-services, ou une convention arrêtée au Sprint 3.1.

## 4. Objectifs

- `POST /saisie/fiches` : ouverture ou récupération de la fiche d'un jour
- `GET /saisie/fiches/{id}/lignes` : lignes d'une fiche
- `POST /saisie/lignes` : ajout d'une ligne
- `PUT /saisie/lignes/{id}` : modification avant soumission
- `DELETE /saisie/lignes/{id}` : suppression avant soumission
- Vérification de la portée d'accès et du caractère modifiable de l'état
- Tests d'intégration des cinq endpoints

## 5. Règles et stories concernées

| Référence | Objet |
|---|---|
| RG-05 | Fiche vierge à chaque nouveau jour |
| RG-01, RG-02 | Nature et session contraintes |
| RG-03, RG-04 | Appliquées par les services du Sprint 3.2 |
| US-03 | Saisie des bénéficiaires jour par jour |
| CT-07 | Nouveau jour : fiche vierge, tableau de la veille non repris |
| CT-08 | Montant non modifiable |

## 6. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis le fichier CLAUDE.md a la racine, section 11 pour le
contrat d'api du service Saisie, et docs/rattachement-processus.md
redige au Sprint 3.1. Confirme en 3 lignes les cinq endpoints
attendus et la convention de rattachement au processus.

CONTEXTE DE CETTE SESSION : Sprint 3.3, endpoints de saisie. Le
domaine et les regles existent depuis les sous-sprints 3.1 et 3.2. On
les expose.
SERVICE CONCERNE : service-saisie uniquement.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Aucune entite JPA exposee en api : DTO en entree comme en sortie.
- Chaque endpoint vient avec ses tests d'integration.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : propose les DTO du sous-sprint : entree d'ouverture
de fiche, entree de creation de ligne, entree de modification, sortie
de ligne, sortie de fiche avec ses lignes. Montre-moi leur structure
et leurs contraintes de validation avant tout controleur.

Rappel : le DTO d'entree de creation de ligne ne contient pas de
montant. Le montant vient de la grille.
```

### Étape 2. Ouverture de fiche

```
Cree le service et l'endpoint POST /saisie/fiches :

Entree : identifiant de processus et date du jour, selon la
convention de rattachement du Sprint 3.1.

Comportement, portant RG-05 :
1. Verifie l'habilitation et la portee d'acces de l'agent sur l'unite
   concernee.
2. Verifie que l'etat est encore modifiable.
3. Si une fiche existe deja pour ce processus et cette date, la
   retourne telle quelle, avec ses lignes.
4. Sinon, cree une fiche vierge et la retourne.

L'operation est idempotente : ouvrir deux fois le meme jour ne cree
pas deux fiches et ne vide pas la premiere. C'est le sens exact de
RG-05, qui impose une fiche neuve par jour, pas une remise a zero a
chaque ouverture.

Montre le service puis le controleur.
```

### Étape 3. Vérification du caractère modifiable

```
Le statut du processus vit dans le service Workflow. Applique la
convention arretee au Sprint 3.1 pour verifier qu'un etat est encore
modifiable avant toute ecriture.

Trois questions a trancher si la convention ne les couvre pas :
1. La verification a-t-elle lieu a chaque ecriture de ligne, ou une
   fois a l'ouverture de la fiche ? La premiere option est sure mais
   multiplie les appels reseau.
2. Que faire si le service Workflow est injoignable ? Refuser la
   saisie, ou l'accepter en pariant que l'etat est modifiable ?
3. Quel code d'erreur retourner quand l'etat n'est plus modifiable ?

Presente les options, attends ma decision. Montre ensuite le code.
```

### Étape 4. Gestion des lignes

```
Cree les trois endpoints de gestion des lignes :

POST /saisie/lignes : appelle le service de creation du Sprint 3.2.
Retourne 201 avec la ligne creee et son montant resolu.

PUT /saisie/lignes/{id} : modification avant soumission. La
modification de la nature ou de la session declenche une nouvelle
resolution du montant et un nouveau controle de doublon : traite-la
comme une creation, pas comme une simple mise a jour de champ.

DELETE /saisie/lignes/{id} : suppression avant soumission.

Les trois verifient la portee d'acces et le caractere modifiable de
l'etat. Les trois tracent l'operation dans le journal d'audit.

Montre les services puis le controleur.
```

### Étape 5. Consultation des lignes

```
Cree l'endpoint GET /saisie/fiches/{id}/lignes : retourne les lignes
d'une fiche, avec pour chacune le beneficiaire, la nature, la
session et le montant applique.

Ajoute le sous-total de la fiche, utile a l'interface.

Verifie la portee d'acces : un agent ne consulte que les fiches des
unites qui lui sont accessibles.

Montre le service puis le controleur.
```

### Étape 6. Tests d'intégration

```
Ecris les tests d'integration des cinq endpoints.

Ouverture de fiche :
1. sans jeton : 401
2. avec un jeton hors portee : 403
3. premiere ouverture d'un jour : 201, fiche vierge
4. seconde ouverture du meme jour : la meme fiche, avec ses lignes,
   non vidée
5. ouverture d'un jour different : fiche distincte, vierge

Le test 4 est le plus important : il prouve que RG-05 n'est pas
interprete comme une remise a zero destructrice.

Gestion des lignes :
6. creation nominale : 201, montant resolu depuis la grille
7. creation en doublon : 409
8. creation sans grille active : 422 ou le code prevu
9. creation avec un montant dans l'entree : le montant est ignore
10. modification changeant la session : nouveau montant resolu
11. modification creant un doublon : refusee
12. suppression : 204, ligne absente ensuite
13. ecriture sur un etat non modifiable : refusee avec le code decide
    a l'etape 3

Consultation :
14. lignes retournees avec leur montant et le sous-total
15. consultation hors portee : 403

Puis lance la cartographie et verifie que les cinq endpoints du
contrat existent, ni plus ni moins.

Montre les fichiers de test.
```

## 7. Fichiers à créer

| Chemin | Nature |
|---|---|
| `service-saisie/.../api/dto/OuvertureFicheRequest.java` | DTO d'entrée |
| `service-saisie/.../api/dto/CreationLigneRequest.java` | DTO d'entrée, sans montant |
| `service-saisie/.../api/dto/ModificationLigneRequest.java` | DTO d'entrée |
| `service-saisie/.../api/dto/LigneResponse.java` | DTO de sortie |
| `service-saisie/.../api/dto/FicheResponse.java` | DTO de sortie, avec sous-total |
| `service-saisie/.../application/FicheJournaliereService.java` | Ouverture, RG-05 |
| `service-saisie/.../application/EtatModifiableService.java` | Vérification inter-services |
| `service-saisie/.../application/LigneService.java` | Modification, suppression, consultation |
| `service-saisie/.../api/SaisieController.java` | Les cinq endpoints |
| `service-saisie/src/test/.../SaisieControllerIT.java` | Tests d'intégration |

## 8. Commandes terminal

Trois services doivent tourner : Identité, Grilles et Saisie.

```bash
cd afb-rations/backend

mvn -pl service-saisie test
mvn -pl service-saisie spring-boot:run
```

Vérifications manuelles, avec un jeton d'agent :

```bash
curl -X POST -H "Authorization: Bearer <jeton_agent>" \
  -H "Content-Type: application/json" \
  -d '{"idProcessus":1,"dateJour":"2026-08-18"}' \
  http://localhost:8082/saisie/fiches

curl -X POST -H "Authorization: Bearer <jeton_agent>" \
  -H "Content-Type: application/json" \
  -d '{"idFicheJournaliere":1,"beneficiaire":{"nom":"MBARGA","prenom":"Jean","numCompteCourant":"00002000123456","codeAgence":"00002"},"nature":"RATION","session":"JOUR"}' \
  http://localhost:8082/saisie/lignes
```

Rejouer le second appel à l'identique : attendu 409.

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| `mvn -pl service-saisie test` | BUILD SUCCESS |
| Quinze tests du sous-sprint | Tous passants |
| Seconde ouverture du même jour | Même fiche, lignes conservées |
| Ouverture d'un jour différent | Fiche distincte et vierge |
| Création nominale | 201, montant issu de la grille |
| Doublon | 409 |
| Grille absente | Code prévu au contrat |
| Montant transmis par le client | Ignoré |
| Modification de session | Montant recalculé |
| État non modifiable | Écriture refusée |
| Accès hors portée | 403 |
| Cinq endpoints du contrat | Ni plus ni moins |

## 10. Points de vigilance

- **RG-05 n'est pas une remise à zéro.** La règle dit qu'un nouveau jour ouvre une fiche neuve, pas qu'une réouverture efface la saisie en cours. Une interprétation destructrice ferait perdre le travail de l'agent au moindre rafraîchissement d'écran.
- La modification d'une ligne n'est pas une simple mise à jour de champ. Changer la session change le montant et peut créer un doublon : les deux contrôles doivent rejouer.
- Le montant ne figure jamais dans les DTO d'entrée. S'il y est, il est ignoré, et le test 9 doit le prouver.
- La vérification du caractère modifiable dépend d'un autre service. Multiplier les appels dégrade le temps de réponse, dont la cible est de 3 secondes en moyenne : le choix de fréquence n'est pas anodin.
- La portée d'accès du Sprint 1.1 doit être appliquée sur les cinq endpoints. Un agent qui pourrait saisir sur l'unité d'un autre fausserait toute la chaîne comptable.
- Ne pas implémenter la consolidation mensuelle ici : c'est le sous-sprint 3.4.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Cinq endpoints conformes au contrat d'API | Vérifié |
| Ouverture de fiche idempotente, non destructrice | Vérifié |
| Trois décisions de l'étape 3 tranchées | Fait |
| Modification rejouant montant et doublon | Vérifié |
| Montant client toujours ignoré | Vérifié |
| Portée d'accès appliquée sur les cinq endpoints | Vérifié |
| Opérations tracées dans le journal | Vérifié |
| Quinze tests passants | Vérifié |
| Aucun endpoint hors contrat | Vérifié |

## 12. Commit

```bash
git add .
git commit -m "sprint-3.3: endpoints de saisie

- Ouverture de fiche idempotente, une fiche par jour
- Gestion des lignes avec valorisation et controle de doublon
- Verification de la portee d'acces et du caractere modifiable
- Tests d'integration des cinq endpoints

Refs: RG-05, US-03, CT-07, CT-08"
```

---

**Fin du Sprint 3.3** — en attente de validation avant le Sprint 3.4
