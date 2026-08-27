# SPRINT 4.2

## Soumission et pièce jointe signée

*Module Paiement des Rations et du Transport de la Garde Armée*

| | |
|---|---|
| **Objet** | Permettre à l'agent de soumettre son état mensuel, avec génération du document et signature |
| **Livrable** | Endpoint de soumission, génération PDF par iText, première signature numérique |
| **Durée** | Une journée |
| **Prérequis** | Sprint 4.1 validé et commité |
| **Sprint suivant** | 4.3, validation et aiguillage au seuil |

## 1. Configuration recommandée

| Étape | Modèle | Effort |
|---|---|---|
| Contrôle de complétude (étapes 2-3) | Opus | Élevé |
| Génération PDF et signature (étapes 4-5) | Sonnet | Moyen |
| Soumission et tests (étapes 6-7) | Opus | Élevé |

**Deux changements manuels.** Passer en Sonnet à l'étape 4, la génération documentaire relevant de l'outillage plus que de la règle métier, puis revenir en Opus à l'étape 6 pour l'orchestration de la soumission.

## 2. Outil de cartographie

Facultatif ici. Le périmètre reste circonscrit au service Workflow et à son appel déjà établi vers la Saisie.

## 3. Contexte

Le processus existe et l'agent y a rattaché ses fiches. Ce sous-sprint lui permet de le soumettre.

Trois choses se produisent à la soumission. Le système **vérifie la complétude** des informations et bloque une soumission incomplète. Il **génère la pièce jointe**, un document PDF unique par processus, qui sera enrichi des signatures successives jusqu'à la clôture. Et il **appose la signature électronique de l'agent**, première des trois possibles.

Un point de conception à ne pas perdre de vue : la pièce jointe est unique par processus, garantie par une contrainte d'unicité en base depuis le Sprint 0.5. Elle n'est pas régénérée à chaque validation, elle est enrichie. Un document par étape aurait multiplié les pièces et rendu la traçabilité illisible.

## 4. Objectifs

- Contrôle de complétude avant soumission
- Génération du document PDF de l'état mensuel par iText 8
- Mécanisme de signature numérique, réutilisable aux niveaux suivants
- `POST /processus/{id}/soumission` : soumission et transfert au Chef d'Unité
- Enregistrement de la première étape de workflow
- Tests couvrant la soumission nominale et le blocage sur état incomplet

## 5. Règles et stories concernées

| Référence | Objet |
|---|---|
| RG-07 | Validation séquentielle : la soumission ouvre le circuit |
| RG-09 | Signature numérique horodatée à chaque validation |
| US-07 | Consultation puis soumission de l'état mensuel |
| CT-12 | Soumission d'un état complet : signature, date, auteur, transfert au DA |
| CT-13 | Soumission d'un état incomplet : bloquée, manques listés |

## 6. Étapes d'implémentation

### Étape 1. Ouvrir la session

```
Tu es mon assistant de developpement pour le projet de digitalisation
du paiement des rations et du transport de la garde armee d'Afriland
First Bank.

AVANT TOUT : lis le fichier CLAUDE.md a la racine, sections 4 et 6.
Confirme en 3 lignes ce que dit le dictionnaire sur piece_jointe et
ce qu'impose RG-09.

CONTEXTE DE CETTE SESSION : Sprint 4.2, soumission et piece jointe.
Le processus et la machine a etats existent depuis le Sprint 4.1. On
implemente la soumission par l'agent, avec generation du document et
premiere signature.

Point de conception : la piece jointe est UNIQUE par processus. Elle
est generee a la soumission puis ENRICHIE des signatures suivantes,
jamais regeneree ni dupliquee.
SERVICE CONCERNE : service-workflow uniquement.

METHODE DE TRAVAIL :
- Un fichier a la fois. Tu montres, j'approuve, tu continues.
- Chaque methode publique vient avec ses tests.
- Si un choix n'est pas couvert par CLAUDE.md, tu poses la question.

PREMIERE ACTION : avant tout code, definis avec moi ce qu'est un etat
complet. Le cahier des charges dit que le systeme verifie la
completude des informations, sans preciser. Propose-moi une liste de
controles candidats et leur justification metier : au moins une ligne
saisie, aucune ligne sans montant, aucun beneficiaire sans compte
courant, autre chose que tu identifies. J'arbitre ce qui entre dans
le controle.
```

### Étape 2. Contrôle de complétude

```
Une fois la liste arbitree, implemente le service de controle de
completude.

Il s'appuie sur l'etat consolide obtenu du service Saisie, via
l'interface du Sprint 4.1.

En cas d'echec, il ne retourne pas un simple booleen : il retourne la
liste des manques constates, pour que l'interface les affiche a
l'agent. Un message generique du type etat incomplet obligerait
l'agent a chercher lui-meme.

Montre le service puis ses tests.
```

### Étape 3. Documentation du contrôle

```
Redige dans docs/ une note listant les controles de completude
retenus et ceux ecartes, avec la raison de chaque choix. Signale-moi
qu'elle doit rejoindre CLAUDE.md a la cloture du Sprint 4.

Cette note evitera qu'un futur sprint reinterprete la completude
differemment.
```

### Étape 4. Génération du document

**Étape en Sonnet, effort moyen.**

```
Cree le service de generation du document PDF avec iText 8.

Contenu attendu, conforme a US-06 et au document de conception :
en-tete avec le logo Afriland, la periode et l'unite, puis le detail
par journee avec beneficiaire, nature, session et montant, les
sous-totaux journaliers et le total du mois, et un espace pour les
signatures.

La charte graphique du document maitre section 8.2 s'applique :
sobriete, rouge reserve aux accents.

Le fichier est stocke selon le chemin porte par piece_jointe. Propose
une convention de nommage incluant l'unite et la periode, et
montre-la moi.

Montre le service.
```

### Étape 5. Mécanisme de signature

```
Cree le mecanisme de signature numerique, portant RG-09.

Il sera appele trois fois au maximum sur un meme processus : agent,
puis Chef d'Unite, puis Directeur Reseau. Il doit donc etre concu
pour enrichir un document existant, pas pour le regenerer.

Chaque signature enregistre le nom de l'acteur, son role, la date et
l'heure. Le compteur nombre_signatures de piece_jointe est incremente.

Question a trancher avant d'ecrire : parle-t-on d'une signature
electronique au sens cryptographique, ou d'une mention signee
horodatee apposee sur le document ? Les specifications disent
signature numerique automatique sans preciser. Les deux lectures ont
des consequences tres differentes en cout et en valeur probante.
Presente-les, j'arbitre.

Montre le service.
```

### Étape 6. Soumission

**Retour en Opus, effort élevé.**

```
Cree le service et l'endpoint POST /processus/{id}/soumission :

1. Verifie l'habilitation : role AGENT_UNITE, portee d'acces sur
   l'unite du processus.
2. Verifie que le statut permet la soumission : EN_COURS_SAISIE
   uniquement.
3. Controle la completude. En cas d'echec, refuse en listant les
   manques.
4. Calcule et enregistre le montant total, a partir de l'etat
   consolide.
5. Genere la piece jointe et y appose la signature de l'agent.
6. Cree l'etape de workflow SOUMISSION_AGENT, statut VALIDEE, avec
   la date et l'acteur.
7. Fait transiter le processus vers SOUMIS puis EN_ATTENTE_DA.
8. Trace l'operation dans le journal d'audit.

Respecte l'ordre du document maitre section 7.3. Le montant total
enregistre ici commandera l'aiguillage au sous-sprint 4.3 : sa
justesse est essentielle.

Montre le service puis le controleur.
```

### Étape 7. Tests

```
Ecris les tests de ce sous-sprint.

Soumission :
1. soumission nominale d'un etat complet : statut EN_ATTENTE_DA,
   montant total enregistre, piece jointe generee et signee, etape
   creee
2. soumission d'un etat incomplet : refusee, manques listes
3. soumission d'un processus deja soumis : refusee
4. soumission d'un processus cloture : refusee
5. soumission par un autre role que AGENT_UNITE : 403
6. soumission hors portee d'acces : 403
7. le montant total enregistre est egal a celui de l'etat consolide

Piece jointe et signature :
8. une seule piece jointe est creee par processus
9. le compteur de signatures vaut un apres la soumission
10. une seconde soumission ne cree pas une seconde piece jointe

Le test 10 protege la contrainte d'unicite posee au Sprint 0.5.

Montre les fichiers de test.
```

## 7. Fichiers à créer

| Chemin | Nature |
|---|---|
| `service-workflow/.../domaine/PieceJointe.java` | Entité |
| `service-workflow/.../infrastructure/PieceJointeRepository.java` | Repository |
| `service-workflow/.../application/CompletudeService.java` | Contrôle de complétude |
| `service-workflow/.../application/DocumentService.java` | Génération PDF |
| `service-workflow/.../application/SignatureService.java` | RG-09 |
| `service-workflow/.../application/SoumissionService.java` | Orchestration |
| `service-workflow/.../api/ProcessusController.java` | Ajout de l'endpoint |
| `service-workflow/src/test/...` | Tests |
| `docs/controles-completude.md` | Note de l'étape 3 |

## 8. Commandes terminal

```bash
cd afb-rations/backend

mvn -pl service-workflow test
mvn -pl service-workflow spring-boot:run
```

```bash
curl -X POST -H "Authorization: Bearer <jeton_agent>" \
  http://localhost:8084/processus/1/soumission
```

Contrôle en base :

```sql
\c rations_workflow
SELECT statut, montant_total FROM processus_mensuel WHERE id = 1;
SELECT nom_fichier, nombre_signatures FROM piece_jointe WHERE id_processus = 1;
SELECT ordre_etape, nom_etape, statut_etape FROM etape_workflow WHERE id_processus = 1;
```

## 9. Tests et vérifications

| Vérification | Attendu |
|---|---|
| `mvn -pl service-workflow test` | BUILD SUCCESS |
| Dix tests du sous-sprint | Tous passants |
| Soumission nominale | EN_ATTENTE_DA, montant enregistré |
| État incomplet | Refus avec liste des manques |
| Montant total | Égal au total de l'état consolidé |
| Pièce jointe | Une seule par processus |
| Compteur de signatures | Vaut un après soumission |
| Seconde soumission | Refusée, aucune seconde pièce jointe |
| Fichier PDF | Généré, lisible, conforme à la charte |
| Étape SOUMISSION_AGENT | Créée avec date et acteur |

## 10. Points de vigilance

- **Une seule pièce jointe par processus.** La contrainte d'unicité en base l'impose, mais le code doit l'appliquer en amont : une tentative de seconde génération produirait une erreur technique illisible.
- Le montant total enregistré à la soumission commande l'aiguillage au sous-sprint suivant. S'il est recalculé différemment à ce moment-là, un dossier pourrait changer de niveau de validation entre deux consultations.
- Le contrôle de complétude doit lister les manques, pas seulement échouer. C'est une exigence d'ergonomie du cahier des charges, et le critère CT-13 le vérifie.
- La signature enrichit le document, elle ne le régénère pas. Régénérer à chaque étape ferait perdre les signatures précédentes.
- La nature exacte de la signature n'est pas tranchée par les spécifications. Ne pas supposer : une signature cryptographique et une mention horodatée n'ont ni le même coût ni la même valeur probante.
- Ne pas implémenter la validation ni l'aiguillage ici.

## 11. Critères de validation

| Critère | Statut attendu |
|---|---|
| Contrôles de complétude arbitrés et documentés | Fait |
| Manques listés en cas de refus | Vérifié |
| Nature de la signature tranchée | Fait |
| Convention de nommage du fichier validée | Fait |
| PDF généré conforme à la charte | Vérifié |
| Pièce jointe unique par processus | Vérifié |
| Montant total cohérent avec l'état consolidé | Vérifié |
| Étape de soumission créée | Vérifié |
| Dix tests passants | Vérifié |
| Aucun endpoint de validation créé | Vérifié |

## 12. Commit

```bash
git add .
git commit -m "sprint-4.2: soumission et piece jointe signee

- Controle de completude listant les manques
- Generation du document mensuel par itext
- Signature de l'agent, piece jointe unique enrichie
- Transfert au chef d'unite

Refs: RG-07, RG-09, US-07, CT-12, CT-13"
```

---

**Fin du Sprint 4.2** — en attente de validation avant le Sprint 4.3
