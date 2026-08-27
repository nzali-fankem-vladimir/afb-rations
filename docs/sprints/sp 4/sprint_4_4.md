# SPRINT 4.4

## Second niveau, retour motivé et séparation des tâches

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Implémenter la validation du Directeur Réseau, le retour motivé et le contrôle de séparation des tâches |
| **Livrable** | Endpoints de validation de second niveau et de retour, service de séparation des tâches, clôture du Sprint 4 |
| **Durée** | Une à deux journées |
| **Prérequis** | Sprint 4.3 validé et commité |
| **Sprint suivant** | 5.1, transmission comptable |

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Séparation des tâches (étapes 2-3) | Opus | Élevé |
| Validation DR et retour (étapes 4-5) | Opus | Élevé |
| Tests et clôture (étapes 6-7) | Opus | Élevé |

Maintenir Opus effort élevé. RG-11 et RG-12 sont deux règles de contrôle interne dont la violation ne produit aucune erreur visible.

## 2. Outil de cartographie

Utilisation obligatoire à l'étape 7, pour la clôture du Sprint 4.

```
py -3.14 -m graphify update .
```

## 3. Contexte

Dernier sous-sprint du service Workflow. Trois mécanismes s'y ajoutent.

La **validation du Directeur Réseau**, qui clôture les états dépassant le seuil. Elle reprend la mécanique du sous-sprint 4.3, sans aiguillage : après le second niveau, il n'y a plus d'échelon.

Le **retour motivé**, qui obéit à deux règles. RG-10 impose un motif : sans explication, l'agent ne peut pas corriger. RG-11 impose que le retour ramène toujours à l'agent d'unité, quel que soit le niveau d'origine. Un retour du Directeur Réseau ne revient pas au Chef d'Unité : il redescend directement à la saisie.

La **séparation des tâches**, RG-12. Un même utilisateur ne cumule pas la saisie et la validation multi-niveau d'un même dossier. C'est une exigence de contrôle interne du cahier des charges : sans elle, une personne pourrait engager seule la banque de bout en bout.

Ce sous-sprint clôt le Sprint 4.

## 4. Objectifs

- Service de séparation des tâches, appliqué aux deux niveaux de validation
- Validation du Directeur Réseau, avec clôture
- `POST /processus/{id}/retour` : retour motivé vers l'agent
- Reprise d'un état retourné, permettant une nouvelle soumission
- Vérification de bout en bout du circuit complet et clôture du Sprint 4

## 5. Règles et stories concernées

| Référence | Objet |
|---|---|
| RG-10 | Motif obligatoire pour tout rejet ou retour |
| RG-11 | Retour toujours vers l'agent d'unité |
| RG-12 | Séparation des tâches |
| RG-07 | Validation séquentielle |
| US-10 | Validation des états dépassant le seuil |
| US-11 | Reprise d'un état retourné |
| CT-16 | Retour sans motif : refusé |
| CT-17 | Validation par celui qui a soumis : refusée |
| CT-19, CT-20 | Validation et retour du Directeur Réseau |
| CT-23, CT-24 | Retour ramenant à l'agent, reprise et resoumission |

## 6. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis le fichier CLAUDE.md a la racine, section 6, regles
RG-10, RG-11 et RG-12. Confirme en 3 lignes ce que chacune impose.

CONTEXTE DE CETTE SESSION : Sprint 4.4, dernier sous-sprint du
service Workflow. La validation de premier niveau et l'aiguillage
existent depuis le Sprint 4.3. On ajoute le second niveau, le retour
motive et la separation des taches.
SERVICE CONCERNE : service-workflow, avec appel au service Identite
pour la verification d'habilitation.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Chaque methode publique vient avec ses tests.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : avant tout code, precise avec moi ce que recouvre
exactement RG-12 sur ce module. La regle dit qu'un meme utilisateur
ne cumule pas la saisie et la validation multi-niveau d'un meme
dossier, et ne choisit pas son N+1.

Trois lectures possibles, dis-moi laquelle correspond au besoin :
1. celui qui a soumis ne peut pas valider, a aucun niveau
2. celui qui a valide a un niveau ne peut pas valider au niveau
   suivant
3. les deux a la fois

Precise aussi ce qui se passe quand une meme personne cumule
legitimement deux roles, cas frequent dans une petite unite.
Presente les consequences, attends ma decision.
```

### Étape 2. Service de séparation des tâches

```
Une fois la lecture arretee, implemente le service de separation des
taches :

Entree : le processus et l'utilisateur qui tente d'agir.
Sortie : autorise ou refuse, avec le motif du refus.

Il s'appuie sur les etapes deja realisees sur le processus, via le
repository du Sprint 4.1, methode de recherche des etapes par acteur.

Le refus doit produire un 403 avec un code explicite, distinct d'un
refus pour role insuffisant : les deux situations n'appellent pas le
meme message pour l'utilisateur.

Montre le service puis ses tests.
```

### Étape 3. Application aux deux niveaux

```
Branche le service de separation des taches sur la validation du
Chef d'Unite, construite au Sprint 4.3, au point d'accroche laisse a
cet effet.

Verifie que le controle intervient avant toute modification d'etat,
conformement a l'ordre du document maitre section 7.3 : habilitation
d'abord, regles ensuite, modification enfin.

Montre les modifications.
```

### Étape 4. Validation du Directeur Réseau

```
Complete l'endpoint POST /processus/{id}/validation pour le role
DIRECTEUR_RESEAU_DR :

1. Verifie l'habilitation, la portee d'acces et la separation des
   taches.
2. Verifie que le statut permet la validation : EN_ATTENTE_DR.
3. Appose la signature du Directeur Reseau. Le compteur passe a trois.
4. Cree l'etape VALIDATION_DR, statut VALIDEE.
5. Cloture le processus : statut CLOTURE, date_cloture renseignee.
6. Trace la decision.

Pas d'aiguillage a ce niveau : apres le second, il n'y a plus
d'echelon. transmis_comptabilite reste a faux, la transmission etant
le Sprint 5.

Question a trancher : le meme endpoint sert les deux niveaux, comme
le prevoit le contrat d'api. Comment determines-tu le niveau
concerne, par le role de l'appelant ou par le statut du processus ?
Presente les deux, en precisant lequel resiste le mieux a une
incoherence entre role et statut.

Montre le service puis le controleur.
```

### Étape 5. Retour motivé

```
Cree le service et l'endpoint POST /processus/{id}/retour :

1. Verifie l'habilitation : role CHEF_UNITE_DA ou
   DIRECTEUR_RESEAU_DR, portee d'acces sur l'unite.
2. Verifie que le statut permet le retour : EN_ATTENTE_DA ou
   EN_ATTENTE_DR.
3. Exige un motif non vide, RG-10. Une chaine d'espaces n'est pas un
   motif.
4. Cree l'etape correspondant au niveau, statut RETOURNEE, avec le
   motif et l'acteur.
5. Fait transiter le processus vers RETOURNE, quel que soit le niveau
   d'origine, RG-11.
6. Trace le retour.

Le motif doit rester consultable par l'agent : verifie qu'il est
expose dans GET /processus/{id}.

Cree aussi la reprise : un processus RETOURNE redevient
EN_COURS_SAISIE et donc modifiable par le service Saisie. Dis-moi si
cette transition est automatique au retour, ou si elle demande une
action explicite de l'agent. Le contrat d'api ne prevoit pas
d'endpoint dedie : presente les deux lectures.

Montre les services puis le controleur.
```

### Étape 6. Tests

```
Ecris les tests de ce sous-sprint.

Separation des taches :
1. l'agent ayant soumis tente de valider : refuse, code explicite
2. un Chef d'Unite n'ayant pas soumis valide : autorise
3. le Chef d'Unite ayant valide tente de valider au second niveau :
   comportement conforme a la decision de l'etape 1
4. cas du cumul de roles : comportement conforme a la decision
5. le refus produit un 403 distinct du refus pour role insuffisant

Validation de second niveau :
6. validation DR nominale : CLOTURE, trois signatures, date de
   cloture
7. validation DR d'un processus EN_ATTENTE_DA : refusee
8. validation DR par un role CHEF_UNITE_DA : refusee
9. apres cloture, transmis_comptabilite vaut toujours faux

Retour :
10. retour DA avec motif : statut RETOURNE, motif enregistre
11. retour DR avec motif : statut RETOURNE, PAS EN_ATTENTE_DA
12. retour sans motif : refuse
13. retour avec un motif compose uniquement d'espaces : refuse
14. le motif est visible dans le detail du processus
15. apres retour et reprise, l'agent peut modifier ses lignes
16. apres correction, la resoumission repart du debut du circuit

Le test 11 verrouille RG-11 : c'est le piege le plus probable du
sous-sprint.

Circuit complet, test d'integration :
17. un dossier sous le seuil : declenchement, saisie, soumission,
    validation DA, cloture
18. un dossier au-dessus du seuil : declenchement, saisie,
    soumission, validation DA, validation DR, cloture

Montre les fichiers de test.
```

### Étape 7. Clôture du Sprint 4

```
Lance la cartographie et verifie :
- que les six endpoints du contrat d'api existent, ni plus ni moins
- qu'aucune valeur de seuil n'est en dur
- que service-workflow n'accede a la base d'aucun autre service
- qu'aucune transition ne sort de CLOTURE

Puis mets a jour CLAUDE.md avec les decisions du Sprint 4 :
1. Les controles de completude retenus (Sprint 4.2).
2. La nature de la signature numerique (Sprint 4.2).
3. Le comportement en cas de parametre de seuil absent ou invalide,
   et la politique de cache (Sprint 4.3).
4. La lecture retenue de RG-12 et le traitement du cumul de roles
   (Sprint 4.4).
5. Le mode de determination du niveau de validation (Sprint 4.4).
6. Le caractere automatique ou explicite de la reprise apres retour
   (Sprint 4.4).

Propose les ajouts section par section.
```

## 7. Fichiers à créer ou modifier

| Chemin | Nature |
|---|---|
| `service-workflow/.../application/SeparationTachesService.java` | RG-12 |
| `service-workflow/.../application/ValidationService.java` | Extension au second niveau |
| `service-workflow/.../application/RetourService.java` | RG-10, RG-11 |
| `service-workflow/.../api/dto/RetourRequest.java` | DTO du motif |
| `service-workflow/.../api/ProcessusController.java` | Ajout de l'endpoint de retour |
| `service-workflow/src/test/...` | Tests unitaires et d'intégration |
| `CLAUDE.md` | Décisions du Sprint 4 |

## 8. Commandes terminal

```bash
cd afb-rations/backend

mvn -pl service-workflow test
mvn -pl service-workflow spring-boot:run
```

Circuit complet, avec trois jetons distincts :

```bash
curl -X POST -H "Authorization: Bearer <jeton_agent>" \
  http://localhost:8084/processus/2/soumission

curl -X POST -H "Authorization: Bearer <jeton_da>" \
  http://localhost:8084/processus/2/validation

curl -X POST -H "Authorization: Bearer <jeton_dr>" \
  http://localhost:8084/processus/2/validation
```

Retour motivé :

```bash
curl -X POST -H "Authorization: Bearer <jeton_dr>" \
  -H "Content-Type: application/json" \
  -d '{"motif":"Montant du 12 aout incoherent avec la grille"}' \
  http://localhost:8084/processus/3/retour
```

Contrôle en base :

```sql
\c rations_workflow
SELECT statut FROM processus_mensuel WHERE id = 3;
SELECT ordre_etape, nom_etape, statut_etape, motif_retour
FROM etape_workflow WHERE id_processus = 3 ORDER BY ordre_etape;
```

Attendu après retour DR : statut `RETOURNE`, jamais `EN_ATTENTE_DA`.

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| `mvn -pl service-workflow test` | BUILD SUCCESS |
| Dix-huit tests du sous-sprint | Tous passants |
| Soumissionnaire tentant de valider | 403, code distinct |
| Validation DR nominale | CLOTURE, trois signatures |
| Retour DR | Statut RETOURNE, pas EN_ATTENTE_DA |
| Retour sans motif ou motif vide | Refusé |
| Motif visible par l'agent | Vérifié |
| Reprise après retour | Lignes modifiables |
| Resoumission | Circuit repris depuis le début |
| Circuit complet sous le seuil | Deux validations, clôture |
| Circuit complet au-dessus du seuil | Trois validations, clôture |
| Six endpoints du contrat | Ni plus ni moins |
| Aucun seuil en dur | Vérifié |

## 10. Points de vigilance

- **RG-11 est le piège du sous-sprint.** Faire revenir un retour du Directeur Réseau au Chef d'Unité paraît logique et serait faux. Le test 11 l'interdit explicitement.
- Un motif composé d'espaces n'est pas un motif. La validation doit porter sur le contenu utile, pas seulement sur la présence du champ.
- La séparation des tâches produit un refus distinct de celui pour rôle insuffisant. Les confondre donnerait à l'utilisateur un message qui ne lui permet pas de comprendre pourquoi il est bloqué.
- Le cumul de rôles dans une petite unité est un cas réel. Une lecture trop stricte de RG-12 pourrait bloquer un circuit là où le métier attend qu'il fonctionne.
- Après validation du Directeur Réseau, il n'y a pas d'aiguillage : la clôture est directe. Rappeler un service d'aiguillage à ce niveau créerait une boucle.
- La transmission comptable reste le Sprint 5. `transmis_comptabilite` doit valoir faux à la fin de ce sous-sprint, pour les deux branches du circuit.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Lecture de RG-12 arbitrée et documentée | Fait |
| Cas du cumul de rôles tranché | Fait |
| Séparation des tâches appliquée aux deux niveaux | Vérifié |
| Refus distinct du refus pour rôle insuffisant | Vérifié |
| Validation DR clôturant sans aiguillage | Vérifié |
| Retour ramenant toujours à l'agent | Vérifié |
| Motif obligatoire et non vide | Vérifié |
| Reprise et resoumission fonctionnelles | Vérifié |
| Circuit complet testé sur les deux branches | Vérifié |
| Six endpoints du contrat | Vérifié |
| CLAUDE.md complété des six décisions du Sprint 4 | Fait |

## 12. Commit

```bash
git add .
git commit -m "sprint-4.4: second niveau, retour motive et separation des taches

- Validation du directeur reseau avec cloture
- Retour ramenant toujours a l'agent, motif obligatoire
- Separation des taches appliquee aux deux niveaux
- Circuit complet teste sur les deux branches du seuil

Refs: RG-10, RG-11, RG-12, US-10, US-11, CT-16, CT-17"
```

---

**Fin du Sprint 4.4 et du Sprint 4.** Le circuit de validation est complet. En attente de validation avant le Sprint 5, transmission comptable.
