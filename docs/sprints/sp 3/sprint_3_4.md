# SPRINT 3.4

## Consolidation mensuelle et clôture du Sprint 3

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Exposer l'état consolidé du mois, consommé par le service Workflow |
| **Livrable** | Endpoint de consolidation, convention d'appel, clôture du sprint |
| **Durée** | Une journée |
| **Prérequis** | Sprint 3.3 validé et commité |
| **Sprint suivant** | 4.1, domaine du workflow |

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Consolidation et totaux (étapes 2-3) | Opus | Élevé |
| Convention d'appel et clôture (étapes 4-6) | Sonnet | Moyen |

**Changement manuel à l'étape 4.** Les deux premières étapes portent RG-06 et le calcul du montant total, qui commandera l'aiguillage au Sprint 4. Ensuite, revenir en Sonnet effort moyen.

## 2. Outil de cartographie

Utilisation obligatoire à l'étape 5, pour la clôture du Sprint 3.

```
py -3.14 -m graphify update .
```

## 3. Contexte

Dernier sous-sprint du service Saisie. Les trois précédents ont permis à l'agent d'enregistrer ses lignes jour après jour. Celui-ci les agrège.

RG-06 dit que les fiches journalières sont consolidées automatiquement en un état mensuel par unité. Cette règle se partage entre deux services : **le service Saisie fournit les données consolidées, le service Workflow porte le montant total sur le processus.** Ce sous-sprint construit la moitié qui revient à la Saisie.

Le montant total mérite une attention particulière : c'est lui qui déterminera, au Sprint 4, si un état passe directement en clôture ou monte au Directeur Réseau. Une erreur d'agrégation ici produirait un aiguillage faux, avec une conséquence de contrôle interne.

Ce sous-sprint clôt le Sprint 3.

## 4. Objectifs

- Service de consolidation produisant l'état mensuel d'un processus
- Calcul du montant total et des sous-totaux par journée
- Endpoint de consultation de l'état consolidé
- Convention d'appel documentée pour le service Workflow
- Vérification de bout en bout du service Saisie et clôture du Sprint 3

## 5. Règles et stories concernées

| Référence | Objet |
|---|---|
| RG-06 | Consolidation mensuelle automatique par unité |
| US-06 | État consolidé sans ressaisie, avec jour, bénéficiaires, montants, sessions et totaux |
| CT-11 | L'état mensuel consolide les jours, bénéficiaires, montants, sessions et totaux |

## 6. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis le fichier CLAUDE.md a la racine, section 6, regle
RG-06. Confirme en 3 lignes ce qu'elle impose et quelle partie revient
au service Saisie.

CONTEXTE DE CETTE SESSION : Sprint 3.4, dernier sous-sprint du
service Saisie. Les fiches et les lignes existent depuis les
sous-sprints 3.1 a 3.3. On construit maintenant la consolidation
mensuelle, que le service Workflow consommera au Sprint 4.
SERVICE CONCERNE : service-saisie uniquement.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Chaque methode publique vient avec ses tests.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : propose la forme de l'etat consolide retourne par
le service. Il doit permettre au service Workflow de calculer le
montant total et a l'interface d'afficher le detail par journee.
Montre-moi la structure du DTO avant tout code, en precisant ce qui
est detaille et ce qui est agrege.
```

### Étape 2. Service de consolidation

```
Une fois la forme validee, implemente le service de consolidation :

Entree : identifiant du processus.
Sortie : l'etat consolide.

Contenu attendu, conforme a US-06 : pour chaque journee saisie, les
lignes avec beneficiaire, nature, session et montant ; le sous-total
de la journee ; et le total du mois.

Le calcul du total se fait sur les montants deja figes dans les
lignes, jamais par une nouvelle resolution de grille. Une ligne
saisie en juillet garde le montant de la grille de juillet, meme si
la grille a change depuis. Explique-moi ou tu places ce calcul et
pourquoi.

Montre le service puis ses tests.
```

### Étape 3. Cohérence du montant total

```
Le montant total conditionne l'aiguillage au Sprint 4 : au plus
100 000 XAF, cloture directe ; au-dela, passage au Directeur Reseau.

Deux points a verifier explicitement :
1. Le total porte sur toutes les fiches du processus, sans en oublier
   ni en compter deux fois.
2. Le type utilise ne perd pas de precision. Les montants sont des
   entiers en FCFA : aucun flottant nulle part dans la chaine de
   calcul.

Ecris un test qui verifie le total sur un jeu de donnees couvrant
plusieurs journees et plusieurs beneficiaires, avec un total attendu
calcule a la main dans le test.

Montre le test.
```

### Étape 4. Endpoint de consolidation

```
Cree l'endpoint de consultation de l'etat consolide.

Le contrat d'api decrit GET /processus/{id}/etat cote service
Workflow, qui s'appuiera sur cet appel. Cote service Saisie, propose
un chemin coherent avec les quatre endpoints existants et signale-moi
qu'il s'agit d'un endpoint interne, non decrit au contrat public.

Verifie la portee d'acces : un agent ne consulte que les etats des
unites qui lui sont accessibles.

Montre le controleur.
```

### Étape 5. Convention d'appel et vérification

```
Redige dans docs/ la note de convention destinee au service Workflow :
chemin, forme de la reponse, comportement en cas de processus sans
aucune fiche, comportement si le service Saisie est injoignable.

Puis lance la cartographie et verifie :
- que service-saisie ne depend d'aucun autre service autrement que
  par appel d'api
- que les cinq endpoints publics du contrat existent, plus le seul
  endpoint interne de consolidation
- qu'aucun montant n'est calcule ailleurs que dans le service de
  consolidation

Liste les ecarts sans les corriger.
```

### Étape 6. Clôture du Sprint 3

```
Mets a jour CLAUDE.md avec les decisions prises pendant le Sprint 3 :
1. Le critere d'identification d'un beneficiaire deja connu
   (Sprint 3.1).
2. La convention de rattachement au processus mensuel (Sprint 3.1).
3. Le comportement en cas de service Grilles injoignable et la
   politique de reessai (Sprint 3.2).
4. La frequence de verification du caractere modifiable et le code
   d'erreur retenu (Sprint 3.3).
5. Le partage de RG-06 entre les services Saisie et Workflow, et le
   chemin de l'endpoint interne de consolidation.

Propose les ajouts section par section, sans reecrire le fichier.
```

## 7. Fichiers à créer

| Chemin | Nature |
|---|---|
| `service-saisie/.../api/dto/EtatConsolideResponse.java` | DTO de sortie |
| `service-saisie/.../api/dto/JourneeConsolideeResponse.java` | Détail par journée |
| `service-saisie/.../application/ConsolidationService.java` | RG-06 côté Saisie |
| `service-saisie/.../api/ConsolidationController.java` | Endpoint interne |
| `service-saisie/src/test/.../ConsolidationServiceTest.java` | Tests |
| `docs/appel-consolidation.md` | Convention pour le service Workflow |
| `CLAUDE.md` | Décisions du Sprint 3 |

## 8. Commandes terminal

```bash
cd afb-rations/backend

mvn -pl service-saisie test
mvn -pl service-saisie spring-boot:run
```

Vérification manuelle, après avoir saisi plusieurs lignes sur plusieurs journées :

```bash
curl -H "Authorization: Bearer <jeton_agent>" \
  http://localhost:8082/saisie/processus/1/etat
```

Contrôle du total en base :

```sql
\c rations_saisie
SELECT f.date_jour, COUNT(l.id) AS lignes, SUM(l.montant_applique) AS sous_total
FROM fiche_journaliere f
JOIN ligne_prestation l ON l.id_fiche_journaliere = f.id
WHERE f.id_processus = 1
GROUP BY f.date_jour
ORDER BY f.date_jour;
```

Attendu : la somme des sous-totaux correspond au total retourné par l'endpoint.

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| `mvn -pl service-saisie test` | BUILD SUCCESS, aucune régression |
| Total sur jeu de données multi-journées | Égal au total calculé à la main |
| Total égal à la somme SQL des montants | Vérifié |
| Aucun flottant dans la chaîne de calcul | Vérifié |
| Montants figés, non recalculés depuis la grille | Vérifié |
| Processus sans aucune fiche | Réponse conforme à la convention |
| Consultation hors portée | 403 |
| Cinq endpoints publics plus un interne | Vérifié |
| Aucune dépendance hors appel d'API | Vérifié |

## 10. Points de vigilance

- **Le montant total commande l'aiguillage au Sprint 4.** Une erreur d'un franc autour du seuil de 100 000 XAF envoie un dossier au mauvais niveau de validation. Le test à total calculé à la main n'est pas une formalité.
- Les montants sont des entiers en FCFA. Un flottant introduit à un seul endroit de la chaîne suffit à produire un écart d'arrondi.
- Le total se calcule sur les montants figés dans les lignes, jamais par une nouvelle résolution de grille. Recalculer donnerait un total différent dès qu'une grille a changé, et rendrait un état validé instable dans le temps.
- Une jointure mal posée peut compter deux fois une ligne ou en oublier. La vérification croisée avec la requête SQL directe permet de le détecter.
- L'endpoint de consolidation est interne. Il ne figure pas au contrat d'API public, qui expose la consolidation via le service Workflow.
- Le Sprint 3 se clôt ici. Vérifier que le service Saisie n'expose que ce qui est prévu, sans endpoint créé par anticipation pour le Sprint 4.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Forme de l'état consolidé validée avant codage | Fait |
| Consolidation conforme à US-06 | Vérifié |
| Total exact, prouvé par un test à valeur calculée | Vérifié |
| Aucun flottant dans le calcul | Vérifié |
| Montants figés, jamais recalculés | Vérifié |
| Portée d'accès appliquée | Vérifié |
| Note de convention rédigée pour le service Workflow | Fait |
| Comportement sur processus vide tranché | Fait |
| CLAUDE.md complété des cinq décisions du Sprint 3 | Fait |
| Aucun endpoint créé par anticipation | Vérifié |

## 12. Commit

```bash
git add .
git commit -m "sprint-3.4: consolidation mensuelle

- Etat consolide par journee avec sous-totaux et total du mois
- Calcul sur les montants figes, sans nouvelle resolution de grille
- Convention d'appel documentee pour le service workflow
- Cloture du sprint 3

Refs: RG-06, US-06, CT-11"
```

---

**Fin du Sprint 3.4 et du Sprint 3.** Le service Saisie est complet. En attente de validation avant le Sprint 4, service Workflow et validation.
