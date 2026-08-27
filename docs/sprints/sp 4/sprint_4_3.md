# SPRINT 4.3

## Validation du Chef d'Unité et aiguillage au seuil

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Implémenter la validation de premier niveau et l'aiguillage automatique selon le montant |
| **Livrable** | Endpoint de validation, service d'aiguillage lisant le seuil en paramètre, clôture des états sous le seuil |
| **Durée** | Une à deux journées |
| **Prérequis** | Sprint 4.2 validé et commité |
| **Sprint suivant** | 4.4, second niveau, retour et séparation des tâches |

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Service d'aiguillage et seuil (étapes 2-3) | Opus | Élevé |
| Validation et clôture (étapes 4-5) | Opus | Élevé |
| Tests (étape 6) | Opus | Élevé |

Maintenir Opus effort élevé sur l'ensemble du sous-sprint. C'est ici que se joue RG-08, la règle dont une erreur passe inaperçue en test superficiel et pose un problème de contrôle interne en production.

## 2. Outil de cartographie

Recommandée en fin de sous-sprint, pour vérifier qu'aucune valeur de seuil ne s'est glissée en dur dans le code ou les tests.

```
py -3.14 -m graphify update .
```

## 3. Contexte

Le dossier est soumis et attend le Chef d'Unité. Ce sous-sprint implémente sa décision et, surtout, ce qu'elle déclenche.

**RG-08 est la règle centrale du module.** Après validation du Chef d'Unité, un état d'au plus 100 000 XAF est clôturé directement ; au-delà, il monte au Directeur Réseau. C'est un mécanisme de contrôle interne : le seuil détermine le niveau d'approbation requis pour engager la banque.

Deux exigences en découlent. Le seuil se lit dans `parametre_systeme`, jamais en dur : le document de configuration en fait un aléa à part entière, et le CLAUDE.md une erreur interdite. Et la comparaison doit être exacte à la borne : un état de 100 000 XAF exactement se clôture, un état de 100 001 XAF monte.

Un état clôturé ici n'est pas encore transmis à la comptabilité. La transmission est le Sprint 5.

## 4. Objectifs

- Service d'aiguillage lisant le seuil dans les paramètres
- `POST /processus/{id}/validation` pour le Chef d'Unité
- Signature du Chef d'Unité apposée sur la pièce jointe existante
- Clôture des états sous le seuil, transfert au Directeur Réseau au-delà
- Tests couvrant les deux branches et le comportement exact à la borne

## 5. Règles et stories concernées

| Référence | Objet |
|---|---|
| RG-08 | Seuil d'aiguillage, lu en paramètre |
| RG-07 | Validation séquentielle |
| RG-09 | Signature numérique horodatée |
| US-08 | Validation par le Chef d'Unité |
| US-09 | Aiguillage selon le montant |
| CT-14 | État sous le seuil : clôturé |
| CT-15 | État au-dessus du seuil : transféré au DR |
| CT-18 | Modification du seuil : l'aiguillage suit la nouvelle valeur |

## 6. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis le fichier CLAUDE.md a la racine, section 6, regle
RG-08, et section 15 sur les erreurs interdites. Confirme en 3 lignes
ce qu'impose RG-08 et pourquoi le seuil ne doit jamais etre code en
dur.

CONTEXTE DE CETTE SESSION : Sprint 4.3, validation du Chef d'Unite et
aiguillage. La soumission existe depuis le Sprint 4.2. On implemente
la decision de premier niveau et l'aiguillage selon le montant.

Un etat cloture ici n'est PAS transmis a la comptabilite : la
transmission est le Sprint 5. Ne l'anticipe pas.
SERVICE CONCERNE : service-workflow uniquement.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Aucune valeur de seuil en dur, y compris dans les tests.
- Chaque methode publique vient avec ses tests.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : avant tout code, precise-moi le comportement exact
a la borne. RG-08 dit inferieur ou egal a 100 000 XAF pour la cloture
directe. Confirme donc : un etat de 100 000 XAF exactement est
cloture, un etat de 100 001 XAF monte au DR. Dis-moi aussi comment tu
comptes ecrire cette comparaison pour qu'elle ne puisse pas etre
inversee par erreur, et comment le test le prouvera.
```

### Étape 2. Lecture du seuil

```
Cree le service de lecture du seuil :

Il lit le parametre de code SEUIL_AIGUILLAGE_DR dans
parametre_systeme, avec actif a vrai.

Trois questions a trancher avant d'ecrire :
1. Que faire si le parametre est absent ou inactif ? Une valeur par
   defaut en dur reintroduirait exactement ce que RG-08 interdit.
   Refuser la validation est plus sur mais bloque le circuit.
2. La valeur est stockee en chaine. Comment garantir qu'une valeur
   non numerique ne provoque pas une erreur en pleine validation ?
3. Le seuil est-il relu a chaque validation, ou mis en cache ? Un
   cache ameliore le temps de reponse mais retarde la prise en compte
   d'une modification, ce que CT-18 verifie.

Presente les options, attends ma decision. Montre ensuite le service.
```

### Étape 3. Service d'aiguillage

```
Cree le service d'aiguillage, cœur de RG-08 :

Entree : le processus valide par le Chef d'Unite.
Sortie : la decision d'aiguillage, cloture directe ou transfert au
Directeur Reseau.

Il lit le seuil via le service de l'etape 2, compare le montant total
enregistre a la soumission, et retourne la decision. Il ne modifie pas
l'etat : c'est le service de validation qui applique la transition.

Cette separation permet de tester l'aiguillage isolement, sur des
montants precis, sans monter tout un processus.

Montre le service puis ses tests unitaires.
```

### Étape 4. Validation du Chef d'Unité

```
Cree le service et l'endpoint POST /processus/{id}/validation, pour
le role CHEF_UNITE_DA :

1. Verifie l'habilitation et la portee d'acces sur l'unite du
   processus.
2. Verifie que le statut permet la validation : EN_ATTENTE_DA.
3. Appose la signature du Chef d'Unite sur la piece jointe existante,
   via le service du Sprint 4.2. Le compteur de signatures passe a
   deux.
4. Cree l'etape VALIDATION_DA, statut VALIDEE, avec l'acteur et la
   date.
5. Appelle le service d'aiguillage.
6. Applique la transition correspondante : vers CLOTURE avec date de
   cloture, ou vers EN_ATTENTE_DR.
7. Trace la decision dans le journal d'audit.

La reponse indique le resultat de l'aiguillage et le seuil applique,
conformement a l'exemple du contrat d'api section 5.

La separation des taches, RG-12, est le sous-sprint 4.4 : ne
l'implemente pas ici, mais laisse le point d'accroche.

Montre le service puis le controleur.
```

### Étape 5. Clôture

```
Complete la transition vers CLOTURE :

1. Statut CLOTURE, date_cloture renseignee.
2. transmis_comptabilite reste a faux : la transmission est le
   Sprint 5.
3. Le processus devient non modifiable, ce que le service Saisie
   verifie deja depuis le Sprint 3.3.

Verifie que la machine a etats du Sprint 4.1 interdit bien toute
sortie de CLOTURE.

Montre le code.
```

### Étape 6. Tests

```
Ecris les tests de ce sous-sprint. Aucun ne code le seuil en dur :
tous le lisent depuis le parametre.

Aiguillage, tests unitaires sur le service isole :
1. montant tres inferieur au seuil : cloture directe
2. montant egal au seuil moins un franc : cloture directe
3. montant EXACTEMENT egal au seuil : cloture directe
4. montant egal au seuil plus un franc : transfert au DR
5. montant tres superieur au seuil : transfert au DR

Les tests 3 et 4 sont les plus importants du sous-sprint : ils
verrouillent la borne.

Parametre :
6. seuil modifie en base : l'aiguillage suit la nouvelle valeur,
   conformement a CT-18
7. parametre absent ou inactif : comportement conforme a la decision
   de l'etape 2
8. valeur non numerique : comportement conforme a la decision

Validation :
9. validation nominale sous le seuil : CLOTURE, date_cloture
   renseignee, deux signatures
10. validation nominale au-dessus du seuil : EN_ATTENTE_DR, deux
    signatures
11. validation d'un processus au statut EN_COURS_SAISIE : refusee
12. validation d'un processus deja cloture : refusee
13. validation par un role autre que CHEF_UNITE_DA : 403
14. validation hors portee d'acces : 403
15. apres cloture, transmis_comptabilite vaut toujours faux

Puis lance la cartographie et cherche toute occurrence de la valeur
du seuil dans le code et les tests. Attendu : aucune.

Montre les fichiers de test.
```

## 7. Fichiers à créer

| Chemin | Nature |
|---|---|
| `service-workflow/.../application/SeuilService.java` | Lecture du paramètre |
| `service-workflow/.../application/AiguillageService.java` | RG-08 |
| `service-workflow/.../application/ValidationService.java` | Validation DA |
| `service-workflow/.../api/dto/ValidationResponse.java` | Résultat et seuil appliqué |
| `service-workflow/.../api/ProcessusController.java` | Ajout de l'endpoint |
| `service-workflow/src/test/.../AiguillageServiceTest.java` | Tests de borne |
| `service-workflow/src/test/.../ValidationServiceTest.java` | Tests de validation |

## 8. Commandes terminal

```bash
cd afb-rations/backend

mvn -pl service-workflow test
mvn -pl service-workflow spring-boot:run
```

```bash
curl -X POST -H "Authorization: Bearer <jeton_da>" \
  http://localhost:8084/processus/1/validation
```

Vérification de CT-18, modification du seuil puis nouvelle validation :

```sql
\c rations_workflow
UPDATE parametre_systeme SET valeur = '50000' WHERE code = 'SEUIL_AIGUILLAGE_DR';
```

Recherche d'une valeur en dur :

```bash
grep -rn "100000\|100_000" backend/service-workflow/src/
```

Attendu : aucun résultat.

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| `mvn -pl service-workflow test` | BUILD SUCCESS |
| Quinze tests du sous-sprint | Tous passants |
| Montant exactement égal au seuil | Clôture directe |
| Montant seuil plus un | Transfert au DR |
| Seuil modifié en base | Aiguillage suivant la nouvelle valeur |
| Aucune valeur de seuil en dur | Vérifié par recherche |
| Deux signatures après validation DA | Vérifié |
| Une seule pièce jointe | Vérifié |
| État clôturé | `transmis_comptabilite` toujours faux |
| Validation hors statut ou hors rôle | Refusée |

## 10. Points de vigilance

- **La borne est le point critique.** Une comparaison stricte au lieu d'une comparaison large envoie au Directeur Réseau un état qui aurait dû être clôturé, et inversement. Les tests 3 et 4 sont les garde-fous.
- **Aucune valeur de seuil en dur, y compris dans les tests.** Un test qui code 100 000 continuerait à passer après une modification du paramètre, masquant précisément la régression que CT-18 doit détecter.
- Le montant comparé est celui enregistré à la soumission, pas un montant recalculé. Recalculer au moment de la validation ferait dépendre l'aiguillage d'une éventuelle modification de grille survenue entre-temps.
- La clôture ne transmet rien. `transmis_comptabilite` reste à faux jusqu'au Sprint 5. Anticiper la transmission ici contournerait le contrôle d'unicité de RG-13.
- La signature enrichit la pièce jointe existante. Le compteur doit passer à deux, pas repartir à un.
- Ne pas implémenter la séparation des tâches ici. Elle est au sous-sprint 4.4, avec le retour et le second niveau.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Comportement à la borne confirmé avant codage | Fait |
| Trois décisions sur le paramètre tranchées | Fait |
| Seuil lu en base, jamais en dur | Vérifié |
| Aiguillage testable isolément | Fait |
| Tests de borne exacte passants | Vérifié |
| CT-18 vérifié par modification réelle du paramètre | Vérifié |
| Signature du DA apposée sur la pièce existante | Vérifié |
| Étape VALIDATION_DA créée | Vérifié |
| Clôture sans transmission | Vérifié |
| Quinze tests passants | Vérifié |

## 12. Commit

```bash
git add .
git commit -m "sprint-4.3: validation du chef d'unite et aiguillage

- Seuil lu dans les parametres systeme, jamais en dur
- Aiguillage teste a la borne exacte
- Signature du chef d'unite sur la piece jointe existante
- Cloture des etats sous le seuil, sans transmission comptable

Refs: RG-08, RG-09, US-08, US-09, CT-14, CT-15, CT-18"
```

---

**Fin du Sprint 4.3** — en attente de validation avant le Sprint 4.4
