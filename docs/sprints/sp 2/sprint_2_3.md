# SPRINT 2.3

## Décision de la Directrice RH

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Permettre à la DRH de valider ou de rejeter une grille, avec fermeture transactionnelle de l'ancienne |
| **Livrable** | Endpoints de validation et de rejet, bascule atomique de la grille active |
| **Durée** | Une journée |
| **Prérequis** | Sprint 2.2 validé et commité |
| **Sprint suivant** | 2.4, résolution du montant et clôture du sprint |

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Bascule transactionnelle (étapes 2-3) | Opus | Élevé |
| Rejet motivé et tests (étapes 4-5) | Opus | Élevé |

Pas de changement de modèle si la session précédente était déjà en Opus effort élevé. Le maintenir : ce sous-sprint contient l'opération la plus délicate du service.

## 2. Outil de cartographie

Facultatif ici. Le périmètre reste circonscrit à un service dont l'état est connu.

## 3. Contexte

L'ARH propose, la DRH décide. Ce sous-sprint implémente la décision.

La difficulté n'est pas la validation en elle-même, mais **la bascule** : activer une grille suppose de fermer celle qu'elle remplace, et ces deux opérations touchent deux lignes différentes. Si la seconde échoue après la première, la base se retrouve soit sans grille active pour un couple, soit avec deux. Le premier cas bloque toute saisie, le second viole l'index partiel et rend le montant applicable ambigu.

C'est le point de RG-14 où une erreur ne se voit pas immédiatement : elle se manifeste au Sprint 3, quand une saisie ne trouve plus de grille ou en trouve deux.

## 4. Objectifs

- `POST /grilles/{id}/validation` : validation par la DRH, avec fermeture de l'ancienne grille active
- `POST /grilles/{id}/rejet` : rejet motivé, l'ancienne grille restant en vigueur
- Bascule atomique : les deux écritures réussissent ensemble ou échouent ensemble
- Traçabilité de la décision dans le journal d'audit
- Tests couvrant la bascule, les transitions interdites et le cas sans grille antérieure

## 5. Règles et stories concernées

| Référence | Objet |
|---|---|
| RG-14 | Validation DRH, activation fermant l'ancienne, rejet motivé |
| US-14 | Validation ou rejet des grilles proposées |
| CT-27 | Validation : grille active, ancienne fermée |
| CT-28 | Rejet sans motif : refusé |
| CT-29 | Après validation, le montant appliqué est celui de la nouvelle grille |

## 6. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis le fichier CLAUDE.md a la racine, section 6, regle
RG-14. Confirme en 3 lignes ce qu'elle impose lors d'une validation.

CONTEXTE DE CETTE SESSION : Sprint 2.3, decision de la DRH. La
creation et la soumission existent depuis le Sprint 2.2. On
implemente la validation et le rejet. Le point sensible est la
bascule : activer une grille implique de fermer celle qu'elle
remplace, en une seule transaction.
SERVICE CONCERNE : service-grilles uniquement.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Chaque methode publique vient avec ses tests.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : decris-moi, en pseudo-code et sans encore ecrire de
java, la sequence exacte de la validation : ce qui est verifie, dans
quel ordre les deux lignes sont modifiees, et ou se situe la frontiere
transactionnelle. Precise ce qui se passe si la grille validee est la
premiere sur son couple, sans ancienne a fermer. Attends ma
validation.
```

### Étape 2. Bascule transactionnelle

```
Une fois la sequence validee, implemente le service de validation :

1. Verifie l'habilitation : role DRH.
2. Charge la grille cible, 404 si absente.
3. Verifie que son statut permet la validation : EN_ATTENTE_DRH
   uniquement, sinon erreur explicite.
4. Recherche la grille ACTIVE couvrant le meme couple nature et
   session.
5. Si elle existe, la ferme en posant sa date_fin.
6. Active la grille cible : statut ACTIVE, id_validateur,
   date_validation.
7. Trace la decision dans le journal d'audit.

Les etapes 5 et 6 sont dans la meme transaction. Montre-moi comment
tu delimites cette transaction et pourquoi ce decoupage protege
l'invariant de RG-14.

Question a trancher avant d'ecrire : quelle date_fin poser sur
l'ancienne grille ? La veille de la date_debut de la nouvelle, ou la
date de validation ? Les deux se defendent, les consequences
different sur la resolution du montant a une date passee. Presente
les deux, j'arbitre.

Montre le service.
```

### Étape 3. Endpoint de validation

```
Cree l'endpoint POST /grilles/{id}/validation, conforme au contrat
d'api. Reserve au role DRH. Retourne la grille validee avec son
nouveau statut.

Precise dans la reponse ou dans la documentation ce qu'il advient de
l'ancienne grille, pour que l'interface puisse l'afficher.

Montre le controleur.
```

### Étape 4. Rejet motivé

```
Cree le service et l'endpoint POST /grilles/{id}/rejet :

1. Verifie l'habilitation : role DRH.
2. Charge la grille, 404 si absente.
3. Verifie le statut : EN_ATTENTE_DRH uniquement.
4. Exige un motif non vide, sinon refuse avec le code du contrat.
5. Passe la grille au statut REJETEE, enregistre le motif, le
   validateur et la date.
6. Ne touche pas a l'ancienne grille active, qui reste en vigueur.
7. Trace la decision.

Le motif est obligatoire : un rejet sans explication empeche l'ARH de
corriger. Montre le service puis le controleur.
```

### Étape 5. Tests

```
Ecris les tests de ce sous-sprint.

Tests unitaires de la validation :
1. validation nominale avec une ancienne grille active : la nouvelle
   est ACTIVE, l'ancienne porte une date_fin
2. validation d'une premiere grille sur un couple, sans ancienne :
   aboutit sans erreur
3. validation d'une grille au statut BROUILLON : refusee
4. validation d'une grille deja ACTIVE : refusee
5. validation d'une grille REJETEE : refusee
6. echec simule lors de la fermeture de l'ancienne : aucune des deux
   lignes n'est modifiee

Le test 6 est le plus important du sous-sprint : il prouve
l'atomicite de la bascule.

Tests unitaires du rejet :
7. rejet nominal avec motif : statut REJETEE, motif enregistre
8. rejet sans motif : refuse
9. rejet d'une grille ACTIVE : refuse
10. apres rejet, l'ancienne grille active est inchangee

Tests d'integration :
11. validation sans jeton : 401
12. validation avec un jeton ARH : 403, la decision est reservee a la
    DRH
13. validation avec un jeton DRH : 200
14. validation sur un identifiant inexistant : 404
15. rejet sans motif : 422 ou le code prevu au contrat

Verification de bout en bout, indispensable : apres validation, la
recherche de grille active du Sprint 2.1 retourne la nouvelle grille
et une seule.

Montre les fichiers de test.
```

## 7. Fichiers à créer ou modifier

| Chemin | Nature |
|---|---|
| `service-grilles/.../api/dto/RejetGrilleRequest.java` | DTO du motif |
| `service-grilles/.../application/DecisionGrilleService.java` | Validation et rejet |
| `service-grilles/.../api/GrilleController.java` | Ajout des deux endpoints |
| `service-grilles/src/test/.../DecisionGrilleServiceTest.java` | Tests unitaires |
| `service-grilles/src/test/.../GrilleControllerIT.java` | Complément d'intégration |

## 8. Commandes terminal

```bash
cd afb-rations/backend

mvn -pl service-grilles test
mvn -pl service-grilles spring-boot:run
```

Séquence de vérification manuelle. Créer d'abord une grille avec un jeton ARH, puis la valider avec un jeton DRH :

```bash
curl -X POST -H "Authorization: Bearer <jeton_drh>" \
  http://localhost:8083/grilles/5/validation

curl -X POST -H "Authorization: Bearer <jeton_drh>" \
  -H "Content-Type: application/json" \
  -d '{"motif":"Montant superieur au bareme en vigueur"}' \
  http://localhost:8083/grilles/6/rejet
```

Contrôle en base après validation :

```sql
\c rations_grilles
SELECT id, nature, session, montant_fcfa, statut_validation, date_debut, date_fin
FROM grille_tarifaire
WHERE nature = 'TRANSPORT' AND session = 'SOIR'
ORDER BY date_debut;
```

Attendu : une seule ligne ACTIVE avec `date_fin` nulle, l'ancienne portant une `date_fin` renseignée.

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| `mvn -pl service-grilles test` | BUILD SUCCESS, aucune régression |
| Quinze tests du sous-sprint | Tous passants |
| Validation nominale | Nouvelle ACTIVE, ancienne fermée |
| Première grille d'un couple | Validation sans erreur |
| Statut incompatible | Refus explicite |
| Échec simulé pendant la bascule | Aucune ligne modifiée |
| Rejet sans motif | Refusé |
| Après rejet | Ancienne grille toujours active |
| Validation par un rôle non DRH | 403 |
| Après validation, recherche de grille active | Une seule grille, la nouvelle |
| Index partiel jamais violé | Aucune erreur de contrainte en base |

## 10. Points de vigilance

- **L'atomicité de la bascule est le point critique du sous-sprint.** Une fermeture réussie suivie d'une activation échouée laisse un couple sans grille active, et bloque toute saisie au Sprint 3. Le test d'échec simulé n'est pas un test de confort.
- Le choix de la `date_fin` de l'ancienne grille a des conséquences durables. Une saisie de rattrapage sur une période passée, au Sprint 6bis, résoudra son montant à la date de la prestation : si les périodes de validité se chevauchent ou laissent un trou, la résolution devient fausse ou impossible.
- Un rejet ne touche jamais l'ancienne grille. Confondre rejet et fermeture priverait le système de grille active sans raison.
- Le motif de rejet doit être non vide, pas seulement non nul. Une chaîne d'espaces n'est pas un motif.
- La validation est réservée à la DRH. Un ARH qui pourrait valider ses propres propositions annulerait tout l'intérêt du double regard.
- Ne pas implémenter la résolution du montant ici : c'est le sous-sprint 2.4.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Séquence de validation décrite et validée avant codage | Fait |
| Décision sur la `date_fin` de l'ancienne grille tranchée | Fait |
| Bascule atomique, prouvée par le test d'échec simulé | Vérifié |
| `POST /grilles/{id}/validation` conforme au contrat | Vérifié |
| `POST /grilles/{id}/rejet` conforme au contrat | Vérifié |
| Motif de rejet obligatoire et non vide | Vérifié |
| Rejet laissant l'ancienne grille active | Vérifié |
| Transitions interdites refusées | Vérifié |
| Décisions tracées dans le journal | Vérifié |
| Quinze tests passants | Vérifié |
| Index partiel jamais violé | Vérifié |

## 12. Commit

```bash
git add .
git commit -m "sprint-2.3: validation et rejet des grilles par la drh

- Bascule transactionnelle : activation de la nouvelle, fermeture de l'ancienne
- Rejet motive laissant l'ancienne grille en vigueur
- Transitions interdites refusees explicitement
- Tests d'atomicite de la bascule

Refs: RG-14, US-14, CT-27, CT-28"
```

---

**Fin du Sprint 2.3** — en attente de validation avant le Sprint 2.4
